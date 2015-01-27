package hex.schemas;

import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel.GBMParameters;
import water.Job;
import water.api.JobV2;
import water.api.JobsHandler;
import water.api.JobsV2;
import water.api.Schema;

/** TODO: only used by old-school web ui: remove!  ModelBuilderHandler does this for all the algos.  */
@Deprecated
public class GBMHandler extends SharedTreeHandler {
  public GBMHandler() {}

  public Schema train(int version, GBMV2 s) {
    GBM builder = s.createAndFillImpl();
    if (builder.error_count() > 0)
      return Schema.schema(version, builder).fillFromImpl(builder);
    assert builder._parms != null; /* impl._job = */
    Job j = builder.trainModel();
    return new JobsV2().fillFromImpl(new JobsHandler.Jobs(j)); // TODO: version
  }
}
