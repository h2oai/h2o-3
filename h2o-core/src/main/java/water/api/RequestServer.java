package water.api;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import water.*;
import water.exceptions.H2OAbstractRuntimeException;
import water.exceptions.H2ONotFoundArgumentException;
import water.fvec.Frame;
import water.nbhm.NonBlockingHashMap;
import water.parser.ParseSetupHandler;
import water.util.GetLogsFromNode;
import water.util.Log;
import water.util.RString;

import java.io.*;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * A Handler class is parameterized by the kind of Schema that it accepts
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
 * @see #register(String, String, Class, String, String, String[], String) registers a specific handler method for the supplied URI pattern and HTTP method (GET, POST, DELETE, PUT)
 */
public class RequestServer extends NanoHTTPD {

  private static final int DEFAULT_VERSION = 2;

  static public RequestServer SERVER;
  private RequestServer( ServerSocket socket ) throws IOException { super(socket,null); }

  private static final String _htmlTemplateFromFile = loadTemplate("/page.html");
  private static volatile String _htmlTemplate = "";


  // Handlers ------------------------------------------------------------

  // An array of regexs-over-URLs and handling Methods.
  // The list is searched in-order, first match gets dispatched.
  private static final LinkedHashMap<Pattern,Route> _routes = new LinkedHashMap<>();   // explicit routes registered below
  private static final LinkedHashMap<Pattern,Route> _fallbacks= new LinkedHashMap<>(); // routes that are version fallbacks (e.g., we asked for v5 but v2 is the latest)
  public static final int numRoutes() { return _routes.size(); }
  public static final Collection<Route> routes() { return _routes.values(); }

  private static HashMap<String, ArrayList<MenuItem>> _navbar = new HashMap<>();
  private static ArrayList<String> _navbarOrdering = new ArrayList<>();

  // NOTE!
  // URL patterns are searched in order.  If you have two patterns that can match on the same URL
  // (e.g., /foo/baz and /foo) you MUST register them in decreasing order of specificity.
  static {
    // Data

    addToNavbar(register("/CreateFrame","GET",CreateFrameHandler.class,"run"         ,"Something something something."),"/CreateFrame", "Create Frame",  "Data");
    addToNavbar(register("/ImportFiles","GET",ImportFilesHandler.class,"importFiles" ,"Import raw data files into a single-column H2O Frame."), "/ImportFiles", "Import Files",  "Data");
    addToNavbar(register("/ParseSetup" ,"POST",ParseSetupHandler.class,"guessSetup"  ,"Guess the parameters for parsing raw byte-oriented data into an H2O Frame."),"/ParseSetup","ParseSetup",    "Data");
    addToNavbar(register("/ParseSetup" ,"GET",ParseSetupHandler .class,"guessSetup"  ,"Guess the parameters for parsing raw byte-oriented data into an H2O Frame.  DEPRECATED: Use POST because of its higher data limit."),"/ParseSetup","ParseSetup",    "Data");

    addToNavbar(register("/Parse"      ,"POST",ParseHandler     .class,"parse"       ,"Parse a raw byte-oriented Frame into a useful columnar data Frame."),"/Parse"      , "Parse",         "Data"); // NOTE: prefer POST due to higher content limits
    addToNavbar(register("/Parse"      ,"GET",ParseHandler      .class,"parse"       ,"Parse a raw byte-oriented Frame into a useful columnar data Frame.  DEPRECATED: Use POST because of its higher data limit."),"/Parse"      , "Parse",         "Data");
    addToNavbar(register("/Inspect"    ,"GET",InspectHandler    .class,"inspect"     ,"View an arbitrary value from the distributed K/V store."),"/Inspect"    , "Inspect",       "Data");

    // Admin
    addToNavbar(register("/Cloud"      ,"GET",CloudHandler      .class,"status"      ,"Determine the status of the nodes in the H2O cloud."),"/Cloud"      , "Cloud",         "Admin");
    register("/Cloud", "HEAD", CloudHandler.class, "status", "Determine the status of the nodes in the H2O cloud.");
    addToNavbar(register("/Jobs"       ,"GET", JobsHandler.class, "list", "Get a list of all the H2O Jobs (long-running actions)."), "/Jobs", "Jobs", "Admin");
    addToNavbar(register("/Timeline"   ,"GET",TimelineHandler   .class,"fetch"       ,"Something something something."),"/Timeline"   , "Timeline",      "Admin");
    addToNavbar(register("/Profiler"   ,"GET",ProfilerHandler   .class,"fetch"       ,"Something something something."),"/Profiler"   , "Profiler",      "Admin");
    addToNavbar(register("/JStack"     ,"GET",JStackHandler     .class,"fetch"       ,"Something something something."),"/JStack"     , "Stack Dump",    "Admin");
    addToNavbar(register("/UnlockKeys" ,"GET",UnlockKeysHandler .class,"unlock"      ,"Unlock all keys in the H2O distributed K/V store, to attempt to recover from a crash."),"/UnlockKeys" , "Unlock Keys",   "Admin");
    addToNavbar(register("/Shutdown"   ,"POST",ShutdownHandler  .class,"shutdown"    ,"Shut down the cluster")         , "/Shutdown"  , "Shutdown",      "Admin");

    // Help and Tutorials get all the rest...
    addToNavbar(register("/Tutorials"  ,"GET",TutorialsHandler  .class,"nop"         ,"H2O tutorials."),"/Tutorials"  , "Tutorials Home","Help");
    register("/"           ,"GET",TutorialsHandler  .class,"nop"                     ,"H2O tutorials."); // TODO: this should hit tutorials if .html, but REST info otherwise

    initializeNavBar();

    // REST only, no html:

    register("/3/About"                                              ,"GET"   ,AboutHandler.class, "get",
            "Return information about this H2O.");

    register("/1/Metadata/endpoints/(?<num>[0-9]+)"                  ,"GET"   ,DocsHandler.class, "fetchRoute",                       new String[] {"num"},
      "Return the REST API endpoint metadata, including documentation, for the endpoint specified by number.");
    register("/1/Metadata/endpoints/(?<path>.*)"                     ,"GET"   ,DocsHandler.class, "fetchRoute",                       new String[] {"path"},
      "Return the REST API endpoint metadata, including documentation, for the endpoint specified by path.");
    register("/1/Metadata/endpoints"                                 ,"GET"   ,DocsHandler.class, "listRoutes",
      "Return a list of all the REST API endpoints.");

    register("/1/Metadata/schemaclasses/(?<classname>.*)"            ,"GET"   ,DocsHandler.class, "fetchSchemaMetadataByClass", new String[] {"classname"},
            "Return the REST API schema metadata for specified schema class.");
    register("/1/Metadata/schemas/(?<schemaname>.*)"                 ,"GET"   ,DocsHandler.class, "fetchSchemaMetadata", new String[] {"schemaname"},
            "Return the REST API schema metadata for specified schema.");
    register("/1/Metadata/schemas"                                   ,"GET"   ,DocsHandler.class, "listSchemas",
            "Return list of all REST API schemas.");


    register("/Typeahead/files"                                  ,"GET",TypeaheadHandler.class, "files",
      "Typehead hander for filename completion.");
    register("/Jobs/(?<key>.*)"                                  ,"GET",JobsHandler     .class, "fetch", new String[] {"key"},
      "Get the status of the given H2O Job (long-running action).");

    register("/Find"                                             ,"GET"   ,FindHandler.class,    "find",
      "Find a value within a Frame.");

    register("/3/Frames/(?<key>.*)/columns/(?<column>.*)/summary","GET"   ,FramesHandler.class, "columnSummary", "columnSummaryDocs", new String[] {"key", "column"},
      "Return the summary metrics for a column, e.g. mins, maxes, mean, sigma, percentiles, etc.");
    register("/3/Frames/(?<key>.*)/columns/(?<column>.*)"        ,"GET"   ,FramesHandler.class, "column",                             new String[] {"key", "column"},
      "Return the specified column from a Frame.");
    register("/3/Frames/(?<key>.*)/columns"                      ,"GET"   ,FramesHandler.class, "columns",                            new String[] {"key"},
      "Return all the columns from a Frame.");
    register("/3/Frames/(?<key>.*)"                              ,"GET"   ,FramesHandler.class, "fetch",                              new String[] {"key"},
      "Return the specified Frame.");
    register("/3/Frames"                                         ,"GET"   ,FramesHandler.class, "list",
      "Return all Frames in the H2O distributed K/V store.");
    register("/2/Frames"                                         ,"GET"   ,FramesHandler.class, "list_or_fetch",
      "Return all Frames in the H2O distributed K/V store (old output format)."); // uses ?key=
    register("/3/Frames/(?<key>.*)"                              ,"DELETE",FramesHandler.class, "delete",                             new String[] {"key"},
      "Delete the specified Frame from the H2O distributed K/V store.");
    register("/3/Frames"                                         ,"DELETE",FramesHandler.class, "deleteAll",
      "Delete all Frames from the H2O distributed K/V store.");

    register("/3/Models/(?<key>.*)"                              ,"GET"   ,ModelsHandler.class, "fetch",                              new String[] {"key"},
      "Return the specified Model from the H2O distributed K/V store, optionally with the list of compatible Frames.");
    register("/3/Models"                                         ,"GET"   ,ModelsHandler.class, "list",
      "Return all Models from the H2O distributed K/V store.");
    register("/3/Models/(?<key>.*)"                              ,"DELETE",ModelsHandler.class, "delete",                             new String[] {"key"},
      "Delete the specified Model from the H2O distributed K/V store.");
    register("/3/Models"                                         ,"DELETE",ModelsHandler.class, "deleteAll",
      "Delete all Models from the H2O distributed K/V store.");

    register("/ModelBuilders/(?<algo>.*)"                      ,"GET"   ,ModelBuildersHandler.class, "fetch",                       new String[] {"algo"},
      "Return the Model Builder metadata for the specified algorithm.");
    register("/ModelBuilders"                                  ,"GET"   ,ModelBuildersHandler.class, "list",
      "Return the Model Builder metadata for all available algorithms.");

    // TODO: filtering isn't working for these first four; we get all results:
    register("/3/ModelMetrics/models/(?<model>.*)/frames/(?<frame>.*)"    ,"GET"   ,ModelMetricsHandler.class, "fetch", new String[] {"model", "frame"},
      "Return the saved scoring metrics for the specified Model and Frame.");
    register("/3/ModelMetrics/models/(?<model>.*)"                        ,"GET"   ,ModelMetricsHandler.class, "fetch",  new String[] {"model"},
      "Return the saved scoring metrics for the specified Model.");
    register("/3/ModelMetrics/frames/(?<frame>.*)/models/(?<model>.*)"    ,"GET"   ,ModelMetricsHandler.class, "fetch", new String[] {"frame", "model"},
      "Return the saved scoring metrics for the specified Model and Frame.");
    register("/3/ModelMetrics/frames/(?<frame>.*)"                        ,"GET"   ,ModelMetricsHandler.class, "fetch",  new String[] {"frame"},
      "Return the saved scoring metrics for the specified Frame.");
    register("/3/ModelMetrics"                                            ,"GET"   ,ModelMetricsHandler.class, "fetch",
      "Return all the saved scoring metrics.");

    register("/3/ModelMetrics/models/(?<model>.*)/frames/(?<frame>.*)"    ,"POST"  ,ModelMetricsHandler.class, "score", new String[] {"model", "frame"},
      "Return the scoring metrics for the specified Frame with the specified Model.  If the Frame has already been scored with the Model then cached results will be returned; otherwise predictions for all rows in the Frame will be generated and the metrics will be returned.");
    register("/3/Predictions/models/(?<model>.*)/frames/(?<frame>.*)"     ,"POST"  ,ModelMetricsHandler.class, "predict", new String[] {"model", "frame"},
      "Score (generate predictions) for the specified Frame with the specified Model.  Both the Frame of predictions and the metrics will be returned.");

    register("/1/WaterMeterCpuTicks/(?<nodeidx>.*)"                         ,"GET"   ,WaterMeterCpuTicksHandler.class, "fetch", new String[] {"nodeidx"},
      "Return a CPU usage snapshot of all cores of all nodes in the H2O cluster.");

    // Node persistent storage
    register("/3/NodePersistentStorage/(?<category>.*)/(?<name>.*)"       ,"POST"  ,NodePersistentStorageHandler.class, "put_with_name", new String[] {"category", "name"}, "Store a named value.");
    register("/3/NodePersistentStorage/(?<category>.*)/(?<name>.*)"       ,"GET"   ,NodePersistentStorageHandler.class, "get_as_string", new String[] {"category", "name"}, "Return value for a given name.");
    register("/3/NodePersistentStorage/(?<category>.*)/(?<name>.*)"       ,"DELETE",NodePersistentStorageHandler.class, "delete",        new String[] {"category", "name"}, "Delete a key.");
    register("/3/NodePersistentStorage/(?<category>.*)"                   ,"POST"  ,NodePersistentStorageHandler.class, "put",           new String[] {"category"},         "Store a value.");
    register("/3/NodePersistentStorage/(?<category>.*)"                   ,"GET"   ,NodePersistentStorageHandler.class, "list",          new String[] {"category"},         "Return all keys stored for a given category.");

    // TODO: register("/3/ModelMetrics/models/(?<model>.*)/frames/(?<frame>.*)"    ,"DELETE",ModelMetricsHandler.class, "delete", new String[] {"model", "frame"});
    // TODO: register("/3/ModelMetrics/frames/(?<frame>.*)/models/(?<model>.*)"    ,"DELETE",ModelMetricsHandler.class, "delete", new String[] {"frame", "model"});
    // TODO: register("/3/ModelMetrics/frames/(?<frame>.*)"                        ,"DELETE",ModelMetricsHandler.class, "delete", new String[] {"frame"});
    // TODO: register("/3/ModelMetrics/models/(?<model>.*)"                        ,"DELETE",ModelMetricsHandler.class, "delete", new String[] {"model"});
    // TODO: register("/3/ModelMetrics"                                            ,"DELETE",ModelMetricsHandler.class, "delete");

    // TODO: register("/3/Predictions/models/(?<model>.*)/frames/(?<frame>.*)"    ,"POST"  ,ModelMetricsHandler.class, "predict", new String[] {"model", "frame"});


    // Log file management.
    // Note:  Hacky pre-route cutout of "/3/Logs/download" is done above in a non-json way.
    register("/3/Logs/nodes/(?<nodeidx>.*)/files/default",     "GET", LogsHandler.class, "fetch", new String[] {"nodeidx"},         "Get default log file for a node.");
    register("/3/Logs/nodes/(?<nodeidx>.*)/files/(?<name>.*)", "GET", LogsHandler.class, "fetch", new String[] {"nodeidx", "name"}, "Get named log file for a node.");


    // ModelBuilder Handler registration must be done for each algo in the application class
    // (e.g., H2OApp), because the Handler class is parameterized by the associated Schema,
    // and this is different for each ModelBuilder in order to handle its parameters in a
    // typesafe way:
    //
    // register("/2/ModelBuilders/(?<algo>.*)"                      ,"POST"  ,ModelBuildersHandler.class, "train", new String[] {"algo"});

    register("/Rapids"                                           ,"POST"  ,RapidsHandler.class, "exec", "Something something R exec something.");
    register("/Rapids"                                           ,"GET"   ,RapidsHandler.class, "exec", "Something something R exec something.  DEPRECATED: Use POST because of its higher data limit.");
    register("/Rapids/isEval"                                    ,"GET"   ,RapidsHandler.class, "isEvaluated", "something something r exec something.");
    register("/DownloadDataset"                                  ,"GET"   ,DownloadDataHandler.class, "fetch", "Download something something.");
    register("/Remove"                                           ,"DELETE",RemoveHandler.class, "remove", "Remove an arbitrary key from the H2O distributed K/V store.");
    register("/RemoveAll"                                        ,"DELETE",RemoveAllHandler.class, "remove", "Remove all keys from the H2O distributed K/V store.");
    register("/LogAndEcho"                                       ,"POST"  ,LogAndEchoHandler.class, "echo", "Save a message to the H2O logfile.");
    register("/Quantiles"                                        ,"GET"   ,QuantilesHandler.class, "quantiles", "Return quantiles for the specified column of the specified Frame."); // TODO: move under Frames!
    register("/InitID"                                           ,"GET"   ,InitIDHandler.class, "issue", "Issue a new session ID.");
  }

  @Deprecated
  /**
   * @deprecated All routes should have doc methods.
   */
  public static Route register(String uri_pattern, String http_method, Class<? extends Handler> handler_class, String handler_method, String summary) {
    return register(uri_pattern, http_method, handler_class, handler_method, null, new String[]{}, summary);
  }

  @Deprecated
  /**
   * @deprecated All routes should have doc methods.
   */
  public static Route register(String uri_pattern, String http_method, Class<? extends Handler> handler_class, String handler_method, String[] path_params, String summary) {
    return register(uri_pattern, http_method, handler_class, handler_method, null, path_params, summary);
  }


  /**
   * Register an HTTP request handler for a given URI pattern, with no path parameters.
   * <p>
   * URIs which match this pattern will have their parameters collected from the query params.
   *
   * @param uri_pattern regular expression which matches the URL path for this request handler; parameters that are embedded in the path must be captured with &lt;code&gt;(?&lt;parm&gt;.*)&lt;/code&gt; syntax
   * @param http_method HTTP verb (GET, POST, DELETE) this handler will accept
   * @param handler_class class which contains the handler method
   * @param handler_method name of the handler method
   * @param doc_method name of a method which returns GitHub Flavored Markdown documentation for the request
   * @see Route
   * @see water.api.RequestServer
   * @return the Route for this request
   */
  public static Route register(String uri_pattern, String http_method, Class<? extends Handler> handler_class, String handler_method, String doc_method, String summary) {
    return register(uri_pattern, http_method, handler_class, handler_method, doc_method, new String[]{}, summary);
  }

  /**
   * Register an HTTP request handler method for a given URL pattern, with parameters extracted from the URI.
   * <p>
   * URIs which match this pattern will have their parameters collected from the path and from the query params
   *
   * @param uri_pattern_raw regular expression which matches the URL path for this request handler; parameters that are embedded in the path must be captured with &lt;code&gt;(?&lt;parm&gt;.*)&lt;/code&gt; syntax
   * @param http_method HTTP verb (GET, POST, DELETE) this handler will accept
   * @param handler_class class which contains the handler method
   * @param handler_method name of the handler method
   * @param doc_method name of a method which returns GitHub Flavored Markdown documentation for the request
   * @param path_params list of parameter names to extract from the uri_pattern; they are matched by name from the named pattern capture group
   * @param summary short help string which summarizes the functionality of this endpoint
   * @see Route
   * @see water.api.RequestServer
   * @return the Route for this request
   */
  public static Route register(String uri_pattern_raw, String http_method, Class<? extends Handler> handler_class, String handler_method, String doc_method, String[] path_params, String summary) {
    assert uri_pattern_raw.startsWith("/");

    // Search handler_class and all its superclasses for the method.
    Method meth = null;
    Method doc_meth = null;

    // TODO: move to ReflectionUtils:
    try {
      for (Method m : handler_class.getMethods()) {
        if (! m.getName().equals(handler_method)) continue;

        Class[] params = m.getParameterTypes();
        if (null == params || params.length != 2) continue;
        if (params[0] != Integer.TYPE) continue;
        if (! Schema.class.isAssignableFrom(params[1])) continue;

        meth = m;
        break;
      }

      if (null != doc_method)
        doc_meth = handler_class.getMethod(doc_method, new Class[]{int.class, StringBuffer.class});
    }
    catch (NoSuchMethodException e) {
      // ignore: H2O.fail below
    }

    if (null == meth)
      throw H2O.fail("Failed to find handler method: " + handler_method + " for handler class: " + handler_class);
    if (null != doc_method && null == doc_meth)
      throw H2O.fail("Failed to find doc method: " + doc_method + " for handler class: " + handler_class);


    if (uri_pattern_raw.matches("^/\\d+/.*")) {
      // register specifies a version
    } else {
      // register all versions
      uri_pattern_raw = "^(/\\d+)?" + uri_pattern_raw;
    }
    assert lookup(handler_method, uri_pattern_raw)==null; // Not shadowed
    Pattern uri_pattern = Pattern.compile(uri_pattern_raw);
    Route route = new Route(http_method, uri_pattern_raw, uri_pattern, summary, handler_class, meth, doc_meth, path_params);
    _routes.put(uri_pattern, route);
    return route;
  }


  static Pattern version_pattern = null;
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
    if (null == version_pattern) version_pattern = Pattern.compile("^/(\\d+)/(.*)");
    Matcher m = version_pattern.matcher(uri);
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
      Route generated = new Route(fallback._http_method, fallback_route_uri, fallback_route_pattern, fallback._summary, fallback._handler_class, fallback._handler_method, fallback._doc_method, fallback._path_params);
      _fallbacks.put(fallback_route_pattern, generated);
    }

    // Better be there in the _fallbacks cache now!
    return lookup(http_method, uri);
  }


  // Keep spinning until we get to launch the NanoHTTPD.  Launched in a
  // seperate thread (I'm guessing here) so the startup process does not hang
  // if the various web-port accesses causes Nano to hang on startup.
  public static Runnable start() {
    Runnable run=new Runnable() {
        @Override public void run()  {
          while( true ) {
            try {
              // Try to get the NanoHTTP daemon started
              synchronized(this) {
                SERVER = new RequestServer(water.init.NetworkInit._apiSocket);
                notifyAll();
              }
              break;
            } catch( Exception ioe ) {
              Log.err("Launching NanoHTTP server got ",ioe);
              try { Thread.sleep(1000); } catch( InterruptedException ignore ) { } // prevent denial-of-service
            }
          }
        }
      };
    new Thread(run, "Request Server launcher").start();
    return run;
  }

  void alwaysLogRequest(String uri, String method, Properties parms) {
    String log = String.format("%-4s %s", method, uri);
    for( Object arg : parms.keySet() ) {
      String value = parms.getProperty((String) arg);
      if( value != null && value.length() != 0 )
        log += " " + arg + "=" + value;
    }

    Log.httpd(log);
  }

    // Log all requests except the overly common ones
  void maybeLogRequest(String uri, String versioned_path, String pattern, Properties parms) {
    if (uri.endsWith(".css")) return;
    if (uri.endsWith(".js")) return;
    if (uri.endsWith(".png")) return;
    if (uri.endsWith(".ico")) return;
    if (uri.startsWith("/Typeahead")) return;
    if (uri.startsWith("/Cloud")) return;
    if (uri.contains("Progress")) return;

    Log.info("Path: " + versioned_path + ", route: " + pattern + ", parms: " + parms);
  }

  // Parse version number.  Java has no ref types, bleah, so return the version
  // number and the "parse pointer" by shift-by-16 compaction.
  // /1/xxx     --> version 1
  // /2/xxx     --> version 2
  // /v1/xxx    --> version 1
  // /v2/xxx    --> version 2
  // /latest/xxx--> DEFAULT_VERSION
  // /xxx       --> DEFAULT_VERSION
  private int parseVersion( String uri ) {
    if( uri.length() <= 1 || uri.charAt(0) != '/' ) // If not a leading slash, then I am confused
      return DEFAULT_VERSION;
    if( uri.startsWith("/latest") )
      return (("/latest".length())<<16)| DEFAULT_VERSION;
    int idx=1;                  // Skip the leading slash
    int version=0;
    char c = uri.charAt(idx);   // Allow both /### and /v###
    if( c=='v' ) c = uri.charAt(++idx);
    while( idx < uri.length() && '0' <= c && c <= '9' ) {
      version = version*10+(c-'0');
      c = uri.charAt(++idx);
    }
    // Allow versions > DEFAULT_VERSION
    if( idx > 10 || version < 1 || uri.charAt(idx) != '/' )
      return (0<<16)| DEFAULT_VERSION; // Failed number parse or baloney version
    // Happy happy version
    return (idx<<16)|version;
  }


  private void capturePathParms(Properties parms, String path, Route route) {
    Matcher m = route._url_pattern.matcher(path);
    if (! m.matches()) {
      throw H2O.fail("Routing regex error: Pattern matched once but not again for pattern: " + route._url_pattern.pattern() + " and path: " + path);
    }

    for (String key : route._path_params) {
      String val;
      try {
        val = m.group(key);
      }
      catch (IllegalArgumentException e) {
        throw H2O.fail("Missing request parameter in the URL: did not find " + key + " in the URL as expected; URL pattern: " + route._url_pattern.pattern() + " with expected parameters: " + Arrays.toString(route._path_params) + " for URL: " + path);
      }
      if (null != val)
        parms.put(key, val);
    }
  }

  // Top-level dispatch based on the URI.  Break down URI into parts;
  // e.g. /2/GBM.html/crunk?hex=some_hex breaks down into:
  //   version:      2
  //   requestType:  ".html"
  //   path:         "GBM/crunk"
  //   parms:        "{hex-->some_hex}"
  @Override public Response serve( String uri, String method, Properties header, Properties parms ) {
    // Jack priority for user-visible requests
    Thread.currentThread().setPriority(Thread.MAX_PRIORITY-1);

    // determine version
    int version = parseVersion(uri);
    int idx = version>>16;
    version &= 0xFFFF;
    String uripath = uri.substring(idx);

    // determine the request type
    RequestType type = RequestType.requestType(uripath);
    String path = type.requestName(uripath); // Strip suffix type from middle of URI
    String versioned_path = "/" + version + path;
    alwaysLogRequest(path, method, parms);

    // Blank response used by R's uri.exists("/")
    if (method.equals("HEAD") && uri.equals("/")) {
      Response r = new Response(HTTP_OK, MIME_PLAINTEXT, "");
      return r;
    }

    // Handle any URLs that bypass the route approach.  This is stuff that has abnormal non-JSON response payloads.
    if (uri.endsWith("/Logs/download")) {
      maybeLogRequest(path, versioned_path, "", parms);
      return downloadLogs();
    }

    // Load resources, or dispatch on handled requests
    try {
      // Find handler for url
      Route route = lookup(method, versioned_path);

      if (route != null && route._handler_class != CloudHandler.class && route._handler_class != TutorialsHandler.class && route._handler_class != TypeaheadHandler.class)
        Schema.registerAllSchemasIfNecessary();

      // if the request is not known, treat as resource request, or 404 if not found
      if( route == null )
        return getResource(version, type, uri);
      else if(route._handler_class ==  water.api.DownloadDataHandler.class) {
        // DownloadDataHandler will throw H2ONotFoundException if the resource is not found
        return wrapDownloadData(HTTP_OK, handle(type, route, version, parms));
      } else {
        capturePathParms(parms, versioned_path, route); // get any parameters like /Frames/<key>
        maybeLogRequest(path, versioned_path, route._url_pattern.pattern(), parms);
        return wrap(handle(type,route,version,parms),type);
      }
    }
    catch (H2OAbstractRuntimeException e) {
      H2OError error = e.toH2OError(uri);

      Log.warn(error._dev_msg);
      Log.warn(error._values.toJsonString());
      Log.warn((Object[])error._stacktrace);

      // Note: don't use Schema.schema(version, error) because we have to work at bootstrap:
      return wrap(new H2OErrorV1().fillFromImpl(error), type);
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

      Log.warn(error._dev_msg);
      Log.warn(error._values.toJsonString());
      Log.warn((Object[])error._stacktrace);

      // Note: don't use Schema.schema(version, error) because we have to work at bootstrap:
      return wrap(new H2OErrorV1().fillFromImpl(error), type);
    }
  }

  // Handling ------------------------------------------------------------------
  private Schema handle( RequestType type, Route route, int version, Properties parms ) throws Exception {
    switch( type ) {
    case html: // These request-types only dictate the response-type;
    case java: // the normal action is always done.
    case json:
    case xml: {
      Class<Handler> clz = (Class<Handler>)route._handler_class;
      // TODO: Handler no longer has state, so we can create single instances and put them in the Routes
      Handler h = clz.newInstance();
      return h.handle(version,route,parms); // Can throw any Exception the handler throws
    }
    case query:
    case help:
    default:
      throw H2O.unimpl();
    }
  }

  private Response wrap( Schema s, RequestType type ) {
    // Convert Schema to desired output flavor
    String http_response_header = H2OError.httpStatusHeader(HttpResponseStatus.OK.getCode());

    if (s instanceof SpecifiesHttpResponseCode)
      http_response_header = H2OError.httpStatusHeader(((SpecifiesHttpResponseCode) s).httpStatus());

    switch( type ) {
    case json:   return new Response(http_response_header, MIME_JSON, s.toJsonString());
    case xml:  //return new Response(http_code, MIME_XML , new String(S.writeXML (new AutoBuffer()).buf()));
    case java:
      throw H2O.unimpl();
    case html: {
      RString html = new RString(_htmlTemplate);
      html.replace("CONTENTS", s.writeHTML(new water.util.DocGen.HTML()).toString());
      return new Response(http_response_header, MIME_HTML, html.toString());
    }
    default:
      throw H2O.fail("Unknown type to wrap(): " + type);
    }
  }

  private Response wrapDownloadData(String http_code, Schema s) {
    DownloadDataV1 dd = (DownloadDataV1)s;
    Response res = new Response(http_code, MIME_DEFAULT_BINARY, dd.csv);
    res.addHeader("Content-Disposition", "filename=" + dd.filename);
    return res;
  }


  // Resource loading ----------------------------------------------------------
  // cache of all loaded resources
  private static final NonBlockingHashMap<String,byte[]> _cache = new NonBlockingHashMap<>();
  // Returns the response containing the given uri with the appropriate mime type.
  private Response getResource(int version, RequestType request_type, String uri) {
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
      throw new H2ONotFoundArgumentException("Resource " + uri + " not found",
                                             "Resource " + uri + " not found");

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

  // html template and navbar handling -----------------------------------------

  private static String loadTemplate(String name) {
    water.H2O.registerResourceRoot(new File("src/main/resources/www"));
    water.H2O.registerResourceRoot(new File("h2o-core/src/main/resources/www"));
    // Try-with-resource
    try (InputStream resource = water.init.JarHash.getResource2(name)) {
      return new String(toByteArray(resource)).replace("%cloud_name", H2O.ARGS.name);
    }
    catch( IOException ioe ) { Log.err(ioe); return null; }
  }

  private static class MenuItem {
    private final String _handler;
    private final String _name;

    private MenuItem(String handler, String name) {
      _handler = handler;
      _name = name;
    }

    private void toHTML(StringBuilder sb) {
      sb.append("<li><a href='").append(_handler).append(".html'>").append(_name).append("</a></li>");
    }
  }

  /**
   * Call this after the last call addToNavbar().
   * This is called automatically for navbar entries from inside H2O.
   * If user app level code calls addToNavbar, then call this again to make those changes visible.
   */
  static void initializeNavBar() { _htmlTemplate = initializeNavBar(_htmlTemplateFromFile); }

  private static String initializeNavBar(String template) {
    StringBuilder sb = new StringBuilder();
    for( String s : _navbarOrdering ) {
      ArrayList<MenuItem> arl = _navbar.get(s);
      if( (arl.size() == 1) && arl.get(0)._name.equals(s) ) {
        arl.get(0).toHTML(sb);
      } else {
        sb.append("<li class='dropdown'>");
        sb.append("<a href='#' class='dropdown-toggle' data-toggle='dropdown'>");
        sb.append(s);
        sb.append("<b class='caret'></b>");
        sb.append("</a>");
        sb.append("<ul class='dropdown-menu'>");
        for( MenuItem i : arl )
          i.toHTML(sb);
        sb.append("</ul></li>");
      }
    }
    RString str = new RString(template);
    str.replace("NAVBAR", sb.toString());
    str.replace("CONTENTS", "%CONTENTS");
    return str.toString();
  }

  // Add a new item to the navbar
  public static String addToNavbar(Route route, String base_url, String name, String category) {
    assert route != null && base_url != null && name != null && category != null;

    ArrayList<MenuItem> arl = _navbar.get(category);
    if( arl == null ) {
      arl = new ArrayList<>();
      _navbar.put(category, arl);
      _navbarOrdering.add(category);
    }
    arl.add(new MenuItem(base_url, name));
    return route._url_pattern.pattern();
  }

  // Return URLs for things that want to appear Frame-inspection page
  static String[] frameChoices( int version, Frame fr ) {
    ArrayList<String> al = new ArrayList<>();
    for( Pattern p : _routes.keySet() ) {
      try {
        Method meth = _routes.get(p)._handler_method;
        Class clz0 = meth.getDeclaringClass();
        Class<Handler> clz = (Class<Handler>)clz0;
        Handler h = clz.newInstance(); // TODO: we don't need to create new instances; handler is stateless
        if( version < h.min_ver() || h.max_ver() < version ) continue;
        // TODO: fixme! String url = Schema.schema(version, schema).acceptsFrame(fr);
        // TODO: fixme! if( url != null ) al.add(url);
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

  private byte[] zipLogs(byte[][] results, String topDir) throws IOException {
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
                        "node" + i +
                        H2O.CLOUD._memary[i].toString().replace(':', '_').replace('/', '_') +
                        ".zip";
        ZipEntry ze = new ZipEntry(filename);
        zos.putNextEntry(ze);
        zos.write(results[i]);
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

    for (int i = 0; i < members.length; i++) {
      byte[] bytes;

      try {
        // Skip nodes that aren't healthy, since they are likely to cause the entire process to hang.
        boolean healthy = (System.currentTimeMillis() - members[i]._last_heard_from) < HeartBeatThread.TIMEOUT;
        if (healthy) {
          GetLogsFromNode g = new GetLogsFromNode();
          g.nodeidx = 0;
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

    String outputFileStem = getOutputLogStem();
    byte[] finalZipByteArray;
    try {
      finalZipByteArray = zipLogs(perNodeZipByteArray, outputFileStem);
    }
    catch (Exception e) {
      finalZipByteArray = e.toString().getBytes();
    }

    NanoHTTPD.Response res = new Response(NanoHTTPD.HTTP_OK,NanoHTTPD.MIME_DEFAULT_BINARY, new ByteArrayInputStream(finalZipByteArray));
    res.addHeader("Content-Length", Long.toString(finalZipByteArray.length));
    res.addHeader("Content-Disposition", "attachment; filename="+outputFileStem + ".zip");
    return res;
  }
}
