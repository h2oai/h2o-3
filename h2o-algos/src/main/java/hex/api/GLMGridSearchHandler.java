package hex.api;

import hex.grid.Grid;
import hex.grid.ModelFactories;
import hex.grid.ModelFactory;
import hex.schemas.GLMGridSearchV99;
import hex.schemas.GLMV3;
import hex.glm.GLMModel;

/**
 * A specific handler for GBM grid search.
 */
public class GLMGridSearchHandler extends GridSearchHandler<GLMGridSearchHandler.GLMGrid,
        GLMGridSearchV99,
        GLMModel.GLMParameters,
        GLMV3.GLMParametersV3> {

  /* This is kind of trick, since our REST framework was not able to
     recognize overloaded function do train. Hence, we use delegation here.
   */
  public GLMGridSearchV99 train(int version, GLMGridSearchV99 gridSearchSchema) {
    return super.do_train(version, gridSearchSchema);
  }

  @Override
  protected ModelFactory<GLMModel.GLMParameters> getModelFactory() {
    return ModelFactories.GLM_MODEL_FACTORY;
  }

  // All usages of this class should be replaced by Grid<GBMModel.GBMParameters>
  // However, current scheme system does not support it, so we have to create
  // explicit class representing Grid<GBMModel.GBMParameters>.
  @Deprecated
  public static class GLMGrid extends Grid<GLMModel.GLMParameters> {

    public GLMGrid() {
      super(null, null, null, null, null);
    }
  }
}
