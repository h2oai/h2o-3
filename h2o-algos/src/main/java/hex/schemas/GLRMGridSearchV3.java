package hex.schemas;

import hex.api.GLRMGridSearchHandler;
import hex.glrm.GLRMModel;

/**
 * End-point for GLRM grid search.
 *
 * @see GridSearchSchema
 */
public class GLRMGridSearchV3 extends GridSearchSchema<GLRMGridSearchHandler.GLRMGrid,
        GLRMGridSearchV3,
    GLRMModel.GLRMParameters,
        GLRMV3.GLRMParametersV3> {

}
