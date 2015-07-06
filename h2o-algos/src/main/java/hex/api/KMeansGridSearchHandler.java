package hex.api;

import hex.GridSearchHandler;
import hex.kmeans.KMeansGrid;
import hex.kmeans.KMeansModel;
import hex.schemas.KMeansGridSearchV99;
import hex.schemas.KMeansV3;
import water.fvec.Frame;

/**
 * A specific handler for GBM grid search.
 */
public class KMeansGridSearchHandler extends GridSearchHandler<KMeansGrid,
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
  protected KMeansGrid createGrid(Frame f) {
    return KMeansGrid.get(f);
  }
}
