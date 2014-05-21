package water.schemas;

import water.api.Tutorials;
import water.H2O;
import water.H2ONode;

public class TutorialsV1 extends Schema<Tutorials,TutorialsV1> {

  // Input fields
  private final Inputs _ins = new Inputs();
  private static class Inputs {
    // This Schema has no inputs
  }

  // Output fields
  private final Outputs _outs = new Outputs();
  private static class Outputs {
    // This Schema has no outputs
  }

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override public TutorialsV1 fillInto( Tutorials h ) {
    throw H2O.unimpl();
  }

  // Version&Schema-specific filling from the handler
  @Override public TutorialsV1 fillFrom( Tutorials h ) {
    throw H2O.unimpl();
  }

}
