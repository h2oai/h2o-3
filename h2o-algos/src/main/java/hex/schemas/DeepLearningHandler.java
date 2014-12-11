package hex.schemas;

import hex.deeplearning.DeepLearning;
import hex.deeplearning.DeepLearningModel;
import water.Job;
import water.api.Handler;
import water.api.JobV2;
import water.api.Schema;

/** TODO: only used by old-school web ui: remove!  ModelBuilderHander does this for all the algos.  */
@Deprecated
public class DeepLearningHandler extends Handler {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public DeepLearningHandler() {}

  // TODO: move this into a new ModelBuilderHandler superclass
  // TODO: also add a score method in the new ModelBuilderHandler superclass
  public DeepLearningV2 train(int version, DeepLearningV2 s) {
    DeepLearning builder = s.createAndFillImpl();
    DeepLearningModel.DeepLearningParameters parms = builder._parms;
    assert parms != null; /* impl._job = */
    builder.trainModel();
    s.parameters = new DeepLearningV2.DeepLearningParametersV2();
    s.job = (JobV2) Schema.schema(version, Job.class).fillFromImpl(builder);
    return s;
  }
}
