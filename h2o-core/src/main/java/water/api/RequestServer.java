package water.api;

import java.io.*;
import java.net.ServerSocket;
import java.util.*;
import water.H2O;
import water.AutoBuffer;
import water.NanoHTTPD;
import water.nbhm.NonBlockingHashMap;
import water.util.Log;
import water.util.RString;
import water.schemas.*;

/** This is a simple web server. */
public class RequestServer extends NanoHTTPD {

  private static final int LATEST_VERSION = 2;

  static RequestServer SERVER;
  private RequestServer( ServerSocket socket ) throws IOException { super(socket,null); }

  // Handlers ------------------------------------------------------------
  protected static final NonBlockingHashMap<String,Class<? extends Handler>> _handlers = new NonBlockingHashMap<>();

  private static HashMap<String, ArrayList<MenuItem>> _navbar = new HashMap();
  private static ArrayList<String> _navbarOrdering = new ArrayList();

  static {
    _htmlTemplateFromFile = loadTemplate("/page.html");
    _htmlTemplate = "";

    // Data
    addToNavbar(registerRequest(ImportFiles.class),"Import Files", "Data");
    addToNavbar(registerRequest(Parse.class),      "Parse",        "Data");

    // Help and Tutorials
    addToNavbar(registerRequest(Tutorials.class),  "Tutorials Home","Help");

    initializeNavBar();
  }

  /** Registers the request with the request server.  */
  public static String registerRequest(Class<? extends Handler> hclass) { 
    String href = hclass.getSimpleName();
    assert !_handlers.containsKey(href) : "Handler with class "+href+" already registered";
    _handlers.put(href,hclass);
    return href;
  }


  // Keep spinning until we get to launch the NanoHTTPD.  Launched in a
  // seperate thread (I'm guessing here) so the startup process does not hang
  // if the various web-port accesses causes Nano to hang on startup.
  public static void start() {
    new Thread( new Runnable() {
        @Override public void run()  {
          while( true ) {
            try {
              // Try to get the NanoHTTP daemon started
              SERVER = new RequestServer(water.init.NetworkInit._apiSocket);
              break;
            } catch( Exception ioe ) {
              Log.err("Launching NanoHTTP server got ",ioe);
              try { Thread.sleep(1000); } catch( InterruptedException ignore ) { } // prevent denial-of-service
            }
          }
        }
      }, "Request Server launcher").start();
  }

  // Log all requests except the overly common ones
  void maybeLogRequest (String uri, String method, Properties parms) {
    if (uri.endsWith(".css")) return;
    if (uri.endsWith(".js")) return;
    if (uri.endsWith(".png")) return;
    if (uri.endsWith(".ico")) return;
    if (uri.startsWith("Typeahead")) return;
    if (uri.startsWith("Cloud.json")) return;
    if (uri.startsWith("LogAndEcho.json")) return;
    if (uri.startsWith("Jobs.json")) return;
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
  // /v1/xx     --> version 1
  // /v2/xx     --> version 2
  // /latest/xx --> LATEST_VERSION
  // /xx        --> LATEST_VERSION
  private int parseVersion( String uri ) {
    if( uri.charAt(0) != '/' ) // If not a leading slash, then I am confused
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
  // e.g. /2/GBM.html breaks down into:
  //   requestType:  ".html"
  //   version:      2
  //   requestName:  "GBM"
  @Override public Response serve( String uri, String method, Properties header, Properties parms ) {
    // Jack priority for user-visible requests
    Thread.currentThread().setPriority(Thread.MAX_PRIORITY-1);

    // The Default URI Handling
    if (uri.isEmpty() || uri.equals("/"))
      uri = "/Tutorials.html";
    maybeLogRequest(uri, method, parms);

    // determine the request type
    RequestType type = RequestType.requestType(uri);
    String uri_base = type.requestName(uri); // Strip suffix type
    
    // determine version
    int version = parseVersion(uri_base);
    int idx = version>>16;
    version &= 0xFFFF;

    // determine handler name
    String requestName = uri_base.substring(idx);
    String remaining = "";
    if( uri_base.charAt(idx) == '/' ) { // If not a leading slash, then I am confused
      requestName = uri_base.substring(1);
      int idx2 = requestName.indexOf('/');
      if( idx2 != -1 ) {
        remaining   = requestName.substring(idx2);
        requestName = requestName.substring(0,idx2);
      }
    }

    // Load resources, or dispatch on handled requests
    try {
      // determine if we have known resource
      Class<? extends Handler> clazz = _handlers.get(requestName);
      // if the request is not know, treat as resource request, or 404 if not found
      if( clazz == null ) return getResource(uri);
      return wrap(HTTP_OK,handle(clazz.newInstance(),version,method,parms,type),type);
    } catch( IllegalArgumentException e ) {
      return wrap(HTTP_BADREQUEST,new HTTP404V1(e.getMessage(),uri),type);
    } catch( Exception e ) {
      // make sure that no Exception is ever thrown out from the request
      return wrap(e.getMessage()!="unimplemented"? HTTP_INTERNALERROR : HTTP_NOTIMPLEMENTED, new HTTP500V1(e),type);
    }
  }

  // Handling ------------------------------------------------------------------
  private Schema handle( Handler h, int version, String method, Properties parms, RequestType type ) {
    Schema S;
    switch( type ) {
    case html: // These request-types only dictate the response-type; 
    case java: // the normal action is always done.
    case json:
    case xml:
      return h.serve(version,method,parms);
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
      html.replace("CONTENTS", new String(S.writeHTML(new AutoBuffer()).buf()));
      return new Response(http_code, MIME_HTML, html.toString());
    }
    default:
      throw H2O.fail();
    }
  }


  // Resource loading ----------------------------------------------------------
  // cache of all loaded resources
  private static final NonBlockingHashMap<String,byte[]> _cache = new NonBlockingHashMap();
  // Returns the response containing the given uri with the appropriate mime type.
  private Response getResource(String uri) {
    byte[] bytes = _cache.get(uri);
    if( bytes == null ) {
      // Try-with-resource
      try (InputStream resource = water.init.JarHash.getResource2(uri)) {
          if( resource != null ) {
            try { bytes = water.persist.Persist.toByteArray(resource); } 
            catch( IOException e ) { Log.err(e); }
            if( bytes != null ) {
              byte[] res = _cache.putIfAbsent(uri,bytes);
              if( res != null ) bytes = res; // Racey update; take what is in the _cache
            }
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
  private static final String _htmlTemplateFromFile;
  private static volatile String _htmlTemplate;

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

  public static String addToNavbar(String r, String name, String category) {
    ArrayList<MenuItem> arl = _navbar.get(category);
    if( arl == null ) {
      arl = new ArrayList();
      _navbar.put(category, arl);
      _navbarOrdering.add(category);
    }
    arl.add(new MenuItem(r, name));
    return r;
  }

}
