package hex.util;

import Jama.CholeskyDecomposition;
import hex.DataInfo;
import hex.FrameTask;
import water.DKV;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.util.ArrayUtils;

import java.util.Arrays;

public class LinearAlgebraUtils {
  /*
   * Return chunk row with categoricals expanded in array tmp
   */
  protected static double[] expandRow(Chunk[] chks, int row_in_chunk, DataInfo dinfo, double[] tmp) {
    int cidx;

    // Categorical columns
    for(int col = 0; col < dinfo._cats; col++) {
      double x = chks[col].atd(row_in_chunk);
      if (Double.isNaN(x)) {
        if (dinfo._catMissing[col] == 0) continue;   // Skip if entry missing and no NA bucket. All indicators will be zero.
        else cidx = dinfo._catOffsets[col+1]-1;     // Otherwise, missing value turns into extra (last) factor
      } else
        cidx = dinfo.getCategoricalId(col, (int)x);
      if(cidx >= 0) tmp[cidx] = 1;
    }

    // Numeric columns
    int chk_cnt = dinfo._cats;
    int exp_cnt = dinfo.numStart();
    for(int col = 0; col < dinfo._nums; col++) {
      double x = chks[chk_cnt].atd(row_in_chunk);
      if(Double.isNaN(x) && dinfo._imputeMissing)   // Impute missing value with mean
        x = dinfo._numMeans[col];
      if(dinfo._normSub != null && dinfo._normMul != null)   // Transform x if requested
        x = (x - dinfo._normSub[col]) * dinfo._normMul[col];
      tmp[exp_cnt] = x;
      exp_cnt++; chk_cnt++;
    }
    return tmp;
  }

  /*
   * Inner product of chunk row (with categoricals expanded) and vec
   */
  protected static double innerProduct(Chunk[] chks, int row_in_chunk, DataInfo dinfo, double[] vec) {
    double sum = 0;
    int cidx;

    // Categorical columns
    for (int col = 0; col < dinfo._cats; col++) {
      double x = chks[col].atd(row_in_chunk);
      if (Double.isNaN(x)) {
        if (dinfo._catMissing[col] == 0) continue;   // Skip if entry missing and no NA bucket. All indicators will be zero.
        else cidx = dinfo._catOffsets[col+1]-1;     // Otherwise, missing value turns into extra (last) factor
      } else
        cidx = dinfo.getCategoricalId(col, (int)x);
      if(cidx >= 0) sum += vec[cidx];   // Ignore categorical levels outside domain
    }

    // Numeric columns
    int chk_cnt = dinfo._cats;
    int exp_cnt = dinfo.numStart();
    for (int col = 0; col < dinfo._nums; col++) {
      double x = chks[chk_cnt].atd(row_in_chunk);
      if(Double.isNaN(x) && dinfo._imputeMissing)   // Impute missing value with mean
        x = dinfo._numMeans[col];
      if(dinfo._normSub != null && dinfo._normMul != null)   // Transform x if requested
        x = (x - dinfo._normSub[col]) * dinfo._normMul[col];
      sum += vec[exp_cnt] * x;
      exp_cnt++; chk_cnt++;
    }
    assert exp_cnt == vec.length;
    return sum;
  }

  /**
   * Computes B = XY where X is n by k and Y is k by p, saving result in new vecs
   * Input: dinfo = X (large frame) with dinfo._adaptedFrame passed to doAll
   *        yt = Y' = transpose of Y (small matrix)
   * Output: XY (large frame) is n by p
   */
  public static class BMulTask extends FrameTask<BMulTask> {
    final double[][] _yt;   // _yt = Y' (transpose of Y)

    public BMulTask(Key jobKey, DataInfo dinfo, double[][] yt) {
      super(jobKey, dinfo);
      _yt = yt;
    }

    @Override protected void processRow(long gid, DataInfo.Row row, NewChunk[] outputs) {
      for(int p = 0; p < _yt.length; p++) {
        double x = row.innerProduct(_yt[p]);
        outputs[p].addNum(x);
      }
    }
  }

  /**
   * Computes B = XY where X is n by k and Y is k by p, saving result in same frame
   * Input: [X,B] (large frame) passed to doAll, where we write to B
   *        yt = Y' = transpose of Y (small matrix)
   *        ncolX = number of columns in X
   */
  public static class BMulInPlaceTask extends MRTask<BMulInPlaceTask> {
    final DataInfo _xinfo;  // Info for frame X
    final double[][] _yt;   // _yt = Y' (transpose of Y)
    final int _ncolX;     // Number of cols in X

    public BMulInPlaceTask(DataInfo xinfo, double[][] yt) {
      assert yt != null && yt[0].length == xinfo._adaptedFrame.numColsExp();
      _xinfo = xinfo;
      _ncolX = xinfo._adaptedFrame.numCols();
      _yt = yt;
    }

    @Override public void map(Chunk[] cs) {
      assert cs.length == _ncolX + _yt.length;
      double sum;

      for(int row = 0; row < cs[0]._len; row++) {
        int bidx = _ncolX;
        for (int p = 0; p < _yt.length; p++) {
          // Inner product of X row with Y column (Y' row)
          sum = innerProduct(cs, row, _xinfo, _yt[p]);

          // Save inner product to B
          cs[bidx].set(row, sum);
          bidx++;
        }
        assert bidx == cs.length;
      }
    }
  }

  /**
   * Computes A'Q where A is n by p and Q is n by k
   * Input: [A,Q] (large frame) passed to doAll
   * Output: atq = A'Q (small matrix) is \tilde{p} by k where \tilde{p} = number of cols in A with categoricals expanded
   */
  public static class SMulTask extends MRTask<SMulTask> {
    final int _ncolA;     // Number of cols in A
    final int _ncolExp;   // Number of cols in A with categoricals expanded
    final int _ncats;     // Number of categorical cols in A
    final int _ncolQ;     // Number of cols in Q
    final double[] _normSub;  // For standardizing A
    final double[] _normMul;
    final int[] _catOffsets;  // Categorical offsets for A
    final int _numStart;      // Beginning of numeric cols in A when categorical cols expanded
    final boolean _use_all_factor_levels;   // Use all factor levels when expanding A?

    public double[][] _atq;    // Output: A'Q is p_exp by k, where p_exp = number of cols in A with categoricals expanded

    public SMulTask(int ncolA, int ncolExp, int ncats, int ncolQ, double[] normSub, double[] normMul, int[] catOffsets, boolean use_all_factor_levels) {
      if(normSub != null) assert normSub.length == ncolA-ncats;
      if(normMul != null) assert normMul.length == ncolA-ncats;
      assert catOffsets != null && (catOffsets.length-1) == ncats;

      _ncolA = ncolA;
      _ncolExp = ncolExp;
      _ncats = ncats;
      _ncolQ = ncolQ;
      _normSub = normSub == null ? new double[_ncolA-_ncats] : normSub;
      if(normMul == null) {
        _normMul = new double[_ncolA-_ncats];
        Arrays.fill(_normMul, 1.0);
      } else
        _normMul = normMul;
      _catOffsets = catOffsets;
      _numStart = _catOffsets[_ncats];
      _use_all_factor_levels = use_all_factor_levels;
    }

    @Override public void map(Chunk cs[]) {
      assert (_ncolA + _ncolQ) == cs.length;
      _atq = new double[_ncolExp][_ncolQ];

      for(int k = _ncolA; k < (_ncolA + _ncolQ); k++) {
        // Categorical columns
        for(int p = 0; p < _ncats; p++) {
          for(int row = 0; row < cs[0]._len; row++) {
            double q = cs[k].atd(row);
            double a = cs[p].atd(row);
            if (Double.isNaN(a)) continue;

            int last_cat = _catOffsets[p+1]-_catOffsets[p]-1;
            int level = (int)a - (_use_all_factor_levels ? 0:1);  // Reduce index by 1 if first factor level dropped during training
            if (level < 0 || level > last_cat) continue;  // Skip categorical level in test set but not in train
            _atq[_catOffsets[p] + level][k-_ncolA] += q;
          }
        }

        // Numeric columns
        int pnum = 0;
        int pexp = _numStart;
        for(int p = _ncats; p < _ncolA; p++) {
          for(int row = 0; row  < cs[0]._len; row++) {
            double q = cs[k].atd(row);
            double a = cs[p].atd(row);
            _atq[pexp][k-_ncolA] += q * (a - _normSub[pnum]) * _normMul[pnum];
          }
          pexp++; pnum++;
        }
        assert pexp == _atq.length;
      }
    }

    @Override public void reduce(SMulTask other) {
      ArrayUtils.add(_atq, other._atq);
    }
  }

  /**
   * Given Cholesky L from A'A = LL', compute Q from A = QR decomposition, where R = L'
   * Dimensions: A is n by p, Q is n by p, R = L' is p by p
   * Input: [A,Q] (large frame) passed to doAll, where we write to Q
   */
  public static class QRfromChol extends MRTask<QRfromChol> {
    final DataInfo _ainfo;   // Info for frame A
    final int _ncolA;     // Number of cols in A
    final int _ncolQ;     // Number of cols in Q
    final double[][] _L;

    public double _err;    // Output: l2 norm of difference between old and new Q

    public QRfromChol(DataInfo ainfo, CholeskyDecomposition chol, double nobs, int ncolQ) {
      _ainfo = ainfo;
      _ncolA = ainfo._adaptedFrame.numCols();
      _ncolQ = ncolQ;

      _L = chol.getL().getArray();
      ArrayUtils.mult(_L, Math.sqrt(nobs));   // Must scale since Cholesky of A'A/nobs where nobs = nrow(A)
      _err = 0;
    }

    public final double[] forwardSolve(double[][] L, double[] b) {
      assert L != null && L.length == L[0].length && L.length == b.length;
      double[] res = new double[b.length];

      for(int i = 0; i < b.length; i++) {
        res[i] = b[i];
        for(int j = 0; j < i; j++)
          res[i] -= L[i][j] * res[j];
        res[i] /= L[i][i];
      }
      return res;
    }

    @Override public void map(Chunk cs[]) {
      assert (_ncolA + _ncolQ) == cs.length;
      double[] arow = new double[_ncolA];

      for(int row = 0; row < cs[0]._len; row++) {
        // 1) Extract single expanded row of A
        expandRow(cs, row, _ainfo, arow);

        // 2) Solve for single row of Q using forward substitution
        double[] qrow = forwardSolve(_L, arow);

        // 3) Save row of solved values into Q
        int i = 0;
        for(int d = _ncolA; d < _ncolA+_ncolQ; d++) {
          double qold = cs[d].atd(row);
          double diff = qrow[i] - qold;
          _err += diff * diff;    // Calculate SSE between Q_new and Q_old
          cs[d].set(row, qrow[i++]);
        }
        assert i == qrow.length;
      }
    }

    @Override protected void postGlobal() { _err = Math.sqrt(_err); }
  }
}
