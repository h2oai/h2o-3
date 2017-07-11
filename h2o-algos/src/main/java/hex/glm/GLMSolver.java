package hex.glm;

import hex.DataInfo;
import water.Futures;

/**
 * Created by tomas on 7/6/17.
 */
public abstract class GLMSolver {
  protected abstract void fit(GLM glmJob, ComputationState state);
  protected Futures cleanup(Futures fs){return fs;}
}
