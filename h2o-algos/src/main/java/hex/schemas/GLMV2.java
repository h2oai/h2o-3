package hex.schemas;

import hex.glm.GLM;
import hex.glm.GLMModel.GLMParameters;
import water.api.ModelParametersSchema;
import water.fvec.Frame;
import water.util.PojoUtils;

/**
 * Created by tomasnykodym on 8/29/14.
 */
public class GLMV2 extends ModelBuilderSchema<GLM,GLMV2,GLMV2.GLMParametersV2> {

  public static final class GLMParametersV2 extends ModelParametersSchema<GLMParameters, GLMParametersV2> {
    // TODO: parameters are all wrong. . .
    public String[] fields() { return new String[] { "destination_key", "max_iters", "normalize" }; }

    // Input fields
    public int max_iters;        // Max iterations
    public boolean normalize = true;

    @Override public GLMParametersV2 fillFromImpl(GLMParameters parms) {
      super.fillFromImpl(parms);
      return this;
    }

    public GLMParameters createImpl() {
      GLMParameters impl = new GLMParameters();
      PojoUtils.copyProperties(impl, this, PojoUtils.FieldNaming.DEST_HAS_UNDERSCORES);
      return impl;
    }
  }

  //==========================
  // Custom adapters go here

  @Override public GLMParametersV2 createParametersSchema() { return new GLMParametersV2(); }

  // TODO: refactor ModelBuilder creation
  @Override public GLM createImpl() {
    GLMParameters parms = parameters.createImpl();
    return new GLM(parms);
  }

  // Return a URL to invoke GLM on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/GLM?training_frame="+fr._key; }
}
