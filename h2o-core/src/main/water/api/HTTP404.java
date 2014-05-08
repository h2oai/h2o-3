package water.api;

/**
 *
 * @author peta
 */
public class HTTP404 extends Request {

//  private transient final Str _error = new Str(ERROR,"Unknown error");
//
//  public HTTP404() {
//    _requestHelp = "Displays the HTTP 404 page with error specified in JSON"
//            + " argument error.";
//    _error._requestHelp = "Error description for the 404. Generally the URL not found.";
//  }
//
  @Override protected Response serve() { return Response.error(this,"Unknown error"); }
//
//  @Override protected String serveJava() {
//    return _error.value();
//  }
//
//  @Override public water.NanoHTTPD.Response serve(NanoHTTPD server, Properties parms, RequestType type) {
//    water.NanoHTTPD.Response r = super.serve(server, parms, type);
//    r.status = NanoHTTPD.HTTP_NOTFOUND;
//    return r;
//  }
//
//  private static final String _html =
//            "<h3>HTTP 404 - Not Found</h3>"
//          + "<div class='alert alert-error'>%ERROR</div>"
//          ;
//
}
