package hex.pca;

import hex.DataInfo;
import hex.DataInfo.Row;
import hex.glm.LSMSolver.ADMMSolver.NonSPDMatrixException;
import hex.gram.Gram;
import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel;
import hex.Model;
import hex.ModelBuilder;
import hex.gram.Gram.*;
import hex.schemas.ModelBuilderSchema;
import hex.schemas.PCAV2;
import hex.gram.Gram.GramTask;
import hex.FrameTask;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.Log;
import water.util.ArrayUtils;
import water.util.TwoDimTable;

import Jama.CholeskyDecomposition;
import Jama.Matrix;
import Jama.QRDecomposition;
import Jama.SingularValueDecomposition;
import java.util.Arrays;

/**
 * Quadratically Regularized PCA
 * This is an algorithm for dimensionality reduction of numerical data.
 * It is a general, parallelized implementation of PCA with quadratic regularization.
 * <a href = "http://web.stanford.edu/~boyd/papers/pdf/glrm.pdf">Generalized Low Rank Models</a>
 * @author anqi_fu
 *
 */
public class PCA extends ModelBuilder<PCAModel,PCAModel.PCAParameters,PCAModel.PCAOutput> {
  // Convergence tolerance
  final private double TOLERANCE = 1e-8;

  @Override
  public ModelBuilderSchema schema() {
    return new PCAV2();
  }

  @Override
  public Job<PCAModel> trainModel() {
    return start(new PCADriver(), 0);
  }

  @Override
  public Model.ModelCategory[] can_build() {
    return new Model.ModelCategory[]{Model.ModelCategory.Clustering};
  }

  public enum Initialization {
    PlusPlus, User
  }

  // Called from an http request
  public PCA(PCAModel.PCAParameters parms) {
    super("PCA", parms);
    init(false);
  }

  @Override
  public void init(boolean expensive) {
    super.init(expensive);
    if (_parms._loading_key == null) _parms._loading_key = Key.make("PCALoading_" + Key.rand());
    if (_parms._gamma < 0) error("_gamma", "lambda must be a non-negative number");

    if (_train == null) return;
    if (_train.numCols() < 2) error("_train", "_train must have more than one column");

    // TODO: Initialize _parms._k = min(ncol(_train), nrow(_train)) if not set
    int k_min = (int)Math.min(_train.numCols(), _train.numRows());
    if (_parms._k < 1 || _parms._k > k_min) error("_k", "_k must be between 1 and " + k_min);
    if (null != _parms._user_points) { // Check dimensions of user-specified centers
      if (_parms._user_points.get().numCols() != _train.numCols())
        error("_user_points","The user-specified points must have the same number of columns (" + _train.numCols() + ") as the training observations");
      else if (_parms._user_points.get().numRows() != _parms._k)
        error("_user_points","The user-specified points must have k = " + _parms._k + " rows");
    }
    // Currently, does not work on categorical data
    Vec[] vecs = _train.vecs();
    for (int i = 0; i < vecs.length; i++) {
      if (!vecs[i].isNumeric()) throw H2O.unimpl();
    }
  }

  /**
   * Given a n by k matrix X, form its Gram matrix
   * @param x Matrix of real numbers
   * @param transpose If true, compute n by n Gram of rows = XX'
   *                  If false, compute k by k Gram of cols = X'X
   * @return A symmetric positive semi-definite Gram matrix
   */
  public static double[][] formGram(double[][] x, boolean transpose) {
    if (x == null) return null;
    int dim_in = transpose ? x[0].length : x.length;
    int dim_out = transpose ? x.length : x[0].length;
    double[][] xgram = new double[dim_out][dim_out];

    // Compute all entries on and above diagonal
    if(transpose) {
      for (int i = 0; i < dim_in; i++) {
        // Outer product = x[i] * x[i]', where x[i] is col i
        for (int j = 0; j < dim_out; j++) {
          for (int k = j; k < dim_out; k++)
            xgram[j][k] += x[j][i] * x[k][i];
        }
      }
    } else {
      for (int i = 0; i < dim_in; i++) {
        // Outer product = x[i]' * x[i], where x[i] is row i
        for (int j = 0; j < dim_out; j++) {
          for (int k = j; k < dim_out; k++)
            xgram[j][k] += x[i][j] * x[i][k];
        }
      }
    }

    // Fill in entries below diagonal since Gram is symmetric
    for (int i = 0; i < dim_in; i++) {
      for (int j = 0; j < dim_out; j++) {
        for (int k = 0; k < j; k++)
          xgram[j][k] = xgram[k][j];
      }
    }
    return xgram;
  }
  public static double[][] formGram(double[][] x) { return formGram(x, false); }

  // Add constant \gamma to the diagonal of a k by k symmetric matrix X
  public static double[] addDiag(double[][] x, double gamma) {
    if (x == null) return null;
    if (x.length != x[0].length)
      throw new IllegalArgumentException("x must be a symmetric matrix!");

    int len = x.length;
    double[] diag = new double[len];
    if (gamma == 0) return diag;
    for (int i = 0; i < len; i++) {
      x[i][i] += gamma;
      diag[i] = gamma;
    }
    return diag;
  }

  // Squared Frobenius norm of a matrix (sum of squared entries)
  public static double frobenius2(double[][] x) {
    if(x == null) return 0;

    double frob = 0;
    for(int i = 0; i < x.length; i++) {
      for(int j = 0; j < x[0].length; j++)
        frob += x[i][j] * x[i][j];
    }
    return frob;
  }

  // Transform each column of a 2-D array
  public static double[][] transform(double[][] centers, int ncats, double[] normSub, double[] normMul) {
    int K = centers.length;
    int N = centers[0].length;
    double[][] value = new double[K][N];
    double[] means = normSub == null ? MemoryManager.malloc8d(N) : normSub;
    double[] mults = normMul == null ? MemoryManager.malloc8d(N) : normMul;

    for( int clu = 0; clu < K; clu++ ) {
      System.arraycopy(centers[clu],0,value[clu],0,N);
      for (int col = ncats; col < N; col++)
        value[clu][col] = (value[clu][col] - means[col]) * mults[col];
    }
    return value;
  }

  class PCADriver extends H2O.H2OCountedCompleter<PCADriver> {

    // Initialize Y to be the k centers from k-means++
    double[][] initialY(DataInfo dinfo) {
      double[][] centers;

      if (null != _parms._user_points) { // User-specified starting points
        int numCenters = _parms._k;
        int numCols = _parms._user_points.get().numCols();
        centers = new double[numCenters][numCols];
        Vec[] centersVecs = _parms._user_points.get().vecs();

        // Get the centers and put into array
        for (int r = 0; r < numCenters; r++) {
          for (int c = 0; c < numCols; c++)
            centers[r][c] = centersVecs[c].at(r);
        }
      } else {  // Run k-means++ and use resulting cluster centers as initial Y
        KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
        parms._train = _parms._train;
        parms._ignored_columns = _parms._ignored_columns;
        parms._dropConsCols = _parms._dropConsCols;
        parms._dropNA20Cols = _parms._dropNA20Cols;
        parms._max_confusion_matrix_size = _parms._max_confusion_matrix_size;
        parms._score_each_iteration = _parms._score_each_iteration;
        parms._init = KMeans.Initialization.PlusPlus;
        parms._k = _parms._k;
        parms._max_iterations = _parms._max_iterations;
        parms._standardize = true;
        parms._seed = _parms._seed;

        KMeansModel km = null;
        KMeans job = null;
        try {
          job = new KMeans(parms);
          km = job.trainModel().get();
        } finally {
          if (job != null) job.remove();
          if (km != null) km.remove();
        }

        // K-means automatically destandardizes centers! Need the original standardized version
        centers = transform(km._output._centers_raw, 0, km._output._normSub, km._output._normMul);
      }
      return centers;
    }

    // Add l2 regularization until Gram matrix is positive definite
    // Maybe try robust Cholesky implementation? http://eigen.tuxfamily.org/dox/classEigen_1_1LDLT.html
    CholeskyDecomposition regularizedCholesky(double[][] gram, int max_attempts) {
      int attempts = 0;
      double addedL2 = 0;   // TODO: Should I report this to the user?
      CholeskyDecomposition chol = new CholeskyDecomposition(new Matrix(gram));
      while(!chol.isSPD() && attempts < max_attempts) {
        if(addedL2 == 0) addedL2 = 1e-5;
        else addedL2 *= 10;
        ++attempts;
        addDiag(gram, addedL2); // try to add L2 penalty to make the Gram SPD
        chol = new CholeskyDecomposition(new Matrix(gram));
      }
      if(!chol.isSPD())
        throw new NonSPDMatrixException(gram);
      return chol;
    }
    CholeskyDecomposition regularizedCholesky(double[][] gram) {
      return regularizedCholesky(gram, 10);
    }

    Cholesky regularizedCholesky(Gram gram, int max_attempts) {
      int attempts = 0;
      double addedL2 = 0;   // TODO: Should I report this to the user?
      Cholesky chol = gram.cholesky(null);
      while(!chol.isSPD() && attempts < max_attempts) {
        if(addedL2 == 0) addedL2 = 1e-5;
        else addedL2 *= 10;
        ++attempts;
        gram.addDiag(addedL2); // try to add L2 penalty to make the Gram SPD
        gram.cholesky(chol);
      }
      if(!chol.isSPD())
        throw new NonSPDMatrixException(gram);
      return chol;
    }
    Cholesky regularizedCholesky(Gram gram) {
      return regularizedCholesky(gram, 10);
    }

    // Stopping criteria
    boolean isDone(PCAModel model) {
      if (!isRunning()) return true; // Stopped/cancelled
      // Stopped for running out of iterations
      if (model._output._iterations > _parms._max_iterations) return true;

      // Stopped when average decrease in objective per iteration < TOLERANCE
      if( model._output._avg_change_obj < TOLERANCE ) return true;
      return false;             // Not stopping
    }

    // Recover eigenvalues and eigenvectors of XY
    void recoverPCA(PCAModel model, DataInfo xinfo) {
      // NOTE: Gram computes X'X/n where n = nrow(A) = number of rows in training set
      GramTask xgram = new GramTask(self(), xinfo).doAll(xinfo._adaptedFrame);
      Cholesky xxchol = regularizedCholesky(xgram._gram);
      // double[][] xx = xgram._gram.getXX();
      // if(_parms._gamma > 0) addDiag(xx, _parms._gamma);
      // CholeskyDecomposition xxchol = regularizedCholesky(xx);

      // R from QR decomposition of X = QR is upper triangular factor of Cholesky of X'X
      // Gram = X'X/n = LL' -> X'X = (L*sqrt(n))(L'*sqrt(n))
      // Matrix x_r = xxchol.getL().transpose();
      Matrix x_r = new Matrix(xxchol.getL()).transpose();
      x_r = x_r.times(Math.sqrt(_train.numRows()));

      QRDecomposition yt_qr = new QRDecomposition(new Matrix(model._output._archetypes));
      Matrix yt_r = yt_qr.getR();   // S from QR decomposition of Y' = ZS
      Matrix rrmul = x_r.times(yt_r.transpose());
      SingularValueDecomposition rrsvd = new SingularValueDecomposition(rrmul);   // RS' = U \Sigma V'

      // Eigenvectors are V'Z' = (ZV)'
      Matrix eigvec = yt_qr.getQ().times(rrsvd.getV());
      model._output._eigenvectors_raw = eigvec.getArray();
      // model._output._eigenvalues = rrsvd.getSingularValues();

      String[] colTypes = new String[_parms._k];
      String[] colFormats = new String[_parms._k];
      String[] colHeaders = new String[_parms._k];
      Arrays.fill(colTypes, "double");
      Arrays.fill(colFormats, "%5f");
      for(int i = 0; i < colHeaders.length; i++) colHeaders[i] = "PC" + String.valueOf(i+1);
      model._output._eigenvectors = new TwoDimTable("Rotation", _train.names(),
              colHeaders, colTypes, colFormats, "", new String[_train.numCols()][], model._output._eigenvectors_raw);

      // Calculate standard deviations from \Sigma
      // Note: Singular values ordered in weakly descending order by algorithm
      double[] sval = rrsvd.getSingularValues();
      double[] sdev = new double[sval.length];
      double[] pcvar = new double[sval.length];
      double tot_var = 0;
      double dfcorr = 1.0 / Math.sqrt(_train.numRows() - 1.0);
      for(int i = 0; i < sval.length; i++) {
        sdev[i] = dfcorr * sval[i];   // Correct since degrees of freedom = n-1
        pcvar[i] = sdev[i] * sdev[i];
        tot_var += pcvar[i];
      }
      model._output._std_deviation = sdev;

      // Calculate proportion of variance explained
      double[] prop_var = new double[sval.length];    // Proportion of total variance
      double[] cum_var = new double[sval.length];    // Cumulative proportion of total variance
      for(int i = 0; i < sval.length; i++) {
        prop_var[i] = pcvar[i] / tot_var;
        cum_var[i] = i == 0 ? prop_var[0] : cum_var[i-1] + prop_var[i];
      }
      model._output._pc_importance = new TwoDimTable("Importance of components",
              new String[] { "Standard deviation", "Proportion of Variance", "Cumulative Proportion" },
              colHeaders, colTypes, colFormats, "", new String[3][], new double[][] { sdev, prop_var, cum_var });
    }

    // Main worker thread
    @Override protected void compute2() {
      PCAModel model = null;
      DataInfo dinfo = null;
      DataInfo xinfo = null;

      try {
        _parms.read_lock_frames(PCA.this); // Fetch & read-lock input frames
        init(true);
        if (error_count() > 0) throw new IllegalArgumentException("Found validation errors: " + validationErrors());

        // The model to be built
        model = new PCAModel(dest(), _parms, new PCAModel.PCAOutput(PCA.this));
        model.delete_and_lock(_key);
        _train.read_lock(_key);

        // Jam A and X into a single frame [A,X] for distributed computation
        // A is read-only training data, X will be modified in place every iteration
        Vec[] vecs = new Vec[_train.numCols() + _parms._k];
        Vec[] xvecs = new Vec[_parms._k];
        for (int i = 0; i < _train.numCols(); i++) vecs[i] = _train.vec(i);
        int c = 0;
        for (int i = _train.numCols(); i < vecs.length; i++) {
          vecs[i] = _train.anyVec().makeZero();
          xvecs[c++] = vecs[i];
        }
        assert c == xvecs.length;

        Frame fr = new Frame(null, vecs);
        dinfo = new DataInfo(Key.make(), fr, null, 0, false, _parms._transform, DataInfo.TransformType.NONE, true);
        DKV.put(dinfo._key, dinfo);
        model._output._normSub = dinfo._normSub == null ? null : Arrays.copyOf(dinfo._normSub, _train.numCols());
        model._output._normMul = dinfo._normMul == null ? null : Arrays.copyOf(dinfo._normMul, _train.numCols());

        // Create separate reference to X for Gram task
        Frame x = new Frame(_parms._loading_key, null, xvecs);
        xinfo = new DataInfo(Key.make(), x, null, 0, false, DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true);
        DKV.put(x._key, x);
        DKV.put(xinfo._key, xinfo);

        // 0) Initialize X and Y matrices
        // a) Initialize Y' matrix using k-means++
        double nobs = _train.numRows() * _train.numCols();
        double[][] yt = ArrayUtils.transpose(initialY(dinfo));
        double yt_norm = frobenius2(yt);

        // b) Initialize X = AY'(YY' + \gamma I)^(-1)
        // Gram ygram_init = new Gram(formGram(yt));
        // if(_parms._gamma > 0) ygram_init.addDiag(_parms._gamma);
        // Cholesky yychol_init = regularizedCholesky(ygram_init);
        double[][] ygram_init = formGram(yt);
        if(_parms._gamma > 0) addDiag(ygram_init, _parms._gamma);
        CholeskyDecomposition yychol_init = regularizedCholesky(ygram_init);

        CholMulTask cmtsk_init = new CholMulTask(dinfo, yychol_init, yt, _train.numCols(), _parms._k);
        cmtsk_init.doAll(dinfo._adaptedFrame);
        double axy_norm = cmtsk_init._objerr;   // Save squared Frobenius norm ||A - XY||_F^2

        model._output._iterations = 0;
        model._output._avg_change_obj = 2 * TOLERANCE;    // Run at least 1 iteration

        while(!isDone(model)) {
          // 1) Compute Y = (X'X + \gamma I)^(-1)X'A
          // a) Form Gram matrix X'X/n, where n = nrow(A)
          GramTask xgram = new GramTask(self(), xinfo).doAll(xinfo._adaptedFrame);

          // b) Get Cholesky decomposition of D/n = (X'X + \gamma I)/n
          if(_parms._gamma > 0) xgram._gram.addDiag(_parms._gamma/_train.numRows());
          Cholesky xxchol = regularizedCholesky(xgram._gram);

          // c) Compute A'X and solve for Y' of DY' = A'X
          yt = new SMulTask(dinfo, _train.numCols(), _parms._k).doAll(dinfo._adaptedFrame)._prod;
          for(int i = 0; i < yt.length; i++) {
            xxchol.solve(yt[i]);
            ArrayUtils.div(yt[i], _train.numRows());  // Divide by n since (D/n)Y' = D(Y'/n)
          }

          // 2) Compute X = AY'(YY' + \gamma I)^(-1)
          // a) Form Gram matrix of Y' = (Y')'Y' = YY'
          // Gram ygram = new Gram(formGram(yt));
          double[][] ygram = formGram(yt);

          // b) Get Cholesky decomposition of D' = D = YY' + \gamma I
          // if(_parms._gamma > 0) ygram.addDiag(_parms._gamma);
          // Cholesky yychol = regularizedCholesky(ygram);
          if(_parms._gamma > 0) addDiag(ygram, _parms._gamma);
          CholeskyDecomposition yychol = regularizedCholesky(ygram);

          // c) Compute AY' and solve for X of XD = AY' -> D'X' = DX' = YA'
          CholMulTask cmtsk = new CholMulTask(dinfo, yychol, yt, _train.numCols(), _parms._k);
          cmtsk.doAll(dinfo._adaptedFrame);

          // 3) Compute average change in objective function
          model._output._avg_change_obj = axy_norm - cmtsk._objerr;
          axy_norm = cmtsk._objerr;
          if(_parms._gamma > 0) {
            double yt_old_norm = yt_norm;
            yt_norm = frobenius2(yt);
            model._output._avg_change_obj += _parms._gamma * ((yt_old_norm - yt_norm) + cmtsk._frob2err);
          }
          model._output._avg_change_obj /= nobs;
          model._output._iterations++;
          model.update(_key); // Update model in K/V store
          update(1);          // One unit of work
        }

        // 4) Save solution to model output
        model._output._archetypes = yt;
        model._output._parameters = _parms;
        recoverPCA(model, xinfo);

        // Optional: This computes XY, but do we need it?
        // BMulTask tsk = new BMulTask(self(), xinfo, yt).doAll(_parms._k, xinfo._adaptedFrame);
        // tsk.outputFrame(_parms._destination_key, _train._names, null);
        done();
      } catch (Throwable t) {
        Job thisJob = DKV.getGet(_key);
        if (thisJob._state == JobState.CANCELLED) {
          Log.info("Job cancelled by user.");
        } else {
          t.printStackTrace();
          failed(t);
          throw t;
        }
      } finally {
        _train.unlock(_key);
        if (model != null) model.unlock(_key);
        if (dinfo != null) dinfo.remove();
        if (xinfo != null) xinfo.remove();
        _parms.read_unlock_frames(PCA.this);
      }
      tryComplete();
    }

    Key self() {
      return _key;
    }
  }

  // Computes A'X on a matrix [A,X], where A is n by p, X is n by k, and k <= p
  // Resulting matrix D = A'X will have dimensions p by k
  private static class SMulTask extends MRTask<SMulTask> {
    DataInfo _dinfo;
    double[] _normSub;  // For standardizing A only
    double[] _normMul;
    int _ncolA, _ncolX; // _ncolA = p, _ncolX = k

    double[][] _prod;   // _prod = D = A'X

    SMulTask(DataInfo dinfo, final int ncolA, final int ncolX) {
      _dinfo = dinfo;
      _normSub = dinfo._normSub == null ? MemoryManager.malloc8d(ncolA) : dinfo._normSub;
      if(dinfo._normMul == null) {
        _normMul = MemoryManager.malloc8d(ncolA);
        Arrays.fill(_normMul, 1.0);
      } else _normMul = dinfo._normMul;
      _ncolA = ncolA; _ncolX = ncolX;
      _prod = new double[ncolA][ncolX];
    }

    @Override public void map(Chunk[] cs) {
      assert (_ncolA + _ncolX) == cs.length;

      // Cycle over columns of A
      for (int i = 0; i < _ncolA; i++) {
        // Cycle over columns of X
        int c = 0;
        for (int j = _ncolA; j < cs.length; j++) {
          double sum = 0;
          for (int row = 0; row < cs[0]._len; row++) {
            double a = cs[i].atd(row);
            double x = cs[j].atd(row);
            if (Double.isNaN(a) || Double.isNaN(x)) continue;
            sum += (a - _normSub[i]) * _normMul[i] * x;
          }
          _prod[i][c++] = sum;
        }
        assert c == _ncolX;
      }
    }

    @Override
    public void reduce(SMulTask other) {
      ArrayUtils.add(_prod, other._prod);
    }
  }

  // Computes XY where X is n by k, Y is k by p, and k <= p
  //  Resulting matrix Z = XY will have dimensions n by k
  private static class BMulTask extends FrameTask<BMulTask> {
    double[][] _yt;   // _yt = Y' (transpose of Y)

    BMulTask(Key jobKey, DataInfo dinfo, final double[][] yt) {
      super(jobKey, dinfo);
      _yt = yt;
    }

    @Override protected void processRow(long gid, Row row, NewChunk[] outputs) {
      double[] nums = row.numVals;
      assert nums.length == _yt[0].length;

      for(int k = 0; k < _yt[0].length; k++) {
        double x = 0;
        int c = _dinfo.numStart();
        for(int d = 0; d < nums.length; d++)
          x += nums[d] * _yt[k][c++];
        assert c == _yt[0].length;
        outputs[k].addNum(x);
      }
    }
  }

  // TODO: Generalize this with boolean flag indicating whether to transpose Y
  // Solves XD = AY' for X where A is n by p, Y is k by p, D is k by k, and n >> p > k
  // Resulting matrix X = (AY')D^(-1) will have dimensions n by k
  private static class CholMulTask extends MRTask<CholMulTask> {
    double[] _normSub;  // For standardizing A only
    double[] _normMul;
    int _ncolA;       // _ncolA = p (number of training cols)
    int _ncolX;       // _ncolX = k (number of PCs)
    double[][] _yt;   // _yt = Y' (transpose of Y)
    CholeskyDecomposition _chol;   // Cholesky decomposition of D = D', since we solve D'X' = DX' = AY'

    double _sserr;      // Sum of squared difference between old and new X
                        // Formula: \sum_{i,j} (xold_{i,j} - xnew_{i,j})^2
    double _frob2err;   // Difference in squared Frobenius norm between old and new X
                        // Formula: \sum_{i,j} xold_{i,j}^2 - \sum_{i,j} xnew_{i,j}^2
    double _objerr;     // Squared Frobenius norm of A - XY using new X (and Y)

    CholMulTask(DataInfo dinfo, final CholeskyDecomposition chol, final double[][] yt) {
      this(dinfo, chol, yt, yt.length, yt[0].length);
    }

    CholMulTask(DataInfo dinfo, final CholeskyDecomposition chol, final double[][] yt, final int ncolA, final int ncolX) {
      assert yt != null && yt.length == ncolA && yt[0].length == ncolX;
      _normSub = dinfo._normSub == null ? MemoryManager.malloc8d(ncolA) : dinfo._normSub;
      if(dinfo._normMul == null) {
        _normMul = MemoryManager.malloc8d(ncolA);
        Arrays.fill(_normMul, 1.0);
      } else _normMul = dinfo._normMul;
      _ncolA = ncolA; _ncolX = ncolX;
      _chol = chol;
      _yt = yt;

      _sserr = 0;
      _frob2err = 0;
      _objerr = 0;
    }

    // In chunk, first _ncolA cols are A, next _ncolX cols are X
    @Override public void map(Chunk[] cs) {
      double[] xrow = new double[_ncolX];
      assert (_ncolX + _ncolA) == cs.length;

      for(int row = 0; row < cs[0]._len; row++) {
        // Compute single row of AY'
        for (int k = 0; k < _ncolX; k++) {
          double x = 0;
          for (int d = 0; d < _ncolA; d++) {
            double a = cs[d].atd(row);
            if (Double.isNaN(a)) continue;
            x += (a - _normSub[d]) * _normMul[d] * _yt[d][k];
          }
          xrow[k] = x;
        }

        // Cholesky solve for single row of X
        // _chol.solve(xrow);
        Matrix tmp = _chol.solve(new Matrix(new double[][] {xrow}).transpose());
        xrow = tmp.getColumnPackedCopy();

        // Compute l2 norm of single row of A - XY (using new X)
        // \sum_{i,j} (A_{i,j} - x_i * y_j)^2 where x_i = row i of X, y_j = col j of Y
        for(int d = 0; d < _ncolA; d++) {
          double a = cs[d].atd(row);
          if(Double.isNaN(a)) continue;
          double xysum = 0;
          for(int k = 0; k < _ncolX; k++)
            xysum += xrow[k] * _yt[d][k];
          double delta = (a - _normSub[d]) * _normMul[d] - xysum;
          _objerr += delta * delta;
        }

        // Update X in place with new solved values
        int i = 0;
        for(int d = _ncolA; d < cs.length; d++) {
          // Sum of squared error between rows of old and new X
          double xold = cs[d].atd(row);
          double delta = xold - xrow[i];
          _sserr += delta * delta;

          // Difference in l2 norm of old and new X rows
          _frob2err += xold * xold - xrow[i] * xrow[i];
          cs[d].set(row, xrow[i++]);
        }
        assert i == xrow.length;
      }
    }

    @Override public void reduce(CholMulTask other) {
      _sserr += other._sserr;
      _frob2err += other._frob2err;
      _objerr += other._objerr;
    }
  }
}
