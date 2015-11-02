package hex.api;

import hex.glrm.GLRMModel;
import hex.grid.Grid;
import hex.grid.ModelFactories;
import hex.grid.ModelFactory;
import hex.schemas.GLRMGridSearchV3;
import hex.schemas.GLRMV3;

/**
 * A specific handler for GBM grid search.
 */
public class GLRMGridSearchHandler
    extends GridSearchHandler<GLRMGridSearchHandler.GLRMGrid,
        GLRMGridSearchV3,
    GLRMModel.GLRMParameters,
        GLRMV3.GLRMParametersV3> {

  /* This is kind of trick, since our REST framework was not able to
     recognize overloaded function do train. Hence, we use delegation here.
   */
  public GLRMGridSearchV3 train(int version, GLRMGridSearchV3 gridSearchSchema) {
    return super.do_train(version, gridSearchSchema);
  }

  @Override
  protected ModelFactory<GLRMModel.GLRMParameters> getModelFactory() {
    return ModelFactories.GLRM_MODEL_FACTORY;
  }

  @Deprecated
  public static class GLRMGrid extends Grid<GLRMModel.GLRMParameters> {

    public GLRMGrid() {
      super(null, null, null, null, null);
    }
  }
}
