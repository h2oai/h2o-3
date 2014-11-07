package hex.schemas;

import hex.glm.GLM;
import hex.glm.GLMModel.GLMParameters;
import water.H2O;
import water.api.Handler;

/** TODO: only used by old-school web ui: remove!  ModelBuilderHander does this for all the algos.  */
@Deprecated
public class GLMHandler extends Handler<GLM, GLMV2> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public GLMV2 train(int version, GLM builder) {
    GLMParameters parms = builder._parms;
    assert parms != null; /* impl._job = */
    builder.trainModel();
    GLMV2 schema = schema(version); // TODO: superclass!
    schema.parameters = new GLMV2.GLMParametersV2();
    schema.job = builder._key;
    return schema;
  }
  @Override protected GLMV2 schema(int version) { GLMV2 schema = new GLMV2(); schema.parameters = schema.createParametersSchema(); return schema; }
  @Override public void compute2() { throw H2O.fail(); }
}