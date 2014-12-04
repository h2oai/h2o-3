package water.api;

import water.H2O;
import water.Iced;
import water.util.DocGen.HTML;

import java.util.Arrays;

class HttpErrorV1 extends Schema<Iced, HttpErrorV1> { // no need to an impl class; we generate these directly
  @API(help="Response header")
  String status_header = null;

  // Output fields
  @API(help="Error message", direction=API.Direction.OUTPUT)
  String errmsg = null;

  @API(help="Error url", direction=API.Direction.OUTPUT)
  String errurl = null;

  @API(help="Stacktrace, if any", direction=API.Direction.OUTPUT)
  String[] stacktrace = null;

  HttpErrorV1( int status_code, String msg, String url ) {
    errmsg = msg; errurl = url;

    switch (status_code) {
    case 400:
      this.status_header = "400 Bad Request";
      break;
    case 404:
      this.status_header = "404 Not Found";
      break;
    case 500:
      this.status_header = "500 Internal Server Error";
      break;
    default:
      throw H2O.unimpl("Unimplemented http status code: " + status_code);
    }
  }

  HttpErrorV1() { }
  HttpErrorV1( Exception e ) {
    status_header = "500 Internal Server Error";
    errmsg = e.getClass().getSimpleName()+": "+e.getMessage();

    StackTraceElement[] trace = e.getStackTrace();
    stacktrace = new String[trace.length];

    for (int i = 0; i < trace.length; i++)
      stacktrace[i] = trace[i].toString();
  }

  @Override public Iced createImpl() { throw H2O.fail(); }
  @Override public Iced fillImpl(Iced ignoreme) { throw H2O.fail(); }
  @Override public HttpErrorV1 fillFromImpl(Iced i) { throw H2O.fail(); }

  // TODO: this is not setting the http header!
  // RequestServer should pull out the status and set the HTTP header
  // when it sees one of these.
  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.bodyHead();
    ab.title(status_header);
    ab.p("<div class='alert alert-error'>").p(errmsg).p("</div>");
    if (null != stacktrace)
      ab.p(Arrays.toString(stacktrace));

    return ab.bodyTail();
  }
}
