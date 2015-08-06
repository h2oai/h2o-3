package hex.api;

import hex.deeplearning.DeepLearningParameters;
import hex.grid.Grid;
import hex.grid.ModelFactories;
import hex.grid.ModelFactory;
import hex.schemas.DeepLearningGridSearchV99;
import hex.schemas.DeepLearningV3;

/**
 * A specific handler for GBM grid search.
 */
public class DeepLearningGridSearchHandler
    extends GridSearchHandler<DeepLearningGridSearchHandler.DeepLearningGrid,
    DeepLearningGridSearchV99,
    DeepLearningParameters,
    DeepLearningV3.DeepLearningParametersV3> {

  /* This is kind of trick, since our REST framework was not able to
     recognize overloaded function do train. Hence, we use delegation here.
   */
  public DeepLearningGridSearchV99 train(int version, DeepLearningGridSearchV99 gridSearchSchema) {
    return super.do_train(version, gridSearchSchema);
  }

  @Override
  protected ModelFactory<DeepLearningParameters> getModelFactory() {
    return ModelFactories.DEEP_LEARNING_MODEL_FACTORY;
  }

  @Deprecated
  public static class DeepLearningGrid extends Grid<DeepLearningParameters> {

    public DeepLearningGrid() {
      super(null, null, null, null);
    }
  }
}
