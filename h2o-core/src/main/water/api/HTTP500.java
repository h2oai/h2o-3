package water.api;

/**
 *
 * @author peta
 */
public class HTTP500 extends Request {

//  private transient final Str _error = new Str(ERROR,"Unknown error");
//
//  public HTTP500() {
//    _requestHelp = "Displays the HTTP 500 page with error specified in JSON"
//            + " argument error. This page is displayed when any unexpected"
//            + " exception is returned from the request processing at any level.";
//    _error._requestHelp = "Error description for the 500. Generally the exception message.";
//  }
  @Override protected Response serve() { return Response.error(this,"Unknown error"); }

//  @Override protected String serveJava() {
//    return _error.value();
//  }
//
//  @Override public water.NanoHTTPD.Response serve(NanoHTTPD server, Properties parms, RequestType type) {
//    // We can be in different thread => so check if _error is specified
//    if (!_error.specified()) { // We are handling exception - so try to find error message in parms
//      for (Argument arg : _arguments) {
//        arg.reset();
//        arg.check(this, parms.getProperty(arg._name,""));
//      }
//    }
//    water.NanoHTTPD.Response r = super.serve(server, parms, type);
//    r.status = NanoHTTPD.HTTP_INTERNALERROR;
//    return r;
//  }
//
//  private static final String _html =
//            "<h3>HTTP 500 - Internal Server Error</h3>"
//          + "<div class='alert alert-error'>%ERROR</div>"
//          ;
}
