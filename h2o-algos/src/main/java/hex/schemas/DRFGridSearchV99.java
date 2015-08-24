package hex.schemas;

import hex.api.DRFGridSearchHandler;
import hex.tree.drf.DRFModel;

/**
 * End-point for DRF grid search.
 *
 * @see hex.schemas.GridSearchSchema
 */
public class DRFGridSearchV99 extends GridSearchSchema<DRFGridSearchHandler.DRFGrid,
    DRFGridSearchV99,
    DRFModel.DRFParameters,
    DRFV3.DRFParametersV3> {

}
