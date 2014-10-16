package water.api;
import water.util.DocGen;
import water.util.JStack;

public class JStackV2 extends Schema<JStack,JStackV2> {
  // No inputs

  // Output
  @API(help="Array of Profiles, one per Node in the Cluster", direction=API.Direction.OUTPUT)
  public JStack _jstack;

  @Override public JStack createImpl() {
    //No inputs to set
    return this._jstack;
  }

  @Override public JStackV2 fillFromImpl(JStack jstack) {
    this._jstack = jstack;
    return this;
  }

  @Override public DocGen.HTML writeHTML_impl( DocGen.HTML ab ) {
    if (_jstack == null ) return ab;
    StringBuilder sb = new StringBuilder();
    _jstack.toHTML(sb);
    ab.p(sb.toString());
    return ab;
  }
}
