package hex.deeplearning;

import water.DKV;
import water.Iced;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.FileVec;
import water.fvec.Frame;
import water.fvec.Vec;
import static water.fvec.Vec.makeCon;

import java.util.Arrays;
import java.util.TreeMap;

public class Storage {

  /**
   * Abstract vector interface
   */
  public abstract interface Vector {
    public abstract double get(int i);
    public abstract void set(int i, double val);
    public abstract void add(int i, double val);
    public abstract int size();
    public abstract double[] raw();
    public abstract Frame toFrame(Key key);
  }

  /**
   * Abstract matrix interface
   */
  public abstract interface Matrix {
    abstract float get(int row, int col);
    abstract void set(int row, int col, float val);
    abstract void add(int row, int col, float val);
    abstract int cols();
    abstract int rows();
    abstract long size();
    abstract float[] raw();
    public Frame toFrame(Key key);
  }

  /**
   * Abstract tensor interface
   */
  public abstract interface Tensor {
    abstract float get(int slice, int row, int col);
    abstract void set(int slice, int row, int col, float val);
    abstract void add(int slice, int row, int col, float val);
    abstract int slices();
    abstract int cols();
    abstract int rows();
    abstract long size();
    abstract float[] raw();
    public Frame toFrame(int slice, Key key);
  }

  /**
   * Dense vector implementation
   */
  public static class DenseVector extends Iced implements Vector {
    private double[] _data;
    DenseVector(int len) { _data = new double[len]; }
    DenseVector(double[] v) { _data = v; }
    @Override public double get(int i) { return _data[i]; }
    @Override public void set(int i, double val) { _data[i] = val; }
    @Override public void add(int i, double val) { _data[i] += val; }
    @Override public int size() { return _data.length; }
    @Override public double[] raw() { return _data; }
    @Override public Frame toFrame(Key key) { return Storage.toFrame(this, key); }
  }

  /**
   * Dense row matrix implementation
   */
  public final static class DenseRowMatrix extends Iced implements Matrix {
    private float[] _data;
    private int _cols;
    private int _rows;
    DenseRowMatrix(int rows, int cols) { this(new float[cols*rows], rows, cols); }
    DenseRowMatrix(float[] v, int rows, int cols) { _data = v; _rows = rows; _cols = cols; }
    @Override public float get(int row, int col) {
      assert(row<_rows && col<_cols) : "_data.length: " + _data.length + ", checking: " + row + " < " + _rows + " && " + col + " < " + _cols;
      return _data[row*_cols + col];
    }
    @Override public void set(int row, int col, float val) { assert(row<_rows && col<_cols); _data[row*_cols + col] = val; }
    @Override public void add(int row, int col, float val) { assert(row<_rows && col<_cols); _data[row*_cols + col] += val; }
    @Override public int cols() { return _cols; }
    @Override public int rows() { return _rows; }
    @Override public long size() { return (long)_rows*(long)_cols; }
    public float[] raw() { return _data; }
    @Override public Frame toFrame(Key key) { return Storage.toFrame(this, key); }
  }

  /**
   * Dense column matrix implementation
   */
  public final static class DenseColMatrix extends Iced implements Matrix {
    private float[] _data;
    private int _cols;
    private int _rows;
    DenseColMatrix(int rows, int cols) { this(new float[cols*rows], rows, cols); }
    DenseColMatrix(float[] v, int rows, int cols) { _data = v; _rows = rows; _cols = cols; }
    DenseColMatrix(DenseRowMatrix m, int rows, int cols) {
      this(rows, cols);
      for (int row=0;row<rows;++row)
        for (int col=0;col<cols;++col)
          set(row,col, m.get(row,col));
    }
    @Override public float get(int row, int col) { assert(row<_rows && col<_cols); return _data[col*_rows + row]; }
    @Override public void set(int row, int col, float val) { assert(row<_rows && col<_cols); _data[col*_rows + row] = val; }
    @Override public void add(int row, int col, float val) { assert(row<_rows && col<_cols); _data[col*_rows + row] += val; }
    @Override public int cols() { return _cols; }
    @Override public int rows() { return _rows; }
    @Override public long size() { return (long)_rows*(long)_cols; }
    public float[] raw() { return _data; }
    @Override public Frame toFrame(Key key) { return Storage.toFrame(this, key); }
  }

  /**
   * Sparse row matrix implementation
   */
  public final static class SparseRowMatrix extends Iced implements Matrix {
    private TreeMap<Integer, Float>[] _rows;
    private int _cols;
    SparseRowMatrix(int rows, int cols) { this(null, rows, cols); }
    SparseRowMatrix(Matrix v, int rows, int cols) {
      _rows = new TreeMap[rows];
      for (int row=0;row<rows;++row) _rows[row] = new TreeMap<>();
      _cols = cols;
      if (v!=null)
        for (int row=0;row<rows;++row)
          for (int col=0;col<cols;++col)
            if (v.get(row,col) != 0f)
              add(row,col, v.get(row,col));
    }
    @Override public float get(int row, int col) { Float v = _rows[row].get(col); if (v == null) return 0f; else return v; }
    @Override public void add(int row, int col, float val) { set(row,col,get(row,col)+val); }
    @Override public void set(int row, int col, float val) { _rows[row].put(col, val); }
    @Override public int cols() { return _cols; }
    @Override public int rows() { return _rows.length; }
    @Override public long size() { return (long)_rows.length*(long)_cols; }
    TreeMap<Integer, Float> row(int row) { return _rows[row]; }
    public float[] raw() { throw new UnsupportedOperationException("raw access to the data in a sparse matrix is not implemented."); }
    @Override public Frame toFrame(Key key) { return Storage.toFrame(this, key); }
  }

  /**
   * Sparse column matrix implementation
   */
  static final class SparseColMatrix extends Iced implements Matrix {
    private TreeMap<Integer, Float>[] _cols;
    private int _rows;
    SparseColMatrix(int rows, int cols) { this(null, rows, cols); }
    SparseColMatrix(Matrix v, int rows, int cols) {
      _rows = rows;
      _cols = new TreeMap[cols];
      for (int col=0;col<cols;++col) _cols[col] = new TreeMap<>();
      if (v!=null)
        for (int row=0;row<rows;++row)
          for (int col=0;col<cols;++col)
            if (v.get(row,col) != 0f)
              add(row,col, v.get(row,col));
    }
    @Override public float get(int row, int col) { Float v = _cols[col].get(row); if (v == null) return 0f; else return v; }
    @Override public void add(int row, int col, float val) { set(row,col,get(row,col)+val); }
    @Override public void set(int row, int col, float val) { _cols[col].put(row, val); }
    @Override public int cols() { return _cols.length; }
    @Override public int rows() { return _rows; }
    @Override public long size() { return (long)_rows*(long)_cols.length; }
    TreeMap<Integer, Float> col(int col) { return _cols[col]; }
    public float[] raw() { throw new UnsupportedOperationException("raw access to the data in a sparse matrix is not implemented."); }
    @Override public Frame toFrame(Key key) { return Storage.toFrame(this, key); }
  }

  /**
   *  Helper to convert the Matrix to a Frame using MRTask
   */
  static class FrameFiller extends MRTask<FrameFiller> {
    final DenseColMatrix dcm;
    final DenseRowMatrix drm;
    final SparseRowMatrix srm;
    final SparseColMatrix scm;
    FrameFiller(Matrix m) {
      if (m instanceof DenseColMatrix) {
        dcm = (DenseColMatrix)m;
        drm = null;
        srm = null;
        scm = null;
      }
      else if (m instanceof DenseRowMatrix) {
        dcm = null;
        drm = (DenseRowMatrix)m;
        srm = null;
        scm = null;
      }
      else if (m instanceof SparseRowMatrix) {
        dcm = null;
        drm = null;
        srm = (SparseRowMatrix)m;
        scm = null;
      }
      else {
        dcm = null;
        drm = null;
        srm = null;
        scm = (SparseColMatrix)m;
      }
    }
    @Override public void map(Chunk[] cs) {
      Matrix m=null;
      if (dcm != null) m = dcm;
      if (drm != null) m = drm;
      if (scm != null) m = scm;
      if (srm != null) m = srm;
      int off = (int)cs[0].start();
      assert(m.cols() == cs.length);
      for (int c = 0; c < cs.length; ++c) {
        for (int r = 0; r < cs[0]._len; ++r) {
          cs[c].set(r, m.get(off + r, c));
        }
      }
    }
  }

  /**
   * Helper to convert a Vector into a Frame
   * @param v Vector
   * @param key Key for output Frame
   * @return Reference to Frame (which is also in DKV)
   */
  static Frame toFrame(Vector v, Key key) {
    final int log_rows_per_chunk = Math.max(1, FileVec.DFLT_LOG2_CHUNK_SIZE - (int) Math.floor(Math.log(1) / Math.log(2.)));
    Vec vv = makeCon(0, v.size(), log_rows_per_chunk, false /* no rebalancing! */);
    Frame f = new Frame(key, new Vec[]{vv}, true);
    try( Vec.Writer vw = f.vecs()[0].open() ) {
      for (int r = 0; r < v.size(); ++r)
        vw.set(r, v.get(r));
    }
    DKV.put(key, f);
    return f;
  }

  /**
   * Helper to convert a Matrix into a Frame
   * @param m Matrix
   * @param key Key for output Frame
   * @return Reference to Frame (which is also in DKV)
   */
  static Frame toFrame(Matrix m, Key key) {
    final int log_rows_per_chunk = Math.max(1, FileVec.DFLT_LOG2_CHUNK_SIZE - (int) Math.floor(Math.log(m.cols()) / Math.log(2.)));
    Vec v[] = new Vec[m.cols()];
    for (int i = 0; i < m.cols(); ++i) {
      v[i] = makeCon(0, m.rows(), log_rows_per_chunk);
    }
    Frame f = new FrameFiller(m).doAll(new Frame(key, v, true))._fr;
    DKV.put(key, f);
    return f;
  }
}
