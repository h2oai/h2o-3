package hex.schemas;

import hex.deeplearning.DeepLearning;
import hex.deeplearning.DeepLearningModel;
import water.Job;
import water.api.*;

/** TODO: only used by old-school web ui: remove!  ModelBuilderHander does this for all the algos.  */
@Deprecated
public class DeepLearningHandler extends Handler {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public DeepLearningHandler() {}

  // TODO: move this into a new ModelBuilderHandler superclass
  // TODO: also add a score method in the new ModelBuilderHandler superclass
  public Schema train(int version, DeepLearningV2 s) {
    DeepLearning builder = s.createAndFillImpl();
    if (builder.error_count() > 0)
      return Schema.schema(version, builder).fillFromImpl(builder);
    assert builder._parms != null; /* impl._job = */
    Job j = builder.trainModel();
    return new JobsV2().fillFromImpl(new JobsHandler.Jobs(j)); // TODO: version
  }
}
