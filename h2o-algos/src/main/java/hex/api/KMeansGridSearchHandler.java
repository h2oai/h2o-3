package hex.api;

import hex.GridSearchHandler;
import hex.kmeans.KMeansGrid;
import hex.kmeans.KMeansModel;
import hex.schemas.GBMGridSearchV3;
import hex.schemas.GBMV3;
import hex.schemas.KMeansGridSearchV3;
import hex.schemas.KMeansV3;
import hex.tree.gbm.GBMGrid;
import hex.tree.gbm.GBMModel;
import water.fvec.Frame;

/**
 * A specific handler for GBM grid search.
 */
public class KMeansGridSearchHandler extends GridSearchHandler<KMeansGrid,
                                                            KMeansGridSearchV3,
                                                            KMeansModel.KMeansParameters,
                                                            KMeansV3.KMeansParametersV3> {

  /* This is kind of trick, since our REST framework was not able to
     recognize overloaded function do train. Hence, we use delegation here.
   */
  public KMeansGridSearchV3 train(int version, KMeansGridSearchV3 gridSearchSchema) {
    return super.do_train(version, gridSearchSchema);
  }

  @Override
  protected KMeansGrid createGrid(Frame f) {
    return KMeansGrid.get(f);
  }
}
