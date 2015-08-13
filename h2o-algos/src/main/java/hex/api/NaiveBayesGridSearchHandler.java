package hex.api;

import hex.grid.Grid;
import hex.grid.ModelFactories;
import hex.grid.ModelFactory;
import hex.naivebayes.NaiveBayesModel;
import hex.schemas.NaiveBayesGridSearchV99;
import hex.schemas.NaiveBayesV3;

/**
 * A specific handler for Naive Bayes grid search.
 */
public class NaiveBayesGridSearchHandler
    extends GridSearchHandler<NaiveBayesGridSearchHandler.NaiveBayesGrid,
    NaiveBayesGridSearchV99,
    NaiveBayesModel.NaiveBayesParameters,
    NaiveBayesV3.NaiveBayesParametersV3> {

  /* This is kind of trick, since our REST framework was not able to
     recognize overloaded function do train. Hence, we use delegation here.
   */
  public NaiveBayesGridSearchV99 train(int version, NaiveBayesGridSearchV99 gridSearchSchema) {
    return super.do_train(version, gridSearchSchema);
  }

  @Override
  protected ModelFactory<NaiveBayesModel.NaiveBayesParameters> getModelFactory() {
    return ModelFactories.NAIVE_BAYES_MODEL_FACTORY;
  }

  @Deprecated
  public static class NaiveBayesGrid extends Grid<NaiveBayesModel.NaiveBayesParameters> {

    public NaiveBayesGrid() {
      super(null, null, null, null);
    }
  }
}
