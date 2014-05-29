package water.schemas;

import water.api.Tutorials;
import water.*;

public class TutorialsV1 extends Schema<Tutorials,TutorialsV1> {
  // This Schema has no inputs
  // This Schema has no outputs


  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override public TutorialsV1 fillInto( Tutorials h ) {
    return this;                // No fields to fill
  }

  // Version&Schema-specific filling from the handler
  @Override public TutorialsV1 fillFrom( Tutorials h ) {
    return this;                // No fields to fill
  }

  @Override public AutoBuffer writeHTML_impl( AutoBuffer ab ) {
    byte[] b = Tutorials.HTML.getBytes();
    return ab.putA1(b,0,b.length);
  }
}
