package ai.h2o.cascade.core;

import ai.h2o.cascade.stdlib.StdlibFunction;
import org.apache.commons.lang.NotImplementedException;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.ArrayList;


/**
 * Cascade version of the {@link Frame} class.
 */
public class CFrame {

  private Frame wrappedFrame;
  private int ncols;
  private long nrows;
  private ArrayList<Column> columns;

  public static class Column {
    public Frame parent;
    public String name;
    public Vec vec;

    public Column(Frame f, int col) {
      parent = f;
      name = f.name(col);
      vec = f.vec(col);
    }
  }


  //--------------------------------------------------------------------------------------------------------------------
  // Constructors
  //--------------------------------------------------------------------------------------------------------------------

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

  /** For internal use only. */
  private CFrame() {}


  public boolean isLightweight() {
    return wrappedFrame != null;
  }

  public Frame getFrame() {
    if (wrappedFrame == null)
      throw error("Cannot unwrap a CFrame");
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


  /**
   * Create new {@code CFrame} by extracting columns given by the
   * {@code indices} from the current frame.
   *
   * @param indices the list of columns to extract
   */
  public CFrame extractColumns(SliceList indices) {
    if (wrappedFrame == null) {
      // TODO
      throw new NotImplementedException();

    } else {
      CFrame res = new CFrame();
      res.nrows = this.nrows;
      res.ncols = (int) indices.count();
      res.columns = new ArrayList<>(res.ncols);

      for (SliceList.SliceIterator iter = indices.iter(); iter.hasNext(); ) {
        int index = (int) iter.nextPrim();
        if (index < 0 || index >= res.ncols)
          throw error("Column index " + index + " is out of bounds");
        res.columns.add(new Column(wrappedFrame, index));
      }
      return res;
    }
  }





  private StdlibFunction.RuntimeError error(String message) {
    return new StdlibFunction.RuntimeError(message);
  }
}
