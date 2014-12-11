package water.api;

import water.util.DocGen;
import water.util.JStack;
import water.util.PojoUtils;

public class JStackV2 extends Schema<JStack, JStackV2> {
  @API(help="Name of node for this set of stacktraces", direction=API.Direction.OUTPUT)
  public String node_name;

  @API(help="Timestamp for this set of stacktraces", direction=API.Direction.OUTPUT)
  public String time;

  @API(help="Stacktraces", direction=API.Direction.OUTPUT)
  public String[] traces;

  @Override public DocGen.HTML writeHTML_impl( DocGen.HTML ab ) {
    StringBuilder sb = new StringBuilder();
    JStack js = new JStack();
    PojoUtils.copyProperties(js, this, PojoUtils.FieldNaming.CONSISTENT);

    js.toHTML(sb);
    ab.p(sb.toString());
    return ab;
  }
}
