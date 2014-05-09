package water.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import water.*;
import water.api.RequestServer.API_VERSION;
import water.util.Log;
import water.util.RString;

/** Base ReST Request Handling
 */
abstract class Request extends Iced {

  /** Override this to check arguments and fill in fields.  Return a query
   *  String response is some args are missing, or null if All Is Well. */

  // MAPPING from URI ==> POJO
  // 
  // THIS IS THE WRONG ARCHITECTURE....  either this call should be auto-gened
  // (auto-gen moves parms into local fields & complains on errors) or 
  // it needs to move into an explicit Schema somehow.
  protected abstract Response checkArguments(Properties parms);
  
  /** Override this to provide the Response for this valid Request */
  protected abstract Response serve();


  // Top-level Request dispatch based first on Request Type (e.g. .html vs .xml
  // vs .json).  (URIs that are not requests such as js & png resources already
  // filtered out in RequestServer before coming here).
  NanoHTTPD.Response serve(NanoHTTPD server, Properties parms, RequestType type) {
    switch( type ) {
//      case help:
//        return wrap(server, HTMLHelp());
    case xml:
    case json:
    case html:
      return serveGrid(server, parms, type);
//      case query: {
//        for (Argument arg: _arguments)
//          arg.reset();
//        String query = buildQuery(parms,type);
//        return wrap(server, query);
//      }
//      case java:
//        checkArguments(parms, type); // Do not check returned query but let it fail in serveJava
//        String javacode = serveJava();
//        return wrap(server, javacode, RequestType.java);
//      default:
//        throw new RuntimeException("Invalid request type " + type.toString());
    }
    throw H2O.unimpl();
  }

  // 2nd-level dispatch based on the Request instance class.
  protected NanoHTTPD.Response serveGrid(NanoHTTPD server, Properties parms, RequestType type) {
    // Check for a well-formed list of parameters.
    // If all are present, the Response is null & we call serve();
    // If things are not good, the Response is a Query for building more parameters.
    Response res = checkArguments(parms);

    if( res == null ) {         // No response, so all parms present & call serve()
      // Start the 'serve' call
      long time = System.currentTimeMillis();
      try { res = serve(); } 
      catch( Throwable t ) { res = new Response(t); }
      res._timeStart = time;
    }

    // Handle Response from 'serve'
    switch( type ) {
    case html: // Default webpage handling: jam Response toString inside the HTML template
      RString html = new RString(htmlTemplate());
      html.replace("CONTENTS", res.toString());
      return server.new Response(NanoHTTPD.HTTP_OK, NanoHTTPD.MIME_HTML, html.toString());
    case json:  return server.new Response(NanoHTTPD.HTTP_OK, NanoHTTPD.MIME_JSON, new String(res.req().writeJSON(new AutoBuffer()).buf()));
    case xml :  //return server.new Response(NanoHTTPD.HTTP_OK, NanoHTTPD.MIME_XML , new String(res.req().writeXML(new AutoBuffer()).buf()));
    case java:  //return server.new Response(NanoHTTPD.HTTP_OK, NanoHTTPD.MIME_PLAINTEXT, buildJava(res));
      throw H2O.unimpl();
    default:
      throw H2O.unimpl();
    }
  }

  // html template and navbar handling -----------------------------------------
  private static final String _htmlTemplateFromFile;
  private static volatile String _htmlTemplate;

  protected String htmlTemplate() { return _htmlTemplate; }

  static {
    _htmlTemplateFromFile = loadTemplate("/page.html");
    _htmlTemplate = "";
  }

  private static String loadTemplate(String name) {
    // Try-with-resource
    try (InputStream resource = water.init.JarHash.getResource2(name)) {
        return new String(water.persist.Persist.toByteArray(resource)).replace("%cloud_name", H2O.ARGS.name);
      }
    catch( IOException ioe ) { Log.err(ioe); return null; }
  }

  private static class MenuItem {
    private final Request _request;
    private final String _name;

    private MenuItem(Request request, String name) {
      _request = request;
      _name = name;
    }

    private void toHTML(StringBuilder sb) {
      sb.append("<li><a href='").append(_request.href()).append(".html'>").append(_name).append("</a></li>");
    }
  }

  private static HashMap<String, ArrayList<MenuItem>> _navbar = new HashMap();
  private static ArrayList<String> _navbarOrdering = new ArrayList();

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

  static Request addToNavbar(Request r, String name, String category) {
    ArrayList<MenuItem> arl = _navbar.get(category);
    if( arl == null ) {
      arl = new ArrayList();
      _navbar.put(category, arl);
      _navbarOrdering.add(category);
    }
    arl.add(new MenuItem(r, name));
    return r;
  }

  // -----------------------------------------------------
  /**
   * Request API versioning.
   * TODO: better solution would be to have an explicit annotation for each request
   *  - something like <code>@API-VERSION(2) @API-VERSION(1)</code>
   *  Annotation will be processed during start of RequestServer and default version will be registered
   *  under /, else /version/name_of_request.
   */
  protected static final API_VERSION[] SUPPORTS_ONLY_V1 = new API_VERSION[] { API_VERSION.V_1 };
  protected static final API_VERSION[] SUPPORTS_ONLY_V2 = new API_VERSION[] { API_VERSION.V_2 };
  protected static final API_VERSION[] SUPPORTS_V1_V2   = new API_VERSION[] { API_VERSION.V_1, API_VERSION.V_2 };
  protected API_VERSION[] supportedVersions() { return SUPPORTS_ONLY_V2; }

  private String href() { return href(supportedVersions()[0]); }
  protected String href(API_VERSION v) { return v._prefix + getClass().getSimpleName(); }

  // -----------------------------------------------------
  final class Response {
    protected final String _msg; // Default good response
    protected final Throwable _t; // Default bad response
    protected long _timeStart;
    protected boolean isError() { return _t != null; }
    protected Request req() { return Request.this; }
    protected Response( String msg ) { _t = null; _msg = msg; }
    protected Response( Throwable t ) {
      _t = t; 
      _msg = t.toString(); 
      Log.err(t);
    }
    @Override public String toString() { return _msg; }
  }
}
