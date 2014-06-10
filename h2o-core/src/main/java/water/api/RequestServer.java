package water.api;

import java.io.*;
import java.net.ServerSocket;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Pattern;

import water.H2O;
import water.AutoBuffer;
import water.NanoHTTPD;
import water.nbhm.NonBlockingHashMap;
import water.util.Log;
import water.util.RString;
import water.schemas.*;
import water.fvec.Frame;

/** This is a simple web server. */
public class RequestServer extends NanoHTTPD {

  private static final int LATEST_VERSION = 2;

  static RequestServer SERVER;
  private RequestServer( ServerSocket socket ) throws IOException { super(socket,null); }

  private static final String _htmlTemplateFromFile = loadTemplate("/page.html");
  private static volatile String _htmlTemplate = "";


  // Handlers ------------------------------------------------------------

  // An array of regexs-over-URLs and handling Methods.
  // The list is searched in-order, first match gets dispatched.
  protected static final LinkedHashMap<Pattern,Method> _handlers = new LinkedHashMap<>();

  private static HashMap<String, ArrayList<MenuItem>> _navbar = new HashMap<>();
  private static ArrayList<String> _navbarOrdering = new ArrayList<>();

  static {
    // Data
    addToNavbar(registerGET("/ImportFiles",ImportFilesHandler.class,"compute2"),"Import Files",  "Data");
    addToNavbar(registerGET("/Parse"      ,ParseHandler      .class,"parse"   ),"Parse",         "Data");
    addToNavbar(registerGET("/Inspect"    ,InspectHandler    .class,"inspect" ),"Inspect",       "Data");

    // Admin
    addToNavbar(registerGET("/Cloud"      ,CloudHandler      .class,"status"  ),"Cloud",         "Admin");
    addToNavbar(registerGET("/JobPoll"    ,JobPollHandler    .class,"poll"    ),"Job Poll",      "Admin");

    // Help and Tutorials get all the rest...
    addToNavbar(registerGET("/Tutorials"  ,TutorialsHandler  .class,"nop"     ),"Tutorials Home","Help");
    addToNavbar(registerGET("/"           ,TutorialsHandler  .class,"nop"     ),"Tutorials Home","Help");

    initializeNavBar();

    registerGET("/Frames/.*", FramesHandler.class, "fetch");
    registerGET("/Frames", FramesHandler.class, "list");
  }

  /** Registers the request with the request server.  */
  public static String registerGET   (String url, Class hclass, String hmeth) { return register("GET"   ,url,hclass,hmeth); }
  public static String registerPUT   (String url, Class hclass, String hmeth) { return register("PUT"   ,url,hclass,hmeth); }
  public static String registerDELETE(String url, Class hclass, String hmeth) { return register("DELETE",url,hclass,hmeth); }
  public static String registerPOST  (String url, Class hclass, String hmeth) { return register("POST"  ,url,hclass,hmeth); }
  private static String register(String method, String url, Class hclass, String hmeth) {
    try {
      assert lookup(method,url)==null; // Not shadowed
      Method meth = hclass.getDeclaredMethod(hmeth);
      _handlers.put(Pattern.compile(method + url), meth);
      return url;
    } catch( NoSuchMethodException nsme ) {
      throw new Error("NoSuchMethodException: "+hclass.getName()+"."+hmeth);
    }
  }

  // Lookup the method/url in the register list, and return a matching Method
  private static Method lookup( String method, String url ) {
    String s = method+url;
    for( Pattern p : _handlers.keySet() )
      if (p.matcher(s).matches())
        return _handlers.get(p);
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
  void maybeLogRequest (String uri, String method, Properties parms) {
    if (uri.endsWith(".css")) return;
    if (uri.endsWith(".js")) return;
    if (uri.endsWith(".png")) return;
    if (uri.endsWith(".ico")) return;
    if (uri.startsWith("/Typeahead")) return;
    if (uri.startsWith("/Cloud")) return;
    if (uri.contains("Progress")) return;

    String log = String.format("%-4s %s", method, uri);
    for( Object arg : parms.keySet() ) {
      String value = parms.getProperty((String) arg);
      if( value != null && value.length() != 0 )
        log += " " + arg + "=" + value;
    }
    Log.info(log);
  }

  // Parse version number.  Java has no ref types, bleah, so return the version
  // number and the "parse pointer" by shift-by-16 compaction.
  // /1/xxx     --> version 1
  // /2/xxx     --> version 2
  // /v1/xxx    --> version 1
  // /v2/xxx    --> version 2
  // /latest/xxx--> LATEST_VERSION
  // /xxx       --> LATEST_VERSION
  private int parseVersion( String uri ) {
    if( uri.length() <= 1 || uri.charAt(0) != '/' ) // If not a leading slash, then I am confused
      return (0<<16)|LATEST_VERSION;
    if( uri.startsWith("/latest") )
      return (("/latest".length())<<16)|LATEST_VERSION;
    int idx=1;                  // Skip the leading slash
    int version=0;
    char c = uri.charAt(idx);   // Allow both /### and /v###
    if( c=='v' ) c = uri.charAt(++idx);
    while( idx < uri.length() && '0' <= c && c <= '9' ) {
      version = version*10+(c-'0');
      c = uri.charAt(++idx);
    }
    if( idx > 10 || version > LATEST_VERSION || version < 1 || uri.charAt(idx) != '/' )
      return (0<<16)|LATEST_VERSION; // Failed number parse or baloney version
    // Happy happy version
    return (idx<<16)|version;
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

    // Load resources, or dispatch on handled requests
    maybeLogRequest(path, method, parms);
    try {
      // Find handler for url
      Method meth = lookup(method,path);
      // if the request is not known, treat as resource request, or 404 if not found
      if( meth == null )
        return getResource(uri);
      else
        return wrap(HTTP_OK,handle(type,meth,version,parms),type);
    } catch( IllegalArgumentException e ) {
      return wrap(HTTP_BADREQUEST,new HTTP404V1(e.getMessage(),uri),type);
    } catch( Exception e ) {
      // make sure that no Exception is ever thrown out from the request
      return wrap("unimplemented".equals(e.getMessage())? HTTP_NOTIMPLEMENTED : HTTP_INTERNALERROR, new HTTP500V1(e),type);
    }
  }

  // Handling ------------------------------------------------------------------
  private Schema handle( RequestType type, Method meth, int version, Properties parms ) throws Exception {
    switch( type ) {
    case html: // These request-types only dictate the response-type;
    case java: // the normal action is always done.
    case json:
    case xml: {
      Class x = meth.getDeclaringClass();
      Class<Handler> clz = (Class<Handler>)x;
      Handler h = clz.newInstance();
      return h.handle(version,meth,parms); // Can throw any Exception the handler throws
    }
    case query:
    case help:
    default:
      throw H2O.unimpl();
    }
  }

  private Response wrap( String http_code, Schema S, RequestType type ) {
    // Convert Schema to desired output flavor
    switch( type ) {
    case json:   return new Response(http_code, MIME_JSON, new String(S.writeJSON(new AutoBuffer()).buf()));
    case xml:  //return new Response(http_code, MIME_XML , new String(S.writeXML (new AutoBuffer()).buf()));
    case java:
      throw H2O.unimpl();
    case html: {
      RString html = new RString(_htmlTemplate);
      html.replace("CONTENTS", S.writeHTML(new water.util.DocGen.HTML()).toString());
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
  public static String addToNavbar(String r, String name, String category) {
    ArrayList<MenuItem> arl = _navbar.get(category);
    if( arl == null ) {
      arl = new ArrayList<>();
      _navbar.put(category, arl);
      _navbarOrdering.add(category);
    }
    arl.add(new MenuItem(r, name));
    return r;
  }

  // Return URLs for things that want to appear Frame-inspection page
  public static String[] frameChoices( int version, Frame fr ) {
    ArrayList<String> al = new ArrayList<>();
    for( Pattern p : _handlers.keySet() ) {
      try {
        Method meth = _handlers.get(p);
        Class clz0 = meth.getDeclaringClass();
        Class<Handler> clz = (Class<Handler>)clz0;
        Handler h = clz.newInstance();
        if( version < h.min_ver() || h.max_ver() > version ) continue;
        String url = h.schema(version).acceptsFrame(fr);
        if( url != null ) al.add(url);
      }
      catch( InstantiationException | IllegalArgumentException | IllegalAccessException ignore ) { }
    }
    return al.toArray(new String[al.size()]);
  }
}
