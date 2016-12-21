package ai.h2o.cascade.core;

import ai.h2o.cascade.stdlib.StdlibFunction;
import water.fvec.Frame;

/**
 * Cascade version of the {@link Frame} class.
 */
public class CFrame {

  private Frame wrappedFrame;
  private int ncols;
  private long nrows;


  /**
   * Construct a new {@code CFrame} object as a simple wrapper around the
   * conventional {@link Frame}. This is a very cheap operation, as no
   * processing of the source frame is being done yet.
   * <p>
   * When {@code CFrame} is created with this constructor, it is considered
   * to exist in the "lightweight" mode. It may later be converted into the
   * normal mode if the cascade runtime demands it.
   */
  public CFrame(Frame f) {
    wrappedFrame = f;
    ncols = f.numCols();
    nrows = f.numRows();
  }


  public boolean isLightweight() {
    return wrappedFrame != null;
  }

  public Frame getFrame() {
    if (wrappedFrame == null)
      throw new StdlibFunction.RuntimeError("Cannot unwrap a CFrame");
    return wrappedFrame;
  }

  /**
   * Number of columns in the frame. This counts only the "output" columns,
   * and does not include any input or intermediate columns.
   */
  public int nCols() {
    return ncols;
  }

  /**
   * Number of rows in the frame.
   */
  public long nRows() {
    return nrows;
  }
}
