package ai.h2o.cascade.stdlib.frame;

import ai.h2o.cascade.stdlib.StdlibFunction;
import water.fvec.Frame;

/**
 * Number of vecs (columns) in a frame.
 */
public class FnNcols extends StdlibFunction {

  public int apply(Frame frame) {
    return frame.numCols();
  }

}
