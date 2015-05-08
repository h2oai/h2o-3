package hex.schemas;

import hex.GridSearchSchema;
import hex.tree.drf.DRFGrid;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBMGrid;
import hex.tree.gbm.GBMModel;

/**
 * End-point for DRF grid search.
 *
 * @see hex.GridSearchSchema
 */
public class DRFGridSearchV3 extends GridSearchSchema<DRFGrid, DRFGridSearchV3, DRFModel.DRFParameters, DRFV3.DRFParametersV3> {
}
