package hex.schemas;

import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel.GBMParameters;
import water.Job;
import water.api.JobV2;
import water.api.Schema;

/** TODO: only used by old-school web ui: remove!  ModelBuilderHander does this for all the algos.  */
@Deprecated
public class GBMHandler extends SharedTreeHandler {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public GBMHandler() {}

  public GBMV2 train(int version, GBMV2 s) {
    GBM builder = s.createAndFillImpl();
    GBMParameters parms = builder._parms;
    assert parms != null; /* impl._job = */
    builder.trainModel();
    s.parameters = new GBMV2.GBMParametersV2();
    s.job = (JobV2) Schema.schema(version, Job.class).fillFromImpl(builder);
    return s;
  }
}
