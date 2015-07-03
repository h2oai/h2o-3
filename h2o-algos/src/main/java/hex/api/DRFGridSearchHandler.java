package hex.api;

import hex.GridSearchHandler;
import hex.schemas.DRFGridSearchV99;
import hex.schemas.DRFV3;
import hex.tree.drf.DRFGrid;
import hex.tree.drf.DRFModel;
import water.fvec.Frame;

/**
 * A specific handler for DRF grid search.
 */
public class DRFGridSearchHandler extends GridSearchHandler<DRFGrid,
        DRFGridSearchV99,
                                                            DRFModel.DRFParameters,
                                                            DRFV3.DRFParametersV3> {

  /* This is kind of trick, since our REST framework was not able to
     recognize overloaded function do train. Hence, we use delegation here.
   */
  public DRFGridSearchV99 train(int version, DRFGridSearchV99 gridSearchSchema) {
    return super.do_train(version, gridSearchSchema);
  }

  @Override
  protected DRFGrid createGrid(Frame f) {
    return DRFGrid.get(f);
  }
}
