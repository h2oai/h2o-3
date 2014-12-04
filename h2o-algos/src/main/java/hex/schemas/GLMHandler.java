package hex.schemas;

import hex.glm.GLM;
import hex.glm.GLMModel.GLMParameters;
import water.Job;
import water.api.Handler;
import water.api.JobV2;
import water.api.Schema;

/** TODO: only used by old-school web ui: remove!  ModelBuilderHander does this for all the algos.  */
@Deprecated
public class GLMHandler extends Handler {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public GLMV2 train(int version, GLMV2 s) {
    GLM builder = s.createAndFillImpl();
    GLMParameters parms = builder._parms;
    assert parms != null; /* impl._job = */
    builder.trainModel();
    s.parameters = new GLMV2.GLMParametersV2();
    s.job = (JobV2) Schema.schema(version, Job.class).fillFromImpl(builder);
    return s;
  }
}