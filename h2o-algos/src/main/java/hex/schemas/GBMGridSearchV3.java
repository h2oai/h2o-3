package hex.schemas;

import hex.GridSearchSchema;
import hex.tree.gbm.GBMGrid;
import hex.tree.gbm.GBMModel;

/**
 * End-point for GBM grid search.
 *
 * @see hex.GridSearchSchema
 */
public class GBMGridSearchV3 extends GridSearchSchema<GBMGrid, GBMGridSearchV3, GBMModel.GBMParameters, GBMV3.GBMParametersV3> {
}
