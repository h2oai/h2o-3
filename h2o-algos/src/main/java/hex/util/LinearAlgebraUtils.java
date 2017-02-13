package hex.util;

import Jama.CholeskyDecomposition;
import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import hex.DataInfo;
import hex.FrameTask;
import hex.ToEigenVec;
import hex.gram.Gram;
import water.DKV;
import water.Job;
import water.Key;
import water.MRTask;
import water.fvec.*;
import water.util.ArrayUtils;

public class LinearAlgebraUtils {
  /*
   * Forward substitution: Solve Lx = b for x with L = lower triangular matrix, b = real vector
   */
  public static double[] forwardSolve(double[][] L, double[] b) {
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

  /*
   * Impute missing values and transform numeric value x in col of dinfo._adaptedFrame
   */
  private static double modifyNumeric(double x, int col, DataInfo dinfo) {
    double y = (Double.isNaN(x) && dinfo._imputeMissing) ? dinfo._numMeans[col] : x;  // Impute missing value with mean
    if (dinfo._normSub != null && dinfo._normMul != null)  // Transform x if requested
      y = (y - dinfo._normSub[col]) * dinfo._normMul[col];
    return y;
  }

  /*
   * Return row with categoricals expanded in array tmp
   */
  public static double[] expandRow(double[] row, DataInfo dinfo, double[] tmp, boolean modify_numeric) {
    // Categorical columns
    int cidx;
    for(int col = 0; col < dinfo._cats; col++) {
      if (Double.isNaN(row[col])) {
        if (dinfo._imputeMissing)
          cidx = dinfo.catNAFill()[col];
        else if (!dinfo._catMissing[col])
          continue;   // Skip if entry missing and no NA bucket. All indicators will be zero.
        else
          cidx = dinfo._catOffsets[col+1]-1;  // Otherwise, missing value turns into extra (last) factor
      } else
        cidx = dinfo.getCategoricalId(col, (int)row[col]);
      if(cidx >= 0) tmp[cidx] = 1;
    }

    // Numeric columns
    int chk_cnt = dinfo._cats;
    int exp_cnt = dinfo.numStart();
    for(int col = 0; col < dinfo._nums; col++) {
      // Only do imputation and transformation if requested
      tmp[exp_cnt] = modify_numeric ? modifyNumeric(row[chk_cnt], col, dinfo) : row[chk_cnt];
      exp_cnt++; chk_cnt++;
    }
    return tmp;
  }

  /**
   * Computes B = XY where X is n by k and Y is k by p, saving result in new vecs
   * Input: dinfo = X (large frame) with dinfo._adaptedFrame passed to doAll
   *        yt = Y' = transpose of Y (small matrix)
   * Output: XY (large frame) is n by p
   */
  public static class BMulTask extends FrameTask<BMulTask> {
    final double[][] _yt;   // _yt = Y' (transpose of Y)

    public BMulTask(Key<Job> jobKey, DataInfo dinfo, double[][] yt) {
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
      assert yt != null && yt[0].length == numColsExp(xinfo._adaptedFrame,true);
      _xinfo = xinfo;
      _ncolX = xinfo._adaptedFrame.numCols();
      _yt = yt;
    }

    @Override public void map(ChunkAry cs) {
      assert cs._numCols == _ncolX + _yt.length;
      // Copy over only X frame chunks
      Chunk[] xchk = new Chunk[_ncolX];
      DataInfo.Row xrow = _xinfo.newDenseRow();
      for(int i = 0; i < _ncolX; ++i)
        xchk[i] = cs.getChunk(i);
      ChunkAry ary = new ChunkAry(cs._vec,cs._cidx,xchk);
      double sum;
      for(int row = 0; row < cs._len; row++) {
        // Extract row of X
        _xinfo.extractDenseRow(ary, row, xrow);
        if (xrow.isBad()) continue;
        int bidx = _ncolX;
        for (double[] ps : _yt ) {
          // Inner product of X row with Y column (Y' row)
          sum = xrow.innerProduct(ps);
          cs.set(row,bidx, sum);   // Save inner product to B
          bidx++;
        }
        assert bidx == cs._numCols;
      }
    }
  }

  /**
   * Computes A'Q where A is n by p and Q is n by k
   * Input: [A,Q] (large frame) passed to doAll
   * Output: atq = A'Q (small matrix) is \tilde{p} by k where \tilde{p} = number of cols in A with categoricals expanded
   */
  public static class SMulTask extends MRTask<SMulTask> {
    final DataInfo _ainfo;  // Info for frame A
    final int _ncolA;     // Number of cols in A
    final int _ncolExp;   // Number of cols in A with categoricals expanded
    final int _ncolQ;     // Number of cols in Q

    public double[][] _atq;    // Output: A'Q is p_exp by k, where p_exp = number of cols in A with categoricals expanded

    public SMulTask(DataInfo ainfo, int ncolQ) {
      _ainfo = ainfo;
      _ncolA = ainfo._adaptedFrame.numCols();
      _ncolExp = numColsExp(ainfo._adaptedFrame,true);
      _ncolQ = ncolQ;
    }

    @Override public void map(ChunkAry cs) {
      assert (_ncolA + _ncolQ) == cs._numCols;
      _atq = new double[_ncolExp][_ncolQ];

      for(int k = _ncolA; k < (_ncolA + _ncolQ); k++) {
        // Categorical columns
        int cidx;
        for(int p = 0; p < _ainfo._cats; p++) {
          for(int row = 0; row < cs._len; row++) {
            if(cs.isNA(row,p) && _ainfo._skipMissing) continue;
            double q = cs.atd(row,k);
            double a = cs.atd(row,p);

            if (Double.isNaN(a)) {
              if (_ainfo._imputeMissing)
                cidx = _ainfo.catNAFill()[p];
              else if (!_ainfo._catMissing[p])
                continue;   // Skip if entry missing and no NA bucket. All indicators will be zero.
              else
                cidx = _ainfo._catOffsets[p+1]-1;     // Otherwise, missing value turns into extra (last) factor
            } else
              cidx = _ainfo.getCategoricalId(p, (int)a);
            if(cidx >= 0) _atq[cidx][k-_ncolA] += q;   // Ignore categorical levels outside domain
          }
        }

        // Numeric columns
        int pnum = 0;
        int pexp = _ainfo.numStart();
        for(int p = _ainfo._cats; p < _ncolA; p++) {
          for(int row = 0; row  < cs._len; row++) {
            if(cs.isNA(row,p) && _ainfo._skipMissing) continue;
            double q = cs.atd(row,k);
            double a = cs.atd(row,p);
            a = modifyNumeric(a, pnum, _ainfo);
            _atq[pexp][k-_ncolA] += q * a;
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
   * Get R = L' from Cholesky decomposition Y'Y = LL' (same as R from Y = QR)
   * @param jobKey Job key for Gram calculation
   * @param yinfo DataInfo for Y matrix
   * @param transpose Should result be transposed to get L?
   * @return L or R matrix from Cholesky of Y Gram
   */
  public static double[][] computeR(Key<Job> jobKey, DataInfo yinfo, boolean transpose) {
    // Calculate Cholesky of Y Gram to get R' = L matrix
    Gram.GramTask gtsk = new Gram.GramTask(jobKey, yinfo);  // Gram is Y'Y/n where n = nrow(Y)
    gtsk.doAll(yinfo._adaptedFrame);
    // Gram.Cholesky chol = gtsk._gram.cholesky(null);   // If Y'Y = LL' Cholesky, then R = L'
    Matrix ygram = new Matrix(gtsk._gram.getXX());
    CholeskyDecomposition chol = new CholeskyDecomposition(ygram);

    double[][] L = chol.getL().getArray();
    ArrayUtils.mult(L, Math.sqrt(gtsk._nobs));  // Must scale since Cholesky of Y'Y/n where nobs = nrow(Y)
    return transpose ? L : ArrayUtils.transpose(L);
  }

  /**
   * Solve for Q from Y = QR factorization and write into new frame
   * @param jobKey Job key for Gram calculation
   * @param yinfo DataInfo for Y matrix
   * @param ywfrm Input frame [Y,W] where we write into W
   * @return l2 norm of Q - W, where W is old matrix in frame, Q is computed factorization
   */
  public static double computeQ(Key<Job> jobKey, DataInfo yinfo, Frame ywfrm) {
    double[][] cholL = computeR(jobKey, yinfo, true);
    ForwardSolve qrtsk = new ForwardSolve(yinfo, cholL);
    qrtsk.doAll(ywfrm);
    return qrtsk._sse;      // \sum (Q_{i,j} - W_{i,j})^2
  }

  /**
   * Solve for Q from Y = QR factorization and write into Y frame
   * @param jobKey Job key for Gram calculation
   * @param yinfo DataInfo for Y matrix
   */
  public static void computeQInPlace(Key<Job> jobKey, DataInfo yinfo) {
    double[][] cholL = computeR(jobKey, yinfo, true);
    ForwardSolveInPlace qrtsk = new ForwardSolveInPlace(yinfo, cholL);
    qrtsk.doAll(yinfo._adaptedFrame);
  }

  /**
   * Given lower triangular L, solve for Q in QL' = A (LQ' = A') using forward substitution
   * Dimensions: A is n by p, Q is n by p, R = L' is p by p
   * Input: [A,Q] (large frame) passed to doAll, where we write to Q
   */
  public static class ForwardSolve extends MRTask<ForwardSolve> {
    final DataInfo _ainfo;   // Info for frame A
    final int _ncols;     // Number of cols in A and in Q
    final double[][] _L;
    public double _sse;    // Output: Sum-of-squared difference between old and new Q

    public ForwardSolve(DataInfo ainfo, double[][] L) {
      assert L != null && L.length == L[0].length && L.length == ainfo._adaptedFrame.numCols();
      _ainfo = ainfo;
      _ncols = ainfo._adaptedFrame.numCols();
      _L = L;
      _sse = 0;
    }

    @Override public void map(ChunkAry cs) {
      assert 2 * _ncols == cs._numCols;

      // Copy over only A frame chunks
      Chunk[] achks = new Chunk[_ncols];
      for(int i = 0; i < _ncols; ++i)
        achks[i] = cs.getChunk(i);
      DataInfo.Row arow = _ainfo.newDenseRow();
      ChunkAry ary = new ChunkAry(cs._vec,cs._cidx,achks);
      for(int row = 0; row < cs._len; row++) {
        // 1) Extract single expanded row of A

        _ainfo.extractDenseRow(ary, row, arow);
        if (arow.isBad()) continue;
        double[] aexp = arow.expandCats();

        // 2) Solve for single row of Q using forward substitution
        double[] qrow = forwardSolve(_L, aexp);

        // 3) Save row of solved values into Q
        int i = 0;
        for(int d = _ncols; d < 2 * _ncols; d++) {
          double qold = cs.atd(row,d);
          double diff = qrow[i] - qold;
          _sse += diff * diff;    // Calculate SSE between Q_new and Q_old
          cs.set(row,d, qrow[i++]);
        }
        assert i == qrow.length;
      }
    }
  }

  /**
   * Given lower triangular L, solve for Q in QL' = A (LQ' = A') using forward substitution
   * Dimensions: A is n by p, Q is n by p, R = L' is p by p
   * Input: A (large frame) passed to doAll, where we overwrite each row of A with its row of Q
   */
  public static class ForwardSolveInPlace extends MRTask<ForwardSolveInPlace> {
    final DataInfo _ainfo;   // Info for frame A
    final int _ncols;     // Number of cols in A
    final double[][] _L;

    public ForwardSolveInPlace(DataInfo ainfo, double[][] L) {
      assert L != null && L.length == L[0].length && L.length == ainfo._adaptedFrame.numCols();
      _ainfo = ainfo;
      _ncols = ainfo._adaptedFrame.numCols();
      _L = L;
    }

    @Override public void map(ChunkAry cs) {
      assert _ncols == cs._numCols;

      // Copy over only A frame chunks
      Chunk[] achks = new Chunk[_ncols];
      for(int i = 0; i < _ncols; ++i)
        achks[i] = cs.getChunk(i);
      DataInfo.Row arow = _ainfo.newDenseRow();
      ChunkAry ary = new ChunkAry(cs._vec,cs._cidx,achks);
      for(int row = 0; row < cs._len; row++) {
        // 1) Extract single expanded row of A
        _ainfo.extractDenseRow(ary, row, arow);
        if (arow.isBad()) continue;
        double[] aexp = arow.expandCats();

        // 2) Solve for single row of Q using forward substitution
        double[] qrow = forwardSolve(_L, aexp);
        assert qrow.length == _ncols;

        // 3) Overwrite row of A with row of solved values Q
        for(int d = 0; d < _ncols; d++)
          cs.set(row, d, qrow[d]);
      }
    }
  }

  /** Number of columns with categoricals expanded.
   *  @return Number of columns with categoricals expanded into indicator columns */
  public static int numColsExp(Frame fr, boolean useAllFactorLevels) {
    final int uAFL = useAllFactorLevels ? 0 : 1;
    int cols = 0;
    for( VecAry vec : fr.vecs().singleVecs() )
      cols += (vec.isCategorical() && vec.domain() != null) ? vec.domain().length - uAFL : 1;
    return cols;
  }

  static double[] multiple(double[] diagYY /*diagonal*/, int nTot, int nVars) {
    int ny = diagYY.length;
    for (int i = 0; i < ny; i++) {
      diagYY[i] *= nTot;
    }
    double[][] uu = new double[ny][ny];
    for (int i = 0; i < ny; i++) {
      for (int j = 0; j < ny; j++) {
        double yyij = i==j ? diagYY[i] : 0;
        uu[i][j] = (yyij - diagYY[i] * diagYY[j] / nTot) / (nVars * Math.sqrt(diagYY[i] * diagYY[j]));
      }
    }
    EigenvalueDecomposition eigen = new EigenvalueDecomposition(new Matrix(uu));
    double[] eigenvalues = eigen.getRealEigenvalues();
    double[][] eigenvectors = eigen.getV().getArray();
    int maxIndex = ArrayUtils.maxIndex(eigenvalues);
    return eigenvectors[maxIndex];
  }

  static class ProjectOntoEigenVector extends MRTask<ProjectOntoEigenVector> {
    ProjectOntoEigenVector(double[] yCoord) { _yCoord = yCoord; }
    final double[] _yCoord; //projection
    @Override public void map(ChunkAry cs, NewChunkAry nc) {
      for (int i=0;i<cs._len;++i) {
        if (cs.isNA(i)) {
          nc.addNA(0);
        } else {
          int which = cs.at4(i);
          nc.addNum((float)_yCoord[which]); //make it more reproducible by casting to float
        }
      }
    }
  }

  public static VecAry toEigen(VecAry src) {
    Frame train = new Frame(Key.<Frame>make(), new String[]{"enum"}, src);
    DataInfo dinfo = new DataInfo(train, null, 0, true /*_use_all_factor_levels*/, DataInfo.TransformType.NONE,
            DataInfo.TransformType.NONE, /* skipMissing */ false, /* imputeMissing */ true,
            /* missingBucket */ false, /* weights */ null, /* offset */ null, /* fold */ null, /* intercept */ false);
    DKV.put(dinfo);
    Gram.GramTask gtsk = new Gram.GramTask(null, dinfo).doAll(dinfo._adaptedFrame);
    // round the numbers to float precision to be more reproducible
//    double[] rounded = gtsk._gram._diag;
    double[] rounded = new double[gtsk._gram._diag.length];
    for (int i = 0; i < rounded.length; ++i)
      rounded[i] = (float) gtsk._gram._diag[i];
    dinfo.remove();
    VecAry v = new ProjectOntoEigenVector(multiple(rounded, (int) gtsk._nobs, 1)).doAll(1, (byte) 3, train).outputFrame().vecs();
    return v;
  }
  public static ToEigenVec toEigen = new ToEigenVec() {
    @Override public VecAry toEigenVec(VecAry src) { return toEigen(src); }
  };
}
