package hex.schemas;

import hex.GridSearchSchema;
import hex.kmeans.KMeansGrid;
import hex.kmeans.KMeansModel;
import hex.tree.gbm.GBMGrid;
import hex.tree.gbm.GBMModel;

/**
 * End-point for KMeans grid search.
 *
 * @see hex.GridSearchSchema
 */
public class KMeansGridSearchV3 extends GridSearchSchema<KMeansGrid, KMeansGridSearchV3, KMeansModel.KMeansParameters, KMeansV3.KMeansParametersV3> {
}
