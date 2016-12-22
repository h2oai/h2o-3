package ai.h2o.cascade.core;

import ai.h2o.cascade.stdlib.StdlibFunction;
import org.apache.commons.lang.NotImplementedException;
import water.fvec.Frame;

import java.util.ArrayList;


/**
 * Cascade version of the {@link Frame} class.
 * <p>
 * A CFrame may exist in two forms: either in "stone", where it just works as
 * a thin wrapper around an underlying {@link Frame}; or as a "ghost", where
 * the frame is essentially a blueprint for how it should be eventually
 * computed.
 */
public class CFrame {

  private Frame stone;
  private int ncols;
  private long nrows;
  private ArrayList<CFrameColumn> columns;


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
    stone = f;
    ncols = f.numCols();
    nrows = f.numRows();
  }

  /** For internal use only. */
  private CFrame() {}



  //--------------------------------------------------------------------------------------------------------------------
  // CFrame properties
  //--------------------------------------------------------------------------------------------------------------------

  /**
   * Return true if the frame is "in stone" (aka materialized, petrified). Such
   * a frame is just a thin wrapper around a regular {@link Frame}.
   */
  public boolean isStoned() {
    return stone != null;
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



  //--------------------------------------------------------------------------------------------------------------------
  // CFrame operations
  //--------------------------------------------------------------------------------------------------------------------

  /**
   * Create new {@code CFrame} by extracting columns given by the
   * {@code indices} from the current frame.
   *
   * @param indices the list of columns to extract
   */
  public CFrame extractColumns(SliceList indices) {
    if (stone == null) {
      // TODO
      throw new NotImplementedException();

    } else {
      CFrame res = new CFrame();
      res.stone = null;
      res.nrows = this.nrows;
      res.ncols = (int) indices.count();
      res.columns = new ArrayList<>(res.ncols);

      SliceList.Iterator iter = indices.iter();
      while (iter.hasNext()) {
        long index = iter.nextPrim();
        if (index < 0 || index >= res.ncols)
          throw error("Column index " + index + " is out of bounds");
        res.columns.add(new CFrameColumn(stone, (int)index));
      }
      return res;
    }
  }




  // Is this really needed?
  public Frame getFrame() {
    if (stone == null)
      throw error("Cannot unwrap a CFrame");
    return stone;
  }

  /** Helper function for raising errors */
  private StdlibFunction.RuntimeError error(String message) {
    return new StdlibFunction.RuntimeError(message);
  }
}
