package hex.schemas;

import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel.KMeansParameters;
import water.Job;
import water.api.Handler;
import water.api.JobV2;
import water.api.Schema;

/** TODO: only used by old-school web ui: remove!  ModelBuilderHandler does this for all the algos.  */
@Deprecated
public class KMeansHandler extends Handler {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public KMeansHandler() {}

  // TODO: move this into a new ModelBuilderHandler superclass
  // TODO: also add a score method in the new ModelBuilderHandler superclass
  public KMeansV2 train(int version, KMeansV2 s) {
    KMeans builder = s.createAndFillImpl();
    KMeansParameters parms = builder._parms;
    assert parms != null; /* impl._job = */
    builder.trainModel();
    s.job = (JobV2) Schema.schema(version, Job.class).fillFromImpl(builder);
    return s;
  }
}
