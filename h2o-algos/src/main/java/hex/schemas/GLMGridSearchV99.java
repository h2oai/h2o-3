package hex.schemas;

import hex.api.GLMGridSearchHandler;
import hex.glm.GLMModel;

/**
 * End-point for GLM grid search.
 *
 * @see hex.schemas.GridSearchSchema
 */
public class GLMGridSearchV99 extends GridSearchSchema<
        GLMGridSearchHandler.GLMGrid,
        GLMGridSearchV99,
        GLMModel.GLMParameters,
        GLMV3.GLMParametersV3> {
}
