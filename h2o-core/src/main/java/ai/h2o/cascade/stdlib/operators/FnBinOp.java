package ai.h2o.cascade.stdlib.operators;

import ai.h2o.cascade.core.GhostFrame;
import ai.h2o.cascade.core.GhostFrame1;
import ai.h2o.cascade.stdlib.StdlibFunction;
import water.Iced;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.List;


/**
 * Base class for all binary operators.
 */
public abstract class FnBinOp extends StdlibFunction {

  protected abstract static class BinOpSpec extends Iced<BinOpSpec> {
    public abstract String name();
    public abstract double apply(double x, double y);
  }


  /**
   * Check that numeric frames {@code lhs} and {@code rhs} are compatible,
   * and return the correct {@code GhostFrame} corresponding to applying
   * function {@code op} to those frames.
   * <br>
   * Two frames are considered compatible if they are both column-compatible
   * and row-compatible. Frames are column-compatible if they have either the
   * same number of columns, or one of the frames has just one column.
   * Similarly, the frames are row-compatible if they have either same number
   * of rows, or one of them has just one row. In other words, the following
   * 9 cases are possible:
   * <pre>
   *                      L.rows == 1   R.rows == 1   L.rows == R.rows
   *   L.cols == 1            1             3                5
   *   R.cols == 1            4             2                6
   *   L.cols == R.cols       7             8                9
   * </pre>
   */
  protected GhostFrame numeric_frame_op_frame(GhostFrame lhs, GhostFrame rhs, BinOpSpec op) {
    int ncolsL = lhs.numCols();
    int ncolsR = rhs.numCols();
    long nrowsL = lhs.numRows();
    long nrowsR = rhs.numRows();
    if (!lhs.isNumeric())
      throw new ValueError(0, "LHS frame contains non-numeric columns");
    if (!rhs.isNumeric())
      throw new ValueError(1, "RHS frame contains non-numeric columns");

    if (ncolsL == 1 && nrowsL == 1) {  // 1
      return new NumericScalarFrameOp(frame2scalar(lhs), rhs, op);
    }
    if (ncolsR == 1 && nrowsR == 1) {  // 2
      return new NumericFrameScalarOp(lhs, frame2scalar(rhs), op);
    }
    if (ncolsL == 1 && nrowsR == 1) {  // 3
      return new NumericColRowOp(lhs, frame2row(rhs), op);
    }
    if (ncolsR == 1 && nrowsL == 1) {  // 4
      return new NumericRowColOp(frame2row(lhs), rhs, op);
    }
    if (ncolsL == 1 && nrowsL == nrowsR) {  // 5
      return new NumericColFrameOp(lhs, rhs, op);
    }
    if (ncolsR == 1 && nrowsL == nrowsR) {  // 6
      return new NumericFrameColOp(lhs, rhs, op);
    }
    if (ncolsL == ncolsR && nrowsL == 1) {  // 7
      return new NumericRowFrameOp(frame2row(lhs), rhs, op);
    }
    if (ncolsL == ncolsR && nrowsR == 1) {  // 8
      return new NumericFrameRowOp(lhs, frame2row(rhs), op);
    }
    if (ncolsL == ncolsR && nrowsL == nrowsR) {  // 9
      return new NumericFrameFrameOp(lhs, rhs, op);
    }
    throw new ValueError(1,
        String.format("RHS frame (%d x %d) is not compatible with the LHS frame (%d x %d)",
            ncolsR, nrowsR, ncolsL, nrowsL)
    );
  }



  //--------------------------------------------------------------------------------------------------------------------
  // Scalar-frame numeric operations
  //--------------------------------------------------------------------------------------------------------------------

  /**
   * A numeric frame which is a result of {@code (x op y)}, where
   * {@code x} is numeric, and {@code y} is a numeric frame.
   */
  protected static class NumericScalarFrameOp extends NumericUniOp {
    public NumericScalarFrameOp() {}  // For serialization
    public NumericScalarFrameOp(double x, GhostFrame y, BinOpSpec op) {
      super(y, x, op);
      if (!parent.isNumeric())
        throw new ValueError(1, "RHS frame is not numeric");
    }

    @Override
    public double getNumValue(int i, int j) {
      return func.apply(d, parent.getNumValue(i, j));
    }
  }


  /**
   * A numeric frame which is a result of {@code (x op y)}, where
   * {@code x} is a numeric frame, and {@code y} is a plain number.
   */
  protected static class NumericFrameScalarOp extends NumericUniOp {
    public NumericFrameScalarOp() {}  // For serialization
    public NumericFrameScalarOp(GhostFrame x, double y, BinOpSpec op) {
      super(x, y, op);
      if (!parent.isNumeric())
        throw new ValueError(0, "LHS frame is not numeric");
    }

    @Override
    public double getNumValue(int i, int j) {
      return func.apply(parent.getNumValue(i, j), d);
    }
  }


  /**
   * Base class for {@link NumericFrameScalarOp} and
   * {@link NumericScalarFrameOp}. It provides common functionality,
   * providing default column names / types.
   */
  private static class NumericUniOp extends GhostFrame1 {
    protected BinOpSpec func;  // Used in the derived classes
    protected double d;        // --"--
    private transient int ncols;

    public NumericUniOp() {}  // For serialization
    public NumericUniOp(GhostFrame parent, double scalar, BinOpSpec op) {
      super(parent);
      func = op;
      d = scalar;
      ncols = parent.numCols();
    }

    @Override public int numCols() {
      return ncols;
    }
    @Override public byte type(int i) {
      return Vec.T_NUM;
    }
    @Override public String name(int i) {
      return "C" + i;
    }
    @Override public boolean isNumeric() { return true; }
  }


  //--------------------------------------------------------------------------------------------------------------------
  // Row-column numeric operations
  //--------------------------------------------------------------------------------------------------------------------

  /**
   * A numeric frame which is a result of {@code (x op y)}, where
   * {@code x} is a single-column numeric frame, and {@code y} is a row of
   * doubles.
   */
  private static class NumericColRowOp extends NumericColumnUniOp {
    public NumericColRowOp() {}  // For serialization
    public NumericColRowOp(GhostFrame lhsCol, double[] rhsRow, BinOpSpec op) {
      super(lhsCol, rhsRow, op);
    }

    @Override
    public double getNumValue(int i, int j) {
      return func.apply(parent.getNumValue(i, 0), row[j]);
    }
  }

  /**
   * A numeric frame which is a result of {@code (x op y)}, where
   * {@code x} is a frame, and {@code y} is a row of doubles.
   */
  private static class NumericFrameRowOp extends NumericColumnUniOp {
    public NumericFrameRowOp() {}  // For serialization
    public NumericFrameRowOp(GhostFrame lhsFrame, double[] rhsRow, BinOpSpec op) {
      super(lhsFrame, rhsRow, op);
    }

    @Override
    public double getNumValue(int i, int j) {
      return func.apply(parent.getNumValue(i, j), row[j]);
    }
  }

  /**
   * A numeric frame which is a result of {@code (x op y)}, where
   * {@code x} is a single-row numeric frame, and {@code y} is a single-column
   * frame.
   */
  private static class NumericRowColOp extends NumericColumnUniOp {
    public NumericRowColOp() {}  // For serialization
    public NumericRowColOp(double[] lhsRow, GhostFrame rhsCol, BinOpSpec op) {
      super(rhsCol, lhsRow, op);
    }

    @Override
    public double getNumValue(int i, int j) {
      return func.apply(row[j], parent.getNumValue(i, 0));
    }
  }

  /**
   * A numeric frame which is a result of {@code (x op y)}, where
   * {@code x} is a row of doubles, and {@code y} is a frame.
   */
  private static class NumericRowFrameOp extends NumericColumnUniOp {
    public NumericRowFrameOp() {}  // For serialization
    public NumericRowFrameOp(double[] lhsRow, GhostFrame rhsFrame, BinOpSpec op) {
      super(rhsFrame, lhsRow, op);
    }

    @Override
    public double getNumValue(int i, int j) {
      return func.apply(row[j], parent.getNumValue(i, j));
    }
  }


  /**
   * Base class for {@link NumericColRowOp} and {@link NumericRowColOp}.
   */
  private static class NumericColumnUniOp extends GhostFrame1 {
    protected double[] row;
    protected BinOpSpec func;

    public NumericColumnUniOp() {}  // For serialization
    public NumericColumnUniOp(GhostFrame column, double[] row, BinOpSpec op) {
      super(column);
      this.row = row;
      func = op;
    }

    @Override public int numCols() { return row.length; }
    @Override public byte type(int i) { return Vec.T_NUM; }
    @Override public String name(int i) { return "C" + i; }
    @Override public boolean isNumeric() { return true; }
  }


  //--------------------------------------------------------------------------------------------------------------------
  // Two-parent frames
  //--------------------------------------------------------------------------------------------------------------------

  private static class NumericColFrameOp extends NumericFrameFrameOp {
    public NumericColFrameOp() {}  // For serialization
    public NumericColFrameOp(GhostFrame lhs, GhostFrame rhs, BinOpSpec op) {
      super(lhs, rhs, op);
    }

    @Override
    public double getNumValue(int i, int j) {
      return func.apply(left.getNumValue(i, 0), right.getNumValue(i, j));
    }
  }


  private static class NumericFrameColOp extends NumericFrameFrameOp {
    public NumericFrameColOp() {}  // For serialization
    public NumericFrameColOp(GhostFrame lhs, GhostFrame rhs, BinOpSpec op) {
      super(lhs, rhs, op);
    }

    @Override
    public double getNumValue(int i, int j) {
      return func.apply(left.getNumValue(i, j), right.getNumValue(i, 0));
    }
  }


  protected static class NumericFrameFrameOp extends GhostFrame {
    protected BinOpSpec func;
    protected GhostFrame left;
    protected GhostFrame right;
    private transient int ncols;
    private transient long nrows;

    public NumericFrameFrameOp() {}  // For serialization
    public NumericFrameFrameOp(GhostFrame lhs, GhostFrame rhs, BinOpSpec function) {
      left = lhs;
      right = rhs;
      func = function;
      ncols = Math.max(left.numCols(), right.numCols());
      nrows = left.numRows();
    }

    @Override public int numCols() {
      return ncols;
    }
    @Override public long numRows() {
      return nrows;
    }
    @Override public byte type(int i) {
      return Vec.T_NUM;
    }
    @Override public String name(int i) {
      return "C" + i;
    }
    @Override public boolean isNumeric() { return true; }

    @Override
    public void prepareInputs(List<Vec> inputs) {
      left.prepareInputs(inputs);
      right.prepareInputs(inputs);
    }

    @Override
    public void preparePerChunk(Chunk[] cs) {
      left.preparePerChunk(cs);
      right.preparePerChunk(cs);
    }

    @Override
    public double getNumValue(int i, int j) {
      return func.apply(left.getNumValue(i, j), right.getNumValue(i, j));
    }
  }



  //--------------------------------------------------------------------------------------------------------------------
  // Private helpers
  //--------------------------------------------------------------------------------------------------------------------

  private double frame2scalar(GhostFrame gf) {
    Frame f = gf.materialize(scope).getWrappedFrame();
    assert f.numCols() == 1 && f.numRows() == 1;
    return f.vec(0).at(0);
  }

  private double[] frame2row(GhostFrame gf) {
    Frame f = gf.materialize(scope).getWrappedFrame();
    assert f.numRows() == 1;
    double[] res = new double[f.numCols()];
    for (int i = 0; i < res.length; ++i) {
      res[i] = f.vec(i).at(0);
    }
    return res;
  }
}
