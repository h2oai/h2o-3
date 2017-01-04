package ai.h2o.cascade.stdlib.frame;

import ai.h2o.cascade.core.GhostFrame;
import ai.h2o.cascade.core.SliceList;
import ai.h2o.cascade.stdlib.StdlibFunction;

/**
 * Extract multiple columns out of a frame.
 */
public class FnCols extends StdlibFunction {

  public GhostFrame apply(GhostFrame frame, SliceList columns) {
    try {
      columns.normalizeR(frame.numCols());
      // return frame.keepColumns(columns);
      return null;
    } catch (IllegalArgumentException e) {
      throw new ValueError(1, e.getMessage());
    }
  }

  public GhostFrame apply(GhostFrame frame, String[] columns) {
    SliceList sl = new SliceList(columns, frame);
    // return frame.keepColumns(sl);
    return null;
  }

}
