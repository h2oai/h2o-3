package water.schemas;

import water.api.Tutorial;
import water.H2O;
import water.H2ONode;

public class TutorialV1 extends Schema<Tutorial,TutorialV1> {

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
}
