package hex.api;

import hex.grid.Grid;
import hex.grid.ModelFactories;
import hex.grid.ModelFactory;
import hex.schemas.SVDGridSearchV99;
import hex.schemas.SVDV99;
import hex.svd.SVDModel;

/**
 * A specific handler for SVD grid search.
 */
public class SVDGridSearchHandler
    extends GridSearchHandler<SVDGridSearchHandler.SVDGrid,
    SVDGridSearchV99,
    SVDModel.SVDParameters,
    SVDV99.SVDParametersV99> {

  /* This is kind of trick, since our REST framework was not able to
     recognize overloaded function do train. Hence, we use delegation here.
   */
  public SVDGridSearchV99 train(int version, SVDGridSearchV99 gridSearchSchema) {
    return super.do_train(version, gridSearchSchema);
  }

  @Override
  protected ModelFactory<SVDModel.SVDParameters> getModelFactory() {
    return ModelFactories.SVD_MODEL_FACTORY;
  }

  @Deprecated
  public static class SVDGrid extends Grid<SVDModel.SVDParameters> {

    public SVDGrid() {
      super(null, null, null, null, null);
    }
  }
}
