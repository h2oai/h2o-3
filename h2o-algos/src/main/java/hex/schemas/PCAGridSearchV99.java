package hex.schemas;

import hex.api.PCAGridSearchHandler;
import hex.pca.PCAModel;

/**
 * End-point for GLRM grid search.
 *
 * @see GridSearchSchema
 */
public class PCAGridSearchV99 extends GridSearchSchema<PCAGridSearchHandler.PCAGrid,
    PCAGridSearchV99,
    PCAModel.PCAParameters,
    PCAV3.PCAParametersV3> {

}
