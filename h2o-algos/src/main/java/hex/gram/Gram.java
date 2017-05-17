package hex.gram;


import hex.DataInfo;
import hex.FrameTask2;
import jsr166y.ForkJoinTask;
import jsr166y.RecursiveAction;
import water.*;
import water.fvec.Chunk;
import water.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;

public final class Gram extends Iced<Gram> {
  boolean _hasIntercept;
  public double[][] _xx;
  public double[] _diag;
  public double[][] _frame2DProduce;  // store result of transpose(Aframe)*eigenvector2Darray
  public int _diagN;
  final int _denseN;
  int _fullN;
  final static int MIN_TSKSZ=10000;

  public Gram(DataInfo dinfo) {
    this(dinfo.fullN(), dinfo.largestCat(), dinfo.numNums(), dinfo._cats,true);
  }

  public Gram(int N, int diag, int dense, int sparse, boolean hasIntercept) {
    _hasIntercept = hasIntercept;
    _fullN = N + (_hasIntercept?1:0);
    _xx = new double[_fullN - diag][];
    _diag = MemoryManager.malloc8d(_diagN = diag);
    _denseN = dense;
    for( int i = 0; i < (_fullN - _diagN); ++i )
      _xx[i] = MemoryManager.malloc8d(diag + i + 1);
  }

  public void dropIntercept(){
    if(!_hasIntercept) throw new IllegalArgumentException("Has no intercept");
    double [][] xx = new double[_xx.length-1][];
    for(int i = 0; i < xx.length; ++i)
      xx[i] = _xx[i];
    _xx = xx;
    _hasIntercept = false;
    --_fullN;
  }

  public Gram deep_clone(){
    Gram res = clone();
    if(_xx != null)
      res._xx = ArrayUtils.deepClone(_xx);
    if(_diag != null)
      res._diag = res._diag.clone();
    return res;
  }

  public final int fullN(){return _fullN;}
  public double _diagAdded;

  public void addDiag(double [] ds) {
    int i = 0;
    for(;i < Math.min(_diagN,ds.length); ++i)
      _diag[i] += ds[i];
    for(;i < ds.length; ++i)
      _xx[i-_diagN][i] += ds[i];
  }

  public double get(int i, int j) {
    if(j > i) {
      int k = i;
      i = j;
      j = k;
//      throw new IllegalArgumentException("Gram stored as lower diagnoal matrix, j must be < i");
    }
    if(i < _diagN)
      return(j == i)?_diag[i]:0;
    return _xx[i-_diagN][j];
  }

  public void addDiag(double d) {addDiag(d,false);}

  public void addDiag(double d, boolean add2Intercept) {
    _diagAdded += d;
    for( int i = 0; i < _diag.length; ++i )
      _diag[i] += d;
    int ii = (!_hasIntercept || add2Intercept)?0:1;
    for( int i = 0; i < _xx.length - ii; ++i )
      _xx[i][_xx[i].length - 1] += d;
  }

  public double sparseness(){
    double [][] xx = getXX();
    double nzs = 0;
    for(int i = 0; i < xx.length; ++i)
      for(int j = 0; j < xx[i].length; ++j)
        if(xx[i][j] != 0) nzs += 1;
    return nzs/(xx.length*xx.length);
  }

  public double diagSum(){
    double res = 0;
    if(_diag != null){
      for(double d:_diag) res += d;
    }
    if(_xx != null){
      for(double [] x:_xx)res += x[x.length-1];
    }
    return res;
  }

  private static double r2_eps = 1e-7;
  private static final int MIN_PAR = 1000;

  private final void updateZij(int i, int j, double [][] Z, double [] gamma) {
    double [] Zi = Z[i];
    double Zij = Zi[j];
    for (int k = 0; k < j; ++k)
      Zij -= gamma[k] * Zi[k];
    Zi[j] = Zij;
  }
  private final void updateZ(final double [] gamma, final double [][] Z, int j){
    for (int i = j + 1; i < Z.length; ++i)  // update xj to zj //
      updateZij(i,j,Z,gamma);
  }

  /**
   * Compute Cholesky decompostion by computing partial QR decomposition (R == LU).
   *
   * The advantage of this method over the standard solve is that it can deal with Non-SPD matrices.
   * Gram matrix comes out as Non-SPD if we have collinear columns.
   * QR decomposition can identify collinear (redundant) columns and remove them from the dataset.
   *
   * QR computation:
   * QR is computed using Gram-Schmidt elimination, using Gram matrix instead of the underlying dataset.
   *
   * Gram-schmidt decomposition can be computed as follows: (from "The Eelements of Statistical Learning")
   * 1. set z0 = x0 = 1 (Intercept)
   * 2. for j = 1:p
   *      for l = 1:j-1
   *        gamma_jl = dot(x_l,x_j)/dot(x_l,x_l)
   *      zj = xj - sum(gamma_j[l]*x_l)
   *      if(zj ~= 0) xj was redundant (collinear)
   * Zjs are orthogonal projections of xk and form base of the X space. (dot(z_i,z_j) == 0 for i != j)
   * In the end, gammas contain (Scaled) R from the QR decomp which is == LU from cholesky decomp.
   *
   *
   * Note that all of these operations can be be computed from the Gram matrix only, as gram matrix contains
   * dot(x_i,x_j) for i = 1..N, j = 1..N
   *
   * We can obviously compute gamma_lk directly, instead of replacing xk with zk, we fix the gram matrix.
   * When doing that, we need to replace dot(xi,xk) with dot(xi,zk) for all i.
   * There are two cases,
   *   1)  dot(xk,xk) -> dot(zk,zk)
   *       dot(xk - sum(gamma_l*x_l,xk - sum(gamma_l*x_l)
   *       = dot(xk,xk) - 2* sum(gamma_l*dot(x_i,x_k) + sum(gamma_l*sum(gamma_k*dot(x_l,x_k)))
   *       (can be simplified using the fact that dot(zi,zj) == 0 for i != j
   *   2)  dot(xi,xk) -> dot(xi,zk) for i != k
   *      dot(xi, xj - sum(gamma_l*x_l))
   *      = dot(xi, xj) - dot(xi,sum(gamma_l*x_l))
   *      = dot(xi,xj) - sum(gamma_l*dot(xi,x_l)) (linear combination
   *
   * The algorithm then goes as follows:
   *   for j = 1:n
   *     for l = 1:j-1
   *       compute gamma_jl
   *     update gram by replacing xk with zk = xk- sum(gamma_jl*s*xl);
   *
   * @param dropped_cols - empty list which will be filled with collinear columns removed during computation
   * @return Cholesky - cholesky decomposition fo the gram
   */
  public Cholesky qrCholesky(ArrayList<Integer> dropped_cols, boolean standardized) {
    final double [][] Z = getXX(true,true);
    final double [][] R = new double[Z.length][];
    final double [] Zdiag = new double[Z.length];
    final double [] ZdiagInv = new double[Z.length];
    for(int i = 0; i < Z.length; ++i)
      ZdiagInv[i] = 1.0/(Zdiag[i] = Z[i][i]);
    for(int j = 0; j < Z.length; ++j) {
      final double [] gamma = R[j] = new double[j+1];
      for(int l = 0; l <= j; ++l) // compute gamma_l_j
        gamma[l] = Z[j][l]*ZdiagInv[l];
      double zjj = Z[j][j];
      for(int k = 0; k < j; ++k) // only need the diagonal, the rest is 0 (dot product of orthogonal vectors)
        zjj += gamma[k] * (gamma[k] * Z[k][k] - 2*Z[j][k]);
      // Check R^2 for the current column and ignore if too high (1-R^2 too low), R^2 = 1- rs_res/rs_tot
      // rs_res = zjj (the squared residual)
      // rs_tot = sum((yi - mean(y))^2) = mean(y^2) - mean(y)^2,
      //   mean(y^2) is on diagonal
      //   mean(y) is in the intercept (0 if standardized)
      //   might not be regularized with number of observations, that's why dividing by intercept diagonal
      double rs_tot = standardized
              ?ZdiagInv[j]
              :1.0/(Zdiag[j]-Z[j][0]*ZdiagInv[0]*Z[j][0]);
      if(j > 0 && zjj*rs_tot  < r2_eps) { // collinear column, drop it!
        zjj = 0;
        dropped_cols.add(j-1);
        ZdiagInv[j] = 0;
      } else
        ZdiagInv[j] = 1./zjj;
      Z[j][j] = zjj;
      int jchunk = Math.max(1,MIN_PAR/(Z.length-j));
      int nchunks = (Z.length - j - 1)/jchunk;
      nchunks = Math.min(nchunks,H2O.NUMCPUS);
      if(nchunks <= 1) { // single trheaded update
        updateZ(gamma,Z,j);
      } else { // multi-threaded update
        final int fjchunk = (Z.length - 1 - j)/nchunks;
        int rem = Z.length - 1 - j - fjchunk*nchunks;
        for(int i = Z.length-rem; i < Z.length; ++i)
          updateZij(i,j,Z,gamma);
        RecursiveAction[] ras = new RecursiveAction[nchunks];
        final int fj = j;
        int k = 0;
        for (int i = j + 1; i < Z.length-rem; i += fjchunk) { // update xj to zj //
          final int fi = i;
          ras[k++] = new RecursiveAction() {
            @Override
            protected final void compute() {
              int max_i = Math.min(fi+fjchunk,Z.length);
              for(int i = fi; i < max_i; ++i)
                updateZij(i,fj,Z,gamma);
            }
          };
        }
        ForkJoinTask.invokeAll(ras);
      }
    }
    // update the R - we computed Rt/sqrt(diag(Z)) which we can directly use to solve the problem
    if(R.length < 500)
      for(int i = 0; i < R.length; ++i)
        for (int j = 0; j <= i; ++j)
          R[i][j] *= Math.sqrt(Z[j][j]);
    else {
      RecursiveAction [] ras = new RecursiveAction[R.length];
      for(int i = 0; i < ras.length; ++i) {
        final int fi = i;
        final double [] Rrow = R[i];
        ras[i] = new RecursiveAction() {
          @Override
          protected void compute() {
            for (int j = 0; j <= fi; ++j)
              Rrow[j] *= Math.sqrt(Z[j][j]);
          }
        };
      }
      ForkJoinTask.invokeAll(ras);
    }
    // drop the ignored cols
    if(dropped_cols.isEmpty()) return new Cholesky(R,new double[0], true);
    double [][] Rnew = new double[R.length-dropped_cols.size()][];
    for(int i = 0; i < Rnew.length; ++i)
      Rnew[i] = new double[i+1];
    int j = 0;
    for(int i = 0; i < R.length; ++i) {
      if(Z[i][i] == 0) continue;
      int k = 0;
      for(int l = 0; l <= i; ++l) {
        if(k < dropped_cols.size() && l == (dropped_cols.get(k)+1)) {
          ++k;
          continue;
        }
        Rnew[j][l - k] = R[i][l];
      }
      ++j;
    }
    return new Cholesky(Rnew,new double[0], true);
  }


  public void dropCols(int[] cols) {
    int diagCols = 0;
    for(int i =0; i < cols.length; ++i)
      if(cols[i] < _diagN) ++diagCols;
      else break;
    int j = 0;
    if(diagCols > 0) {
      double [] diag = MemoryManager.malloc8d(_diagN - diagCols);
      int k = 0;
      for(int i = 0; i < _diagN; ++i)
        if (j < cols.length && cols[j] == i) {
          ++j;
        } else  diag[k++] = _diag[i];
      _diag = diag;
    }
    double [][] xxNew = new double[_xx.length-cols.length+diagCols][];
    int iNew = 0;
    for(int i = 0; i < _xx.length; ++i) {
      if(j < cols.length && (_diagN + i) == cols[j]){
        ++j; continue;
      }
      if(j == 0) {
        xxNew[iNew++] = _xx[i];
        continue;
      }
      int l = 0,m = 0;
      double [] x = MemoryManager.malloc8d(_xx[i].length-j);
      for(int k = 0; k < _xx[i].length; ++k)
        if(l < cols.length && k == cols[l]) {
          ++l;
        } else
          x[m++] = _xx[i][k];
      xxNew[iNew++] = x;
    }
    _xx = xxNew;
    _diagN = _diag.length;
    _fullN = _xx[_xx.length-1].length;
  }

  public int[] findZeroCols(){
    ArrayList<Integer> zeros = new ArrayList<>();
    if(_diag != null)
      for(int i = 0; i < _diag.length; ++i)
        if(_diag[i] == 0)zeros.add(i);
    for(int i = 0; i < _xx.length; ++i)
      if(_xx[i][_xx[i].length-1] == 0)
        zeros.add(_xx[i].length-1);
    if(zeros.size() == 0) return new int[0];
    int [] ary = new int[zeros.size()];
    for(int i = 0; i < zeros.size(); ++i)
      ary[i] = zeros.get(i);
    return ary;
  }

  public String toString(){
    if(_fullN >= 1000) return "Gram(" + _fullN + ")";
    else return ArrayUtils.pprint(getXX(true,false));
  }

  static public class InPlaceCholesky {
    final double _xx[][];             // Lower triangle of the symmetric matrix.
    private boolean _isSPD;
    private InPlaceCholesky(double xx[][], boolean isspd) { _xx = xx; _isSPD = isspd; }
    static private class BlockTask extends RecursiveAction {
      final double[][] _xx;
      final int _i0, _i1, _j0, _j1;
      public BlockTask(double xx[][], int ifr, int ito, int jfr, int jto) {
        _xx = xx;
        _i0 = ifr; _i1 = ito; _j0 = jfr; _j1 = jto;
      }
      @Override public void compute() {
        for (int i=_i0; i < _i1; i++) {
          double rowi[] = _xx[i];
          for (int k=_j0; k < _j1; k++) {
            double rowk[] = _xx[k];
            double s = 0.0;
            for (int jj = 0; jj < k; jj++) s += rowk[jj]*rowi[jj];
            rowi[k] = (rowi[k] - s) / rowk[k];
          }
        }
      }
    }
    public static InPlaceCholesky decompose_2(double xx[][], int STEP, int P) {
      boolean isspd = true;
      final int N = xx.length;
      P = Math.max(1, P);
      for (int j=0; j < N; j+=STEP) {
        // update the upper left triangle.
        final int tjR = Math.min(j+STEP, N);
        for (int i=j; i < tjR; i++) {
          double rowi[] = xx[i];
          double d = 0.0;
          for (int k=j; k < i; k++) {
            double rowk[] = xx[k];
            double s = 0.0;
            for (int jj = 0; jj < k; jj++) s += rowk[jj]*rowi[jj];
            rowi[k] = s = (rowi[k] - s) / rowk[k];
            d += s*s;
          }
          for (int jj = 0; jj < j; jj++) { double s = rowi[jj]; d += s*s; }
          d = rowi[i] - d;
          isspd = isspd && (d > 0.0);
          rowi[i] = Math.sqrt(Math.max(0.0, d));
        }
        if (tjR == N) break;
        // update the lower strip
        int i = tjR;
        Futures fs = new Futures();
        int rpb = 0;                // rows per block
        int p = P;                  // concurrency
        while ( tjR*(rpb=(N - tjR)/p)<Gram.MIN_TSKSZ && p>1) --p;
        while (p-- > 1) {
          fs.add(new BlockTask(xx,i,i+rpb,j,tjR).fork());
          i += rpb;
        }
        new BlockTask(xx,i,N,j,tjR).compute();
        fs.blockForPending();
      }
      return new InPlaceCholesky(xx, isspd);
    }
    public double[][] getL() { return _xx; }
    public boolean isSPD() { return _isSPD; }
  }

  public Cholesky cholesky(Cholesky chol) {
    return cholesky(chol,true,"");
  }
  /**
   * Compute the Cholesky decomposition.
   *
   * In case our gram starts with diagonal submatrix of dimension N, we exploit this fact to reduce the complexity of the problem.
   * We use the standard decomposition of the Cholesky factorization into submatrices.
   *
   * We split the Gram into 3 regions (4 but we only consider lower diagonal, sparse means diagonal region in this context):
   *     diagonal
   *     diagonal*dense
   *     dense*dense
   * Then we can solve the Cholesky in 3 steps:
   *  1. We solve the diagonal part right away (just do the sqrt of the elements).
   *  2. The diagonal*dense part is simply divided by the sqrt of diagonal.
   *  3. Compute Cholesky of dense*dense - outer product of Cholesky of diagonal*dense computed in previous step
   *
   * @param chol
   * @return the Cholesky decomposition
   */
  public Cholesky cholesky(Cholesky chol, boolean parallelize,String id) {
    long start = System.currentTimeMillis();
    if( chol == null ) {
      double[][] xx = _xx.clone();
      for( int i = 0; i < xx.length; ++i )
        xx[i] = xx[i].clone();
      chol = new Cholesky(xx, _diag.clone());
    }
    final Cholesky fchol = chol;
    final int sparseN = _diag.length;
    final int denseN = _fullN - sparseN;
    // compute the cholesky of the diagonal and diagonal*dense parts
    if( _diag != null ) for( int i = 0; i < sparseN; ++i ) {
      double d = 1.0 / (chol._diag[i] = Math.sqrt(_diag[i]));
      for( int j = 0; j < denseN; ++j )
        chol._xx[j][i] = d*_xx[j][i];
    }
    ForkJoinTask [] fjts = new ForkJoinTask[denseN];
    // compute the outer product of diagonal*dense
    //Log.info("SPARSEN = " + sparseN + "    DENSEN = " + denseN);
    final int[][] nz = new int[denseN][];
    for( int i = 0; i < denseN; ++i ) {
      final int fi = i;
      fjts[i] = new RecursiveAction() {
        @Override protected void compute() {
          int[] tmp = new int[sparseN];
          double[] rowi = fchol._xx[fi];
          int n = 0;
          for( int k = 0; k < sparseN; ++k )
            if (rowi[k] != .0) tmp[n++] = k;
          nz[fi] = Arrays.copyOf(tmp, n);
        }
      };
    }
    ForkJoinTask.invokeAll(fjts);
    for( int i = 0; i < denseN; ++i ) {
      final int fi = i;
      fjts[i] = new RecursiveAction() {
        @Override protected void compute() {
          double[] rowi = fchol._xx[fi];
          int[]    nzi  = nz[fi];
          for( int j = 0; j <= fi; ++j ) {
            double[] rowj = fchol._xx[j];
            int[]    nzj  = nz[j];
            double s = 0;
            for (int t=0,z=0; t < nzi.length && z < nzj.length; ) {
              int k1 = nzi[t];
              int k2 = nzj[z];
              if (k1 < k2) { t++; continue; }
              else if (k1 > k2) { z++; continue; }
              else {
                s += rowi[k1] * rowj[k1];
                t++; z++;
              }
            }
            rowi[j + sparseN] = _xx[fi][j + sparseN] - s;
          }
        }
      };
    }
    ForkJoinTask.invokeAll(fjts);
    // compute the cholesky of dense*dense-outer_product(diagonal*dense)
    double[][] arr = new double[denseN][];
    for( int i = 0; i < arr.length; ++i )
      arr[i] = Arrays.copyOfRange(fchol._xx[i], sparseN, sparseN + denseN);
    int p = Runtime.getRuntime().availableProcessors();
    InPlaceCholesky d = InPlaceCholesky.decompose_2(arr, 10, p);
    fchol.setSPD(d.isSPD());
    arr = d.getL();
    for( int i = 0; i < arr.length; ++i )
      System.arraycopy(arr[i], 0, fchol._xx[i], sparseN, i + 1);
    return chol;
  }

  public double[][] getXX(){return getXX(false, false);}
  public double[][] getXX(boolean lowerDiag, boolean icptFist) {
    final int N = _fullN;
    double[][] xx = new double[N][];
    for( int i = 0; i < N; ++i )
      xx[i] = MemoryManager.malloc8d(lowerDiag?i+1:N);

    return getXX(xx, lowerDiag, icptFist);
  }

  public double[][]getXX(double[][] xalloc) { return getXX(xalloc,false, false);}
  public double[][] getXX(double[][] xalloc, boolean lowerDiag, boolean icptFist) {

    int xlen = xalloc.length;
    for (int rowInd = 0; rowInd < xlen; rowInd++) {
      Arrays.fill(xalloc[rowInd], 0.0);
    }

    int off = 0;
    if(icptFist) {
      double [] icptRow = _xx[_xx.length-1];
      xalloc[0][0] = icptRow[icptRow.length-1];
      for(int i = 0; i < icptRow.length-1; ++i)
        xalloc[i+1][0] = icptRow[i];
      off = 1;
    }
    for( int i = 0; i < _diag.length; ++i )
      xalloc[i+off][i+off] = _diag[i];
    for( int i = 0; i < _xx.length - off; ++i ) {
      for( int j = 0; j < _xx[i].length; ++j ) {
        xalloc[i + _diag.length + off][j + off] = _xx[i][j];
        if(!lowerDiag)
          xalloc[j + off][i + _diag.length + off] = _xx[i][j];
      }
    }
    return xalloc;
  }

  public void add(Gram grm) {
    ArrayUtils.add(_xx,grm._xx);
    ArrayUtils.add(_diag,grm._diag);
  }

  public final boolean hasNaNsOrInfs() {
    for( int i = 0; i < _xx.length; ++i )
      for( int j = 0; j < _xx[i].length; ++j )
        if( Double.isInfinite(_xx[i][j]) || Double.isNaN(_xx[i][j]) ) return true;
    for( double d : _diag )
      if( Double.isInfinite(d) || Double.isNaN(d) ) return true;
    return false;
  }

  public static final class Cholesky {
    public final double[][] _xx;
    protected final double[] _diag;
    private boolean _isSPD;
    private boolean _icptFirst;

    public Cholesky(double[][] xx, double[] diag) {
      _xx = xx;
      _diag = diag;
      _icptFirst = false;
    }

    public Cholesky(double[][] xx, double[] diag, boolean icptFirst) {
      _xx = xx;
      _diag = diag;
      _icptFirst = icptFirst;
      _isSPD = true;
    }


    public void solve(final double [][] ys){
      RecursiveAction [] ras = new RecursiveAction[ys.length];
      for(int i = 0; i < ras.length; ++i) {
        final int fi = i;
        ras[i] = new RecursiveAction() {
          @Override
          protected void compute() {
            ys[fi][fi] = 1;
            solve(ys[fi]);
          }
        };
      }
      ForkJoinTask.invokeAll(ras);
    }
    public double [][] getInv(){
      double [][] res = new double[_xx[_xx.length-1].length][_xx[_xx.length-1].length];
      for(int i = 0; i < res.length; ++i)
        res[i][i] = 1;
      solve(res);
      return res;
    }

    public double [] getInvDiag(){
      final double [] res = new double[_xx.length + _diag.length];
      RecursiveAction [] ras = new RecursiveAction[res.length];
      for(int i = 0; i < ras.length; ++i) {
        final int fi = i;
        ras[i] = new RecursiveAction() {
          @Override
          protected void compute() {
            double [] tmp = new double[res.length];
            tmp[fi] = 1;
            solve(tmp);
            res[fi] = tmp[fi];
          }
        };
      }
      ForkJoinTask.invokeAll(ras);
      return res;
    }

    public double[][] getL() {
      final int N = _xx.length+_diag.length;
      double[][] xx = new double[N][];
      for( int i = 0; i < N; ++i )
        xx[i] = MemoryManager.malloc8d(N);
      for( int i = 0; i < _diag.length; ++i )
        xx[i][i] = _diag[i];
      for( int i = 0; i < _xx.length; ++i ) {
        for( int j = 0; j < _xx[i].length; ++j ) {
          xx[i + _diag.length][j] = _xx[i][j];
        }
      }
      return xx;
    }

    /**
     * Find solution to A*x = y.
     *
     * Result is stored in the y input vector. May throw NonSPDMatrix exception in case Gram is not
     * positive definite.
     *
     * @param y
     */
    public final void   solve(double[] y) {
      if( !isSPD() ) throw new NonSPDMatrixException();
      if(_icptFirst) {
        double icpt = y[y.length-1];
        for(int i = y.length-1; i > 0; --i)
          y[i] = y[i-1];
        y[0] = icpt;
      }
      // diagonal
      for( int k = 0; k < _diag.length; ++k )
        y[k] /= _diag[k];
      // rest
      final int n = _xx.length == 0?0:_xx[_xx.length-1].length;
      // Solve L*Y = B;
      for( int k = _diag.length; k < n; ++k ) {
        double d = 0;
        for( int i = 0; i < k; i++ )
          d += y[i] * _xx[k - _diag.length][i];
        y[k] = (y[k]-d)/_xx[k - _diag.length][k];
      }
      // Solve L'*X = Y;
      for( int k = n - 1; k >= _diag.length; --k ) {
        y[k] /= _xx[k - _diag.length][k];
        for( int i = 0; i < k; ++i )
          y[i] -= y[k] * _xx[k - _diag.length][i];
      }
      // diagonal
      for( int k = _diag.length - 1; k >= 0; --k )
        y[k] /= _diag[k];
      if(_icptFirst) {
        double icpt = y[0];
        for(int i = 1; i < y.length; ++i)
          y[i-1] = y[i];
        y[y.length-1] = icpt;
      }
    }
    public final boolean isSPD() {return _isSPD;}
    public final void setSPD(boolean b) {_isSPD = b;}
  }

  public final void addRowSparse(DataInfo.Row r, double w) {
    final int intercept = _hasIntercept?1:0;
    final int denseRowStart = _fullN - _denseN - _diagN - intercept; // we keep dense numbers at the right bottom of the matrix, -1 is for intercept
    assert _denseN + denseRowStart == _xx.length-intercept;
    final double [] interceptRow = _hasIntercept?_xx[_xx.length-1]:null;
    // nums
    for(int i = 0; i < r.nNums; ++i) {
      int cid = r.numIds[i];
      final double [] mrow = _xx[cid - _diagN];
      final double d = w*r.numVals[i];
      for(int j = 0; j <= i; ++j)
        mrow[r.numIds[j]] += d*r.numVals[j];
      if(_hasIntercept)
        interceptRow[cid] += d; // intercept*x[i]
      // nums * cats
      for(int j = 0; j < r.nBins; ++j)
        mrow[r.binIds[j]] += d;
    }
    if(_hasIntercept){
      // intercept*intercept
      interceptRow[interceptRow.length-1] += w;
      // intercept X cat
      for(int j = 0; j < r.nBins; ++j)
        interceptRow[r.binIds[j]] += w;
    }
    final boolean hasDiag = (_diagN > 0 && r.nBins > 0 && r.binIds[0] < _diagN);
    // cat X cat
    for(int i = hasDiag?1:0; i < r.nBins; ++i){
      final double [] mrow = _xx[r.binIds[i] - _diagN];
      for(int j = 0; j <= i; ++j)
        mrow[r.binIds[j]] += w;
    }
    // DIAG
    if(hasDiag && r.nBins > 0)
      _diag[r.binIds[0]] += w;
  }
  public final void addRow(DataInfo.Row row, double w) {
    if(row.numIds == null)
      addRowDense(row,w);
    else
      addRowSparse(row, w);
  }

  public final void   addRowDense(DataInfo.Row row, double w) {
    final int intercept = _hasIntercept?1:0;
    final int denseRowStart = _fullN - _denseN - _diagN - intercept; // we keep dense numbers at the right bottom of the matrix, -1 is for intercept
    final int denseColStart = _fullN - _denseN - intercept;

    assert _denseN + denseRowStart == _xx.length-intercept;
    final double [] interceptRow = _hasIntercept?_xx[_denseN + denseRowStart]:null;
    // nums
    for(int i = 0; i < _denseN; ++i) if(row.numVals[i] != 0) {
      final double [] mrow = _xx[i+denseRowStart];
      final double d = w * row.numVals[i];
      for(int j = 0; j <= i; ++j) if(row.numVals[j] != 0)
        mrow[j+denseColStart] += d* row.numVals[j];
      if(_hasIntercept)
        interceptRow[i+denseColStart] += d; // intercept*x[i]
      // nums * cats
      for(int j = 0; j < row.nBins; ++j)
        mrow[row.binIds[j]] += d;
    }
    if(_hasIntercept){
      // intercept*intercept
      interceptRow[_denseN+denseColStart] += w;
      // intercept X cat
      for(int j = 0; j < row.nBins; ++j)
        interceptRow[row.binIds[j]] += w;
    }
    final boolean hasDiag = (_diagN > 0 && row.nBins > 0 && row.binIds[0] < _diagN);
    // cat X cat
    for(int i = hasDiag?1:0; i < row.nBins; ++i){
      final double [] mrow = _xx[row.binIds[i] - _diagN];
      for(int j = 0; j <= i; ++j)
        mrow[row.binIds[j]] += w;
    }
    // DIAG
    if(hasDiag)
      _diag[row.binIds[0]] += w;
  }
  public void mul(double x){
    if(_diag != null)for(int i = 0; i < _diag.length; ++i)
      _diag[i] *= x;
    for(int i = 0; i < _xx.length; ++i)
      for(int j = 0; j < _xx[i].length; ++j)
        _xx[i][j] *= x;
  }

  public double [] mul(double [] x){
    double [] res = MemoryManager.malloc8d(x.length);
    mul(x,res);
    return res;
  }
  private double [][] XX = null;

/*  public void mul(double [] x, double [] res){
    Arrays.fill(res,0);
    if(XX == null) XX = getXX(false,false);
    for(int i = 0; i < XX.length; ++i){
      double d  = 0;
      double [] xi = XX[i];
      for(int j = 0; j < XX.length; ++j)
        d += xi[j]*x[j];
      res[i] = d;
    }
  }*/

  /*
  This method will not allocate the extra memory and hence is considered for lowMemory systems.
  However, need to consider case when you have categoricals!  Make them part of the matrix
  in the multiplication process.  Done!
   */
  public void mul(double[] x, double[] res){
    int colSize = fullN();        // actual gram matrix size
    int offsetForCat = colSize-_xx.length; // offset for categorical columns

    for (int rowIndex = 0; rowIndex < colSize; rowIndex++) {
      double d = 0;
      if (rowIndex >=offsetForCat) {
        for (int colIndex = 0; colIndex < rowIndex; colIndex++) {   // below diagonal
          d += _xx[rowIndex - offsetForCat][colIndex] * x[colIndex];
        }
      }
      // on diagonal
      d+= (rowIndex>=offsetForCat)?_xx[rowIndex-offsetForCat][rowIndex]*x[rowIndex]:_diag[rowIndex]*x[rowIndex];

      for (int colIndex = rowIndex+1; colIndex < colSize; colIndex++) {   // above diagonal
        if (rowIndex<offsetForCat) {
          if ((colIndex>=offsetForCat)) {
            d += _xx[colIndex-offsetForCat][rowIndex]*x[colIndex];
          }
        } else {
          d += _xx[colIndex-offsetForCat][rowIndex]*x[colIndex];
        }
/*        d += (rowIndex<offsetForCat)?((colIndex<offsetForCat)?0:_xx[colIndex-offsetForCat][rowIndex]*x[colIndex]):
                _xx[colIndex-offsetForCat][rowIndex]*x[colIndex];*/
    }
      res[rowIndex] = d;
    }
  }

  /**
   * Task to compute outer product of a matrix normalized by the number of observations (not counting rows with NAs).
   * in R's notation g = X%*%T(X)/nobs, nobs = number of rows of X with no NA.  Copied from GramTask.
   * @author wendycwong
   */
  public static class OuterGramTask extends MRTask<OuterGramTask> {
    public Gram _gram;
    public long _nobs;
    boolean _intercept = false;
    int[] _catOffsets;
    double _scale;    // 1/(number of samples)
    final Key<Job> _jobKey;
    protected final DataInfo _dinfo;


    public OuterGramTask(Key<Job> jobKey, DataInfo dinfo){
      _dinfo = dinfo;
      _jobKey = jobKey;
      _catOffsets = dinfo._catOffsets != null?Arrays.copyOf(dinfo._catOffsets, dinfo._catOffsets.length):null;
      _scale = dinfo._adaptedFrame.numRows() > 0?1.0/dinfo._adaptedFrame.numRows():0.0;
    }

    /*
    Need to do our own thing here since we need to access and multiple different rows of a chunck.
     */
    @Override public void map(Chunk[] chks) { // TODO: implement the sparse option.
      chunkInit();

      DataInfo.Row rowi = _dinfo.newDenseRow();
      DataInfo.Row rowj = _dinfo.newDenseRow();
      Chunk[] chks2 = new Chunk[chks.length];

      // perform inner product within local chunk
      innerProductChunk(rowi, rowj, chks, chks);

      // perform inner product of local chunk with other chunks with lower chunk index
      for (int chkIndex = 0; chkIndex < chks[0].cidx(); chkIndex++) {
        for (int colIndex = 0; colIndex < chks2.length; colIndex++) {   // grab the alternate chunk
          chks2[colIndex] = _fr.vec(colIndex).chunkForChunkIdx(chkIndex);
        }
        innerProductChunk(rowi, rowj, chks, chks2);
      }
      chunkDone();
    }

    /*
    This method performs inner product operation over one chunk.
     */
    public void innerProductChunk(DataInfo.Row rowi, DataInfo.Row rowj, Chunk[] localChunk, Chunk[] alterChunk) {
      int rowOffsetLocal = (int) localChunk[0].start();   // calculate row indices for this particular chunks of data
      int rowOffsetAlter = (int) alterChunk[0].start();
      int localChkRows = localChunk[0]._len;
      int alterChkRows = alterChunk[0]._len;

      for (int rowL = 0; rowL < localChkRows; rowL++) {
        _dinfo.extractDenseRow(localChunk, rowL, rowi);

        if (!rowi.isBad()) {
          ++_nobs;
          int rowIOffset = rowL + rowOffsetLocal;

          for (int j = 0; j < alterChkRows; j++) {
            int rowJOffset = j+rowOffsetAlter;

            if (rowJOffset > rowIOffset) {  // we are done with this chunk, next chunk please
              break;
            }
            _dinfo.extractDenseRow(alterChunk, j, rowj); //grab the row from new chunk and perform inner product of rows
            if ((!rowi.isBad() && rowi.weight != 0) && (!rowj.isBad() && rowj.weight != 0)) {
              this._gram._xx[rowIOffset][rowJOffset] = rowi.dotSame(rowj);
            }
          }
        }
      }
    }

    /*
    Basically, every time we get an array of chunks, we will generate certain parts of the
    gram matrix for only this block.
     */
    public void chunkInit(){
      _gram = new Gram((int)_dinfo._adaptedFrame.numRows(), 0, _dinfo.numNums(), _dinfo._cats, _intercept);
    }

    public void chunkDone(){
        _gram.mul(_scale);
    }
    /*
    Since each chunk only change a certain part of the gram matrix, we can add them all together when we
    are doing the reduce job.  Hence, this part should be left alone.
     */
    @Override public void reduce(OuterGramTask gt) {
      _gram.add(gt._gram);
      _nobs += gt._nobs;
    }
  }

  /**
   * Task to compute gram matrix normalized by the number of observations (not counting rows with NAs).
   * in R's notation g = t(X)%*%X/nobs, nobs = number of rows of X with no NA.
   * @author tomasnykodym
   */
  public static class GramTask extends FrameTask2<GramTask> {
    private  boolean _std = true;
    public Gram _gram;
    public long _nobs;
    boolean _intercept = false;

    public GramTask(Key<Job> jobKey, DataInfo dinfo){
      super(null,dinfo,jobKey);
    }
    public GramTask(Key<Job> jobKey, DataInfo dinfo, boolean std, boolean intercept){
      super(null,dinfo,jobKey);
      _std = std;
      _intercept = intercept;
    }
    @Override public void chunkInit(){
      _gram = new Gram(_dinfo.fullN(), _dinfo.largestCat(), _dinfo.numNums(), _dinfo._cats, _intercept);
    }
    double _prev = 0;
    @Override protected void processRow(DataInfo.Row r) {
      _gram.addRow(r, r.weight);
      ++_nobs;
      double current = (_gram.get(_dinfo.fullN()-1,_dinfo.fullN()-1) - _prev);
      _prev += current;
    }
    @Override public void chunkDone(){
      if(_std) {
        if (_nobs > 0) {  // removing NA rows may produce _nobs=0
          double r = 1.0 / _nobs;
          _gram.mul(r);
        }
      }
    }
    @Override public void reduce(GramTask gt){
      if(_std) {
        if ((_nobs > 0) && (gt._nobs > 0)) {  // removing NA rows may produce _nobs=0
          double r1 = (double) _nobs / (_nobs + gt._nobs);
          _gram.mul(r1);

          double r2 = (double) gt._nobs / (_nobs + gt._nobs);
          gt._gram.mul(r2);
        }
      }
      _gram.add(gt._gram);
      _nobs += gt._nobs;
    }
  }
  public static class NonSPDMatrixException extends RuntimeException {
    public NonSPDMatrixException(){}
    public NonSPDMatrixException(String msg){super(msg);}
  }
  public static class CollinearColumnsException extends RuntimeException {
    public CollinearColumnsException(){}
    public CollinearColumnsException(String msg){super(msg);}
  }
}

