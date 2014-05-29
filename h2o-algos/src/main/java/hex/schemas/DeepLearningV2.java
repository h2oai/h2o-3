package hex.schemas;

import water.Iced;
import water.schemas.Schema;
import water.api.Handler;
import water.schemas.API;
import water.H2O;
import water.Key;

public class DeepLearningV2 extends Schema {

  // Input fields
  @API(help="Input source frame",validation="/*this input is required*/")
  Key src;

  // Output fields
  @API(help="Job Key")
  Key job;

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
