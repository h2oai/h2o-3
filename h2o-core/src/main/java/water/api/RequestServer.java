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
 * @see water.api.Handler a class which contains HTTP request handler methods and other helpers
 * @see water.api.Schema a class which provides a stable external representation for entities and request parameters
 * @see #register(String, String, String, Class, String, String, HandlerFactory) registers a specific handler method for
 *      the supplied URI pattern and HTTP method (GET, POST, DELETE, PUT)
 */
public class RequestServer extends NanoHTTPD {

  // Returned in REST API responses as X-h2o-rest-api-version
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



  //------ Route Registration ------------------------------------------------------------------------------------------

  static {
    // Data
    register("createFrame",
        "POST /3/CreateFrame", CreateFrameHandler.class, "run",
        "Create a synthetic H2O Frame with random data. You can specify the number of rows/columns, as well as column" +
        " types: integer, real, boolean, time, string, categorical. The frame may also have a dedicated \"response\" " +
        "column, and some of the entries in the dataset may be created as missing.");

    register("splitFrame",
        "POST /3/SplitFrame", SplitFrameHandler.class, "run",
        "Split an H2O Frame.");

    register("_interaction_run",
        "POST /3/Interaction", InteractionHandler.class, "run",
        "Create interactions between categorical columns.");

    register("_missingInserter_run",
        "POST /3/MissingInserter", MissingInserterHandler.class, "run",
        "Insert missing values.");

    register("_dctTransformer_run",
        "POST /99/DCTTransformer", DCTTransformerHandler.class, "run",
        "Row-by-row discrete cosine transforms in 1D, 2D and 3D.");

    register("_tabulate_run",
        "POST /99/Tabulate", TabulateHandler.class, "run",
        "Tabulate one column vs another.");

    register("importFiles_deprecated",
        "GET /3/ImportFiles", ImportFilesHandler.class, "importFiles",
        "[DEPRECATED] Import raw data files into a single-column H2O Frame.");

    register("importFiles",
        "POST /3/ImportFiles", ImportFilesHandler.class, "importFiles",
        "Import raw data files into a single-column H2O Frame.");

    register("importSqlTable",
        "POST /99/ImportSQLTable", ImportSQLTableHandler.class, "importSQLTable",
        "Import SQL table into an H2O Frame.");

    register("_parseSetup_guessSetup",
        "POST /3/ParseSetup", ParseSetupHandler.class, "guessSetup",
        "Guess the parameters for parsing raw byte-oriented data into an H2O Frame.");

    register("_parse_parse",
        "POST /3/Parse", ParseHandler.class, "parse",
        "Parse a raw byte-oriented Frame into a useful columnar data Frame."); // NOTE: prefer POST due to higher content limits

    register("parseSvmLight",
        "POST /3/ParseSVMLight", ParseHandler.class, "parseSVMLight",
        "Parse a raw byte-oriented Frame into a useful columnar data Frame."); // NOTE: prefer POST due to higher content limits

    // Admin
    register("cloudStatus",
        "GET /3/Cloud", CloudHandler.class, "status",
        "Determine the status of the nodes in the H2O cloud.");

    register("_cloud_head",
        "HEAD /3/Cloud", CloudHandler.class, "head",
        "Determine the status of the nodes in the H2O cloud.");

    register("jobs",
        "GET /3/Jobs", JobsHandler.class, "list",
        "Get a list of all the H2O Jobs (long-running actions).");

    register("timeline",
        "GET /3/Timeline", TimelineHandler.class, "fetch",
        "Debugging tool that provides information on current communication between nodes.");

    register("profiler",
        "GET /3/Profiler", ProfilerHandler.class, "fetch",
        "Report real-time profiling information for all nodes (sorted, aggregated stack traces).");

    register("stacktraces",
        "GET /3/JStack", JStackHandler.class, "fetch",
        "Report stack traces for all threads on all nodes.");

    register("testNetwork",
        "GET /3/NetworkTest", NetworkTestHandler.class, "fetch",
        "Run a network test to measure the performance of the cluster interconnect.");

    register("unlockAllKeys",
        "POST /3/UnlockKeys", UnlockKeysHandler.class, "unlock",
        "Unlock all keys in the H2O distributed K/V store, to attempt to recover from a crash.");

    register("shutdownCluster",
        "POST /3/Shutdown", ShutdownHandler.class, "shutdown",
        "Shut down the cluster.");

    // REST only, no html:
    register("about",
        "GET /3/About", AboutHandler.class, "get",
        "Return information about this H2O cluster.");

    register("endpoints",
        "GET /3/Metadata/endpoints", MetadataHandler.class, "listRoutes",
        "Return the list of (almost) all REST API endpoints.");

    register("endpoint",
        "GET /3/Metadata/endpoints/{path}", MetadataHandler.class, "fetchRoute",
        "Return the REST API endpoint metadata, including documentation, for the endpoint specified by path or index.");

    register("schemaForClass",
        "GET /3/Metadata/schemaclasses/{classname}", MetadataHandler.class, "fetchSchemaMetadataByClass",
        "Return the REST API schema metadata for specified schema class.");

    register("schema",
        "GET /3/Metadata/schemas/{schemaname}", MetadataHandler.class, "fetchSchemaMetadata",
        "Return the REST API schema metadata for specified schema.");

    register("schemas",
        "GET /3/Metadata/schemas", MetadataHandler.class, "listSchemas",
        "Return list of all REST API schemas.");

    register("typeaheadFileSuggestions",
        "GET /3/Typeahead/files", TypeaheadHandler.class, "files",
        "Typeahead hander for filename completion.");

    register("job",
        "GET /3/Jobs/{job_id}", JobsHandler.class, "fetch",
        "Get the status of the given H2O Job (long-running action).");

    register("cancelJob",
        "POST /3/Jobs/{job_id}/cancel", JobsHandler.class, "cancel",
        "Cancel a running job.");

    register("_find_find",
        "GET /3/Find", FindHandler.class, "find",
        "Find a value within a Frame.");

    register("exportFrame_deprecated",
        "GET /3/Frames/{frame_id}/export/{path}/overwrite/{force}", FramesHandler.class, "export",
        "[DEPRECATED] Export a Frame to the given path with optional overwrite.");

    register("exportFrame",
        "POST /3/Frames/{frame_id}/export", FramesHandler.class, "export",
        "Export a Frame to the given path with optional overwrite.");

    register("frameColumnSummary",
        "GET /3/Frames/{frame_id}/columns/{column}/summary", FramesHandler.class, "columnSummary",
        "Return the summary metrics for a column, e.g. min, max, mean, sigma, percentiles, etc.");

    register("frameColumnDomain",
        "GET /3/Frames/{frame_id}/columns/{column}/domain", FramesHandler.class, "columnDomain",
        "Return the domains for the specified categorical column (\"null\" if the column is not a categorical).");

    register("frameColumn",
        "GET /3/Frames/{frame_id}/columns/{column}", FramesHandler.class, "column",
        "Return the specified column from a Frame.");

    register("frameColumns",
        "GET /3/Frames/{frame_id}/columns", FramesHandler.class, "columns",
        "Return all the columns from a Frame.");

    register("frameSummary",
        "GET /3/Frames/{frame_id}/summary", FramesHandler.class, "summary",
        "Return a Frame, including the histograms, after forcing computation of rollups.");

    register("frame",
        "GET /3/Frames/{frame_id}", FramesHandler.class, "fetch",
        "Return the specified Frame.");

    register("frames",
        "GET /3/Frames", FramesHandler.class, "list",
        "Return all Frames in the H2O distributed K/V store.");

    register("deleteFrame",
        "DELETE /3/Frames/{frame_id}", FramesHandler.class, "delete",
        "Delete the specified Frame from the H2O distributed K/V store.");

    register("deleteAllFrames",
        "DELETE /3/Frames", FramesHandler.class, "deleteAll",
        "Delete all Frames from the H2O distributed K/V store.");


    // Handle models
    register("model",
        "GET /3/Models/{model_id}", ModelsHandler.class, "fetch",
        "Return the specified Model from the H2O distributed K/V store, optionally with the list of compatible Frames.");

    register("models",
        "GET /3/Models", ModelsHandler.class, "list",
        "Return all Models from the H2O distributed K/V store.");

    register("deleteModel",
        "DELETE /3/Models/{model_id}", ModelsHandler.class, "delete",
        "Delete the specified Model from the H2O distributed K/V store.");

    register("deleteAllModels",
        "DELETE /3/Models", ModelsHandler.class, "deleteAll",
        "Delete all Models from the H2O distributed K/V store.");

    // Get java code for models as
    register("_models_fetchPreview",
        "GET /3/Models.java/{model_id}/preview", ModelsHandler.class, "fetchPreview",
        "Return potentially abridged model suitable for viewing in a browser (currently only used for java model code).");

    // Register resource also with .java suffix since we do not want to break API
    register("_models_fetchJavaCode",
        "GET /3/Models.java/{model_id}", ModelsHandler.class, "fetchJavaCode",
        "[DEPRECATED] Return the stream containing model implementation in Java code.");

    // Model serialization - import/export calls
    register("importModel",
        "POST /99/Models.bin/{model_id}", ModelsHandler.class, "importModel",
        "Import given binary model into H2O.");

    register("exportModel",
        "GET /99/Models.bin/{model_id}", ModelsHandler.class, "exportModel",
        "Export given model.");


    register("grid",
        "GET /99/Grids/{grid_id}", GridsHandler.class, "fetch",
        "Return the specified grid search result.");

    register("grids",
        "GET /99/Grids", GridsHandler.class, "list",
        "Return all grids from H2O distributed K/V store.");


    register("newModelId",
        "POST /3/ModelBuilders/{algo}/model_id", ModelBuildersHandler.class, "calcModelId",
        "Return a new unique model_id for the specified algorithm.");

    register("modelBuilder",
        "GET /3/ModelBuilders/{algo}", ModelBuildersHandler.class, "fetch",
        "Return the Model Builder metadata for the specified algorithm.");

    register("modelBuilders",
        "GET /3/ModelBuilders", ModelBuildersHandler.class, "list",
        "Return the Model Builder metadata for all available algorithms.");


    // TODO: filtering isn't working for these first four; we get all results:
    register("_mmFetch1",
        "GET /3/ModelMetrics/models/{model}/frames/{frame}", ModelMetricsHandler.class, "fetch",
        "Return the saved scoring metrics for the specified Model and Frame.");

    register("_mmDelete1",
        "DELETE /3/ModelMetrics/models/{model}/frames/{frame}", ModelMetricsHandler.class, "delete",
        "Return the saved scoring metrics for the specified Model and Frame.");

    register("_mmFetch2",
        "GET /3/ModelMetrics/models/{model}", ModelMetricsHandler.class, "fetch",
        "Return the saved scoring metrics for the specified Model.");

    register("_mmFetch3",
        "GET /3/ModelMetrics/frames/{frame}/models/{model}", ModelMetricsHandler.class, "fetch",
        "Return the saved scoring metrics for the specified Model and Frame.");

    register("_mmDelete2",
        "DELETE /3/ModelMetrics/frames/{frame}/models/{model}", ModelMetricsHandler.class, "delete",
        "Return the saved scoring metrics for the specified Model and Frame.");

    register("_mmFetch4",
        "GET /3/ModelMetrics/frames/{frame}", ModelMetricsHandler.class, "fetch",
        "Return the saved scoring metrics for the specified Frame.");

    register("_mmFetch5",
        "GET /3/ModelMetrics", ModelMetricsHandler.class, "fetch",
        "Return all the saved scoring metrics.");

    register("_mm_score",
        "POST /3/ModelMetrics/models/{model}/frames/{frame}", ModelMetricsHandler.class, "score",
        "Return the scoring metrics for the specified Frame with the specified Model.  If the Frame has already been " +
        "scored with the Model then cached results will be returned; otherwise predictions for all rows in the Frame " +
        "will be generated and the metrics will be returned.");

    register("_predictions_predict1",
        "POST /3/Predictions/models/{model}/frames/{frame}", ModelMetricsHandler.class, "predict",
        "Score (generate predictions) for the specified Frame with the specified Model.  Both the Frame of " +
        "predictions and the metrics will be returned.");

    register("_predictions_predict2",
        "POST /4/Predictions/models/{model}/frames/{frame}", ModelMetricsHandler.class, "predictAsync",
        "Score (generate predictions) for the specified Frame with the specified Model.  Both the Frame of " +
        "predictions and the metrics will be returned.");

    register("_waterMeterCpuTicks_fetch",
        "GET /3/WaterMeterCpuTicks/{nodeidx}", WaterMeterCpuTicksHandler.class, "fetch",
        "Return a CPU usage snapshot of all cores of all nodes in the H2O cluster.");

    register("_waterMeterIo_fetch",
        "GET /3/WaterMeterIo/{nodeidx}", WaterMeterIoHandler.class, "fetch",
        "Return IO usage snapshot of all nodes in the H2O cluster.");

    register("_waterMeterIo_fetchAll",
        "GET /3/WaterMeterIo", WaterMeterIoHandler.class, "fetch_all",
        "Return IO usage snapshot of all nodes in the H2O cluster.");

    // Node persistent storage
    register("npsContains",
        "GET /3/NodePersistentStorage/categories/{category}/names/{name}/exists",
        NodePersistentStorageHandler.class, "exists",
        "Return true or false.");

    register("npsExistsCategory",
        "GET /3/NodePersistentStorage/categories/{category}/exists", NodePersistentStorageHandler.class, "exists",
        "Return true or false.");

    register("npsEnabled",
        "GET /3/NodePersistentStorage/configured", NodePersistentStorageHandler.class, "configured",
        "Return true or false.");

    register("npsPut",
        "POST /3/NodePersistentStorage/{category}/{name}", NodePersistentStorageHandler.class, "put_with_name",
        "Store a named value.");

    register("npsGet",
        "GET /3/NodePersistentStorage/{category}/{name}", NodePersistentStorageHandler.class, "get_as_string",
        "Return value for a given name.");

    register("npsRemove",
        "DELETE /3/NodePersistentStorage/{category}/{name}", NodePersistentStorageHandler.class, "delete",
        "Delete a key.");

    register("npsCreateCategory",
        "POST /3/NodePersistentStorage/{category}", NodePersistentStorageHandler.class, "put",
        "Store a value.");

    register("npsKeys",
        "GET /3/NodePersistentStorage/{category}", NodePersistentStorageHandler.class, "list",
        "Return all keys stored for a given category.");

    // TODO: register("DELETE /3/ModelMetrics/models/{model}/frames/{frame}", ModelMetricsHandler.class, "delete");
    // TODO: register("DELETE /3/ModelMetrics/frames/{frame}/models/{model}", ModelMetricsHandler.class, "delete");
    // TODO: register("DELETE /3/ModelMetrics/frames/{frame}", ModelMetricsHandler.class, "delete");
    // TODO: register("DELETE /3/ModelMetrics/models/{model}", ModelMetricsHandler.class, "delete");
    // TODO: register("DELETE /3/ModelMetrics", ModelMetricsHandler.class, "delete");
    // TODO: register("POST /3/Predictions/models/{model}/frames/{frame}", ModelMetricsHandler.class, "predict");

    // Log file management.
    // Note:  Hacky pre-route cutout of "/3/Logs/download" is done above in a non-json way.
    register("_logs_fetch",
        "GET /3/Logs/nodes/{nodeidx}/files/{name}", LogsHandler.class, "fetch",
        "Get named log file for a node.");


    // ModelBuilder Handler registration must be done for each algo in the application class
    // (e.g., H2OApp), because the Handler class is parameterized by the associated Schema,
    // and this is different for each ModelBuilder in order to handle its parameters in a
    // typesafe way:
    //   register("POST /3/ModelBuilders/{algo}", ModelBuildersHandler.class, "train", "Train {algo}");
    //

    register("killDash3",
        "GET /3/KillMinus3", KillMinus3Handler.class, "killm3",
        "Kill minus 3 on *this* node");

    register("_rapids_exec",
        "POST /99/Rapids", RapidsHandler.class, "exec",
        "Execute an Rapids AST.");

    register("_assembly_toJava",
        "GET /99/Assembly.java/{assembly_id}/{pojo_name}", AssemblyHandler.class, "toJava",
        "Generate a Java POJO from the Assembly");

    register("_assembly_fit",
        "POST /99/Assembly", AssemblyHandler.class, "fit",
        "Fit an assembly to an input frame");

    register("_downloadDataset_fetch",
        "GET /3/DownloadDataset", DownloadDataHandler.class, "fetch",
        "Download dataset as a CSV.");

    register("_downloadDataset_fetchStreaming",
        "GET /3/DownloadDataset.bin", DownloadDataHandler.class, "fetchStreaming",
        "Download dataset as a CSV.");

    register("deleteKey",
        "DELETE /3/DKV/{key}", RemoveHandler.class, "remove",
        "Remove an arbitrary key from the H2O distributed K/V store.");

    register("deleteAllKeys",
        "DELETE /3/DKV", RemoveAllHandler.class, "remove",
        "Remove all keys from the H2O distributed K/V store.");

    register("_logAndEcho_echo",
        "POST /3/LogAndEcho", LogAndEchoHandler.class, "echo",
        "Save a message to the H2O logfile.");

    register("newSession",
        "GET /3/InitID", InitIDHandler.class, "issue",
        "Issue a new session ID.");

    register("endSession",
        "DELETE /3/InitID", InitIDHandler.class, "endSession",
        "End a session.");

    register("garbageCollect",
        "POST /3/GarbageCollect", GarbageCollectHandler.class, "gc",
        "Explicitly call System.gc().");

    register("_sample_status",
        "GET /99/Sample", CloudHandler.class, "status",
        "Example of an experimental endpoint.  Call via /EXPERIMENTAL/Sample.  Experimental endpoints can change at " +
        "any moment.");
  }

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

    Schema.registerAllSchemasIfNecessary();

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
