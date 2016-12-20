package ai.h2o.cascade.stdlib.frame;

import ai.h2o.cascade.stdlib.StdlibFunction;
import water.fvec.Frame;

/**
 * Number of rows in a frame.
 */
public class FnNrows extends StdlibFunction {

  public long apply(Frame frame) {
    return frame.numRows();
  }

}
