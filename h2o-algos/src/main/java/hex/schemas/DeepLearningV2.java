package hex.schemas;

import water.api.Schema;
import water.api.API;
import water.Key;
import water.fvec.Frame;

public class DeepLearningV2 extends Schema<DeepLearningHandler,DeepLearningV2> {

  // Input fields
  @API(help="Input source frame",validation="/*this input is required*/")
  public Key src;

  // Output fields
  @API(help="Job Key")
  Key job;

  @API(help="Model Error")
  float error;

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override public DeepLearningV2 fillInto( DeepLearningHandler h ) {
    h._src = src;
    return this;
  }

  // Version&Schema-specific filling from the handler
  @Override public DeepLearningV2 fillFrom( DeepLearningHandler h ) {
    error = h._dlm.error();
    return this;
  }

  // Return a URL to invoke DeepLearning on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/DeepLearning?src="+fr._key; }
}
