package hex.coxph;

import jsr166y.ForkJoinTask;
import jsr166y.RecursiveAction;
import org.apache.commons.math3.analysis.function.Abs;
import water.*;
import water.fvec.FileVec;
import water.fvec.Frame;
import water.fvec.Vec;
import static water.fvec.Vec.makeCon;

public class Storage {

  /**
   * Abstract matrix interface
   */
  public interface Matrix {
    double get(int row, int col);
    void set(int row, int col, double val);
    void add(int row, int col, double val);
    int cols();
    int rows();
    long size();
    double[] raw();
    Frame toFrame(Key<Frame> key);
  }

  static abstract class AbstractMatrix<T extends AbstractMatrix> extends Iced<T> implements Matrix {
    @Override public final Frame toFrame(Key<Frame> key) { return Storage.toFrame(this, key); }
  }

  /**
   * Dense row matrix implementation
   */
  public static class DenseRowMatrix extends AbstractMatrix<DenseRowMatrix> {
    private double[] _data;
    private int _cols;
    private int _rows;
    DenseRowMatrix(int rows, int cols) { this(MemoryManager.malloc8d(cols * rows), rows, cols); }
    private DenseRowMatrix(double[] v, int rows, int cols) { _data = v; _rows = rows; _cols = cols; }
    @Override public double get(int row, int col) {
      assert(row<_rows && col<_cols) : "_data.length: " + _data.length + ", checking: " + row + " < " + _rows + " && " + col + " < " + _cols;
      return _data[row*_cols + col];
    }
    @Override public void set(int row, int col, double val) { assert(row<_rows && col<_cols); _data[row*_cols + col] = val; }
    @Override public void add(int row, int col, double val) { assert(row<_rows && col<_cols); _data[row*_cols + col] += val; }
    @Override public int cols() { return _cols; }
    @Override public int rows() { return _rows; }
    @Override public long size() { return (long)_rows*(long)_cols; }
    @Override public double[] raw() { return _data; }
  }

  /**
   * Helper to convert a Matrix into a Frame
   *
   * @param m Matrix
   * @param key Key for output Frame
   * @return Reference to Frame (which is also in DKV)
   */
  private static Frame toFrame(Matrix m, Key<Frame> key) {
    H2O.submitTask(new ConvertMatrixToFrame(m, key)).join();
    Frame f = DKV.getGet(key);
    assert f != null;
    return f;
  }

  private static class ConvertMatrixToFrame extends H2O.H2OCountedCompleter<ConvertMatrixToFrame> {

    private final Matrix _m;
    private final Key<Frame> _key;

    private ConvertMatrixToFrame(Matrix m, Key<Frame> key) { _m = m; _key = key; }

    @Override
    public void compute2() {
      final int log_rows_per_chunk = Math.max(1, FileVec.DFLT_LOG2_CHUNK_SIZE - (int) Math.floor(Math.log(_m.rows()) / Math.log(2.)));
      Vec vs[] = new Vec[_m.cols()];
      FillVec[] fv = new FillVec[_m.cols()];
      for (int i = 0; i < _m.cols(); ++i) {
        vs[i] = makeCon(0, _m.rows(), log_rows_per_chunk);
        fv[i] = new FillVec(_m, vs[i], i);
      }
      ForkJoinTask.invokeAll(fv);
      Frame f = new Frame(_key, vs, true);
      DKV.put(_key, f);

      tryComplete();
    }

  }

  private static class FillVec extends RecursiveAction {
    FillVec(Matrix m, Vec v, int col) {
      _m = m; _v = v; _col = col;
    }
    final Matrix _m;
    final Vec _v;
    final int _col;
    @Override public void compute() {
      try (Vec.Writer vw = _v.open()) {
        for (int r = 0; r < _m.rows(); r++)
          vw.set(r, _m.get(r, _col));
      }
    }
  }

}
