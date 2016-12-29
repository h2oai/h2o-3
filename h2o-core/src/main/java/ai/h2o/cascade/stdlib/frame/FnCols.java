package ai.h2o.cascade.stdlib.frame;

import ai.h2o.cascade.core.SliceList;
import ai.h2o.cascade.core.WorkFrame;
import ai.h2o.cascade.stdlib.StdlibFunction;

/**
 * Extract multiple columns out of a frame.
 */
public class FnCols extends StdlibFunction {

  public WorkFrame apply(WorkFrame frame, SliceList columns) {
    try {
      columns.normalizeR(frame.nCols());
      return frame.keepColumns(columns);
    } catch (IllegalArgumentException e) {
      throw new ValueError(1, e.getMessage());
    }
  }

  public WorkFrame apply(WorkFrame frame, String[] columns) {
    SliceList sl = new SliceList(columns, frame);
    return frame.keepColumns(sl);
  }

}
