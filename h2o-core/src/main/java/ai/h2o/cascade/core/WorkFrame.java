package ai.h2o.cascade.core;

import ai.h2o.cascade.stdlib.StdlibFunction;
import water.H2O;
import water.fvec.Frame;

import java.util.ArrayList;
import java.util.List;


/**
 * Cascade version of the {@link Frame} class.
 *
 * <p> A WorkFrame may exist in two forms: either in "stone", where it is merely
 * a thin wrapper around an underlying {@link Frame}; or as a "ghost", where
 * the frame comprises a blueprint for how it should eventually be computed.
 *
 */
public class WorkFrame {  // not Iced: not intended to be stored in DKV

  private Frame stone;
  private int ncols;
  private long nrows;
  private List<WorkFrameColumn> columns;

  // If this flag is true, then the WorkFrame exists only on the stack and is not
  // referenced by any Cascade variable. This means that this WorkFrame can be
  // modified in-place without the risk of affecting anyone else. On the other
  // hand, if this flag is false, then the caller who wishes to modify this
  // frame will have to copy it. This flag is always true for "ghost"-mode
  // CFrames.
  private boolean mutable;


  //--------------------------------------------------------------------------------------------------------------------
  // Constructors
  //--------------------------------------------------------------------------------------------------------------------

  /**
   * Construct a new {@code WorkFrame} object as a simple wrapper around the
   * conventional {@link Frame}. This is a very cheap operation, as no
   * processing of the source frame is being done yet.
   * <p>
   * When {@code WorkFrame} is created with this constructor, it is considered
   * to exist in the "stone" mode. It may later be converted into the
   * ghost mode if the cascade runtime requires it.
   */
  public WorkFrame(Frame f) {
    stone = f;
    ncols = f.numCols();
    nrows = f.numRows();
  }

  /** For internal use only. */
  private WorkFrame() {}



  //--------------------------------------------------------------------------------------------------------------------
  // WorkFrame properties
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


  public WorkFrameColumn column(int i) {
    if (columns == null) return null;
    return columns.get(i - ncols + columns.size());
  }


  public byte type(int i) {
    if (stone == null)
      return columns.get(i - ncols + columns.size()).vec.get_type();
    else
      return stone.vec(i).get_type();
  }



  //--------------------------------------------------------------------------------------------------------------------
  // WorkFrame operations
  //--------------------------------------------------------------------------------------------------------------------

  /**
   * Create new {@code WorkFrame} by extracting columns given by the
   * {@code indices} from the current frame.
   *
   * @param indices the list of columns to extract
   */
  public WorkFrame extractColumns(SliceList indices) {
    if (stone == null) {
      // TODO
      throw H2O.unimpl();

    } else {
      WorkFrame res = new WorkFrame();
      res.stone = null;
      res.nrows = this.nrows;
      res.ncols = (int) indices.count();
      res.columns = new ArrayList<>(res.ncols);

      SliceList.Iterator iter = indices.iter();
      while (iter.hasNext()) {
        long index = iter.nextPrim();
        if (index < 0 || index >= res.ncols)
          throw error("Column index " + index + " is out of bounds");
        res.columns.add(new WorkFrameColumn(stone, (int)index));
      }
      return res;
    }
  }


  /**
   * Return index of the column with the given {@code name}, or {@code -1} if
   * such column does not exist in the frame.
   *
   * <p>Note: this performs linear O(N) search, and is therefore not very
   * optimal for bulk search of multiple column names.
   */
  public int findColumnByName(String name) {
    if (stone == null) {
      for (int i = 0; i < ncols; i++) {
        WorkFrameColumn column = column(i);
        if (column.name.equals(name)) {
          return i;
        }
      }
      return -1;
    } else {
      return stone.find(name);
    }
  }



  // Is this really needed?
  public Frame getStoneFrame() {
    if (stone == null)
      throw error("Cannot unwrap a WorkFrame");
    return stone;
  }

  /** Helper function for raising errors */
  private StdlibFunction.RuntimeError error(String message) {
    return new StdlibFunction.RuntimeError(message);
  }
}
