package hex.api;

import hex.grid.Grid;
import hex.grid.ModelFactories;
import hex.grid.ModelFactory;
import hex.pca.PCAModel;
import hex.schemas.PCAGridSearchV99;
import hex.schemas.PCAV3;

/**
 * A specific handler for PCA grid search.
 */
public class PCAGridSearchHandler
    extends GridSearchHandler<PCAGridSearchHandler.PCAGrid,
    PCAGridSearchV99,
    PCAModel.PCAParameters,
    PCAV3.PCAParametersV3> {

  /* This is kind of trick, since our REST framework was not able to
     recognize overloaded function do train. Hence, we use delegation here.
   */
  public PCAGridSearchV99 train(int version, PCAGridSearchV99 gridSearchSchema) {
    return super.do_train(version, gridSearchSchema);
  }

  @Override
  protected ModelFactory<PCAModel.PCAParameters> getModelFactory() {
    return ModelFactories.PCA_MODEL_FACTORY;
  }

  @Deprecated
  public static class PCAGrid extends Grid<PCAModel.PCAParameters> {

    public PCAGrid() {
      super(null, null, null, null, null);
    }
  }
}
