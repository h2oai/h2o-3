package water.schemas;

import java.util.Arrays;
import water.AutoBuffer;
import water.H2O;
import water.api.Handler;
import water.util.RString;

public class HTTP404V1 extends Schema {
  // This Schema has no inputs

  // Output fields
  @API(help="Error message")
  final String errmsg;

  @API(help="Error url")
  final String errurl;

  public HTTP404V1( String msg, String url ) { errmsg = msg; errurl = url; }

  @Override public HTTP404V1 fillInto( Handler h ) { throw H2O.fail(); }
  @Override public HTTP404V1 fillFrom( Handler h ) { throw H2O.fail(); }

  private final String _html = 
    "<div class='container'><div class='row-fluid'><div class='span12'>"+
    "<h3>HTTP 404 - Not Found</h3>"+
    "<div class='alert alert-error'>%ERROR</div>"+
    "</div></div></div>";

  @Override public AutoBuffer writeHTML_impl( AutoBuffer ab ) {
    RString str = new RString(_html);
    str.replace("ERROR", errmsg);
    return ab.putHTMLRaw(str.toString());
  }


}
