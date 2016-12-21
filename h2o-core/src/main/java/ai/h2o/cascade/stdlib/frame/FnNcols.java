package ai.h2o.cascade.stdlib.frame;

import ai.h2o.cascade.core.CFrame;
import ai.h2o.cascade.stdlib.StdlibFunction;

/**
 * Number of vecs (columns) in a frame.
 */
public class FnNcols extends StdlibFunction {

  public int apply(CFrame frame) {
    return frame.nCols();
  }

}
