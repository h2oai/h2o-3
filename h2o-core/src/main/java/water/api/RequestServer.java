package water.api;

import com.google.code.regexp.Matcher;
import com.google.code.regexp.Pattern;
import water.*;
import water.exceptions.*;
import water.fvec.Frame;
import water.init.NodePersistentStorage;
import water.nbhm.NonBlockingHashMap;
import water.rapids.Assembly;
import water.util.*;

import java.io.*;
import java.lang.reflect.Method;
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
 * @see water.api.Handler a class which contains HTTP request handler methods and other helpers
 * @see water.api.Schema a class which provides a stable external representation for entities and request parameters
 * @see #register(String, String, Class, String, String, HandlerFactory) registers a specific handler method for the
 *      supplied URI pattern and HTTP method (GET, POST, DELETE, PUT)
 */
public class RequestServer extends NanoHTTPD {
  // Returned in REST API responses as X-h2o-rest-api-version
  public static final int H2O_REST_API_VERSION = 3;

  static public RequestServer SERVER;
  private RequestServer() {}


  // Handlers ------------------------------------------------------------

  // An array of regexs-over-URLs and handling Methods.
  // The list is searched in-order, first match gets dispatched.
  private static final LinkedHashMap<java.util.regex.Pattern,Route> _routes = new LinkedHashMap<>();   // explicit routes registered below
  private static final LinkedHashMap<java.util.regex.Pattern,Route> _fallbacks= new LinkedHashMap<>(); // routes that are version fallbacks (e.g., we asked for v5 but v2 is the latest)
  public static int numRoutes() { return _routes.size(); }
  public static Collection<Route> routes() { return _routes.values(); }

  private static Pattern version_pattern = null;
  private static Pattern getVersionPattern() {
    if (version_pattern == null) version_pattern = Pattern.compile("^/(\\d+|EXPERIMENTAL|LATEST)/(.*)");
    return version_pattern;
  }
  private static Set<String> methods = null;
  private static Set<String> getMethods() {
    if (methods == null) {
      methods = new HashSet<>(4);
      methods.add("HEAD");
      methods.add("GET");
      methods.add("POST");
      methods.add("DELETE");
    }
    return methods;
  }


  // NOTE!
  // URL patterns are searched in order.  If you have two patterns that can match on the same URL
  // (e.g., /foo/baz and /foo) you MUST register them in decreasing order of specificity.
  static {
    // Data
    register("POST /3/CreateFrame", CreateFrameHandler.class, "run",
        "Create a synthetic H2O Frame.");

    register("POST /3/SplitFrame", SplitFrameHandler.class, "run",
        "Split an H2O Frame.");

    register("POST /3/Interaction", InteractionHandler.class, "run",
        "Create interactions between categorical columns.");

    register("POST /3/MissingInserter", MissingInserterHandler.class, "run",
        "Insert missing values.");

    register("POST /99/DCTTransformer", DCTTransformerHandler.class, "run",
        "Row-by-row discrete cosine transforms in 1D, 2D and 3D.");

    register("POST /99/Tabulate", TabulateHandler.class, "run",
        "Tabulate one column vs another.");

    register("GET /3/ImportFiles", ImportFilesHandler.class, "importFiles",
        "Import raw data files into a single-column H2O Frame.");

    register("POST /3/ImportFiles", ImportFilesHandler.class, "importFiles",
        "Import raw data files into a single-column H2O Frame.");

    register("POST /99/ImportSQLTable", ImportSQLTableHandler.class, "importSQLTable",
        "Import SQL table into an H2O Frame.");

    register("POST /3/ParseSetup", ParseSetupHandler.class, "guessSetup",
        "Guess the parameters for parsing raw byte-oriented data into an H2O Frame.");

    register("POST /3/Parse", ParseHandler.class, "parse",
        "Parse a raw byte-oriented Frame into a useful columnar data Frame."); // NOTE: prefer POST due to higher content limits

    register("POST /3/ParseSVMLight", ParseHandler.class, "parseSVMLight",
        "Parse a raw byte-oriented Frame into a useful columnar data Frame."); // NOTE: prefer POST due to higher content limits

    // Admin
    register("GET /3/Cloud", CloudHandler.class, "status",
        "Determine the status of the nodes in the H2O cloud.");

    register("HEAD /3/Cloud", CloudHandler.class, "head",
        "Determine the status of the nodes in the H2O cloud.");

    register("GET /3/Jobs", JobsHandler.class, "list",
        "Get a list of all the H2O Jobs (long-running actions).");

    register("GET /3/Timeline", TimelineHandler.class, "fetch",
        "Debugging tool that provides information on current communication between nodes.");

    register("GET /3/Profiler", ProfilerHandler.class, "fetch",
        "Report real-time profiling information for all nodes (sorted, aggregated stack traces).");

    register("GET /3/JStack", JStackHandler.class, "fetch",
        "Report stack traces for all threads on all nodes.");

    register("GET /3/NetworkTest", NetworkTestHandler.class, "fetch",
        "Run a network test to measure the performance of the cluster interconnect.");

    register("POST /3/UnlockKeys", UnlockKeysHandler.class, "unlock",
        "Unlock all keys in the H2O distributed K/V store, to attempt to recover from a crash.");

    register("POST /3/Shutdown", ShutdownHandler.class, "shutdown",
        "Shut down the cluster.");

    // REST only, no html:
    register("GET /3/About", AboutHandler.class, "get",
        "Return information about this H2O cluster.");

    register("GET /3/Metadata/endpoints/(?<num>[0-9]+)", MetadataHandler.class, "fetchRoute",
        "Return the REST API endpoint metadata, including documentation, for the endpoint specified by number.");

    register("GET /3/Metadata/endpoints/{path}", MetadataHandler.class, "fetchRoute",
        "Return the REST API endpoint metadata, including documentation, for the endpoint specified by path.");

    register("GET /3/Metadata/endpoints", MetadataHandler.class, "listRoutes",
        "Return a list of all the REST API endpoints.");

    register("GET /3/Metadata/schemaclasses/{classname}", MetadataHandler.class, "fetchSchemaMetadataByClass",
        "Return the REST API schema metadata for specified schema class.");

    register("GET /3/Metadata/schemas/{schemaname}", MetadataHandler.class, "fetchSchemaMetadata",
        "Return the REST API schema metadata for specified schema.");

    register("GET /3/Metadata/schemas", MetadataHandler.class, "listSchemas",
        "Return list of all REST API schemas.");

    register("GET /3/Typeahead/files", TypeaheadHandler.class, "files",
        "Typehead hander for filename completion.");

    register("GET /3/Jobs/{job_id}", JobsHandler.class, "fetch",
        "Get the status of the given H2O Job (long-running action).");

    register("POST /3/Jobs/{job_id}/cancel", JobsHandler.class, "cancel",
        "Cancel a running job.");

    register("GET /3/Find", FindHandler.class, "find",
        "Find a value within a Frame.");

    register("GET /3/Frames/{frame_id}/export/{path}/overwrite/{force}", FramesHandler.class, "export",
        "[DEPRECATED] Export a Frame to the given path with optional overwrite.");

    register("POST /3/Frames/{frame_id}/export", FramesHandler.class, "export",
        "Export a Frame to the given path with optional overwrite.");

    register("GET /3/Frames/{frame_id}/columns/{column}/summary", FramesHandler.class, "columnSummary",
        "Return the summary metrics for a column, e.g. mins, maxes, mean, sigma, percentiles, etc.");

    register("GET /3/Frames/{frame_id}/columns/{column}/domain" , FramesHandler.class, "columnDomain",
        "Return the domains for the specified column. \"null\" if the column is not a categorical.");

    register("GET /3/Frames/{frame_id}/columns/{column}", FramesHandler.class, "column",
        "Return the specified column from a Frame.");

    register("GET /3/Frames/{frame_id}/columns", FramesHandler.class, "columns",
        "Return all the columns from a Frame.");

    register("GET /3/Frames/{frame_id}/summary", FramesHandler.class, "summary",
        "Return a Frame, including the histograms, after forcing computation of rollups.");

    register("GET /3/Frames/{frame_id}", FramesHandler.class, "fetch",
        "Return the specified Frame.");

    register("GET /3/Frames", FramesHandler.class, "list",
        "Return all Frames in the H2O distributed K/V store.");

    register("DELETE /3/Frames/{frame_id}", FramesHandler.class, "delete",
        "Delete the specified Frame from the H2O distributed K/V store.");

    register("DELETE /3/Frames", FramesHandler.class, "deleteAll",
        "Delete all Frames from the H2O distributed K/V store.");

    // Handle models
    register("GET /3/Models/{model_id}", ModelsHandler.class, "fetch",
        "Return the specified Model from the H2O distributed K/V store, optionally with the list of compatible Frames.");

    register("GET /3/Models", ModelsHandler.class, "list",
        "Return all Models from the H2O distributed K/V store.");

    register("DELETE /3/Models/{model_id}", ModelsHandler.class, "delete",
        "Delete the specified Model from the H2O distributed K/V store.");

    register("DELETE /3/Models", ModelsHandler.class, "deleteAll",
        "Delete all Models from the H2O distributed K/V store.");

    // Get java code for models as
    register("GET /3/Models.java/{model_id}/preview", ModelsHandler.class, "fetchPreview",
        "Return potentially abridged model suitable for viewing in a browser (currently only used for java model code).");

    // Register resource also with .java suffix since we do not want to break API
    // FIXME: remove in new REST API version
    register("GET /3/Models.java/{model_id}", ModelsHandler.class, "fetchJavaCode",
        "Return the stream containing model implementation in Java code.");

    // Model serialization - import/export calls
    register("POST /99/Models.bin/{model_id}", ModelsHandler.class, "importModel",
        "Import given binary model into H2O.");

    register("GET /99/Models.bin/{model_id}", ModelsHandler.class, "exportModel",
        "Export given model.");


    register("GET /99/Grids/{grid_id}", GridsHandler.class, "fetch",
        "Return the specified grid search result.");

    register("GET /99/Grids", GridsHandler.class, "list",
        "Return all grids from H2O distributed K/V store.");


    register("POST /3/ModelBuilders/{algo}/model_id", ModelBuildersHandler.class, "calcModelId",
        "Return a new unique model_id for the specified algorithm.");

    register("GET /3/ModelBuilders/{algo}", ModelBuildersHandler.class, "fetch",
        "Return the Model Builder metadata for the specified algorithm.");

    register("GET /3/ModelBuilders", ModelBuildersHandler.class, "list",
        "Return the Model Builder metadata for all available algorithms.");

    // TODO: filtering isn't working for these first four; we get all results:
    register("GET /3/ModelMetrics/models/{model}/frames/{frame}", ModelMetricsHandler.class, "fetch",
        "Return the saved scoring metrics for the specified Model and Frame.");

    register("DELETE /3/ModelMetrics/models/{model}/frames/{frame}", ModelMetricsHandler.class, "delete",
        "Return the saved scoring metrics for the specified Model and Frame.");

    register("GET /3/ModelMetrics/models/{model}", ModelMetricsHandler.class, "fetch",
        "Return the saved scoring metrics for the specified Model.");

    register("GET /3/ModelMetrics/frames/{frame}/models/{model}", ModelMetricsHandler.class, "fetch",
        "Return the saved scoring metrics for the specified Model and Frame.");

    register("DELETE /3/ModelMetrics/frames/{frame}/models/{model}", ModelMetricsHandler.class, "delete",
        "Return the saved scoring metrics for the specified Model and Frame.");

    register("GET /3/ModelMetrics/frames/{frame}", ModelMetricsHandler.class, "fetch",
        "Return the saved scoring metrics for the specified Frame.");

    register("GET /3/ModelMetrics", ModelMetricsHandler.class, "fetch",
        "Return all the saved scoring metrics.");

    register("POST /3/ModelMetrics/models/{model}/frames/{frame}", ModelMetricsHandler.class, "score",
        "Return the scoring metrics for the specified Frame with the specified Model.  If the Frame has already been " +
        "scored with the Model then cached results will be returned; otherwise predictions for all rows in the Frame " +
        "will be generated and the metrics will be returned.");

    register("POST /3/Predictions/models/{model}/frames/{frame}", ModelMetricsHandler.class, "predict",
        "Score (generate predictions) for the specified Frame with the specified Model.  Both the Frame of " +
        "predictions and the metrics will be returned.");

    register("POST /4/Predictions/models/{model}/frames/{frame}", ModelMetricsHandler.class, "predict2",
        "Score (generate predictions) for the specified Frame with the specified Model.  Both the Frame of " +
        "predictions and the metrics will be returned.");

    register("GET /3/WaterMeterCpuTicks/{nodeidx}", WaterMeterCpuTicksHandler.class, "fetch",
        "Return a CPU usage snapshot of all cores of all nodes in the H2O cluster.");

    register("GET /3/WaterMeterIo/{nodeidx}", WaterMeterIoHandler.class, "fetch",
        "Return IO usage snapshot of all nodes in the H2O cluster.");

    register("GET /3/WaterMeterIo", WaterMeterIoHandler.class, "fetch_all",
        "Return IO usage snapshot of all nodes in the H2O cluster.");

    // Node persistent storage
    register("GET /3/NodePersistentStorage/categories/{category}/names/{name}/exists", NodePersistentStorageHandler.class, "exists",
        "Return true or false.");

    register("GET /3/NodePersistentStorage/categories/{category}/exists", NodePersistentStorageHandler.class, "exists",
        "Return true or false.");

    register("GET /3/NodePersistentStorage/configured", NodePersistentStorageHandler.class, "configured",
        "Return true or false.");

    register("POST /3/NodePersistentStorage/{category}/{name}", NodePersistentStorageHandler.class, "put_with_name",
        "Store a named value.");

    register("GET /3/NodePersistentStorage/{category}/{name}", NodePersistentStorageHandler.class, "get_as_string",
        "Return value for a given name.");

    register("DELETE /3/NodePersistentStorage/{category}/{name}", NodePersistentStorageHandler.class, "delete",
        "Delete a key.");

    register("POST /3/NodePersistentStorage/{category}", NodePersistentStorageHandler.class, "put",
        "Store a value.");

    register("GET /3/NodePersistentStorage/{category}", NodePersistentStorageHandler.class, "list",
        "Return all keys stored for a given category.");

    // TODO: register("DELETE /3/ModelMetrics/models/{model}/frames/{frame}", ModelMetricsHandler.class, "delete");
    // TODO: register("DELETE /3/ModelMetrics/frames/{frame}/models/{model}", ModelMetricsHandler.class, "delete");
    // TODO: register("DELETE /3/ModelMetrics/frames/{frame}", ModelMetricsHandler.class, "delete");
    // TODO: register("DELETE /3/ModelMetrics/models/{model}", ModelMetricsHandler.class, "delete");
    // TODO: register("DELETE /3/ModelMetrics", ModelMetricsHandler.class, "delete");
    // TODO: register("POST /3/Predictions/models/{model}/frames/{frame}", ModelMetricsHandler.class, "predict");

    // Log file management.
    // Note:  Hacky pre-route cutout of "/3/Logs/download" is done above in a non-json way.
    register("GET /3/Logs/nodes/{nodeidx}/files/{name}", LogsHandler.class, "fetch",
        "Get named log file for a node.");


    // ModelBuilder Handler registration must be done for each algo in the application class
    // (e.g., H2OApp), because the Handler class is parameterized by the associated Schema,
    // and this is different for each ModelBuilder in order to handle its parameters in a
    // typesafe way:
    //   register("POST /3/ModelBuilders/{algo}", ModelBuildersHandler.class, "train", "Train {algo}");
    //

    register("GET /3/KillMinus3", KillMinus3Handler.class, "killm3",
        "Kill minus 3 on *this* node");

    register("POST /99/Rapids", RapidsHandler.class, "exec",
        "Execute an Rapids AST.");

    register("GET /99/Assembly.java/{assembly_id}/{pojo_name}", AssemblyHandler.class, "toJava",
        "Generate a Java POJO from the Assembly");

    register("POST /99/Assembly", AssemblyHandler.class, "fit",
        "Fit an assembly to an input frame");

    register("GET /3/DownloadDataset", DownloadDataHandler.class, "fetch",
        "Download dataset as a CSV.");

    register("GET /3/DownloadDataset.bin", DownloadDataHandler.class, "fetchStreaming",
        "Download dataset as a CSV.");

    register("DELETE /3/DKV/{key}", RemoveHandler.class, "remove",
        "Remove an arbitrary key from the H2O distributed K/V store.");

    register("DELETE /3/DKV", RemoveAllHandler.class, "remove",
        "Remove all keys from the H2O distributed K/V store.");

    register("POST /3/LogAndEcho", LogAndEchoHandler.class, "echo",
        "Save a message to the H2O logfile.");

    register("GET /3/InitID", InitIDHandler.class, "issue",
        "Issue a new session ID.");

    register("DELETE /3/InitID", InitIDHandler.class, "endSession",
        "End a session.");

    register("POST /3/GarbageCollect", GarbageCollectHandler.class, "gc",
        "Explicitly call System.gc().");

    register("GET /99/Sample", CloudHandler.class, "status",
        "Example of an experimental endpoint.  Call via /EXPERIMENTAL/Sample.  Experimental endpoints can change at " +
        "any moment.");
  }

  /**
   * Register an HTTP request handler method for a given URL pattern, with parameters extracted from the URI.
   * <p>
   * URIs which match this pattern will have their parameters collected from the path and from the query params
   *
   * @param http_method HTTP verb (GET, POST, DELETE) this handler will accept
   * @param uri_pattern_raw regular expression which matches the URL path for this request handler; parameters that are embedded in the path must be captured with &lt;code&gt;(?&lt;parm&gt;.*)&lt;/code&gt; syntax
   * @param handler_class class which contains the handler method
   * @param handler_method name of the handler method
   * @param summary help string which explains the functionality of this endpoint
   * @param handler_factory factory to create instance of handler
   * @see Route
   * @see water.api.RequestServer
   * @return the Route for this request
   */
  public static Route register(String http_method, String uri_pattern_raw, Class<? extends Handler> handler_class, String handler_method, String summary, HandlerFactory handler_factory) {
    if (!getMethods().contains(http_method))
      throw new AssertionError("http_method should be one of GET|POST|HEAD|DELETE");
    assert uri_pattern_raw.startsWith("/");

    // Search handler_class and all its superclasses for the method.
    Method method = null;

    // TODO: move to ReflectionUtils:
    for (Method m : handler_class.getMethods()) {
      if (!m.getName().equals(handler_method)) continue;

      Class[] params = m.getParameterTypes();
      if (params == null || params.length != 2) continue;
      if (params[0] != Integer.TYPE) continue;
      if (!Schema.class.isAssignableFrom(params[1])) continue;

      method = m;
      break;
    }
    if (method == null)
      throw H2O.fail("Failed to find handler method: " + handler_method + " for handler class: " + handler_class);

    if (!uri_pattern_raw.equals("/")) {
      Matcher m = getVersionPattern().matcher(uri_pattern_raw);
      if (!m.matches())
        throw H2O.fail("Route URL pattern must begin with a version: " + uri_pattern_raw);

      int version = Integer.valueOf(m.group(1));
      if (version > Schema.getHighestSupportedVersion() && version != Schema.getExperimentalVersion())
        throw H2O.fail("Route version is greater than the max supported: " + uri_pattern_raw);
    }

    // Convert convenience URL params into actual regex expressions: "/3/Job/{job_id}" => "/3/Job/(?<job_id>.*)"
    uri_pattern_raw = uri_pattern_raw.replaceAll("\\{(\\w+)\\}", "(?<$1>.*)");

    // Get the group names in the uri pattern and remove any underscores,
    // since underscores are not actually allowed in java regex group names.
    ArrayList<String> params_list = new ArrayList<String>();
    Pattern group_pattern = Pattern.compile("\\?<(\\w+)>");
    Matcher group_matcher = group_pattern.matcher(uri_pattern_raw);
    StringBuffer new_uri_buffer = new StringBuffer();
    while (group_matcher.find()) {
      String group = group_matcher.group(1);
      params_list.add(group);
      group_matcher.appendReplacement(new_uri_buffer, "?<" + group.replace("_", "") + ">");
    }
    group_matcher.appendTail(new_uri_buffer);
    uri_pattern_raw = new_uri_buffer.toString();

    assert lookup(handler_method, uri_pattern_raw)==null; // Not shadowed
    Pattern uri_pattern = Pattern.compile(uri_pattern_raw);
    Route route = new Route(http_method,
                            uri_pattern_raw,
                            uri_pattern, summary,
                            handler_class, method,
                            params_list.toArray(new String[params_list.size()]),
                            handler_factory);
    _routes.put(uri_pattern.pattern(), route);
    return route;
  }

  /**
   * Register an HTTP request handler method for a given URL pattern, with parameters extracted from the URI.
   * <p>
   * URIs which match this pattern will have their parameters collected from the path and from the query params
   *
   * @param method_uri combined method / url pattern of the request, e.g.: "GET /3/Jobs/{job_id}"
   * @param handler_class class which contains the handler method
   * @param handler_method name of the handler method
   * @param summary help string which explains the functionality of this endpoint
   * @see Route
   * @see water.api.RequestServer
   * @return the Route for this request
   */
  public static Route register(String method_uri, Class<? extends Handler> handler_class, String handler_method, String summary) {
    String[] spl = method_uri.split(" ");
    assert spl.length == 2 : "Unexpected method_uri parameter: " + method_uri;
    return register(spl[0], spl[1], handler_class, handler_method, summary, HandlerFactory.DEFAULT);
  }


  // Lookup the method/url in the register list, and return a matching Method
  protected static Route lookup( String http_method, String uri ) {
    if (null == http_method || null == uri)
      return null;

    // Search the explicitly registered routes:
    for( Route r : _routes.values() )
      if (r._url_pattern.matcher(uri).matches())
        if (http_method.equals(r._http_method))
          return r;

    // Search the fallbacks cache:
    for( Route r : _fallbacks.values() )
      if (r._url_pattern.matcher(uri).matches())
        if (http_method.equals(r._http_method))
          return r;

    // Didn't find a registered route and didn't find a cached fallback, so do a backward version search and cache if we find a match:
    Matcher m = getVersionPattern().matcher(uri);
    if (! m.matches()) return null;

    // Ok then. . .  Try to fall back to a previous version.
    int version = Integer.valueOf(m.group(1));
    if (version == Route.MIN_VERSION) return null; // don't go any lower

    String lower_uri = "/" + (version - 1) + "/" + m.group(2);
    Route fallback = lookup(http_method, lower_uri);

    if (null == fallback) return null;

    // Store the route fallback for later.
    Matcher route_m = version_pattern.matcher(fallback._url_pattern_raw);
    if (! route_m.matches()) throw H2O.fail("Found a fallback route that doesn't have a version: " + fallback);

    // register fallbacks for all the versions <= the one in URI we were originally given and >  the one in the fallback route:
    int route_version = Integer.valueOf(route_m.group(1));
    for (int i = version; i > route_version && i >= Route.MIN_VERSION; i--) {
      String fallback_route_uri = "/" + i + "/" + route_m.group(2);
      Pattern fallback_route_pattern = Pattern.compile(fallback_route_uri);
      Route generated = new Route(fallback._http_method,
                                  fallback_route_uri,
                                  fallback_route_pattern,
                                  fallback._summary,
                                  fallback._handler_class,
                                  fallback._handler_method,
                                  fallback._path_params,
                                  fallback._handler_factory);
      _fallbacks.put(fallback_route_pattern.pattern(), generated);
    }

    // Better be there in the _fallbacks cache now!
    return lookup(http_method, uri);
  }

  public static void finalizeRegistration() {
    Schema.registerAllSchemasIfNecessary();

    // Need a stub RequestServer to handle calls to serve() from Jetty.
    // But no threads are started here anymore.
    SERVER = new RequestServer();

    H2O.getJetty().acceptRequests();
  }

  public static void alwaysLogRequest(String uri, String method, Properties parms) {
    // This is never called anymore.
    throw H2O.fail();
  }

    // Log all requests except the overly common ones
  boolean maybeLogRequest(String method, String uri, String pattern, Properties parms, Properties header) {
    if (uri.endsWith(".css") ||
        uri.endsWith(".js") ||
        uri.endsWith(".png") ||
        uri.endsWith(".ico")) return false;

    if (uri.contains("/Cloud") ||
        (uri.contains("/Jobs") && method.equals("GET")) ||
        uri.contains("/Log") ||
        uri.contains("/Progress") ||
        uri.contains("/Typeahead") ||
        uri.contains("/WaterMeterCpuTicks")) return false;

    String paddedMethod = String.format("%-6s", method);
    Log.info("Method: " + paddedMethod, ", URI: " + uri + ", route: " + pattern + ", parms: " + parms);
    return true;
  }

  private void capturePathParms(Properties parms, String path, Route route) {
    Matcher m = route._url_pattern.matcher(path);
    if (! m.matches()) {
      throw H2O.fail("Routing regex error: Pattern matched once but not again for pattern: " + route._url_pattern.pattern() + " and path: " + path);
    }

    // Java doesn't allow _ in group names but we want them in field names, so remove all _ from the path params before we look up the group capture value
    for (String key : route._path_params) {
      String key_no_underscore = key.replace("_","");
      String val;
      try {
        val = m.group(key_no_underscore);
      }
      catch (IllegalArgumentException e) {
        throw H2O.fail("Missing request parameter in the URL: did not find " + key + " in the URL as expected; URL pattern: " + route._url_pattern.pattern() + " with expected parameters: " + Arrays.toString(route._path_params) + " for URL: " + path);
      }
      if (null != val)
        parms.put(key, val);
    }
  }

  private Response response404(String what, RequestType type) {
    H2ONotFoundArgumentException e = new H2ONotFoundArgumentException(what + " not found", what + " not found");
    H2OError error = e.toH2OError(what);

    Log.warn(error._dev_msg);
    Log.warn(error._values.toJsonString());
    Log.warn((Object[]) error._stacktrace);

    return wrap(new H2OErrorV3().fillFromImpl(error), type);
  }

  // Top-level dispatch based on the URI.  Break down URI into parts;
  // e.g. /2/GBM.html/crunk?hex=some_hex breaks down into:
  //   version:      2
  //   requestType:  ".html"
  //   path:         "GBM/crunk"
  //   parms:        {"hex": "some_hex"}
  @Override
  public Response serve(String uri, String method, Properties header, Properties parms) {
    // Jack priority for user-visible requests
    Thread.currentThread().setPriority(Thread.MAX_PRIORITY - 1);

    // determine the request type
    RequestType type = RequestType.requestType(uri);

    // Blank response used by R's uri.exists("/")
    if (uri.equals("/") && method.equals("HEAD")) {
      return new Response(HTTP_OK, MIME_PLAINTEXT, "");
    }

    String versioned_path = uri;
    String path = uri;
    int version = 1;

    Matcher m = getVersionPattern().matcher(uri);
    if (m.matches()) {
      switch (m.group(1)) {
        case "LATEST":
          version = Schema.getLatestOrHighestSupportedVersion();
          break;
        case "EXPERIMENTAL":
          version = 99;
          break;
        default:
          version = Integer.valueOf(m.group(1));
          break;
      }
      String uripath = "/" + m.group(2);
      path = type.requestName(uripath); // Strip suffix type from middle of URI
      versioned_path = "/" + version + path;
    }

    // Load resources, or dispatch on handled requests
    try {
      boolean logged;
      // Handle any URLs that bypass the route approach.  This is stuff that has abnormal non-JSON response payloads.
      if (method.equals("GET") && uri.equals("/")) {
        logged = maybeLogRequest(method, uri, "", parms, header);
        if (logged) GAUtils.logRequest(uri, header);
        return redirectToFlow();
      }
      if (method.equals("GET") && uri.endsWith("/Logs/download")) {
        logged = maybeLogRequest(method, uri, "", parms, header);
        if (logged) GAUtils.logRequest(uri, header);
        return downloadLogs();
      }
      if (method.equals("GET")) {
        Pattern p2 = Pattern.compile(".*/NodePersistentStorage.bin/([^/]+)/([^/]+)");
        Matcher m2 = p2.matcher(uri);
        boolean b2 = m2.matches();
        if (b2) {
          String categoryName = m2.group(1);
          String keyName = m2.group(2);
          return downloadNps(categoryName, keyName);
        }
      }

      // Find handler for url
      Route route = lookup(method, versioned_path);

      // if the request is not known, treat as resource request, or 404 if not found
      if (route == null) {
        if (method.equals("GET")) {
          return getResource(type, uri);
        } else {
          return response404(method + " " + uri, type);
        }
      } else {
        capturePathParms(parms, versioned_path, route); // get any parameters like /Frames/<key>
        logged = maybeLogRequest(method, uri, route._url_pattern.namedPattern(), parms, header);
        if (logged) GAUtils.logRequest(uri, header);
        Schema s = handle(type, route, version, parms);
        PojoUtils.filterFields(s, (String)parms.get("_include_fields"), (String)parms.get("_exclude_fields"));
        Response r = wrap(s, type);
        return r;
      }
    }
    catch (H2OFailException e) {
      H2OError error = e.toH2OError(uri);

      Log.fatal("Caught exception (fatal to the cluster): " + error.toString());

      // Note: don't use Schema.schema(version, error) because we have to work at bootstrap:
      H2O.fail(wrap(new H2OErrorV3().fillFromImpl(error), type).toString());
      // unreachable, but the compiler doesn't know it:
      return null;
    }
    catch (H2OModelBuilderIllegalArgumentException e) {
      H2OModelBuilderError error = e.toH2OError(uri);

      Log.warn("Caught exception: " + error.toString());

      // Note: don't use Schema.schema(version, error) because we have to work at bootstrap:
      return wrap(new H2OModelBuilderErrorV3().fillFromImpl(error), type);
    }
    catch (H2OAbstractRuntimeException e) {
      H2OError error = e.toH2OError(uri);

      Log.warn("Caught exception: " + error.toString());

      // Note: don't use Schema.schema(version, error) because we have to work at bootstrap:
      return wrap(new H2OErrorV3().fillFromImpl(error), type);
    }
    // TODO: kill the server if someone called H2O.fail()
    catch( Exception e ) { // make sure that no Exception is ever thrown out from the request
      H2OError error = new H2OError(e, uri);

      // some special cases for which we return 400 because it's likely a problem with the client request:
      if (e instanceof IllegalArgumentException)
        error._http_status = HttpResponseStatus.BAD_REQUEST.getCode();
      else if (e instanceof FileNotFoundException)
        error._http_status = HttpResponseStatus.BAD_REQUEST.getCode();
      else if (e instanceof MalformedURLException)
        error._http_status = HttpResponseStatus.BAD_REQUEST.getCode();

      Log.err("Caught exception: " + error.toString());

      // Note: don't use Schema.schema(version, error) because we have to work at bootstrap:
      return wrap(new H2OErrorV3().fillFromImpl(error), type);
    }
  }

  // Handling ------------------------------------------------------------------
  private Schema handle( RequestType type, Route route, int version, Properties parms ) throws Exception {
    switch( type ) {
    case html: // These request-types only dictate the response-type;
    case java: // the normal action is always done.
    case json:
    case xml: {
      Handler h = route._handler;
      return h.handle(version,route,parms); // Can throw any Exception the handler throws
    }
    case query:
    case help:
    default:
      throw H2O.unimpl("Unknown type: " + type.toString());
    }
  }

  private Response wrap( Schema s, RequestType type ) {
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

    switch( type ) {
    case html: // return JSON for html requests
    case json:
      return new Response(http_response_header, MIME_JSON, s.toJsonString());
    case xml:
      //return new Response(http_code, MIME_XML , new String(S.writeXML (new AutoBuffer()).buf()));
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
      throw H2O.unimpl("Unknown type to wrap(): " + type);
    }
  }


  // Resource loading ----------------------------------------------------------
  // cache of all loaded resources
  private static final NonBlockingHashMap<String,byte[]> _cache = new NonBlockingHashMap<>();
  // Returns the response containing the given uri with the appropriate mime type.
  private Response getResource(RequestType request_type, String uri) {
    byte[] bytes = _cache.get(uri);
    if( bytes == null ) {
      // Try-with-resource
      try (InputStream resource = water.init.JarHash.getResource2(uri)) {
          if( resource != null ) {
            try { bytes = toByteArray(resource); }
            catch( IOException e ) { Log.err(e); }

            // PP 06-06-2014 Disable caching for now so that the browser
            //  always gets the latest sources and assets when h2o-client is rebuilt.
            // TODO need to rethink caching behavior when h2o-dev is merged into h2o.
            //
            // if( bytes != null ) {
            //  byte[] res = _cache.putIfAbsent(uri,bytes);
            //  if( res != null ) bytes = res; // Racey update; take what is in the _cache
            //}
            //

          }
        } catch( IOException ignore ) { }
    }
    if( bytes == null || bytes.length == 0 ) // No resource found?
      return response404("Resource " + uri, request_type);

    String mime = MIME_DEFAULT_BINARY;
    if( uri.endsWith(".css") )
      mime = "text/css";
    else if( uri.endsWith(".html") )
      mime = "text/html";
    Response res = new Response(HTTP_OK,mime,new ByteArrayInputStream(bytes));
    res.addHeader("Content-Length", Long.toString(bytes.length));
    return res;
  }

  // Convenience utility
  private static byte[] toByteArray(InputStream is) throws IOException {
    try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[0x2000];
        for( int len; (len = is.read(buffer)) != -1; )
          os.write(buffer, 0, len);
        return os.toByteArray();
      }
  }

  // Return URLs for things that want to appear Frame-inspection page
  static String[] frameChoices( int version, Frame fr ) {
    ArrayList<String> al = new ArrayList<>();
    for( java.util.regex.Pattern p : _routes.keySet() ) {
      try {
        Method meth = _routes.get(p)._handler_method;
        Class clz0 = meth.getDeclaringClass();
        Class<Handler> clz = (Class<Handler>)clz0;
        Handler h = clz.newInstance(); // TODO: we don't need to create new instances; handler is stateless
      }
      catch( InstantiationException | IllegalArgumentException | IllegalAccessException ignore ) { }
    }
    return al.toArray(new String[al.size()]);
  }

  // ---------------------------------------------------------------------
  // Download logs support
  // ---------------------------------------------------------------------

  private String getOutputLogStem() {
    String pattern = "yyyyMMdd_hhmmss";
    SimpleDateFormat formatter = new SimpleDateFormat(pattern);
    String now = formatter.format(new Date());

    return "h2ologs_" + now;
  }

  private byte[] zipLogs(byte[][] results, byte[] clientResult, String topDir) throws IOException {
    int l = 0;
    assert H2O.CLOUD._memary.length == results.length : "Unexpected change in the cloud!";
    for (int i = 0; i<results.length;l+=results[i++].length);
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

    NanoHTTPD.Response res = new Response(NanoHTTPD.HTTP_OK,NanoHTTPD.MIME_DEFAULT_BINARY, new ByteArrayInputStream(finalZipByteArray));
    res.addHeader("Content-Length", Long.toString(finalZipByteArray.length));
    res.addHeader("Content-Disposition", "attachment; filename="+outputFileStem + ".zip");
    return res;
  }


  // ---------------------------------------------------------------------
  // Download NPS support
  // ---------------------------------------------------------------------

  private Response downloadNps(String categoryName, String keyName) {
    NodePersistentStorage nps = H2O.getNPS();
    AtomicLong length = new AtomicLong();
    InputStream is = nps.get(categoryName, keyName, length);
    NanoHTTPD.Response res = new Response(NanoHTTPD.HTTP_OK, NanoHTTPD.MIME_DEFAULT_BINARY, is);
    res.addHeader("Content-Length", Long.toString(length.get()));
    res.addHeader("Content-Disposition", "attachment; filename="+keyName + ".flow");
    return res;
  }

  private Response redirectToFlow() {
    StringBuilder sb = new StringBuilder();
    NanoHTTPD.Response res = new Response(NanoHTTPD.HTTP_REDIRECT, NanoHTTPD.MIME_PLAINTEXT, sb.toString());
    res.addHeader("Location", "/flow/index.html");
    return res;
  }
}
