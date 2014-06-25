package water.api;
import water.util.DocGen;
import water.util.JStack;

public class JStackV2 extends Schema<JStackHandler,JStackV2> {
  // No inputs

  // Output
  @API(help="Array of Profiles, one per Node in the Cluster")
  public JStack _jstack;

  @Override protected JStackV2 fillInto(JStackHandler jstack) {
    //No inputs to set
    return this;
  }

  @Override public JStackV2 fillFrom(JStackHandler jstack) {
    _jstack = jstack._jstack;
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
