package hex.api;

import hex.grid.Grid;
import hex.grid.ModelFactories;
import hex.grid.ModelFactory;
import hex.kmeans.KMeansModel;
import hex.schemas.KMeansGridSearchV99;
import hex.schemas.KMeansV3;

/**
 * A specific handler for GBM grid search.
 */
public class KMeansGridSearchHandler extends GridSearchHandler<KMeansGridSearchHandler.KmeansGrid,
    KMeansGridSearchV99,
    KMeansModel.KMeansParameters,
    KMeansV3.KMeansParametersV3> {

  /* This is kind of trick, since our REST framework was not able to
     recognize overloaded function do train. Hence, we use delegation here.
   */
  public KMeansGridSearchV99 train(int version, KMeansGridSearchV99 gridSearchSchema) {
    return super.do_train(version, gridSearchSchema);
  }


  @Override
  protected ModelFactory<KMeansModel.KMeansParameters> getModelFactory() {
    return ModelFactories.KMEANS_MODEL_FACTORY;
  }

  @Deprecated
  public static class KmeansGrid extends Grid<KMeansModel.KMeansParameters> {

    public KmeansGrid() {
      super(null, null, null, null, null);
    }
  }
}
