package water.api;

import water.*;
import water.exceptions.*;
import water.init.NodePersistentStorage;
import water.nbhm.NonBlockingHashMap;
import water.rapids.Assembly;
import water.util.*;

import java.io.*;
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * This is a simple web server which accepts HTTP requests and routes them
 * to methods in Handler classes for processing.  Schema classes are used to
 * provide a more stable external JSON interface while allowing the implementation
 * to evolve rapidly.  As part of request handling the framework translates
 * back and forth between the stable external representation of objects (Schema)
 * and the less stable internal classes.
 * <p>
 * Request <i>routing</i> is done by searching a list of registered
 * handlers, in order of registration, for a handler whose path regex matches
 * the request URI and whose HTTP method (GET, POST, DELETE...) matches the
 * request's method.  If none is found an HTTP 404 is returned.
 * <p>
 * A Handler class is parametrized by the kind of Schema that it accepts
 * for request handling, as well as the internal implementation class (Iced
 * class) that the Schema translates from and to.  Handler methods are allowed to
 * return other Schema types than in the  type parameter if that makes
 * sense for a given request.  For example, a prediction (scoring) call on
 * a Model might return a Frame schema.
 * <p>
 * When an HTTP request is accepted the framework does the following steps:
 * <ol>
 *   <li>searches the registered handler methods for a matching URL and HTTP method</li>
 *   <li>collects any parameters which are captured from the URI and adds them to the map of HTTP query parameters</li>
 *   <li>creates an instance of the correct Handler class and calls handle() on it, passing the version, route and params</li>
 *   <li>Handler.handle() creates the correct Schema object given the version and calls fillFromParms(params) on it</li>
 *   <li>calls schema.createImpl() to create a schema-independent "back end" object</li>
 *   <li>dispatches to the handler method, passing in the schema-independent impl object and returning the result Schema object</li>
 * </ol>
 *
 * @see water.api.Handler
 * @see water.api.Schema
 * @see water.api.RegisterV3Api
 */
public class RequestServer extends NanoHTTPD {

  // Returned in REST API responses as X-h2o-rest-api-version-max
  public static final int H2O_REST_API_VERSION = 3;

  // RequestServer singleton
  public static RequestServer SERVER;
  private RequestServer() {}

  private static RouteTree routesTree = new RouteTree("");
  private static ArrayList<Route> routesList = new ArrayList<>(150);
  private static boolean registrationOpen = true;

  public static int numRoutes() { return routesList.size(); }
  public static ArrayList<Route> routes() { return routesList; }
  public static Route lookupRoute(RequestUri uri) { return routesTree.lookup(uri, null); }

  /**
   * Calculates number of routes having the specified version.
   */
  public static int numRoutes(int version) {
    int count = 0;
    for (Route route : routesList)
      if (route.getVersion() == version)
        count++;
    return count;
  }

  //------ Route Registration ------------------------------------------------------------------------------------------

  /**
   * Register an HTTP request handler method for a given URL pattern, with parameters extracted from the URI.
   * <p>
   * URIs which match this pattern will have their parameters collected from the path and from the query params
   *
   * @param api_name suggested method name for this endpoint in the external API library. These names should be
   *                 unique. If null, the api_name will be created from the class name and the handler method name.
   * @param method_uri combined method / url pattern of the request, e.g.: "GET /3/Jobs/{job_id}"
   * @param handler_class class which contains the handler method
   * @param handler_method name of the handler method
   * @param summary help string which explains the functionality of this endpoint
   * @see Route
   * @see water.api.RequestServer
   * @return the Route for this request
   */
  public static Route register(
      String api_name, String method_uri, Class<? extends Handler> handler_class, String handler_method, String summary
  ) {
    String[] spl = method_uri.split(" ");
    assert spl.length == 2 : "Unexpected method_uri parameter: " + method_uri;
    return register(api_name, spl[0], spl[1], handler_class, handler_method, summary, HandlerFactory.DEFAULT);
  }


  /**
   * @param api_name suggested method name for this endpoint in the external API library. These names should be
   *                 unique. If null, the api_name will be created from the class name and the handler method name.
   * @param http_method HTTP verb (GET, POST, DELETE) this handler will accept
   * @param url url path, possibly containing placeholders in curly braces, e.g: "/3/DKV/{key}"
   * @param handler_class class which contains the handler method
   * @param handler_method name of the handler method
   * @param summary help string which explains the functionality of this endpoint
   * @param handler_factory factory to create instance of handler (used by Sparkling Water)
   * @return the Route for this request
   */
  public static Route register(
      String api_name,
      String http_method,
      String url,
      Class<? extends Handler> handler_class,
      String handler_method,
      String summary,
      HandlerFactory handler_factory
  ) {
    assert registrationOpen : "finalizeRegistration() has been called, cannot register any additional routes";
    assert api_name != null : "api_name should not be null";
    try {
      RequestUri uri = new RequestUri(http_method, url);
      Route route = new Route(uri, api_name, summary, handler_class, handler_method, handler_factory);
      routesTree.add(uri, route);
      routesList.add(route);
      return route;
    } catch (MalformedURLException e) {
      throw H2O.fail(e.getMessage());
    }
  }

  /**
   * This method must be called after all requests have been registered.
   */
  public static void finalizeRegistration() {
    assert registrationOpen : "finalizeRegistration() should not be called more than once";
    registrationOpen = false;

    SchemaServer.registerAllSchemasIfNecessary();

    // Need a stub RequestServer to handle calls to serve() from Jetty.
    // But no threads are started here anymore.
    SERVER = new RequestServer();

    H2O.getJetty().acceptRequests();
  }



  //------ Handling Requests -------------------------------------------------------------------------------------------

  /**
   * Top-level dispatch based on the URI.
   */
  @Override
  public Response serve(String url, String method, Properties header, Properties parms) {
    try {
      // Jack priority for user-visible requests
      Thread.currentThread().setPriority(Thread.MAX_PRIORITY - 1);

      RequestType type = RequestType.requestType(url);
      RequestUri uri = new RequestUri(method, url);

      // Log the request
      maybeLogRequest(uri, header, parms);

      // For certain "special" requests that produce non-JSON payloads we require special handling.
      Response special = maybeServeSpecial(uri);
      if (special != null) return special;

      // Determine the Route corresponding to this request, and also fill in {parms} with the path parameters
      Route route = routesTree.lookup(uri, parms);

      //----- DEPRECATED API handling ------------
      // These APIs are broken, because they lead users to create invalid URLs. For example the endpoint
      //   /3/Frames/{frameid}/export/{path}/overwrite/{force}
      // is invalid, because it leads to URLs like this:
      //   /3/Frames/predictions_9bd5_GLM_model_R_1471148_36_on_RTMP_sid_afec_27/export//tmp/pred.csv/overwrite/TRUE
      // Here both the {frame_id} and {path} usually contain "/" (making them non-tokens), they may contain other
      // special characters not valid within URLs (for example if filename is not in ASCII); finally the use of strings
      // to represent booleans creates ambiguities: should I write "true", "True", "TRUE", or perhaps "1"?
      //
      // TODO These should be removed as soon as possible...
      if (url.startsWith("/3/Frames/")) {
        // /3/Frames/{frame_id}/export/{path}/overwrite/{force}
        if ((url.toLowerCase().endsWith("/overwrite/true") || url.toLowerCase().endsWith("/overwrite/false")) && url.contains("/export/")) {
          int i = url.indexOf("/export/");
          boolean force = url.toLowerCase().endsWith("true");
          parms.put("frame_id", url.substring(10, i));
          parms.put("path", url.substring(i+8, url.length()-15-(force?0:1)));
          parms.put("force", force? "true" : "false");
          route = findRouteByApiName("exportFrame_deprecated");
        }
        // /3/Frames/{frame_id}/export
        else if (url.endsWith("/export")) {
          parms.put("frame_id", url.substring(10, url.length()-7));
          route = findRouteByApiName("exportFrame");
        }
        // /3/Frames/{frame_id}/columns/{column}/summary
        else if (url.endsWith("/summary") && url.contains("/columns/")) {
          int i = url.indexOf("/columns/");
          parms.put("frame_id", url.substring(10, i));
          parms.put("column", url.substring(i+9, url.length()-8));
          route = findRouteByApiName("frameColumnSummary");
        }
        // /3/Frames/{frame_id}/columns/{column}/domain
        else if (url.endsWith("/domain") && url.contains("/columns/")) {
          int i = url.indexOf("/columns/");
          parms.put("frame_id", url.substring(10, i));
          parms.put("column", url.substring(i+9, url.length()-7));
          route = findRouteByApiName("frameColumnDomain");
        }
        // /3/Frames/{frame_id}/columns/{column}
        else if (url.contains("/columns/")) {
          int i = url.indexOf("/columns/");
          parms.put("frame_id", url.substring(10, i));
          parms.put("column", url.substring(i+9));
          route = findRouteByApiName("frameColumn");
        }
        // /3/Frames/{frame_id}/summary
        else if (url.endsWith("/summary")) {
          parms.put("frame_id", url.substring(10, url.length()-8));
          route = findRouteByApiName("frameSummary");
        }
        // /3/Frames/{frame_id}/columns
        else if (url.endsWith("/columns")) {
          parms.put("frame_id", url.substring(10, url.length()-8));
          route = findRouteByApiName("frameColumns");
        }
        // /3/Frames/{frame_id}
        else {
          parms.put("frame_id", url.substring(10));
          route = findRouteByApiName(method.equals("DELETE")? "deleteFrame" : "frame");
        }
      }
      //------------------------------------------

      if (route == null) {
        // if the request is not known, treat as resource request, or 404 if not found
        if (uri.isGetMethod())
          return getResource(type, url);
        else
          return response404(method + " " + url, type);

      } else {
        Schema response = route._handler.handle(uri.getVersion(), route, parms);
        PojoUtils.filterFields(response, (String)parms.get("_include_fields"), (String)parms.get("_exclude_fields"));
        return serveSchema(response, type);
      }

    }
    catch (H2OFailException e) {
      H2OError error = e.toH2OError(url);
      Log.fatal("Caught exception (fatal to the cluster): " + error.toString());
      throw H2O.fail(serveError(error).toString());
    }
    catch (H2OModelBuilderIllegalArgumentException e) {
      H2OModelBuilderError error = e.toH2OError(url);
      Log.warn("Caught exception: " + error.toString());
      return serveSchema(new H2OModelBuilderErrorV3().fillFromImpl(error), RequestType.json);
    }
    catch (H2OAbstractRuntimeException e) {
      H2OError error = e.toH2OError(url);
      Log.warn("Caught exception: " + error.toString());
      return serveError(error);
    }
    // TODO: kill the server if someone called H2O.fail()
    catch (Exception e) {
      // make sure that no Exception is ever thrown out from the request
      H2OError error = new H2OError(e, url);
      // some special cases for which we return 400 because it's likely a problem with the client request:
      if (e instanceof IllegalArgumentException || e instanceof FileNotFoundException || e instanceof MalformedURLException)
        error._http_status = HttpResponseStatus.BAD_REQUEST.getCode();
      Log.err("Caught exception: " + error.toString());
      return serveError(error);
    }
  }

  /**
   * Log the request (unless it's an overly common one).
   */
  private void maybeLogRequest(RequestUri uri, Properties header, Properties parms) {
    String url = uri.getUrl();
    if (url.endsWith(".css") ||
        url.endsWith(".js") ||
        url.endsWith(".png") ||
        url.endsWith(".ico")) return;

    String[] path = uri.getPath();
    if (path[2].equals("Cloud") ||
        path[2].equals("Jobs") && uri.isGetMethod() ||
        path[2].equals("Log") ||
        path[2].equals("Progress") ||
        path[2].equals("Typeahead") ||
        path[2].equals("WaterMeterCpuTicks")) return;

    Log.info(uri + ", parms: " + parms);
    GAUtils.logRequest(url, header);
  }



  //------ Lookup tree for Routes --------------------------------------------------------------------------------------

  private static class RouteTree {

    private String root;
    private boolean isWildcard;
    private HashMap<String, RouteTree> branches;
    private Route leaf;

    public RouteTree(String token) {
      isWildcard = isWildcardToken(token);
      root = isWildcard ? "*" : token;
      branches = new HashMap<>();
      leaf = null;
    }

    public void add(RequestUri uri, Route route) {
      String[] path = uri.getPath();
      addByPath(path, 0, route);
    }

    public Route lookup(RequestUri uri, Properties parms) {
      if (!uri.isApiUrl()) return null;
      String[] path = uri.getPath();
      ArrayList<String> path_params = new ArrayList<>(3);

      Route route = this.lookupByPath(path, 0, path_params);

      // Fill in the path parameters
      if (parms != null && route != null) {
        String[] param_names = route._path_params;
        assert path_params.size() == param_names.length;
        for (int i = 0; i < param_names.length; i++)
          parms.put(param_names[i], path_params.get(i));
      }
      return route;
    }

    private void addByPath(String[] path, int index, Route route) {
      if (index + 1 < path.length) {
        String nextToken = isWildcardToken(path[index+1])? "*" : path[index+1];
        if (!branches.containsKey(nextToken))
          branches.put(nextToken, new RouteTree(nextToken));
        branches.get(nextToken).addByPath(path, index + 1, route);
      } else {
        assert leaf == null : "Duplicate path encountered: " + Arrays.toString(path);
        leaf = route;
      }
    }

    private Route lookupByPath(String[] path, int index, ArrayList<String> path_params) {
      assert isWildcard || root.equals(path[index]);
      if (index + 1 < path.length) {
        String nextToken = path[index+1];
        // First attempt an exact match
        if (branches.containsKey(nextToken)) {
          Route route = branches.get(nextToken).lookupByPath(path, index+1, path_params);
          if (route != null) return route;
        }
        // Then match against a wildcard
        if (branches.containsKey("*")) {
          path_params.add(path[index+1]);
          Route route = branches.get("*").lookupByPath(path, index + 1, path_params);
          if (route != null) return route;
          path_params.remove(path_params.size() - 1);
        }
        // If we are at the deepest level of the tree and no match was found, attempt to look for alternative versions.
        // For example, if the user requests /4/About, and we only have /3/About, then we should deliver that version
        // instead.
        if (index == path.length - 2) {
          int v = Integer.parseInt(nextToken);
          for (String key : branches.keySet()) {
            if (Integer.parseInt(key) <= v) {
              // We also create a new branch in the tree to memorize this new route path.
              RouteTree newBranch = new RouteTree(nextToken);
              newBranch.leaf = branches.get(key).leaf;
              branches.put(nextToken, newBranch);
              return newBranch.leaf;
            }
          }
        }
      } else {
        return leaf;
      }
      return null;
    }

    private static boolean isWildcardToken(String token) {
      return token.equals("*") || token.startsWith("{") && token.endsWith("}");
    }
  }

  private Route findRouteByApiName(String apiName) {
    for (Route route : routesList) {
      if (route._api_name.equals(apiName))
        return route;
    }
    return null;
  }

  //------ Handling of Responses ---------------------------------------------------------------------------------------

  /**
   * Handle any URLs that bypass the standard route approach.  This is stuff that has abnormal non-JSON response
   * payloads.
   * @param uri RequestUri object of the incoming request.
   * @return Response object, or null if the request does not require any special handling.
   */
  private Response maybeServeSpecial(RequestUri uri) {
    assert uri != null;

    if (uri.isHeadMethod()) {
      // Blank response used by R's uri.exists("/")
      if (uri.getUrl().equals("/"))
        return new Response(HTTP_OK, MIME_PLAINTEXT, "");
    }
    if (uri.isGetMethod()) {
      // url "/3/Foo/bar" => path ["", "GET", "Foo", "bar", "3"]
      String[] path = uri.getPath();
      if (path[2].equals("")) return redirectToFlow();
      if (path[2].equals("Logs") && path[3].equals("download")) return downloadLogs();
      if (path[2].equals("NodePersistentStorage.bin") && path.length == 6) return downloadNps(path[3], path[4]);
    }
    return null;
  }

  private Response response404(String what, RequestType type) {
    H2ONotFoundArgumentException e = new H2ONotFoundArgumentException(what + " not found", what + " not found");
    H2OError error = e.toH2OError(what);

    Log.warn(error._dev_msg);
    Log.warn(error._values.toJsonString());
    Log.warn((Object[]) error._stacktrace);

    return serveError(error);
  }

  private Response serveSchema(Schema s, RequestType type) {
    // Convert Schema to desired output flavor
    String http_response_header = H2OError.httpStatusHeader(HttpResponseStatus.OK.getCode());

    // If we're given an http response code use it.
    if (s instanceof SpecifiesHttpResponseCode) {
      http_response_header = H2OError.httpStatusHeader(((SpecifiesHttpResponseCode) s).httpStatus());
    }

    // If we've gotten an error always return the error as JSON
    if (s instanceof SpecifiesHttpResponseCode && HttpResponseStatus.OK.getCode() != ((SpecifiesHttpResponseCode) s).httpStatus()) {
      type = RequestType.json;
    }

    switch (type) {
      case html: // return JSON for html requests
      case json:
        return new Response(http_response_header, MIME_JSON, s.toJsonString());
      case xml:
        throw H2O.unimpl("Unknown type: " + type.toString());
      case java:
        if (s instanceof H2OErrorV3) {
          return new Response(http_response_header, MIME_JSON, s.toJsonString());
        }
        if (s instanceof AssemblyV99) {
          Assembly ass = DKV.getGet(((AssemblyV99) s).assembly_id);
          Response r = new Response(http_response_header, MIME_DEFAULT_BINARY, ass.toJava(((AssemblyV99) s).pojo_name));
          r.addHeader("Content-Disposition", "attachment; filename=\""+JCodeGen.toJavaId(((AssemblyV99) s).pojo_name)+".java\"");
          return r;
        } else if (s instanceof StreamingSchema) {
          StreamingSchema ss = (StreamingSchema) s;
          Response r = new StreamResponse(http_response_header, MIME_DEFAULT_BINARY, ss.getStreamWriter());
          // Needed to make file name match class name
          r.addHeader("Content-Disposition", "attachment; filename=\"" + ss.getFilename() + "\"");
          return r;
        } else {
          throw new H2OIllegalArgumentException("Cannot generate java for type: " + s.getClass().getSimpleName());
        }
      default:
        throw H2O.unimpl("Unknown type to serveSchema(): " + type);
    }
  }

  @SuppressWarnings(value = "unchecked")
  private Response serveError(H2OError error) {
    // Note: don't use Schema.schema(version, error) because we have to work at bootstrap:
    return serveSchema(new H2OErrorV3().fillFromImpl(error), RequestType.json);
  }

  private Response redirectToFlow() {
    Response res = new Response(HTTP_REDIRECT, MIME_PLAINTEXT, "");
    res.addHeader("Location", "/flow/index.html");
    return res;
  }

  private Response downloadNps(String categoryName, String keyName) {
    NodePersistentStorage nps = H2O.getNPS();
    AtomicLong length = new AtomicLong();
    InputStream is = nps.get(categoryName, keyName, length);
    Response res = new Response(HTTP_OK, MIME_DEFAULT_BINARY, is);
    res.addHeader("Content-Length", Long.toString(length.get()));
    res.addHeader("Content-Disposition", "attachment; filename=" + keyName + ".flow");
    return res;
  }

  private Response downloadLogs() {
    Log.info("\nCollecting logs.");

    H2ONode[] members = H2O.CLOUD.members();
    byte[][] perNodeZipByteArray = new byte[members.length][];
    byte[] clientNodeByteArray = null;

    for (int i = 0; i < members.length; i++) {
      byte[] bytes;

      try {
        // Skip nodes that aren't healthy, since they are likely to cause the entire process to hang.
        boolean healthy = (System.currentTimeMillis() - members[i]._last_heard_from) < HeartBeatThread.TIMEOUT;
        if (healthy) {
          GetLogsFromNode g = new GetLogsFromNode();
          g.nodeidx = i;
          g.doIt();
          bytes = g.bytes;
        } else {
          bytes = "Node not healthy".getBytes();
        }
      }
      catch (Exception e) {
        bytes = e.toString().getBytes();
      }
      perNodeZipByteArray[i] = bytes;
    }

    if (H2O.ARGS.client) {
      byte[] bytes;
      try {
        GetLogsFromNode g = new GetLogsFromNode();
        g.nodeidx = -1;
        g.doIt();
        bytes = g.bytes;
      }
      catch (Exception e) {
        bytes = e.toString().getBytes();
      }
      clientNodeByteArray = bytes;
    }

    String outputFileStem = getOutputLogStem();
    byte[] finalZipByteArray;
    try {
      finalZipByteArray = zipLogs(perNodeZipByteArray, clientNodeByteArray, outputFileStem);
    }
    catch (Exception e) {
      finalZipByteArray = e.toString().getBytes();
    }

    Response res = new Response(HTTP_OK, MIME_DEFAULT_BINARY, new ByteArrayInputStream(finalZipByteArray));
    res.addHeader("Content-Length", Long.toString(finalZipByteArray.length));
    res.addHeader("Content-Disposition", "attachment; filename=" + outputFileStem + ".zip");
    return res;
  }

  private String getOutputLogStem() {
    String pattern = "yyyyMMdd_hhmmss";
    SimpleDateFormat formatter = new SimpleDateFormat(pattern);
    String now = formatter.format(new Date());
    return "h2ologs_" + now;
  }

  private byte[] zipLogs(byte[][] results, byte[] clientResult, String topDir) throws IOException {
    int l = 0;
    assert H2O.CLOUD._memary.length == results.length : "Unexpected change in the cloud!";
    for (byte[] result : results) l += result.length;
    ByteArrayOutputStream baos = new ByteArrayOutputStream(l);

    // Add top-level directory.
    ZipOutputStream zos = new ZipOutputStream(baos);
    {
      ZipEntry zde = new ZipEntry (topDir + File.separator);
      zos.putNextEntry(zde);
    }

    try {
      // Add zip directory from each cloud member.
      for (int i =0; i<results.length; i++) {
        String filename =
            topDir + File.separator +
                "node" + i + "_" +
                H2O.CLOUD._memary[i].getIpPortString().replace(':', '_').replace('/', '_') +
                ".zip";
        ZipEntry ze = new ZipEntry(filename);
        zos.putNextEntry(ze);
        zos.write(results[i]);
        zos.closeEntry();
      }

      // Add zip directory from the client node.  Name it 'driver' since that's what Sparking Water users see.
      if (clientResult != null) {
        String filename =
            topDir + File.separator +
                "driver.zip";
        ZipEntry ze = new ZipEntry(filename);
        zos.putNextEntry(ze);
        zos.write(clientResult);
        zos.closeEntry();
      }

      // Close the top-level directory.
      zos.closeEntry();
    } finally {
      // Close the full zip file.
      zos.close();
    }

    return baos.toByteArray();
  }

  // cache of all loaded resources
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection") // remove this once TO-DO below is addressed
  private static final NonBlockingHashMap<String,byte[]> _cache = new NonBlockingHashMap<>();

  // Returns the response containing the given uri with the appropriate mime type.
  private Response getResource(RequestType request_type, String url) {
    byte[] bytes = _cache.get(url);
    if (bytes == null) {
      // Try-with-resource
      try (InputStream resource = water.init.JarHash.getResource2(url)) {
        if( resource != null ) {
          try { bytes = toByteArray(resource); }
          catch (IOException e) { Log.err(e); }

          // PP 06-06-2014 Disable caching for now so that the browser
          //  always gets the latest sources and assets when h2o-client is rebuilt.
          // TODO need to rethink caching behavior when h2o-dev is merged into h2o.
          //
          // if (bytes != null) {
          //  byte[] res = _cache.putIfAbsent(url, bytes);
          //  if (res != null) bytes = res; // Racey update; take what is in the _cache
          //}
          //

        }
      } catch( IOException ignore ) { }
    }
    if (bytes == null || bytes.length == 0) // No resource found?
      return response404("Resource " + url, request_type);

    String mime = MIME_DEFAULT_BINARY;
    if (url.endsWith(".css"))
      mime = "text/css";
    else if (url.endsWith(".html"))
      mime = "text/html";
    Response res = new Response(HTTP_OK, mime, new ByteArrayInputStream(bytes));
    res.addHeader("Content-Length", Long.toString(bytes.length));
    return res;
  }

  // Convenience utility
  private static byte[] toByteArray(InputStream is) throws IOException {
    try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[0x2000];
      for (int len; (len = is.read(buffer)) != -1; )
        os.write(buffer, 0, len);
      return os.toByteArray();
    }
  }

}
