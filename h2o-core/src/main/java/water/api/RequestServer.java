package water.api;

import water.DKV;
import water.H2O;
import water.H2OError;
import water.H2OModelBuilderError;
import water.H2ONode;
import water.RPC;
import water.UDPRebooted;
import water.api.schemas3.H2OErrorV3;
import water.api.schemas3.H2OModelBuilderErrorV3;
import water.api.schemas99.AssemblyV99;
import water.exceptions.H2OAbstractRuntimeException;
import water.exceptions.H2OFailException;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.exceptions.H2ONotFoundArgumentException;
import water.init.NodePersistentStorage;
import water.nbhm.NonBlockingHashMap;
import water.rapids.Assembly;
import water.server.ServletUtils;
import water.util.GetLogsFromNode;
import water.util.HttpResponseStatus;
import water.util.IcedHashMapGeneric;
import water.util.JCodeGen;
import water.util.Log;
import water.util.PojoUtils;
import water.util.StringUtils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
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
 * @see water.api.RegisterV3Api
 */
public class RequestServer extends HttpServlet {

  // TODO: merge doGeneric() and serve()
  //       Originally we had RequestServer based on NanoHTTPD. At some point we switched to JettyHTTPD, but there are
  //       still some leftovers from the Nano times.
  // TODO: invoke DatasetServlet, PostFileServlet and NpsBinServlet using standard Routes
  //       Right now those 3 servlets are handling 5 "special" api endpoints from JettyHTTPD, and we also have several
  //       "special" endpoints in maybeServeSpecial(). We don't want them to be special. The Route class should be
  //       made flexible enough to generate responses of various kinds, and then all of those "special" cases would
  //       become regular API calls.
  // TODO: Move JettyHTTPD.sendErrorResponse here, and combine with other error-handling functions
  //       That method is only called from 3 servlets mentioned above, and we want to standardize the way how errors
  //       are handled in different responses.
  //

  // Returned in REST API responses as X-h2o-rest-api-version-max
  // Do not bump to 4 until when the API v4 is fully ready for release.
  public static final int H2O_REST_API_VERSION = 3;

  private static RouteTree routesTree = new RouteTree("");
  private static ArrayList<Route> routesList = new ArrayList<>(150);

  public static int numRoutes() { return routesList.size(); }
  public static ArrayList<Route> routes() { return routesList; }
  public static Route lookupRoute(RequestUri uri) { return routesTree.lookup(uri, null); }

  private static HttpLogFilter[] _filters=new HttpLogFilter[]{defaultFilter()};
  public static void setFilters(HttpLogFilter... filters) {
    _filters=filters;
  }

  /**
   * Some HTTP response status codes
   */
  public static final String
      HTTP_OK = "200 OK",
      HTTP_CREATED = "201 Created",
      HTTP_ACCEPTED = "202 Accepted",
      HTTP_NO_CONTENT = "204 No Content",
      HTTP_PARTIAL_CONTENT = "206 Partial Content",
      HTTP_REDIRECT = "301 Moved Permanently",
      HTTP_NOT_MODIFIED = "304 Not Modified",
      HTTP_BAD_REQUEST = "400 Bad Request",
      HTTP_UNAUTHORIZED = "401 Unauthorized",
      HTTP_FORBIDDEN = "403 Forbidden",
      HTTP_NOT_FOUND = "404 Not Found",
      HTTP_BAD_METHOD = "405 Method Not Allowed",
      HTTP_PRECONDITION_FAILED = "412 Precondition Failed",
      HTTP_TOO_LONG_REQUEST = "414 Request-URI Too Long",
      HTTP_RANGE_NOT_SATISFIABLE = "416 Requested Range Not Satisfiable",
      HTTP_TEAPOT = "418 I'm a Teapot",
      HTTP_THROTTLE = "429 Too Many Requests",
      HTTP_INTERNAL_ERROR = "500 Internal Server Error",
      HTTP_NOT_IMPLEMENTED = "501 Not Implemented",
      HTTP_SERVICE_NOT_AVAILABLE = "503 Service Unavailable";

  /**
   * Common mime types for dynamic content
   */
  public static final String
      MIME_PLAINTEXT = "text/plain",
      MIME_HTML = "text/html",
      MIME_CSS = "text/css",
      MIME_JSON = "application/json",
      MIME_JS = "application/javascript",
      MIME_JPEG = "image/jpeg",
      MIME_PNG = "image/png",
      MIME_SVG = "image/svg+xml",
      MIME_GIF = "image/gif",
      MIME_WOFF = "application/x-font-woff",
      MIME_DEFAULT_BINARY = "application/octet-stream",
      MIME_XML = "text/xml";

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
  public static Route registerEndpoint(
      String api_name, String method_uri, Class<? extends Handler> handler_class, String handler_method, String summary
  ) {
    String[] spl = method_uri.split(" ");
    assert spl.length == 2 : "Unexpected method_uri parameter: " + method_uri;
    return registerEndpoint(api_name, spl[0], spl[1], handler_class, handler_method, summary, HandlerFactory.DEFAULT);
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
  public static Route registerEndpoint(
      String api_name,
      String http_method,
      String url,
      Class<? extends Handler> handler_class,
      String handler_method,
      String summary,
      HandlerFactory handler_factory
  ) {
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
   * Register an HTTP request handler for the given URL pattern.
   *
   * @param method_uri combined method/url pattern of the endpoint, for
   *                   example: {@code "GET /3/Jobs/{job_id}"}
   * @param handler_clz class of the handler (should inherit from
   *                    {@link RestApiHandler}).
   */
  public static Route registerEndpoint(String method_uri, Class<? extends RestApiHandler> handler_clz) {
    try {
      RestApiHandler handler = handler_clz.newInstance();
      return registerEndpoint(handler.name(), method_uri, handler_clz, null, handler.help());
    } catch (Exception e) {
      throw H2O.fail(e.getMessage());
    }
  }



  //------ Handling Requests -------------------------------------------------------------------------------------------

  @Override protected void doGet(HttpServletRequest rq, HttpServletResponse rs)    { doGeneric("GET", rq, rs); }
  @Override protected void doPut(HttpServletRequest rq, HttpServletResponse rs)    { doGeneric("PUT", rq, rs); }
  @Override protected void doPost(HttpServletRequest rq, HttpServletResponse rs)   { doGeneric("POST", rq, rs); }
  @Override protected void doHead(HttpServletRequest rq, HttpServletResponse rs)   { doGeneric("HEAD", rq, rs); }
  @Override protected void doDelete(HttpServletRequest rq, HttpServletResponse rs) { doGeneric("DELETE", rq, rs); }
  @Override protected void doOptions(HttpServletRequest rq, HttpServletResponse rs) {
    if (System.getProperty(H2O.OptArgs.SYSTEM_DEBUG_CORS) != null) {
      rs.setHeader("Access-Control-Allow-Origin", "*");
      rs.setHeader("Access-Control-Allow-Headers", "Content-Type");
      rs.setStatus(HttpServletResponse.SC_OK);
    }
  }

  /**
   * Top-level dispatch handling
   */
  public void doGeneric(String method, HttpServletRequest request, HttpServletResponse response) {
    try {
      ServletUtils.startTransaction(request.getHeader("User-Agent"));

      // Note that getServletPath does an un-escape so that the %24 of job id's are turned into $ characters.
      String uri = request.getServletPath();

      Properties headers = new Properties();
      Enumeration<String> en = request.getHeaderNames();
      while (en.hasMoreElements()) {
        String key = en.nextElement();
        String value = request.getHeader(key);
        headers.put(key, value);
      }

      final String contentType = request.getContentType();
      Properties parms = new Properties();
      String postBody = null;
      if (System.getProperty(H2O.OptArgs.SYSTEM_PROP_PREFIX + "debug.cors") != null) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
      }

      if (contentType != null && contentType.startsWith(MIME_JSON)) {
        StringBuffer jb = new StringBuffer();
        String line = null;
        try {
          BufferedReader reader = request.getReader();
          while ((line = reader.readLine()) != null)
            jb.append(line);
        } catch (Exception e) {
          throw new H2OIllegalArgumentException("Exception reading POST body JSON for URL: " + uri);
        }
        postBody = jb.toString();
      } else {
        // application/x-www-form-urlencoded
        Map<String, String[]> parameterMap;
        parameterMap = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
          String key = entry.getKey();
          String[] values = entry.getValue();

          if (values.length == 1) {
            parms.put(key, values[0]);
          } else if (values.length > 1) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            boolean first = true;
            for (String value : values) {
              if (!first) sb.append(",");
              sb.append("\"").append(value).append("\"");
              first = false;
            }
            sb.append("]");
            parms.put(key, sb.toString());
          }
        }
      }

      // Make serve() call.
      NanoResponse resp = serve(uri, method, headers, parms, postBody);

      // Un-marshal Nano response back to Jetty.
      String choppedNanoStatus = resp.status.substring(0, 3);
      assert (choppedNanoStatus.length() == 3);
      int sc = Integer.parseInt(choppedNanoStatus);
      ServletUtils.setResponseStatus(response, sc);

      response.setContentType(resp.mimeType);

      Properties header = resp.header;
      Enumeration<Object> en2 = header.keys();
      while (en2.hasMoreElements()) {
        String key = (String) en2.nextElement();
        String value = header.getProperty(key);
        response.setHeader(key, value);
      }

      resp.writeTo(response.getOutputStream());

    } catch (IOException e) {
      e.printStackTrace();
      ServletUtils.setResponseStatus(response, 500);
      Log.err(e);
      // Trying to send an error message or stack trace will produce another IOException...
    } finally {
      ServletUtils.logRequest(method, request, response);
      // Handle shutdown if it was requested.
      if (H2O.getShutdownRequested()) {
        (new Thread() {
          public void run() {
            boolean [] confirmations = new boolean[H2O.CLOUD.size()];
            if (H2O.SELF.index() >= 0) {
              confirmations[H2O.SELF.index()] = true;
            }
            for(H2ONode n:H2O.CLOUD._memary) {
              if(n != H2O.SELF)
                new RPC<>(n, new UDPRebooted.ShutdownTsk(H2O.SELF,n.index(), 1000, confirmations, 0)).call();
            }
            try { Thread.sleep(2000); }
            catch (Exception ignore) {}
            int failedToShutdown = 0;
            // shutdown failed
            for(boolean b:confirmations)
              if(!b) failedToShutdown++;
            Log.info("Orderly shutdown: " + (failedToShutdown > 0? failedToShutdown + " nodes failed to shut down! ":"") + " Shutting down now.");
            H2O.closeAll();
            H2O.exit(failedToShutdown);
          }
        }).start();
      }
      ServletUtils.endTransaction();
    }
  }

  /**
   * Subsequent handling of the dispatch
   */
  public static NanoResponse serve(String url, String method, Properties header, Properties parms, String post_body) {
    try {
      // Jack priority for user-visible requests
      Thread.currentThread().setPriority(Thread.MAX_PRIORITY - 1);

      RequestType type = RequestType.requestType(url);
      RequestUri uri = new RequestUri(method, url);

      // Log the request
      maybeLogRequest(uri, header, parms);

      // For certain "special" requests that produce non-JSON payloads we require special handling.
      NanoResponse special = maybeServeSpecial(uri);
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
        // /3/Frames/{frame_id}/light
        else if (url.endsWith("/light")) {
          parms.put("frame_id", url.substring(10, url.length()-"/light".length()));
          route = findRouteByApiName("lightFrame");
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
      } else if (url.startsWith("/3/ModelMetrics/predictions_frame/")){
        route = findRouteByApiName("makeMetrics");
      }
      //------------------------------------------

      if (route == null) {
        // if the request is not known, treat as resource request, or 404 if not found
        if (uri.isGetMethod())
          return getResource(type, url);
        else
          return response404(method + " " + url, type);

      } else {
        Schema response = route._handler.handle(uri.getVersion(), route, parms, post_body);
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
    catch (AssertionError e) {
      H2OError error = new H2OError(
              System.currentTimeMillis(),
              url,
              e.toString(),
              e.toString(),
              HttpResponseStatus.INTERNAL_SERVER_ERROR.getCode(),
              new IcedHashMapGeneric.IcedHashMapStringObject(),
              e);
      Log.err("Caught assertion error: " + error.toString());
      return serveError(error);
    }
    catch (Exception e) {
      // make sure that no Exception is ever thrown out from the request
      H2OError error = new H2OError(e, url);
      // some special cases for which we return 400 because it's likely a problem with the client request:
      if (e instanceof IllegalArgumentException || e instanceof FileNotFoundException || e instanceof MalformedURLException)
        error._http_status = HttpResponseStatus.BAD_REQUEST.getCode();
      Log.err("Caught exception: " + error.toString() +";parms=" + parms);
      return serveError(error);
    }
  }

  /**
   * Log the request (unless it's an overly common one).
   */
  private static void maybeLogRequest(RequestUri uri, Properties header, Properties parms) {
    for(HttpLogFilter f: _filters)
      if( f.filter(uri,header,parms) ) return; // do not log anything if filtered
    String url = uri.getUrl();
    Log.info(uri + ", parms: " + parms);
  }

  /**
   * Create a new HttpLogFilter.
   *
   * Implement this interface to create new filters used by maybeLogRequest
   */
  public interface HttpLogFilter {
    boolean filter(RequestUri uri, Properties header, Properties parms);
  }

  /**
   * Provide the default filters for H2O's HTTP logging.
   * @return an array of HttpLogFilter instances
   */
  public static HttpLogFilter defaultFilter() {
    return new HttpLogFilter() { // this is much prettier with 1.8 lambdas
      @Override public boolean filter(RequestUri uri, Properties header, Properties parms) {
        String url = uri.getUrl();
        if (url.endsWith(".css") ||
          url.endsWith(".js")  ||
          url.endsWith(".png") ||
          url.endsWith(".ico")) return true;
        String[] path = uri.getPath();
        return path[2].equals("Cloud") ||
          path[2].equals("Jobs") && uri.isGetMethod() ||
          path[2].equals("Log") ||
          path[2].equals("Progress") ||
          path[2].equals("Typeahead") ||
          path[2].equals("WaterMeterCpuTicks") ||
          path[2].equals("DecryptionSetup"); // contains password information
      }
    };
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
            if (branches.get(key).leaf == null) continue;
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

  private static Route findRouteByApiName(String apiName) {
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
  private static NanoResponse maybeServeSpecial(RequestUri uri) {
    assert uri != null;

    if (uri.isHeadMethod()) {
      // Blank response used by R's uri.exists("/")
      if (uri.getUrl().equals("/"))
        return new NanoResponse(HTTP_OK, MIME_PLAINTEXT, "");
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

  private static NanoResponse response404(String what, RequestType type) {
    H2ONotFoundArgumentException e = new H2ONotFoundArgumentException(what + " not found", what + " not found");
    H2OError error = e.toH2OError(what);
    Log.warn(error._dev_msg);
    return serveError(error);
  }

  private static NanoResponse serveSchema(Schema s, RequestType type) {
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

    if (s instanceof H2OErrorV3) {
      return new NanoResponse(http_response_header, MIME_JSON, s.toJsonString());
    }
    if (s instanceof StreamingSchema) {
      StreamingSchema ss = (StreamingSchema) s;
      NanoResponse r = new NanoStreamResponse(http_response_header, MIME_DEFAULT_BINARY, ss.getStreamWriter());
      // Needed to make file name match class name
      r.addHeader("Content-Disposition", "attachment; filename=\"" + ss.getFilename() + "\"");
      return r;
    }

    // TODO: remove this entire switch
    switch (type) {
      case html: // return JSON for html requests
      case json:
        return new NanoResponse(http_response_header, MIME_JSON, s.toJsonString());
      case xml:
        throw H2O.unimpl("Unknown type: " + type.toString());
      case java:
        if (s instanceof AssemblyV99) {
          // TODO: fix the AssemblyV99 response handler so that it produces the appropriate StreamingSchema
          Assembly ass = DKV.getGet(((AssemblyV99) s).assembly_id);
          NanoResponse r = new NanoResponse(http_response_header, MIME_DEFAULT_BINARY, ass.toJava(((AssemblyV99) s).pojo_name));
          r.addHeader("Content-Disposition", "attachment; filename=\""+JCodeGen.toJavaId(((AssemblyV99) s).pojo_name)+".java\"");
          return r;
        } else {
          throw new H2OIllegalArgumentException("Cannot generate java for type: " + s.getClass().getSimpleName());
        }
      default:
        throw H2O.unimpl("Unknown type to serveSchema(): " + type);
    }
  }

  @SuppressWarnings(value = "unchecked")
  private static NanoResponse serveError(H2OError error) {
    // Note: don't use Schema.schema(version, error) because we have to work at bootstrap:
    return serveSchema(new H2OErrorV3().fillFromImpl(error), RequestType.json);
  }

  private static NanoResponse redirectToFlow() {
    NanoResponse res = new NanoResponse(HTTP_REDIRECT, MIME_PLAINTEXT, "");
    res.addHeader("Location", H2O.ARGS.context_path + "/flow/index.html");
    return res;
  }

  private static NanoResponse downloadNps(String categoryName, String keyName) {
    NodePersistentStorage nps = H2O.getNPS();
    AtomicLong length = new AtomicLong();
    InputStream is = nps.get(categoryName, keyName, length);
    NanoResponse res = new NanoResponse(HTTP_OK, MIME_DEFAULT_BINARY, is);
    res.addHeader("Content-Length", Long.toString(length.get()));
    res.addHeader("Content-Disposition", "attachment; filename=" + keyName + ".flow");
    return res;
  }

  private static NanoResponse downloadLogs() {
    Log.info("\nCollecting logs.");

    H2ONode[] members = H2O.CLOUD.members();
    byte[][] perNodeZipByteArray = new byte[members.length][];
    byte[] clientNodeByteArray = null;

    for (int i = 0; i < members.length; i++) {
      byte[] bytes;

      try {
        // Skip nodes that aren't healthy, since they are likely to cause the entire process to hang.
        if (members[i].isHealthy()) {
          GetLogsFromNode g = new GetLogsFromNode();
          g.nodeidx = i;
          g.doIt();
          bytes = g.bytes;
        } else {
          bytes = StringUtils.bytesOf("Node not healthy");
        }
      }
      catch (Exception e) {
        bytes = StringUtils.toBytes(e);
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
        bytes = StringUtils.toBytes(e);
      }
      clientNodeByteArray = bytes;
    }

    String outputFileStem = getOutputLogStem();
    byte[] finalZipByteArray;
    try {
      finalZipByteArray = zipLogs(perNodeZipByteArray, clientNodeByteArray, outputFileStem);
    }
    catch (Exception e) {
      finalZipByteArray = StringUtils.toBytes(e);
    }

    NanoResponse res = new NanoResponse(HTTP_OK, MIME_DEFAULT_BINARY, new ByteArrayInputStream(finalZipByteArray));
    res.addHeader("Content-Length", Long.toString(finalZipByteArray.length));
    res.addHeader("Content-Disposition", "attachment; filename=" + outputFileStem + ".zip");
    return res;
  }

  private static String getOutputLogStem() {
    String pattern = "yyyyMMdd_hhmmss";
    SimpleDateFormat formatter = new SimpleDateFormat(pattern);
    String now = formatter.format(new Date());
    return "h2ologs_" + now;
  }

  private static byte[] zipLogs(byte[][] results, byte[] clientResult, String topDir) throws IOException {
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
  private static NanoResponse getResource(RequestType request_type, String url) {
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

    int i = url.lastIndexOf('.');
    String mime;
    switch (url.substring(i + 1)) {
      case "js": mime = MIME_JS; break;
      case "css": mime = MIME_CSS; break;
      case "htm":case "html": mime = MIME_HTML; break;
      case "jpg":case "jpeg": mime = MIME_JPEG; break;
      case "png": mime = MIME_PNG; break;
      case "svg": mime = MIME_SVG; break;
      case "gif": mime = MIME_GIF; break;
      case "woff": mime = MIME_WOFF; break;
      default: mime = MIME_DEFAULT_BINARY;
    }
    NanoResponse res = new NanoResponse(HTTP_OK, mime, new ByteArrayInputStream(bytes));
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


  /**
   * Dummy Rest API context which is redirecting calls to static method API.
   */
  public static class DummyRestApiContext implements RestApiContext {
    @Override
    public Route registerEndpoint(String apiName, String methodUri,
                                  Class<? extends Handler> handlerClass, String handlerMethod,
                                  String summary) {
      return RequestServer.registerEndpoint(apiName, methodUri, handlerClass, handlerMethod, summary);
    }

    @Override
    public Route registerEndpoint(String apiName, String httpMethod, String url,
                                  Class<? extends Handler> handlerClass, String handlerMethod,
                                  String summary, HandlerFactory handlerFactory) {
      return RequestServer.registerEndpoint(apiName, httpMethod, url, handlerClass, handlerMethod, summary, handlerFactory);
    }

    @Override
    public Route registerEndpoint(String methodUri, Class<? extends RestApiHandler> handlerClass) {
      return RequestServer.registerEndpoint(methodUri, handlerClass);
    }

    private Set<Schema> allSchemas = new HashSet<>();

    @Override
    public void registerSchema(Schema... schemas) {
      for (Schema schema : schemas) {
        allSchemas.add(schema);
      }
    }

    public Schema[] getAllSchemas() {
      return allSchemas.toArray(new Schema[0]);
    }
  };

}
