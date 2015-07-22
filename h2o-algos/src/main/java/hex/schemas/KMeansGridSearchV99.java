package hex.schemas;

import hex.api.KMeansGridSearchHandler;
import hex.kmeans.KMeansModel;

/**
 * End-point for KMeans grid search.
 *
 * @see hex.schemas.GridSearchSchema
 */
public class KMeansGridSearchV99 extends GridSearchSchema<KMeansGridSearchHandler.KmeansGrid,
    KMeansGridSearchV99,
    KMeansModel.KMeansParameters,
    KMeansV3.KMeansParametersV3> {

}
