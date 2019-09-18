package hex.util;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import hex.DataInfo;
import hex.FrameTask;
import hex.Interaction;
import hex.ToEigenVec;
import hex.gram.Gram;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;

import static java.util.Arrays.sort;
import static org.apache.commons.lang.ArrayUtils.reverse;

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
    double y = (Double.isNaN(x) && dinfo._imputeMissing) ? dinfo._numNAFill[col] : x;  // Impute missing value
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
      } else {
        if ((dinfo._catOffsets[col + 1] - dinfo._catOffsets[col]) == 1)
          cidx = dinfo.getCategoricalId(col, 0);
        else
          cidx = dinfo.getCategoricalId(col, (int) row[col]);
      }

      if (((dinfo._catOffsets[col+1]-dinfo._catOffsets[col]) == 1) && cidx >=0)  // binary data here, no column expansion, copy data
        tmp[cidx] = row[col];
      else if(cidx >= 0) tmp[cidx] = 1;
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

  public static double[][] reshape1DArray(double[] arr, int m, int n) {
    double[][] arr2D = new double[m][n];
    for (int i = 0; i < m; i++) {
      System.arraycopy(arr, i * n, arr2D[i], 0, n);
    }
    return arr2D;
  }

  public static EigenPair[] createSortedEigenpairs(double[] eigenvalues, double[][] eigenvectors) {
    int count = eigenvalues.length;
    EigenPair eigenPairs[] = new EigenPair[count];
    for (int i = 0; i < count; i++) {
      eigenPairs[i] = new EigenPair(eigenvalues[i], eigenvectors[i]);
    }
    sort(eigenPairs);
    return eigenPairs;
  }

  public static EigenPair[] createReverseSortedEigenpairs(double[] eigenvalues, double[][] eigenvectors) {
    EigenPair[] eigenPairs = createSortedEigenpairs(eigenvalues, eigenvectors);
    reverse(eigenPairs);
    return eigenPairs;
  }

  public static double[] extractEigenvaluesFromEigenpairs(EigenPair[] eigenPairs) {
    int count = eigenPairs.length;
    double[] eigenvalues = new double[count];
    for (int i = 0; i < count; i++) {
      eigenvalues[i] = eigenPairs[i].eigenvalue;
    }
    return eigenvalues;
  }

  public static double[][] extractEigenvectorsFromEigenpairs(EigenPair[] eigenPairs) {
    int count = eigenPairs.length;
    double[][] eigenvectors = new double[count][];
    for (int i = 0; i < count; i++) {
      eigenvectors[i] = eigenPairs[i].eigenvector;
    }
    return eigenvectors;
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

    public BMulInPlaceTask(DataInfo xinfo, double[][] yt, int nColsExp) {
      assert yt != null && yt[0].length == nColsExp;
      _xinfo = xinfo;
      _ncolX = xinfo._adaptedFrame.numCols();
      _yt = yt;
    }

    @Override public void map(Chunk[] cs) {
      assert cs.length == _ncolX + _yt.length;
      // Copy over only X frame chunks
      Chunk[] xchk = new Chunk[_ncolX];
      DataInfo.Row xrow = _xinfo.newDenseRow();
      System.arraycopy(cs,0,xchk,0,_ncolX);
      double sum;
      for(int row = 0; row < cs[0]._len; row++) {
        // Extract row of X
        _xinfo.extractDenseRow(xchk, row, xrow);
        if (xrow.isBad()) continue;
        int bidx = _ncolX;
        for (double[] ps : _yt ) {
          // Inner product of X row with Y column (Y' row)
          sum = xrow.innerProduct(ps);
          cs[bidx].set(row, sum);   // Save inner product to B
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

    public SMulTask(DataInfo ainfo, int ncolQ, int ncolExp) {
      _ainfo = ainfo;
      _ncolA = ainfo._adaptedFrame.numCols();
      _ncolExp = ncolExp;   // when call from GLRM or PCA
      _ncolQ = ncolQ;
    }

    @Override public void map(Chunk cs[]) {
      assert (_ncolA + _ncolQ) == cs.length;
      _atq = new double[_ncolExp][_ncolQ];  // not okay to share.

      for(int k = _ncolA; k < (_ncolA + _ncolQ); k++) {
        // Categorical columns
        int cidx;
        for(int p = 0; p < _ainfo._cats; p++) {
          for(int row = 0; row < cs[0]._len; row++) {
            if(cs[p].isNA(row) && _ainfo._skipMissing) continue;
            double q = cs[k].atd(row);
            double a = cs[p].atd(row);

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
          for(int row = 0; row  < cs[0]._len; row++) {
            if(cs[p].isNA(row) && _ainfo._skipMissing) continue;
            double q = cs[k].atd(row);
            double a = cs[p].atd(row);
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
  public static double[][] computeR(Key<Job> jobKey, DataInfo yinfo, boolean transpose, double[][] xx) {
    // Calculate Cholesky of Y Gram to get R' = L matrix
    Gram.GramTask gtsk = new Gram.GramTask(jobKey, yinfo);  // Gram is Y'Y/n where n = nrow(Y)
    gtsk.doAll(yinfo._adaptedFrame);
    Gram.Cholesky chol = gtsk._gram.cholesky(null);   // If Y'Y = LL' Cholesky, then R = L'
    double[][] L = chol.getL();
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
  public static double computeQ(Key<Job> jobKey, DataInfo yinfo, Frame ywfrm, double[][] xx) {
    double[][] cholL = computeR(jobKey, yinfo, true, xx);
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
    double[][] cholL = computeR(jobKey, yinfo, true, null);
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

    @Override public void map(Chunk cs[]) {
      assert 2 * _ncols == cs.length;

      // Copy over only A frame chunks
      Chunk[] achks = new Chunk[_ncols];
      System.arraycopy(cs,0,achks,0,_ncols);

      for(int row = 0; row < cs[0]._len; row++) {
        // 1) Extract single expanded row of A
        DataInfo.Row arow = _ainfo.newDenseRow();
        _ainfo.extractDenseRow(achks, row, arow);
        if (arow.isBad()) continue;
        double[] aexp = arow.expandCats();

        // 2) Solve for single row of Q using forward substitution
        double[] qrow = forwardSolve(_L, aexp);

        // 3) Save row of solved values into Q
        int i = 0;
        for(int d = _ncols; d < 2 * _ncols; d++) {
          double qold = cs[d].atd(row);
          double diff = qrow[i] - qold;
          _sse += diff * diff;    // Calculate SSE between Q_new and Q_old
          cs[d].set(row, qrow[i++]);
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

    @Override public void map(Chunk cs[]) {
      assert _ncols == cs.length;

      // Copy over only A frame chunks
      Chunk[] achks = new Chunk[_ncols];
      System.arraycopy(cs,0,achks,0,_ncols);

      for(int row = 0; row < cs[0]._len; row++) {
        // 1) Extract single expanded row of A
        DataInfo.Row arow = _ainfo.newDenseRow();
        _ainfo.extractDenseRow(achks, row, arow);
        if (arow.isBad()) continue;
        double[] aexp = arow.expandCats();

        // 2) Solve for single row of Q using forward substitution
        double[] qrow = forwardSolve(_L, aexp);
        assert qrow.length == _ncols;

        // 3) Overwrite row of A with row of solved values Q
        for(int d = 0; d < _ncols; d++)
          cs[d].set(row, qrow[d]);
      }
    }
  }

  /** Number of columns with categoricals expanded.
   *  @return Number of columns with categoricals expanded into indicator columns */
  public static int numColsExp(Frame fr, boolean useAllFactorLevels) {
    final int uAFL = useAllFactorLevels ? 0 : 1;
    int cols = 0;
    for( Vec vec : fr.vecs() )
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
        if (Double.isNaN(uu[i][j])) {
          uu[i][j] = 0;
        }
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
    @Override public void map(Chunk[] cs, NewChunk[] nc) {
      for (int i=0;i<cs[0]._len;++i) {
        if (cs[0].isNA(i)) {
          nc[0].addNA();
        } else {
          int which = (int) cs[0].at8(i);
          nc[0].addNum((float)_yCoord[which]); //make it more reproducible by casting to float
        }
      }
    }
  }

  public static Vec toEigen(Vec src) {
    Key<Frame> source = Key.make();
    Key<Frame> dest = Key.make();
    Frame train = new Frame(source, new String[]{"enum"}, new Vec[]{src});
    int maxLevels = 1024; // keep eigen projection method reasonably fast
    boolean created=false;
    if (src.cardinality()>maxLevels) {
      DKV.put(train);
      created=true;
      Log.info("Reducing the cardinality of a categorical column with " + src.cardinality() + " levels to " + maxLevels);
      Interaction inter = new Interaction();
      inter._source_frame = train._key;
      inter._max_factors = maxLevels; // keep only this many most frequent levels
      inter._min_occurrence = 2; // but need at least 2 observations for a level to be kept
      inter._pairwise = false;
      inter._factor_columns = train.names();
      train = inter.execImpl(dest).get();
    }
    DataInfo dinfo = new DataInfo(train, null, 0, true /*_use_all_factor_levels*/, DataInfo.TransformType.NONE,
            DataInfo.TransformType.NONE, /* skipMissing */ false, /* imputeMissing */ true,
            /* missingBucket */ false, /* weights */ false, /* offset */ false, /* fold */ false, /* intercept */ false);
    DKV.put(dinfo);
    Gram.GramTask gtsk = new Gram.GramTask(null, dinfo).doAll(dinfo._adaptedFrame);
    // round the numbers to float precision to be more reproducible
//    double[] rounded = gtsk._gram._diag;
    double[] rounded = new double[gtsk._gram._diag.length];
    for (int i = 0; i < rounded.length; ++i)
      rounded[i] = (float) gtsk._gram._diag[i];
    dinfo.remove();
    Vec v = new ProjectOntoEigenVector(multiple(rounded, (int) gtsk._nobs, 1)).doAll(1, (byte) 3, train).outputFrame().anyVec();
    if (created) {
      train.remove();
      DKV.remove(source);
    }
    return v;
  }
  public static ToEigenVec toEigen = new ToEigenVec() {
    @Override public Vec toEigenVec(Vec src) { return toEigen(src); }
  };

  public static String getMatrixInString(double[][] matrix) {
    int dimX = matrix.length;
    if (dimX <= 0) {
      return "";
    }
    int dimY = matrix[0].length;
    for (int x = 1; x < dimX; x++) {
      if (matrix[x].length != dimY) {
        return "Stacked matrix!";
      }
    }
    StringBuilder stringOfMatrix = new StringBuilder();
    for (int x = 0; x < dimX; x++) {
      for (int y = 0; y < dimY; y++) {
        if (matrix[x][y] > 0) {
          stringOfMatrix.append(' ');   // a leading space before a number
        }
        stringOfMatrix.append(String.format("%.4f\t", matrix[x][y]));
      }
      stringOfMatrix.append('\n');
    }
    return stringOfMatrix.toString();
  }
}
