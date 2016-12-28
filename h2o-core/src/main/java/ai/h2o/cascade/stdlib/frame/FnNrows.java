package ai.h2o.cascade.stdlib.frame;

import ai.h2o.cascade.core.WorkFrame;
import ai.h2o.cascade.stdlib.StdlibFunction;

/**
 * Number of rows in a frame.
 */
public class FnNrows extends StdlibFunction {

  public long apply(WorkFrame frame) {
    return frame.nRows();
  }

}
