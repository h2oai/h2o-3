package hex.api;

import hex.glrm.GLRMModel;
import hex.grid.Grid;
import hex.grid.ModelFactories;
import hex.grid.ModelFactory;
import hex.schemas.GLRMGridSearchV99;
import hex.schemas.GLRMV99;

/**
 * A specific handler for GBM grid search.
 */
public class GLRMGridSearchHandler
    extends GridSearchHandler<GLRMGridSearchHandler.GLRMGrid,
    GLRMGridSearchV99,
    GLRMModel.GLRMParameters,
    GLRMV99.GLRMParametersV99> {

  /* This is kind of trick, since our REST framework was not able to
     recognize overloaded function do train. Hence, we use delegation here.
   */
  public GLRMGridSearchV99 train(int version, GLRMGridSearchV99 gridSearchSchema) {
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
