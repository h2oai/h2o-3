package hex.api;

import hex.GridSearchHandler;
import hex.schemas.DRFGridSearchV3;
import hex.schemas.DRFV3;
import hex.schemas.GBMGridSearchV3;
import hex.schemas.GBMV3;
import hex.tree.drf.DRFGrid;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBMGrid;
import hex.tree.gbm.GBMModel;
import water.fvec.Frame;

/**
 * A specific handler for DRF grid search.
 */
public class DRFGridSearchHandler extends GridSearchHandler<DRFGrid,
                                                            DRFGridSearchV3,
                                                            DRFModel.DRFParameters,
                                                            DRFV3.DRFParametersV3> {

  /* This is kind of trick, since our REST framework was not able to
     recognize overloaded function do train. Hence, we use delegation here.
   */
  public DRFGridSearchV3 train(int version, DRFGridSearchV3 gridSearchSchema) {
    return super.do_train(version, gridSearchSchema);
  }

  @Override
  protected DRFGrid createGrid(Frame f) {
    return DRFGrid.get(f);
  }
}
