package hex.schemas;

import water.schemas.Schema;
import water.api.Handler;
import water.schemas.API;
import water.H2O;
import water.Key;
import water.H2ONode;

public class DeepLearningV2 extends Schema {

  // Input fields
  private final Inputs _ins = new Inputs();
  private static class Inputs {
    @API(help="Input source frame",validation="/*this input is required*/")
    Key src;
  }

  // Output fields
  private final Outputs _outs = new Outputs();
  private static class Outputs {
    @API(help="Job Key")
    Key job;
  }

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override public DeepLearningV2 fillInto( Handler h ) {
    throw H2O.unimpl();
  }

  // Version&Schema-specific filling from the handler
  @Override public DeepLearningV2 fillFrom( Handler h ) {
    throw H2O.unimpl();
  }

}
