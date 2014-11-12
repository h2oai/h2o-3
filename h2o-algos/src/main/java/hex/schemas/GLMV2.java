package hex.schemas;

import hex.glm.GLM;
import hex.glm.GLMModel.GLMParameters;
import water.api.SupervisedModelParametersSchema;
import water.fvec.Frame;

/**
 * Created by tomasnykodym on 8/29/14.
 */
public class GLMV2 extends SupervisedModelBuilderSchema<GLM,GLMV2,GLMV2.GLMParametersV2> {

  public static final class GLMParametersV2 extends SupervisedModelParametersSchema<GLMParameters, GLMParametersV2> {
    // TODO: parameters are all wrong. . .
    public String[] fields() { return new String[] { "destination_key", "max_iters", "normalize" }; }

    // Input fields
    public int max_iters;        // Max iterations
    public boolean normalize;
  }

  //==========================
  // Custom adapters go here

  // Return a URL to invoke GLM on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/GLM?training_frame="+fr._key; }
}
