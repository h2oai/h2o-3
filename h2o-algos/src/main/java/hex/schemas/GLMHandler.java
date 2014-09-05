package hex.schemas;

import hex.glm.GLM;
import hex.glm.GLMModel.GLMParameters;
import water.H2O;
import water.api.Handler;

/**
 * Created by tomasnykodym on 8/29/14.
 */
public class GLMHandler extends Handler<GLM, GLMV2> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public GLMV2 train(int version, GLM builder) {
    GLMParameters parms = builder._parms;
    assert parms != null; /* impl._job = */
    builder.train();
    GLMV2 schema = schema(version); // TODO: superclass!
    schema.parameters = new GLMV2.GLMParametersV2();
    schema.job = builder._key;
    return schema;
  }
  @Override protected GLMV2 schema(int version) { return new GLMV2(); }
  @Override public void compute2() { throw H2O.fail(); }
}