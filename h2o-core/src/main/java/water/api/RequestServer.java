package water.api;

import java.io.*;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import water.H2O;
import water.AutoBuffer;
import water.NanoHTTPD;
import water.nbhm.NonBlockingHashMap;
import water.util.Log;
import water.parser.ParseSetupHandler;
import water.util.RString;
import water.fvec.Frame;

/** This is a simple web server. */
public class RequestServer extends NanoHTTPD {

  private static final int DEFAULT_VERSION = 2;

  static RequestServer SERVER;
  private RequestServer( ServerSocket socket ) throws IOException { super(socket,null); }

  private static final String _htmlTemplateFromFile = loadTemplate("/page.html");
  private static volatile String _htmlTemplate = "";

  final static class Route {
    public final String  _http_method;
    public final Pattern _url_pattern;
    public final Class   _handler_class;
    public final Method  _handler_method;
    // NOTE: Java 7 captures and lets you look up subpatterns by name but won't give you the list of names, so we need this redundant list:
    public final String[] _path_params; // list of params we capture from the url pattern, e.g. for /17/MyComplexObj/(.*)/(.*)

    public Route(String http_method, Pattern url_pattern, Class handler_class, Method handler_method, String[] path_params) {
      assert http_method != null && url_pattern != null && handler_class != null && handler_method != null && path_params != null;
      _http_method = http_method;
      _url_pattern = url_pattern;
      _handler_class = handler_class;
      _handler_method = handler_method;
      _path_params = path_params;
    }

    @Override
    public boolean equals(Object o) {
      if( this == o ) return true;
      if( !(o instanceof Route) ) return false;
      Route route = (Route) o;
      if( !_handler_class .equals(route._handler_class )) return false;
      if( !_handler_method.equals(route._handler_method)) return false;
      if( !_http_method   .equals(route._http_method   )) return false;
      if( !_url_pattern   .equals(route._url_pattern   )) return false;
      if( !Arrays.equals(_path_params, route._path_params)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      long result = _http_method.hashCode();
      result = 31 * result + _url_pattern.hashCode();
      result = 31 * result + _handler_class.hashCode();
      result = 31 * result + _handler_method.hashCode();
      result = 31 * result + Arrays.hashCode(_path_params);
      return (int)result;
    }
  }


  // Handlers ------------------------------------------------------------

  // An array of regexs-over-URLs and handling Methods.
  // The list is searched in-order, first match gets dispatched.
  private static final LinkedHashMap<Pattern,Route> _routes = new LinkedHashMap<>();

  private static HashMap<String, ArrayList<MenuItem>> _navbar = new HashMap<>();
  private static ArrayList<String> _navbarOrdering = new ArrayList<>();

  static {
    // Data
    addToNavbar(register("/ImportFiles","GET",ImportFilesHandler.class,"compute2"),"/ImportFiles", "Import Files",  "Data");
    addToNavbar(register("/ParseSetup" ,"GET",ParseSetupHandler .class,"guessSetup"),"/ParseSetup","ParseSetup",    "Data");
    addToNavbar(register("/Parse"      ,"GET",ParseHandler      .class,"parse"   ),"/Parse"      , "Parse",         "Data");
    addToNavbar(register("/Inspect"    ,"GET",InspectHandler    .class,"inspect" ),"/Inspect"    , "Inspect",       "Data");

    // Admin
    addToNavbar(register("/Cloud"      ,"GET",CloudHandler      .class,"status"  ),"/Cloud"      , "Cloud",         "Admin");
    addToNavbar(register("/Jobs"       ,"GET",JobsHandler       .class,"list"    ),"/Jobs"       , "Jobs",          "Admin");
    addToNavbar(register("/Timeline"   ,"GET",TimelineHandler   .class,"compute2"),"/Timeline"   , "Timeline",      "Admin");

    // Help and Tutorials get all the rest...
    addToNavbar(register("/Tutorials"  ,"GET",TutorialsHandler  .class,"nop"     ),"/Tutorials"  , "Tutorials Home","Help");
    register("/"           ,"GET",TutorialsHandler  .class,"nop"     );

    initializeNavBar();

    // REST only, no html:
    register("/Typeahead/files" ,"GET",TypeaheadHandler.class, "files");
    register("/JobPoll"         ,"GET",JobPollHandler  .class, "poll" );

    register("/3/Frames/(?<key>.*)/columns/(?<column>.*)"   ,"GET",FramesHandler.class, "column", new String[] {"key", "column"});
    register("/3/Frames/(?<key>.*)/columns"                 ,"GET",FramesHandler.class, "columns", new String[] {"key"});
    register("/3/Frames/(?<key>.*)"                         ,"GET",FramesHandler.class, "fetch", new String[] {"key"});
    register("/3/Frames"                                    ,"GET",FramesHandler.class, "list");
    register("/2/Frames"                                    ,"GET",FramesHandler.class, "list_or_fetch"); // uses ?key=

    register("/3/Models/(?<key>.*)"                         ,"GET",ModelsHandler.class, "fetch", new String[] {"key"});
    register("/3/Models"                                    ,"GET",ModelsHandler.class, "list");
    register("/2/Models"                                    ,"GET",ModelsHandler.class, "list_or_fetch"); // uses ?key=
  }

  public static Route register(String url_pattern, String http_method, Class handler_class, String handler_method) {
    return register(url_pattern, http_method, handler_class, handler_method, new String[]{});
  }

  public static Route register(String url_pattern, String http_method, Class handler_class, String handler_method, String[] path_params) {
    assert url_pattern.startsWith("/");
    try {
      Method meth = handler_class.getDeclaredMethod(handler_method);
      if (url_pattern.matches("^/v?\\d+/.*")) {
        // register specifies a version
      } else {
        // register all versions
        url_pattern = "^(/v?\\d+)?" + url_pattern;
      }
      assert lookup(handler_method,url_pattern)==null; // Not shadowed
      Pattern p = Pattern.compile(url_pattern);
      Route route = new Route(http_method, p, handler_class, meth, path_params);
      _routes.put(p, route);
      return route;
    } catch( NoSuchMethodException nsme ) {
      throw new Error("NoSuchMethodException: "+handler_class.getName()+"."+handler_method);
    }
  }

  // Lookup the method/url in the register list, and return a matching Method
  private static Route lookup( String http_method, String url ) {
    if (null == http_method || null == url)
      return null;

    for( Route r : _routes.values() )
      if (r._url_pattern.matcher(url).matches())
        if (http_method.equals(r._http_method))
          return r;

    return null;
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
      return (0<<16)| DEFAULT_VERSION;
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
    if (null == route._path_params) return; // path_params is public, so someone may set it to null

    Matcher m = route._url_pattern.matcher(path);
    if (! m.matches()) {
      throw H2O.fail("Routing regex error: Pattern matched once but not again for pattern: " + route._url_pattern.pattern() + " and path: " + path);
    }

    for (String key : route._path_params) {
      String val = null;
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

  // Top-level dispatch based the URI.  Break down URI into parts;
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

    // Load resources, or dispatch on handled requests
    try {
      // Find handler for url
      Route route = lookup(method, versioned_path);

      // if the request is not known, treat as resource request, or 404 if not found
      if( route == null )
        return getResource(uri);
      else {
        capturePathParms(parms, versioned_path, route); // get any parameters like /Frames/<key>
        maybeLogRequest(path, versioned_path, route._url_pattern.pattern(), parms);
        return wrap(HTTP_OK,handle(type,route,version,parms),type);
      }
    } catch( IllegalArgumentException e ) {
      return wrap(HTTP_BADREQUEST,new HTTP404V1(e.getMessage(),uri),type);
    } catch( Exception e ) {
      // make sure that no Exception is ever thrown out from the request
      return wrap("unimplemented".equals(e.getMessage())? HTTP_NOTIMPLEMENTED : HTTP_INTERNALERROR, new HTTP500V1(e),type);
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
      Handler h = clz.newInstance(); // NOTE: currently h has state, so we must create new instances
      return h.handle(version,route,parms); // Can throw any Exception the handler throws
    }
    case query:
    case help:
    default:
      throw H2O.unimpl();
    }
  }

  private Response wrap( String http_code, Schema s, RequestType type ) {
    // Convert Schema to desired output flavor
    switch( type ) {
    case json:   return new Response(http_code, MIME_JSON, new String(s.writeJSON(new AutoBuffer()).buf()));
    case xml:  //return new Response(http_code, MIME_XML , new String(S.writeXML (new AutoBuffer()).buf()));
    case java:
      throw H2O.unimpl();
    case html: {
      RString html = new RString(_htmlTemplate);
      html.replace("CONTENTS", s.writeHTML(new water.util.DocGen.HTML()).toString());
      return new Response(http_code, MIME_HTML, html.toString());
    }
    default:
      throw H2O.fail();
    }
  }


  // Resource loading ----------------------------------------------------------
  // cache of all loaded resources
  private static final NonBlockingHashMap<String,byte[]> _cache = new NonBlockingHashMap<>();
  // Returns the response containing the given uri with the appropriate mime type.
  private Response getResource(String uri) {
    byte[] bytes = _cache.get(uri);
    if( bytes == null ) {
      // Try-with-resource
      try (InputStream resource = water.init.JarHash.getResource2(uri)) {
          if( resource != null ) {
            try { bytes = water.persist.Persist.toByteArray(resource); }
            catch( IOException e ) { Log.err(e); }

            // PP 06-06-2014 Disable caching for now so that the browser
            //  always gets the latest sources and assets when h2o-client is rebuilt.
            // TODO need to rethink caching behavior when h2o2 is merged into h2o.
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
      return wrap(HTTP_NOTFOUND,new HTTP404V1("Resource "+uri+" not found",uri),RequestType.html);
    String mime = MIME_DEFAULT_BINARY;
    if( uri.endsWith(".css") )
      mime = "text/css";
    else if( uri.endsWith(".html") )
      mime = "text/html";
    Response res = new Response(HTTP_OK,mime,new ByteArrayInputStream(bytes));
    res.addHeader("Content-Length", Long.toString(bytes.length));
    return res;
  }

  // html template and navbar handling -----------------------------------------

  private static String loadTemplate(String name) {
    water.H2O.registerResourceRoot(new File("src/main/resources/www"));
    // Try-with-resource
    try (InputStream resource = water.init.JarHash.getResource2(name)) {
        return new String(water.persist.Persist.toByteArray(resource)).replace("%cloud_name", H2O.ARGS.name);
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
        Handler h = clz.newInstance();
        if( version < h.min_ver() || h.max_ver() < version ) continue;
        String url = h.schema(version).acceptsFrame(fr);
        if( url != null ) al.add(url);
      }
      catch( InstantiationException | IllegalArgumentException | IllegalAccessException ignore ) { }
    }
    return al.toArray(new String[al.size()]);
  }
}
