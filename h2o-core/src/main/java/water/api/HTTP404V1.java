package water.api;

import water.H2O;
import water.util.DocGen.HTML;

class HTTP404V1 extends Schema {
  // This Schema has no inputs

  // Output fields
  @API(help="Error message")
  final String errmsg;

  @API(help="Error url")
  final String errurl;

  HTTP404V1( String msg, String url ) { errmsg = msg; errurl = url; }

  @Override protected HTTP404V1 fillInto( Handler h ) { throw H2O.fail(); }
  @Override protected HTTP404V1 fillFrom( Handler h ) { throw H2O.fail(); }

  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.bodyHead();
    ab.title("HTTP 404 - Not Found");
    ab.p("<div class='alert alert-error'>").p(errmsg).p("</div>");
    return ab.bodyTail();
  }
}
