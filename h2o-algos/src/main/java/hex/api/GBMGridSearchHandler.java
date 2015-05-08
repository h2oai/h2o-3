package hex.api;

import hex.GridSearchHandler;
import hex.schemas.GBMGridSearchV3;
import hex.schemas.GBMV3;
import hex.tree.gbm.GBMGrid;
import hex.tree.gbm.GBMModel;
import water.fvec.Frame;

/**
 * A specific handler for GBM grid search.
 */
public class GBMGridSearchHandler extends GridSearchHandler<GBMGrid,
        GBMGridSearchV3,
                                                            GBMModel.GBMParameters,
                                                            GBMV3.GBMParametersV3> {

  /* This is kind of trick, since our REST framework was not able to
     recognize overloaded function do train. Hence, we use delegation here.
   */
  public GBMGridSearchV3 train(int version, GBMGridSearchV3 gridSearchSchema) {
    return super.do_train(version, gridSearchSchema);
  }

  @Override
  protected GBMGrid createGrid(Frame f) {
    return GBMGrid.get(f);
  }
}
