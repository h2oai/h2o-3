package hex.glm;

import water.H2O;

/**
 * Created by tomas on 9/20/17.
 */
public class AutoSolver extends GLMSolver {
  @Override
  protected void fit(GLMImpl glmJob) {
    // solver picking logic goes here
    throw H2O.unimpl();
  }
}
