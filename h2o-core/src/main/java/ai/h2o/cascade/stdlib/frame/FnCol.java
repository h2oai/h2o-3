package ai.h2o.cascade.stdlib.frame;

import ai.h2o.cascade.core.CFrame;
import ai.h2o.cascade.core.SliceList;
import ai.h2o.cascade.stdlib.StdlibFunction;

/**
 * Extract a single column out of a frame.
 */
public class FnCol extends StdlibFunction {

  public CFrame apply(CFrame frame, int column) {
    if (column < 0)
      throw new ValueError(1, "Column index cannot be negative");
    if (column >= frame.nCols())
      throw new ValueError(1, "Column index exceeds number of columns in the frame");

    return frame.extractColumns(new SliceList(column));
  }

  public CFrame apply(CFrame frame, String colname) {
    int i = frame.findColumnByName(colname);
    if (i == -1)
      throw new ValueError(1, "Column '" + colname + "' was not found in the frame");
    return apply(frame, i);
  }

}
