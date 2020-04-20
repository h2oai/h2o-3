package hex.util;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import hex.DataInfo;
import hex.FrameTask;
import hex.Interaction;
import hex.ToEigenVec;
import hex.gam.MatrixFrameUtils.TriDiagonalMatrix;
import hex.gram.Gram;
import jsr166y.ForkJoinTask;
import jsr166y.RecursiveAction;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.Arrays;

import static java.util.Arrays.sort;
import static org.apache.commons.lang.ArrayUtils.reverse;

public class LinearAlgebraUtils {
  /*
   * Forward substitution: Solve Lx = b for x with L = lower triangular matrix, b = real vector
   */
  public static double[] forwardSolve(double[][] L, double[] b) {
    assert L != null && L.length == b.length; // && L.length == L[0].length, allow true lower triangular matrix
    double[] res = new double[b.length];

    for(int i = 0; i < b.length; i++) {
      res[i] = b[i];
      for(int j = 0; j < i; j++)
        res[i] -= L[i][j] * res[j];
      res[i] /= L[i][i];
    }
    return res;
  }


  /**
   * Given a matrix aMat as a double [][] array, this function will return an array that is the
   * square root of the diagonals of aMat.  Note that the first index is column and the second index
   * is row.
   * @param aMat
   * @return
   */
  public static double[] sqrtDiag(double[][] aMat) {
    int matrixSize = aMat.length;
    double[] answer = new double[matrixSize];
    for (int index=0; index < matrixSize; index++)
      answer[index] = Math.sqrt(aMat[index][index]);
    return answer;
  }
  
  public static double[][] chol2Inv(final double[][] cholR, boolean upperTriag) {
    final int matrixSize = cholR.length;  // cholR is actuall transpose(R) from QR
    double[][] cholL = upperTriag?ArrayUtils.transpose(cholR):cholR; // this is R from QR
    final double[][] inverted = new double[matrixSize][];
    RecursiveAction[] ras = new RecursiveAction[matrixSize];
    for (int index=0; index<matrixSize; index++) {
      final double[] oneColumn = new double[matrixSize];
      oneColumn[index] = 1.0;
      final int i = index;
      ras[i] = new RecursiveAction() {
        @Override protected void compute() {
          double[] upperColumn = forwardSolve(cholL, oneColumn);
          inverted[i] = Arrays.copyOf(upperColumn, matrixSize);
        }
      };
    }
    ForkJoinTask.invokeAll(ras);
    double[][] cholRNew = upperTriag?cholR:ArrayUtils.transpose(cholR);
    for (int index=0; index<matrixSize; index++) {
      final double[] oneColumn = new double[matrixSize];
      oneColumn[index] = 1.0;
      final int i = index;
      ras[i] = new RecursiveAction() {
        @Override protected void compute() {
          double[] lowerColumn = new double[matrixSize];
          backwardSolve(cholRNew, inverted[i], lowerColumn);
          inverted[i] = Arrays.copyOf(lowerColumn, matrixSize);
        }
      };
    }
    ForkJoinTask.invokeAll(ras);
    return inverted;
  }

  /**
   * Given the cholesky decomposition of X = QR, this method will return the inverse of
   * transpose(X)*X by attempting to solve for transpose(R)*R*XTX_inverse = Identity matrix
   * 
   * @param cholR
   * @return
   */
  public static double[][] chol2Inv(final double[][] cholR) {
    return chol2Inv(cholR, true);
  }

  /***
   * Generate D matrix as a lower diagonal matrix since it is symmetric and contains only 3 diagonals
   * @param hj
   * @return
   */
  public static double[][] generateTriDiagMatrix(final double[] hj) {
    final int matrixSize = hj.length-1;  // matrix size is numKnots-2
    final double[][] lowDiag = new double[matrixSize][];
    RecursiveAction[] ras = new RecursiveAction[matrixSize];
    for (int index=0; index<matrixSize; index++) {
      final int rowSize = index+1;
      final int i = index;
      final double hjIndex = hj[index];
      final double hjIndexP1 = hj[index+1];
      final double oneO3 = 1.0/3.0;
      final double oneO6 = 1.0/6.0;
      final double[] tempDiag = MemoryManager.malloc8d(rowSize);
      ras[i] = new RecursiveAction() {
        @Override protected void compute() {
          ;
          tempDiag[i] = (hjIndex+hjIndexP1)*oneO3;
          if (i > 0)
            tempDiag[i-1] = hjIndex*oneO6;
          lowDiag[i] = Arrays.copyOf(tempDiag, rowSize);
        }
      };
    }
    ForkJoinTask.invokeAll(ras);
    return lowDiag;
  }

  public static double[][] expandLowTrian2Ful(double[][] cholL) {
    int numRows = cholL.length;
    final double[][] result = new double[numRows][];
    RecursiveAction[] ras = new RecursiveAction[numRows];
    for (int index = 0; index < numRows; index++) {
      final int i = index;
      final double[] tempResult = MemoryManager.malloc8d(numRows);
      ras[i] = new RecursiveAction() {
        @Override protected void compute() {
          for (int colIndex = 0; colIndex <= i; colIndex++)
            tempResult[colIndex] = cholL[i][colIndex];
          result[i] = Arrays.copyOf(tempResult, numRows);
        }
      };
    }
    ForkJoinTask.invokeAll(ras);
    return result;
  }
  
  public static double[][] matrixMultiply(double[][] A, double[][] B ) {
    int arow = A[0].length; // number of rows of result
    int acol = A.length;    // number columns in A
    int bcol = B.length;    // number of columns of B
    final double[][] result = new double[bcol][];
    RecursiveAction[] ras = new RecursiveAction[acol];
    for (int index = 0; index < acol; index++) {
      final int i = index;
      final double[] tempResult = new double[arow];
      ras[i] = new RecursiveAction() {
        @Override protected void compute() {
          ArrayUtils.multArrVec(A, B[i], tempResult);
          result[i] = Arrays.copyOf(tempResult, arow);
        }
      };
    }
    ForkJoinTask.invokeAll(ras);
    return result;
  }

  /**
   * 
   * @param A
   * @param B
   * @param transposeResult: true will return A*B. Otherwise will return transpose(A*B)
   * @return
   */
  public static double[][] matrixMultiplyTriagonal(double[][] A, TriDiagonalMatrix B, boolean transposeResult) {
    int arow = A.length; // number of rows of result
    int bcol = B._size+2;    // number of columns of B, K+2
    final int lastCol = bcol-1;
    final int secondLastCol = bcol-2; // also equal to K
    final int kMinus1 = bcol-3;
    final int kMinus2 = bcol-4;
    final double[][] result = new double[bcol][];
    RecursiveAction[] ras = new RecursiveAction[bcol];
    for (int index = 0; index < bcol; index++) { // column  index
      final int i = index;
      final double[] tempResult = new double[arow];
      final double[] bCol = new double[B._size];
      ras[i] = new RecursiveAction() {
        @Override protected void compute() {
          if (i==0) {
            bCol[0] = B._first_diag[0];
          } else if (i==1) {
            bCol[0] = B._second_diag[0];
            bCol[1] = B._first_diag[1];
          } else if (i==lastCol) {
            bCol[kMinus1] = B._third_diag[kMinus1];
          } else if (i==secondLastCol) {
            bCol[kMinus2] = B._third_diag[kMinus2];
            bCol[kMinus1] =B._second_diag[kMinus1];
          } else {
            bCol[i-2] = B._third_diag[i-2];
            bCol[i-1] = B._second_diag[i-1];
            bCol[i] = B._first_diag[i];
          }
          
          ArrayUtils.multArrVec(A, bCol, tempResult);
          result[i] = Arrays.copyOf(tempResult, arow);
        }
      };
    }
    ForkJoinTask.invokeAll(ras);
    return transposeResult?ArrayUtils.transpose(result):result;
  }

  public static double[] backwardSolve(double[][] L, double[] b, double[] res) {
    assert L != null && L.length == L[0].length && L.length == b.length;
    if (res==null)  // only allocate memory if needed
      res = new double[b.length];
    int lastIndex = b.length-1;
    for (int rowIndex = lastIndex; rowIndex >= 0; rowIndex--) {
      res[rowIndex] = b[rowIndex];
      for (int colIndex = lastIndex; colIndex > rowIndex; colIndex--) {
        res[rowIndex] -= L[rowIndex][colIndex]*res[colIndex];
      }
      res[rowIndex] /= L[rowIndex][rowIndex];
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
  
  public static class FindMaxIndex extends MRTask<FindMaxIndex> {
    public long _maxIndex = -1;
    int _colIndex;
    double _maxValue;
    
    public FindMaxIndex(int colOfInterest, double maxValue) {
      _colIndex = colOfInterest;
      _maxValue = maxValue;
    }
    
    @Override
    public void map(Chunk[] cs) {
      int rowLen = cs[0].len();
      long startRowIndex = cs[0].start();
      for (int rowIndex=0; rowIndex < rowLen; rowIndex++) {
        double rowVal = cs[_colIndex].atd(rowIndex);
        if (rowVal == _maxValue) {
          _maxIndex = startRowIndex+rowIndex;
        }
      }
    }
    
    @Override public void reduce(FindMaxIndex other) {
      if (this._maxIndex < 0)
        this._maxIndex = other._maxIndex;
      else if (this._maxIndex > other._maxIndex)
        this._maxIndex = other._maxIndex; 
    }
  }
  
  public static class CopyQtoQMatrix extends MRTask<CopyQtoQMatrix> {
    @Override public void map(Chunk[] cs) {
      int totColumn = cs.length;  // all columns in cs.
      int halfColumn = totColumn/2; // start of Q matrix
      int totRows = cs[0].len();
      for (int rowIndex=0; rowIndex < totRows; rowIndex++) {
        for (int colIndex=0; colIndex < halfColumn; colIndex++) {
          cs[colIndex].set(rowIndex, cs[colIndex+halfColumn].atd(rowIndex));
        }
      }
    }
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
   * Compute B = XY where where X is n by k and Y is k by p and they are both stored as Frames.  The 
   * result will be stored in part of X as X|B.  Make sure you allocate the correct memory to your X
   * frame.  In addition, this will only work with numerical columns.
   * 
   * Note that there are a size limitation on y Frame.  It needs to have row indexed by integer values only and
   * not long.  Otherwise, the result will be jibberish.
   */
  public static class BMulTaskMatrices extends MRTask<BMulTaskMatrices> {
    final Frame _y; // frame to store y
    final int _nyChunks;  // number of chunks of y Frame
    final int _yColNum;

    public BMulTaskMatrices(Frame y) {
      _y = y;
      _nyChunks = _y.anyVec().nChunks();
      _yColNum = _y.numCols();
    }
    
    private void mulResultPerYChunk(Chunk[] xChunk, Chunk[] yChunk) {
      int xChunkLen = xChunk[0].len();
      int yColLen = yChunk.length;
      int yChunkLen = yChunk[0].len();
      int resultColOffset = xChunk.length-yColLen;  // start of result column in xChunk
      int xChunkColOffset = (int) yChunk[0].start();
      for (int colIndex=0; colIndex < yColLen; colIndex++) {
        int resultColIndex = colIndex+resultColOffset;
        for (int rowIndex=0; rowIndex < xChunkLen; rowIndex++) {
          double origResult = xChunk[resultColIndex].atd(rowIndex);
          for (int interIndex=0; interIndex < yChunkLen; interIndex++) {
            origResult += xChunk[interIndex+xChunkColOffset].atd(rowIndex)*yChunk[colIndex].atd(interIndex);
          }
          xChunk[resultColIndex].set(rowIndex, origResult);
        }
      }
    }
    
    @Override public void map(Chunk[] xChunk) {
      Chunk[] ychunk = new Chunk[_y.numCols()];
      for (int ychunkInd=0; ychunkInd < _nyChunks; ychunkInd++) {
        for (int chkIndex =0 ; chkIndex < _yColNum; chkIndex++) // grab a y chunk
          ychunk[chkIndex] = _y.vec(chkIndex).chunkForChunkIdx(ychunkInd);
        mulResultPerYChunk(xChunk, ychunk);
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
    public boolean _originalImplementation = true;  // if true will produce xB+b0.  If false, just inner product
    
    public BMulInPlaceTask(DataInfo xinfo, double[][] yt, int nColsExp) {
      assert yt != null && yt[0].length == nColsExp;
      _xinfo = xinfo;
      _ncolX = xinfo._adaptedFrame.numCols();
      _yt = yt;
    }

    public BMulInPlaceTask(DataInfo xinfo, double[][] yt, int nColsExp, boolean originalWay) {
      assert yt != null && yt[0].length == nColsExp;
      _xinfo = xinfo;
      _ncolX = xinfo._adaptedFrame.numCols();
      _yt = yt;
      _originalImplementation = originalWay;
    }

    @Override public void map(Chunk[] cs) {
      assert cs.length == _ncolX + _yt.length;
      int lastColInd = _ncolX-1;
      // Copy over only X frame chunks
      Chunk[] xchk = new Chunk[_ncolX]; // only refer to X part, old part of frame
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
          sum = _originalImplementation?xrow.innerProduct(ps):xrow.innerProduct(ps)-ps[lastColInd];
          cs[bidx].set(row, sum);   // Save inner product to B, new part of frame
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

  /***
   * compute the cholesky of xx which stores the lower part of a symmetric square tridiagonal matrix.  We assume
   * that all the elements are positive and it is in place replacement where L will be stored back in the input
   * xx.
   * @param xx
   * @return
   */
  public static void choleskySymDiagMat(double[][] xx) {
    xx[0][0] = Math.sqrt(xx[0][0]);
    int rowNumber = xx.length;
    for (int row = 1; row < rowNumber; row++) {
      // deals with lower diagonal element
      int lowerDiag = row-1;
      if (lowerDiag > 0) {
        int kMinus2 = lowerDiag - 1;
        xx[row][lowerDiag] = (xx[row][lowerDiag] - xx[row][kMinus2])/xx[lowerDiag][lowerDiag];
      } else {
        xx[row][lowerDiag] = xx[row][lowerDiag]/xx[lowerDiag][lowerDiag];
      }
      // deals with diagonal element
      xx[row][row] = Math.sqrt(xx[row][row]-xx[row][lowerDiag]*xx[row][lowerDiag]);
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
    xx = computeR(jobKey, yinfo, true);
    ForwardSolve qrtsk = new ForwardSolve(yinfo, xx);
    qrtsk.doAll(ywfrm);
    return qrtsk._sse;      // \sum (Q_{i,j} - W_{i,j})^2
  }

  public static double[][] computeQ(Key<Job> jobKey, DataInfo yinfo, Frame ywfrm) {
    double[][] xx = computeR(jobKey, yinfo, true);
    ForwardSolve qrtsk = new ForwardSolve(yinfo, xx);
    qrtsk.doAll(ywfrm);
    return xx;      // \sum (Q_{i,j} - W_{i,j})^2
  }

  /**
   * Solve for Q from Y = QR factorization and write into Y frame
   * @param jobKey Job key for Gram calculation
   * @param yinfo DataInfo for Y matrix
   */
  public static double[][] computeQInPlace(Key<Job> jobKey, DataInfo yinfo) {
    double[][] cholL = computeR(jobKey, yinfo, true);
    ForwardSolveInPlace qrtsk = new ForwardSolveInPlace(yinfo, cholL);
    qrtsk.doAll(yinfo._adaptedFrame);
    return cholL;
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
