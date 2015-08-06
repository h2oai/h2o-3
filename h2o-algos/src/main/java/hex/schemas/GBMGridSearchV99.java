package hex.schemas;

import hex.api.GBMGridSearchHandler;
import hex.tree.gbm.GBMModel;

/**
 * End-point for GBM grid search.
 *
 * @see hex.schemas.GridSearchSchema
 */
public class GBMGridSearchV99 extends GridSearchSchema<
    GBMGridSearchHandler.GBMGrid,
    GBMGridSearchV99,
    GBMModel.GBMParameters,
    GBMV3.GBMParametersV3> {
}
