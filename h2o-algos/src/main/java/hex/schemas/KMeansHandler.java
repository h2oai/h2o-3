package hex.schemas;

import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel.KMeansParameters;
import hex.tree.gbm.GBM;
import water.Job;
import water.api.*;

/** TODO: only used by old-school web ui: remove!  ModelBuilderHandler does this for all the algos.  */
@Deprecated
public class KMeansHandler extends Handler {
  public KMeansHandler() {}

  // TODO: move this into a new ModelBuilderHandler superclass
  // TODO: also add a score method in the new ModelBuilderHandler superclass
  public Schema train(int version, KMeansV2 s) {
    KMeans builder = s.createAndFillImpl();
    if (builder.error_count() > 0)
      return Schema.schema(version, builder).fillFromImpl(builder);
    assert builder._parms != null; /* impl._job = */
    Job j = builder.trainModel();
    return new JobsV2().fillFromImpl(new JobsHandler.Jobs(j)); // TODO: version
  }
}
