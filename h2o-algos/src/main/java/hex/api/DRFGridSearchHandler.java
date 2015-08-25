package hex.api;

import hex.grid.Grid;
import hex.grid.ModelFactories;
import hex.grid.ModelFactory;
import hex.schemas.DRFGridSearchV99;
import hex.schemas.DRFV3;
import hex.tree.drf.DRFModel;

/**
 * A specific handler for DRF grid search.
 */
public class DRFGridSearchHandler extends GridSearchHandler<DRFGridSearchHandler.DRFGrid,
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
  protected ModelFactory<DRFModel.DRFParameters> getModelFactory() {
    return ModelFactories.DRF_MODEL_FACTORY;
  }

  @Deprecated
  public static class DRFGrid extends Grid<DRFModel.DRFParameters> {

    public DRFGrid() {
      super(null, null, null, null, null);
    }
  }
}
