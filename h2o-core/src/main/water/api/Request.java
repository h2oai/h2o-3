package water.api;

//import hex.KMeansModel;
//import hex.glm.GLMModel;
//import hex.nb.NBModel;
//import hex.pca.PCAModel;
//import hex.rf.RFModel;
//
//import java.io.InputStream;
//import java.lang.annotation.*;
import java.util.Properties;
//
import water.*;
//import water.api.Request.Validator.NOPValidator;
import water.api.RequestServer.API_VERSION;
//import water.fvec.Frame;
//import water.util.*;

abstract class Request {
//  private @interface API {
//    String help();
//    /** Must be specified. */
//    boolean required() default false;
//    /** For keys. If specified, the key must exist. */
//    boolean mustExist() default false;
//    int since() default 1;
//    int until() default Integer.MAX_VALUE;
//    Class<? extends Filter> filter() default Filter.class;
//    Class<? extends Filter>[] filters() default {};
//    /** Forces an input field to also appear in JSON. */
//    boolean json() default false;
//    long   lmin() default Long  .MIN_VALUE;
//    long   lmax() default Long  .MAX_VALUE;
//    double dmin() default Double.NEGATIVE_INFINITY;
//    double dmax() default Double.POSITIVE_INFINITY;
//    boolean hide() default false;
//    String displayName() default "";
//    boolean gridable() default true;
//    Class<? extends Validator> validator() default NOPValidator.class;
//    ParamImportance importance() default ParamImportance.UNIMPORTANT;
//  }
//
//  private interface Validator<V> extends Freezable {
//    void validateRaw(String value) throws IllegalArgumentException;
//    void validateValue(V value) throws IllegalArgumentException;
//    /** Dummy helper class for NOP validator. */
//    private static class NOPValidator<V> extends Iced implements Validator<V> {
//      @Override private void validateRaw(String value) { }
//      @Override private void validateValue(V value) { }
//    }
//  }
//
//  private static interface Filter {
//    boolean run(Object value);
//  }
//
//  /** NOP filter, use to define a field as input.  */
//  private class Default implements Filter {
//    @Override private boolean run(Object value) { return true; }
//  }
//
//  //
//
//  private String _requestHelp;
//
//  protected Request(String help) {
//    _requestHelp = help;
//  }
//
//  protected Request() {
//  }
//
//  private String href() { return href(supportedVersions()[0]); }
  protected String href(API_VERSION v) { return v._prefix + getClass().getSimpleName(); }

//  protected RequestType hrefType() {
//    return RequestType.www;
//  }
//
//  protected boolean log() {
//    return true;
//  }
//
//  protected void registered(API_VERSION version) {
//  }
//
//  protected Request create(Properties parms) {
//    return this;
//  }
//
//  /** Implements UI call.
//   *
//   * <p>This should be call only from
//   * UI layer - i.e., RequestServer.</p>
//   *
//   * @see RequestServer
//   */
//  protected abstract Response serve();
//
//  protected String serveJava() { throw new UnsupportedOperationException("This request does not provide Java code!"); }
//
//  private NanoHTTPD.Response serve(NanoHTTPD server, Properties parms, RequestType type) {
//    switch( type ) {
//      case help:
//        return wrap(server, HTMLHelp());
//      case xml:
//      case json:
//      case www:
//        return serveGrid(server, parms, type);
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
//    }
//  }
//
//  protected NanoHTTPD.Response serveGrid(NanoHTTPD server, Properties parms, RequestType type) {
//    String query = checkArguments(parms, type);
//    if( query != null )
//      return wrap(server, query, type);
//    long time = System.currentTimeMillis();
//    Response response = null;
//    try {
//      response = serve();
//    } catch (IllegalArgumentException iae) { // handle illegal arguments
//      response = Response.error(iae);
//    }
//    response.setTimeStart(time);
//    return serveResponse(server, parms, type, response);
//  }

  private NanoHTTPD.Response serveResponse(NanoHTTPD server, Properties parms, RequestType type, NanoHTTPD.Response response) {
    throw H2O.unimpl();
//    // Argh - referencing subclass, sorry for that, but it is temporary hack
//    // for transition between v1 and v2 API
//    if (this instanceof Request2) ((Request2) this).fillResponseInfo(response);
//    if (this instanceof Parse2)   ((Parse2)   this).fillResponseInfo(response); // FIXME: Parser2 should inherit from Request2
//    if( type == RequestType.json ) {
//      return response._req == null ? //
//              wrap(server, response.toJson()) : //
//              wrap(server, new String(response._req.writeJSON(new AutoBuffer()).buf()), RequestType.json);
//    }
//    else if (type == RequestType.xml) {
//      if (response._req == null) {
//        String xmlString = response.toXml();
//        NanoHTTPD.Response r = wrap(server, xmlString, RequestType.xml);
//        return r;
//      }
//      else {
//        String jsonString = new String(response._req.writeJSON(new AutoBuffer()).buf());
//        org.json.JSONObject jo2 = new org.json.JSONObject(jsonString);
//        String xmlString = org.json.XML.toString(jo2);
//        NanoHTTPD.Response r = wrap(server, xmlString, RequestType.xml);
//        return r;
//      }
//    }
//    return wrap(server, build(response));
  }

//  protected NanoHTTPD.Response wrap(NanoHTTPD server, String response) {
//    RString html = new RString(htmlTemplate());
//    html.replace("CONTENTS", response);
//    return server.new Response(NanoHTTPD.HTTP_OK, NanoHTTPD.MIME_HTML, html.toString());
//  }
//
//  protected NanoHTTPD.Response wrap(NanoHTTPD server, JsonObject response) {
//    return server.new Response(NanoHTTPD.HTTP_OK, NanoHTTPD.MIME_JSON, response.toString());
//  }
//
//  protected NanoHTTPD.Response wrap(NanoHTTPD server, String value, RequestType type) {
//    if( type == RequestType.xml )
//      return server.new Response(NanoHTTPD.HTTP_OK, NanoHTTPD.MIME_XML, value);
//    if( type == RequestType.json )
//      return server.new Response(NanoHTTPD.HTTP_OK, NanoHTTPD.MIME_JSON, value);
//    if (type == RequestType.java)
//      return server.new Response(NanoHTTPD.HTTP_OK, NanoHTTPD.MIME_PLAINTEXT, value);
//    return wrap(server, value);
//  }
//
//  // html template and navbar handling -----------------------------------------
//
//  /**
//   * Read from file once.
//   */
//  private static final String _htmlTemplateFromFile;
//
//  /**
//   * Written by initializeNavBar().
//   */
//  private static volatile String _htmlTemplate;
//
//  protected String htmlTemplate() { return _htmlTemplate; }
//
//  static {
//    _htmlTemplateFromFile = loadTemplate("/page.html");
//    _htmlTemplate = "";
//  }
//
//  static final String loadTemplate(String name) {
//    InputStream resource = Boot._init.getResource2(name);
//    try {
//      if( H2O.NAME != null )
//        return new String(ByteStreams.toByteArray(resource)).replace("%cloud_name", H2O.NAME);
//    } catch( NullPointerException e ) {
//      if( !Log._dontDie ) {
//        Log.err(e);
//        Log.die(name+" not found in resources.");
//      }
//    } catch( Exception e ) {
//      Log.err(e);
//      Log.die(e.getMessage());
//    } finally {
//      Utils.close(resource);
//    }
//    return null;
//  }
//
//  private static class MenuItem {
//    private final Request _request;
//    private final String _name;
//    private final boolean _useNewTab;
//
//    private MenuItem(Request request, String name, boolean useNewTab) {
//      _request = request;
//      _name = name;
//      _useNewTab = useNewTab;
//    }
//
//    private void toHTML(StringBuilder sb) {
//      sb.append("<li><a href='");
//      sb.append(_request.href() + _request.hrefType()._suffix);
//      sb.append("'");
//
//      if (_useNewTab) {
//        sb.append(" target='_blank'");
//      }
//
//      sb.append(">");
//      sb.append(_name);
//      sb.append("</a></li>");
//    }
//  }
//
//  private static HashMap<String, ArrayList<MenuItem>> _navbar = new HashMap();
//  private static ArrayList<String> _navbarOrdering = new ArrayList();
//
//  /**
//   * Call this after the last call addToNavbar().
//   * This is called automatically for navbar entries from inside H2O.
//   * If user app level code calls addToNavbar, then call this again to make those changes visible.
//   */
//  private static void initializeNavBar() { _htmlTemplate = initializeNavBar(_htmlTemplateFromFile); }
//
//  private static String initializeNavBar(String template) {
//    StringBuilder sb = new StringBuilder();
//    for( String s : _navbarOrdering ) {
//      ArrayList<MenuItem> arl = _navbar.get(s);
//      if( (arl.size() == 1) && arl.get(0)._name.equals(s) ) {
//        arl.get(0).toHTML(sb);
//      } else {
//        sb.append("<li class='dropdown'>");
//        sb.append("<a href='#' class='dropdown-toggle' data-toggle='dropdown'>");
//        sb.append(s);
//        sb.append("<b class='caret'></b>");
//        sb.append("</a>");
//        sb.append("<ul class='dropdown-menu'>");
//        for( MenuItem i : arl )
//          i.toHTML(sb);
//        sb.append("</ul></li>");
//      }
//    }
//    RString str = new RString(template);
//    str.replace("NAVBAR", sb.toString());
//    str.replace("CONTENTS", "%CONTENTS");
//    return str.toString();
//  }
//
//  private static Request addToNavbar(Request r, String name) {
//    assert (!_navbar.containsKey(name));
//    ArrayList<MenuItem> arl = new ArrayList();
//    boolean useNewTab = false;
//    arl.add(new MenuItem(r, name, useNewTab));
//    _navbar.put(name, arl);
//    _navbarOrdering.add(name);
//    return r;
//  }
//
//  private static Request addToNavbar(Request r, String name, String category) {
//    boolean useNewTab = false;
//    return addToNavbar(r, name, category, useNewTab);
//  }
//
//  private static Request addToNavbar(Request r, String name, String category, boolean useNewTab) {
//    ArrayList<MenuItem> arl = _navbar.get(category);
//    if( arl == null ) {
//      arl = new ArrayList();
//      _navbar.put(category, arl);
//      _navbarOrdering.add(category);
//    }
//    arl.add(new MenuItem(r, name, useNewTab));
//    return r;
//  }
//
//
//  // TODO clean this stuff, typeahead should take type name
//  protected static Class mapTypeahead(Class c) {
//    if(c != null) {
//      if( PCAModel.class.isAssignableFrom(c) )
//        return TypeaheadPCAModelKeyRequest.class;
//      if( NBModel.class.isAssignableFrom(c) )
//        return TypeaheadNBModelKeyRequest.class;
//      if( GLMModel.class.isAssignableFrom(c))
//        return TypeaheadGLMModelKeyRequest.class;
//      if( RFModel.class.isAssignableFrom(c))
//        return TypeaheadRFModelKeyRequest.class;
//      if( KMeansModel.class.isAssignableFrom(c))
//        return TypeaheadKMeansModelKeyRequest.class;
//      if( Model.class.isAssignableFrom(c))
//        return TypeaheadModelKeyRequest.class;
//      if( Frame.class.isAssignableFrom(c) || ValueArray.class.isAssignableFrom(c) )
//        return TypeaheadHexKeyRequest.class;
//    }
//    return TypeaheadKeysRequest.class;
//  }
//
//  // ==========================================================================
//
//  private boolean toHTML(StringBuilder sb) {
//    return false;
//  }
//  private void toJava(StringBuilder sb) {}
//
//  private String toDocGET() {
//    return null;
//  }
//
//  /**
//   * Example of passing and failing request. Will be prepended with
//   * "curl -s localhost:54321/Request.json". Return param/value pairs that will be used to build up
//   * a URL, and the result from serving the URL will show up as an example.
//   */
//  private String[] DocExampleSucc() {
//    return null;
//  }
//
//  private String[] DocExampleFail() {
//    return null;
//  }
//
//  private String HTMLHelp() {
//    return DocGen.HTML.genHelp(this);
//  }
//
//  private String ReSTHelp() {
//    return DocGen.ReST.genHelp(this);
//  }
//  // Dummy write of a leading field, so the auto-gen JSON can just add commas
//  // before each succeeding field.
//  @Override private AutoBuffer writeJSONFields(AutoBuffer bb) { return bb.putJSON4("Request2",0); }
//
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
  API_VERSION[] supportedVersions() { return SUPPORTS_ONLY_V2; }
}
