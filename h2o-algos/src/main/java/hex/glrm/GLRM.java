package hex.glrm;

import Jama.CholeskyDecomposition;
import Jama.Matrix;
import Jama.QRDecomposition;
import Jama.SingularValueDecomposition;

import hex.*;
import hex.gram.Gram;
import hex.gram.Gram.*;
import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel;
import hex.pca.PCA;
import hex.pca.PCAModel;
import hex.schemas.GLRMV99;
import hex.glrm.GLRMModel.GLRMParameters;
import hex.schemas.ModelBuilderSchema;
import hex.svd.SVD;
import hex.svd.SVDModel;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.RandomUtils;

import java.util.Arrays;
import java.util.Random;

/**
 * Generalized Low Rank Models
 * This is an algorithm for dimensionality reduction of a dataset. It is a general, parallelized
 * optimization algorithm that applies to a variety of loss and regularization functions.
 * Categorical columns are handled by expansion into 0/1 indicator columns for each level.
 * <a href = "http://web.stanford.edu/~boyd/papers/pdf/glrm.pdf">Generalized Low Rank Models</a>
 * @author anqi_fu
 */
public class GLRM extends ModelBuilder<GLRMModel,GLRMModel.GLRMParameters,GLRMModel.GLRMOutput> {
  // Convergence tolerance
  private final double TOLERANCE = 1e-6;

  // Maximum number of columns when categoricals expanded
  private final int MAX_COLS_EXPANDED = 5000;

  // Number of columns in training set (p)
  private transient int _ncolA;
  private transient int _ncolY;    // With categoricals expanded into 0/1 indicator cols

  // Number of columns in fitted X matrix (k)
  private transient int _ncolX;

  @Override public ModelBuilderSchema schema() {
    return new GLRMV99();
  }

  @Override public Job<GLRMModel> trainModel() {
    return start(new GLRMDriver(), _parms._max_iterations+1);
  }

  @Override public ModelCategory[] can_build() {
    return new ModelCategory[]{ModelCategory.Clustering};
  }

  @Override public BuilderVisibility builderVisibility() { return BuilderVisibility.Experimental; };

  public enum Initialization {
    Random, SVD, PlusPlus, User
  }

  // Called from an http request
  public GLRM(GLRMParameters parms) {
    super("GLRM", parms);
    init(false);
  }

  @Override public void init(boolean expensive) {
    super.init(expensive);
    if (_parms._loading_key == null) _parms._loading_key = Key.make("GLRMLoading_" + Key.rand());
    if (_parms._gamma_x < 0) error("_gamma_x", "gamma must be a non-negative number");
    if (_parms._gamma_y < 0) error("_gamma_y", "gamma_y must be a non-negative number");
    if (_parms._max_iterations < 1 || _parms._max_iterations > 1e6)
      error("_max_iterations", "max_iterations must be between 1 and 1e6 inclusive");
    if (_parms._init_step_size <= 0)
      error ("_init_step_size", "init_step_size must be a positive number");
    if (_parms._min_step_size < 0 || _parms._min_step_size > _parms._init_step_size)
      error("_min_step_size", "min_step_size must be between 0 and " + _parms._init_step_size);

    if (_train == null) return;
    if (_train.numCols() < 2) error("_train", "_train must have more than one column");

    // TODO: Initialize _parms._k = min(ncol(_train), nrow(_train)) if not set
    _ncolY = _train.numColsExp(true, false);
    int k_min = (int) Math.min(_ncolY, _train.numRows());
    if (_ncolY > MAX_COLS_EXPANDED)
      warn("_train", "_train has " + _ncolY + " columns when categoricals are expanded. Algorithm may be slow.");

    if (_parms._k < 1 || _parms._k > k_min) error("_k", "_k must be between 1 and " + k_min);
    if (null != _parms._user_points) { // Check dimensions of user-specified centers
      if (_parms._init != GLRM.Initialization.User)
        error("init", "init must be 'User' if providing user-specified points");

      if (_parms._user_points.get().numCols() != _train.numCols())
        error("_user_points", "The user-specified points must have the same number of columns (" + _train.numCols() + ") as the training observations");
      else if (_parms._user_points.get().numRows() != _parms._k)
        error("_user_points", "The user-specified points must have k = " + _parms._k + " rows");
      else {
        int zero_vec = 0;
        Vec[] centersVecs = _parms._user_points.get().vecs();
        for (int c = 0; c < _train.numCols(); c++) {
          if(centersVecs[c].naCnt() > 0) {
            error("_user_points", "The user-specified points cannot contain any missing values"); break;
          } else if(centersVecs[c].isConst() && centersVecs[c].max() == 0)
            zero_vec++;
        }
        if (zero_vec == _train.numCols())
          error("_user_points", "The user-specified points cannot all be zero");
      }
    }

    _ncolX = _parms._k;
    _ncolA = _train.numCols();
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

  // Transform each column of a 2-D array, assuming categoricals sorted before numeric cols
  public static double[][] transform(double[][] centers, double[] normSub, double[] normMul, int ncats, int nnums) {
    int K = centers.length;
    int N = centers[0].length;
    assert ncats + nnums == N;
    double[][] value = new double[K][N];
    double[] means = normSub == null ? MemoryManager.malloc8d(nnums) : normSub;
    double[] mults = normMul == null ? MemoryManager.malloc8d(nnums) : normMul;

    for (int clu = 0; clu < K; clu++) {
      System.arraycopy(centers[clu], 0, value[clu], 0, ncats);
      for (int col = 0; col < nnums; col++)
        value[clu][ncats+col] = (centers[clu][ncats+col] - means[col]) * mults[col];
    }
    return value;
  }

  // More efficient implementation assuming sdata cols aligned with adaptedFrame
  public static double[][] expandCats(double[][] sdata, DataInfo dinfo) {
    if(sdata == null || dinfo._cats == 0) return sdata;
    assert sdata[0].length == dinfo._adaptedFrame.numCols();

    // Column count for expanded matrix
    int catsexp = dinfo._catOffsets[dinfo._catOffsets.length-1];
    double[][] cexp = new double[sdata.length][catsexp + dinfo._nums];

    // Expand out categorical columns
    int cidx;
    for(int j = 0; j < dinfo._cats; j++) {
      for(int i = 0; i < sdata.length; i++) {
        if (Double.isNaN(sdata[i][j])) {
          if (dinfo._catMissing[j] == 0) continue;   // Skip if entry missing and no NA bucket. All indicators will be zero.
          else cidx = dinfo._catOffsets[j+1]-1;     // Otherwise, missing value turns into extra (last) factor
        } else
          cidx = dinfo.getCategoricalId(j, (int)sdata[i][j]);
        if(cidx >= 0) cexp[i][cidx] = 1;  // Ignore categorical levels outside domain
      }
    }

    // Copy over numeric columns
    for(int j = 0; j < dinfo._nums; j++) {
      for(int i = 0; i < sdata.length; i++)
        cexp[i][catsexp+j] = sdata[i][dinfo._cats+j];
    }
    return cexp;
  }

  class GLRMDriver extends H2O.H2OCountedCompleter<GLRMDriver> {

    // Initialize Y matrix
    public double[][] initialY(DataInfo dinfo) {
      double[][] centers, centers_exp;

      if (null != _parms._user_points) { // User-specified starting points
        Vec[] centersVecs = _parms._user_points.get().vecs();
        centers = new double[_parms._k][_ncolA];

        // Get the centers and put into array
        for (int c = 0; c < _ncolA; c++) {
          for (int r = 0; r < _parms._k; r++)
            centers[r][c] = centersVecs[c].at(r);
        }

        // Permute cluster columns to align with dinfo and expand out categoricals
        centers = ArrayUtils.permuteCols(centers, dinfo._permutation);
        centers_exp = expandCats(centers, dinfo);

      } else if (_parms._init == Initialization.Random) {  // Generate array from standard normal distribution
        return ArrayUtils.gaussianArray(_parms._k, _ncolY);

      } else if (_parms._init == Initialization.SVD) {  // Run SVD on A'A/n (Gram) and set Y to be the right singular vectors
        PCAModel.PCAParameters parms = new PCAModel.PCAParameters();
        parms._train = _parms._train;
        parms._ignored_columns = _parms._ignored_columns;
        parms._ignore_const_cols = _parms._ignore_const_cols;
        parms._score_each_iteration = _parms._score_each_iteration;
        parms._use_all_factor_levels = true;   // Since GLRM requires Y matrix to have fully expanded ncols
        parms._k = _parms._k;
        parms._max_iterations = _parms._max_iterations;
        parms._transform = _parms._transform;
        parms._seed = _parms._seed;
        parms._pca_method = PCAModel.PCAParameters.Method.GramSVD;

        PCAModel pca = null;
        PCA job = null;
        try {
          job = new PCA(parms);
          pca = job.trainModel().get();
        } finally {
          if (job != null) job.remove();
          if (pca != null) pca.remove();
        }

        // Ensure SVD centers align with adapted training frame cols
        assert pca._output._permutation.length == dinfo._permutation.length;
        for(int i = 0; i < dinfo._permutation.length; i++)
          assert pca._output._permutation[i] == dinfo._permutation[i];
        centers_exp = ArrayUtils.transpose(pca._output._eigenvectors_raw);
        // for(int i = 0; i < centers_exp.length; i++)
        //  ArrayUtils.mult(centers_exp[i], pca._output._std_deviation[i] * Math.sqrt(pca._output._nobs-1));

      } else {  // Run k-means++ and use resulting cluster centers as initial Y
        KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
        parms._train = _parms._train;
        parms._ignored_columns = _parms._ignored_columns;
        parms._ignore_const_cols = _parms._ignore_const_cols;
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

        // Permute cluster columns to align with dinfo, normalize nums, and expand out cats to indicator cols
        centers = ArrayUtils.permuteCols(km._output._centers_raw, dinfo.mapNames(km._output._names));
        centers = transform(centers, dinfo._normSub, dinfo._normMul, dinfo._cats, dinfo._nums);
        centers_exp = expandCats(centers, dinfo);
      }
      assert centers_exp[0].length == _ncolY;

      // If all centers are zero or any are NaN, initialize to standard normal random matrix
      double frob = frobenius2(centers_exp);
      if(frob == 0 || Double.isNaN(frob)) {
        warn("_init", "Initialization failed. Setting initial Y to standard normal random matrix instead...");
        centers_exp = ArrayUtils.gaussianArray(_parms._k, _ncolY);
      }
      return centers_exp;
    }

    // Initialize X matrix
    public void initialX(DataInfo dinfo, double[][] yt, double[] normSub, double[] normMul) {
      // In case of L2 loss and regularization, initialize X = AY'(YY' + \gamma)^(-1)
      if(_parms._loss == GLRMParameters.Loss.L2 && (_parms._gamma_x == 0 || _parms._regularization_x == GLRMParameters.Regularizer.L2)
              && (_parms._gamma_y == 0 || _parms._regularization_y == GLRMParameters.Regularizer.L2)) {
        Log.info("Initializing X = AY'(YY' + gamma I)^(-1) where A = training data");
        double[][] ygram = ArrayUtils.formGram(yt);
        if (_parms._gamma_y > 0) {
          for(int i = 0; i < ygram.length; i++)
            ygram[i][i] += _parms._gamma_y;
        }
        CholeskyDecomposition yychol = regularizedCholesky(ygram, 10, false);
        if(!yychol.isSPD())
          Log.warn("Initialization failed: (YY' + gamma I) is non-SPD. Setting X to a matrix of random numbers. Results will be numerically unstable");
        else {
          CholMulTask cmtsk = new CholMulTask(dinfo, _parms, yychol, yt, _ncolA, _ncolX, normSub, normMul);
          cmtsk.doAll(dinfo._adaptedFrame);
        }
      }
    }

    // Stopping criteria
    private boolean isDone(GLRMModel model, int steps_in_row, double step) {
      if (!isRunning()) return true;  // Stopped/cancelled

      // Stopped for running out of iterations
      if (model._output._iterations >= _parms._max_iterations) return true;

      // Stopped for falling below minimum step size
      if (step <= _parms._min_step_size) return true;

      // Stopped when enough steps and average decrease in objective per iteration < TOLERANCE
      if (model._output._iterations > 10 && steps_in_row > 3 &&
              Math.abs(model._output._avg_change_obj) < TOLERANCE) return true;
      return false;       // Not stopping
    }

    // Regularized Cholesky decomposition using H2O implementation
    public Cholesky regularizedCholesky(Gram gram, int max_attempts) {
      int attempts = 0;
      double addedL2 = 0;   // TODO: Should I report this to the user?
      Cholesky chol = gram.cholesky(null);

      while(!chol.isSPD() && attempts < max_attempts) {
        if(addedL2 == 0) addedL2 = 1e-5;
        else addedL2 *= 10;
        ++attempts;
        gram.addDiag(addedL2); // try to add L2 penalty to make the Gram SPD
        Log.info("Added L2 regularization = " + addedL2 + " to diagonal of Gram matrix");
        gram.cholesky(chol);
      }
      if(!chol.isSPD())
        throw new Gram.NonSPDMatrixException();
      return chol;
    }
    public Cholesky regularizedCholesky(Gram gram) { return regularizedCholesky(gram, 10); }

    // Regularized Cholesky decomposition using JAMA implementation
    public CholeskyDecomposition regularizedCholesky(double[][] gram, int max_attempts, boolean throw_exception) {
      int attempts = 0;
      double addedL2 = 0;
      Matrix gmat = new Matrix(gram);
      CholeskyDecomposition chol = new CholeskyDecomposition(gmat);

      while(!chol.isSPD() && attempts < max_attempts) {
        if(addedL2 == 0) addedL2 = 1e-5;
        else addedL2 *= 10;
        ++attempts;

        for(int i = 0; i < gram.length; i++) gmat.set(i,i,addedL2); // try to add L2 penalty to make the Gram SPD
        Log.info("Added L2 regularization = " + addedL2 + " to diagonal of Gram matrix");
        chol = new CholeskyDecomposition(gmat);
      }
      if(!chol.isSPD() && throw_exception)
        throw new Gram.NonSPDMatrixException();
      return chol;
    }
    public CholeskyDecomposition regularizedCholesky(double[][] gram) { return regularizedCholesky(gram, 10, true); }

    // Recover singular values and eigenvectors of XY
    public void recoverSVD(GLRMModel model, DataInfo xinfo) {
      // NOTE: Gram computes X'X/n where n = nrow(A) = number of rows in training set
      GramTask xgram = new GramTask(self(), xinfo).doAll(xinfo._adaptedFrame);
      Cholesky xxchol = regularizedCholesky(xgram._gram);

      // R from QR decomposition of X = QR is upper triangular factor of Cholesky of X'X
      // Gram = X'X/n = LL' -> X'X = (L*sqrt(n))(L'*sqrt(n))
      Matrix x_r = new Matrix(xxchol.getL()).transpose();
      x_r = x_r.times(Math.sqrt(_train.numRows()));

      QRDecomposition yt_qr = new QRDecomposition(new Matrix(model._output._archetypes));
      Matrix yt_r = yt_qr.getR();   // S from QR decomposition of Y' = ZS
      Matrix rrmul = x_r.times(yt_r.transpose());
      SingularValueDecomposition rrsvd = new SingularValueDecomposition(rrmul);   // RS' = U \Sigma V'

      // Eigenvectors are V'Z' = (ZV)'
      Matrix eigvec = yt_qr.getQ().times(rrsvd.getV());
      model._output._eigenvectors = eigvec.getArray();

      // Singular values ordered in weakly descending order by algorithm
      model._output._singular_vals = rrsvd.getSingularValues();
    }

    @Override protected void compute2() {
      GLRMModel model = null;
      DataInfo dinfo = null, xinfo = null, tinfo = null;
      Frame fr = null, x = null;

      try {
        init(true);   // Initialize parameters
        _parms.read_lock_frames(GLRM.this); // Fetch & read-lock input frames
        if (error_count() > 0) throw new IllegalArgumentException("Found validation errors: " + validationErrors());

        // The model to be built
        model = new GLRMModel(dest(), _parms, new GLRMModel.GLRMOutput(GLRM.this));
        model.delete_and_lock(self());

        // Save adapted frame info for scoring later
        tinfo = new DataInfo(Key.make(), _train, _valid, 0, true, _parms._transform, DataInfo.TransformType.NONE, false, false, /* weights */ false, /* offset */ false);
        DKV.put(tinfo._key, tinfo);

        // Save standardization vectors for use in scoring later
        model._output._normSub = tinfo._normSub == null ? new double[tinfo._nums] : tinfo._normSub;
        if(tinfo._normMul == null) {
          model._output._normMul = new double[tinfo._nums];
          Arrays.fill(model._output._normMul, 1.0);
        } else
          model._output._normMul = tinfo._normMul;
        model._output._permutation = tinfo._permutation;
        model._output._nnums = tinfo._nums;
        model._output._ncats = tinfo._cats;
        model._output._catOffsets = tinfo._catOffsets;
        model._output._names_expanded = tinfo.coefNames();

        // 0) a) Initialize Y matrix
        long nobs = _train.numRows() * _train.numCols();
        for(int i = 0; i < _train.numCols(); i++) nobs -= _train.vec(i).naCnt();   // TODO: Should we count NAs?
        model._output._nobs = nobs;
        double[][] yt = ArrayUtils.transpose(initialY(tinfo));

        // 0) b) Initialize X matrix to random numbers
        // Jam A and X into a single frame for distributed computation
        // [A,X,W] A is read-only training data, X is matrix from prior iteration, W is working copy of X this iteration
        Vec[] vecs = new Vec[_ncolA + 2*_ncolX];
        for (int i = 0; i < _ncolA; i++) vecs[i] = _train.vec(i);
        for (int i = _ncolA; i < vecs.length; i++) vecs[i] = _train.anyVec().makeGaus(_parms._seed);
        fr = new Frame(null, vecs);
        dinfo = new DataInfo(Key.make(), fr, null, 0, true, _parms._transform, DataInfo.TransformType.NONE, false, false, /* weights */ false, /* offset */ false);
        DKV.put(dinfo._key, dinfo);
        initialX(dinfo, yt, model._output._normSub, model._output._normMul);

        // Compute initial objective function
        ObjCalc objtsk = new ObjCalc(dinfo, _parms, yt, _ncolA, _ncolX, model._output._normSub, model._output._normMul, _parms._gamma_x != 0).doAll(dinfo._adaptedFrame);
        model._output._objective = objtsk._loss + _parms._gamma_x * objtsk._xold_reg + _parms._gamma_y * _parms.regularize_y(yt);
        model._output._iterations = 0;
        model._output._avg_change_obj = 2 * TOLERANCE;    // Run at least 1 iteration
        model.update(_key);  // Update model in K/V store
        update(1);           // One unit of work

        boolean overwriteX = false;
        double step = _parms._init_step_size;   // Initial step size
        int steps_in_row = 0;                   // Keep track of number of steps taken that decrease objective

        while (!isDone(model, steps_in_row, step)) {
          // TODO: Should step be divided by number of original or expanded (with 0/1 categorical) cols?
          // 1) Update X matrix given fixed Y
          UpdateX xtsk = new UpdateX(dinfo, _parms, yt, step/_ncolA, overwriteX, _ncolA, _ncolX, model._output._normSub, model._output._normMul);
          xtsk.doAll(dinfo._adaptedFrame);
          
          // 2) Update Y matrix given fixed X
          UpdateY ytsk = new UpdateY(dinfo, _parms, yt, step/_ncolA, _ncolA, _ncolX, model._output._normSub, model._output._normMul);
          double[][] ytnew = ytsk.doAll(dinfo._adaptedFrame)._ytnew;

          // 3) Compute average change in objective function
          objtsk = new ObjCalc(dinfo, _parms, ytnew, _ncolA, _ncolX, model._output._normSub, model._output._normMul).doAll(dinfo._adaptedFrame);
          double obj_new = objtsk._loss + _parms._gamma_x * xtsk._xreg + _parms._gamma_y * ytsk._yreg;
          model._output._avg_change_obj = (model._output._objective - obj_new) / nobs;
          model._output._iterations++;

          // step = 1.0 / model._output._iterations;   // Step size \alpha_k = 1/iters
          if(model._output._avg_change_obj > 0) {   // Objective decreased this iteration
            model._output._archetypes = yt = ytnew;
            model._output._objective = obj_new;
            step *= 1.05;
            steps_in_row = Math.max(1, steps_in_row+1);
            overwriteX = true;
          } else {    // If objective increased, re-run with smaller step size
            step = step / Math.max(1.5, -steps_in_row);
            steps_in_row = Math.min(0, steps_in_row-1);
            overwriteX = false;
            Log.info("Iteration " + model._output._iterations + ": Objective increased to " + model._output._objective + "; reducing step size to " + step);
          }
          model._output._step_size = step;
          model.update(self()); // Update model in K/V store
          update(1);          // One unit of work
        }

        // 4) Save solution to model output
        // Save X frame for user reference later
        Vec[] xvecs = new Vec[_ncolX];
        for (int i = 0; i < _ncolX; i++) xvecs[i] = fr.vec(idx_xnew(i, _ncolA, _ncolX));
        x = new Frame(_parms._loading_key, null, xvecs);
        xinfo = new DataInfo(Key.make(), x, null, 0, true, DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, false, false, /* weights */ false, /* offset */ false);
        DKV.put(x._key, x);
        DKV.put(xinfo._key, xinfo);
        model._output._loading_key = _parms._loading_key;
        model._output._archetypes = yt;
        model._output._step_size = step;
        if (_parms._recover_svd) recoverSVD(model, xinfo);

        // Optional: This computes XY, but do we need it?
        // BMulTask tsk = new BMulTask(self(), xinfo, yt).doAll(dinfo._adaptedFrame.numCols(), xinfo._adaptedFrame);
        // tsk.outputFrame(_parms._destination_key, _train._names, null);
        model.update(self());
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
        _parms.read_unlock_frames(GLRM.this);
        if (model != null) model.unlock(_key);
        if (dinfo != null) dinfo.remove();
        if (xinfo != null) xinfo.remove();
        if (tinfo != null) tinfo.remove();
        // if (x != null && !_parms._keep_loading) x.delete();
        // Clean up old copy of X matrix
        if (fr != null) {
          for(int i = 0; i < _ncolX; i++)
            fr.vec(idx_xold(i, _ncolA)).remove();
        }
      }
      tryComplete();
    }

    Key self() {
      return _key;
    }
  }

  // In chunk, first _ncolA cols are A, next _ncolX cols are X
  protected static int idx_xold(int c, int ncolA) { return ncolA+c; }
  protected static int idx_xnew(int c, int ncolA, int ncolX) { return ncolA+ncolX+c; }
  protected static int idx_ycat(int c, int level, DataInfo dinfo) {   // TODO: Deal with case of missing bucket
    // assert !Double.isNaN(level) && level >= 0 && level < dinfo._catLvls[c].length;
    assert dinfo._adaptedFrame.domains() != null : "Domain of categorical column cannot be null";
    assert !Double.isNaN(level) && level >= 0 && level < dinfo._adaptedFrame.domains()[c].length;
    return dinfo._catOffsets[c]+level;
  }
  protected static int idx_ynum(int c, DataInfo dinfo) {
    return dinfo._catOffsets[dinfo._catOffsets.length-1]+c;
  }
  protected static Chunk chk_xold(Chunk chks[], int c, int ncolA) { return chks[ncolA+c]; }
  protected static Chunk chk_xnew(Chunk chks[], int c, int ncolA, int ncolX) { return chks[ncolA+ncolX+c]; }

  protected static double[][] yt_block(double[][] yt, int cidx, DataInfo dinfo) {
    return yt_block(yt, cidx, dinfo, false);
  }
  protected static double[][] yt_block(double[][] yt, int cidx, DataInfo dinfo, boolean transpose) {
    int catlvls = dinfo._adaptedFrame.domains() == null ? 1 : dinfo._adaptedFrame.domains()[cidx].length;
    // double[][] block = new double[dinfo._catLvls[cidx].length][yt[0].length];

    double[][] block;
    if(transpose) {
      block = new double[yt[0].length][catlvls];
      for (int col = 0; col < block.length; col++) {
        for (int level = 0; level < block[0].length; level++) {
          block[col][level] = yt[idx_ycat(cidx, level, dinfo)][col];
        }
      }
    } else {
      block = new double[catlvls][yt[0].length];
      for (int col = 0; col < block[0].length; col++) {
        for (int level = 0; level < block.length; level++) {
          block[level][col] = yt[idx_ycat(cidx, level, dinfo)][col];
        }
      }
    }
    return block;
  }

  private static class UpdateX extends MRTask<UpdateX> {
    // Input
    DataInfo _dinfo;
    GLRMParameters _parms;
    final double _alpha;      // Step size divided by num cols in A
    final boolean _update;    // Should we update X from working copy?
    final double[][] _yt;     // _yt = Y' (transpose of Y)
    final int _ncolA;         // Number of cols in training frame
    final int _ncolX;         // Number of cols in X (k)
    final double[] _normSub;  // For standardizing training data
    final double[] _normMul;

    // Output
    double _loss;    // Loss evaluated on A - XY using new X (and current Y)
    double _xreg;    // Regularization evaluated on new X

    UpdateX(DataInfo dinfo, GLRMParameters parms, double[][] yt, double alpha, boolean update, int ncolA, int ncolX, double[] normSub, double[] normMul) {
      assert yt != null && yt[0].length == ncolX;
      _parms = parms;
      _yt = yt;
      _alpha = alpha;
      _update = update;
      _ncolA = ncolA;
      _ncolX = ncolX;

      // dinfo contains [A,X,W], but we only use its info on A (cols 1 to ncolA)
      assert dinfo._cats <= ncolA;
      _dinfo = dinfo;
      _normSub = normSub;
      _normMul = normMul;
    }

    @Override public void map(Chunk[] cs) {
      assert (_ncolA + 2*_ncolX) == cs.length;
      double[] a = new double[_ncolA];
      Random rand = RandomUtils.getRNG(_parms._seed + cs[0].start());
      _loss = _xreg = 0;

      for(int row = 0; row < cs[0]._len; row++) {
        double[] grad = new double[_ncolX];
        double[] xnew = new double[_ncolX];

        // Copy old working copy of X to current X if requested
        if(_update) {
          for(int k = 0; k < _ncolX; k++)
            chk_xold(cs,k,_ncolA).set(row, chk_xnew(cs,k,_ncolA,_ncolX).atd(row));
        }

        // Compute gradient of objective at row
        // Categorical columns
        for(int j = 0; j < _dinfo._cats; j++) {
          a[j] = cs[j].atd(row);
          if(Double.isNaN(a[j])) continue;   // Skip missing observations in row

          // Calculate x_i * Y_j where Y_j is sub-matrix corresponding to categorical col j
          // double[] xy = new double[_dinfo._catLvls[j].length];
          double[] xy = new double[_dinfo._adaptedFrame.domains()[j].length];
          for(int level = 0; level < xy.length; level++) {
            for(int k = 0; k < _ncolX; k++) {
              xy[level] += chk_xold(cs,k,_ncolA).atd(row) * _yt[idx_ycat(j,level,_dinfo)][k];
            }
          }

          // Gradient wrt x_i is matrix product \grad L_{i,j}(x_i * Y_j, A_{i,j}) * Y_j'
          double[] weight = _parms.mlgrad(xy, (int)a[j]);
          double[][] ytsub = yt_block(_yt,j,_dinfo);
          for(int k = 0; k < _ncolX; k++) {
            for(int c = 0; c < weight.length; c++)
              grad[k] += weight[c] * ytsub[c][k];
          }
        }

        // Numeric columns
        for(int j = _dinfo._cats; j < _ncolA; j++) {
          int js = j - _dinfo._cats;
          int yidx = idx_ynum(js,_dinfo);
          a[j] = cs[j].atd(row);
          if(Double.isNaN(a[j])) continue;   // Skip missing observations in row

          // Inner product x_i * y_j
          double xy = 0;
          for(int k = 0; k < _ncolX; k++)
            xy += chk_xold(cs,k,_ncolA).atd(row) * _yt[yidx][k];

          // Sum over y_j weighted by gradient of loss \grad L_{i,j}(x_i * y_j, A_{i,j})
          double weight = _parms.lgrad(xy, (a[j] - _normSub[js]) * _normMul[js]);
          for(int k = 0; k < _ncolX; k++)
            grad[k] += weight * _yt[yidx][k];
        }

        // Update row x_i of working copy with new values
        double[] u = new double[_ncolX];
        for(int k = 0; k < _ncolX; k++) {
          double xold = chk_xold(cs,k,_ncolA).atd(row);   // Old value of x_i
          u[k] = xold - _alpha * grad[k];
          // xnew[k] = _parms.rproxgrad_x(xold - _alpha * grad[k], _alpha);  // Proximal gradient
          // chk_xnew(cs,k,_ncolA,_ncolX).set(row, xnew[k]);
          // _xreg += _parms.regularize_x(xnew[k]);
        }
        xnew = _parms.rproxgrad_x(u, _alpha, rand);
        _xreg += _parms.regularize_x(xnew);
        for(int k = 0; k < _ncolX; k++)
          chk_xnew(cs,k,_ncolA,_ncolX).set(row,xnew[k]);

        // Compute loss function using new x_i
        // Categorical columns
        for(int j = 0; j < _dinfo._cats; j++) {
          if(Double.isNaN(a[j])) continue;   // Skip missing observations in row
          double[] xy = ArrayUtils.multVecArr(xnew, yt_block(_yt,j,_dinfo,true));
          _loss += _parms.mloss(xy, (int) a[j]);
        }

        // Numeric columns
        for(int j = _dinfo._cats; j < _ncolA; j++) {
          int js = j - _dinfo._cats;
          if(Double.isNaN(a[j])) continue;   // Skip missing observations in row
          double xy = ArrayUtils.innerProduct(xnew, _yt[idx_ynum(js,_dinfo)]);
          _loss += _parms.loss(xy, (a[j] - _normSub[js]) * _normMul[js]);
        }
      }
    }

    @Override public void reduce(UpdateX other) {
      _loss += other._loss;
      _xreg += other._xreg;
    }
  }

  private static class UpdateY extends MRTask<UpdateY> {
    // Input
    DataInfo _dinfo;
    GLRMParameters _parms;
    final double _alpha;      // Step size divided by num cols in A
    final double[][] _ytold;  // Old Y' matrix
    final int _ncolA;         // Number of cols in training frame
    final int _ncolX;         // Number of cols in X (k)
    final double[] _normSub;  // For standardizing training data
    final double[] _normMul;

    // Output
    double[][] _ytnew;  // New Y matrix
    double _yreg;       // Regularization evaluated on new Y

    UpdateY(DataInfo dinfo, GLRMParameters parms, double[][] yt, double alpha, int ncolA, int ncolX, double[] normSub, double[] normMul) {
      assert yt != null && yt[0].length == ncolX;
      _parms = parms;
      _alpha = alpha;
      _ncolA = ncolA;
      _ncolX = ncolX;
      _ytold = yt;
      _yreg = 0;
      // _ytnew = new double[_ncolA][_ncolX];

      // dinfo contains [A,X,W], but we only use its info on A (cols 1 to ncolA)
      assert dinfo._cats <= ncolA;
      _dinfo = dinfo;
      _normSub = normSub;
      _normMul = normMul;
    }

    @Override public void map(Chunk[] cs) {
      assert (_ncolA + 2*_ncolX) == cs.length;
      _ytnew = new double[_ytold.length][_ncolX];

      // Categorical columns
      for(int j = 0; j < _dinfo._cats; j++) {
        // Compute gradient of objective at column
        for(int row = 0; row < cs[0]._len; row++) {
          double a = cs[j].atd(row);
          if(Double.isNaN(a)) continue;   // Skip missing observations in column

          // Calculate x_i * Y_j where Y_j is sub-matrix corresponding to categorical col j
          // double[] xy = new double[_dinfo._catLvls[j].length];
          double[] xy = new double[_dinfo._adaptedFrame.domains()[j].length];
          for(int level = 0; level < xy.length; level++) {
            for(int k = 0; k < _ncolX; k++) {
              xy[level] += chk_xnew(cs,k,_ncolA,_ncolX).atd(row) * _ytold[idx_ycat(j,level,_dinfo)][k];
            }
          }

          // Gradient for level p is x_i weighted by \grad_p L_{i,j}(x_i * Y_j, A_{i,j})
          double[] weight = _parms.mlgrad(xy, (int)a);
          for(int level = 0; level < xy.length; level++) {
            for(int k = 0; k < _ncolX; k++)
              _ytnew[idx_ycat(j,level,_dinfo)][k] += weight[level] * chk_xnew(cs,k,_ncolA,_ncolX).atd(row);
          }
        }
      }

      // Numeric columns
      for(int j = _dinfo._cats; j < _ncolA; j++) {
        int js = j - _dinfo._cats;
        int yidx = idx_ynum(js,_dinfo);

        // Compute gradient of objective at column
        for(int row = 0; row < cs[0]._len; row++) {
          double a = cs[j].atd(row);
          if(Double.isNaN(a)) continue;   // Skip missing observations in column

          // Inner product x_i * y_j
          double xy = 0;
          for(int k = 0; k < _ncolX; k++)
            xy += chk_xnew(cs,k,_ncolA,_ncolX).atd(row) * _ytold[yidx][k];

          // Sum over x_i weighted by gradient of loss \grad L_{i,j}(x_i * y_j, A_{i,j})
          double weight = _parms.lgrad(xy, (a - _normSub[js]) * _normMul[js]);
          for(int k = 0; k < _ncolX; k++)
            _ytnew[yidx][k] += weight * chk_xnew(cs,k,_ncolA,_ncolX).atd(row);
        }
      }
    }

    @Override public void reduce(UpdateY other) {
      ArrayUtils.add(_ytnew, other._ytnew);
    }

    @Override protected void postGlobal() {
      assert _ytnew.length == _ytold.length && _ytnew[0].length == _ytold[0].length;
      Random rand = RandomUtils.getRNG(_parms._seed);

      // Compute new y_j values using proximal gradient
      for(int j = 0; j < _ytnew.length; j++) {
        double[] u = new double[_ytnew[0].length];
        for(int k = 0; k < _ytnew[0].length; k++) {
          // double u = _ytold[j][k] - _alpha * _ytnew[j][k];
          // _ytnew[j][k] = _parms.rproxgrad_y(u, _alpha);
          // _yreg += _parms.regularize_y(_ytnew[j][k]);
          u[k] = _ytold[j][k] - _alpha * _ytnew[j][k];
        }
        _ytnew[j] = _parms.rproxgrad_y(u, _alpha, rand);
        _yreg += _parms.regularize_y(_ytnew[j]);
      }
    }
  }

  // Calculate the sum loss function in the optimization objective
  private static class ObjCalc extends MRTask<ObjCalc> {
    // Input
    DataInfo _dinfo;
    GLRMParameters _parms;
    final double[][] _yt;     // _yt = Y' (transpose of Y)
    final int _ncolA;         // Number of cols in training frame
    final int _ncolX;         // Number of cols in X (k)
    final double[] _normSub;  // For standardizing training data
    final double[] _normMul;
    final boolean _regX;      // Should I calculate regularization of (old) X matrix?

    // Output
    double _loss;
    double _xold_reg;

    ObjCalc(DataInfo dinfo, GLRMParameters parms, double[][] yt, int ncolA, int ncolX, double[] normSub, double[] normMul) {
      this(dinfo, parms, yt, ncolA, ncolX, normSub, normMul, false);
    }
    ObjCalc(DataInfo dinfo, GLRMParameters parms, double[][] yt, int ncolA, int ncolX, double[] normSub, double[] normMul, boolean regX) {
      assert yt != null && yt[0].length == ncolX;
      _parms = parms;
      _yt = yt;
      _ncolA = ncolA;
      _ncolX = ncolX;
      _regX = regX;
      _loss = _xold_reg = 0;

      assert dinfo._cats <= ncolA;
      _dinfo = dinfo;
      _normSub = normSub;
      _normMul = normMul;
    }

    @Override public void map(Chunk[] cs) {
      assert (_ncolA + 2*_ncolX) == cs.length;

      for(int row = 0; row < cs[0]._len; row++) {
        // Categorical columns
        for(int j = 0; j < _dinfo._cats; j++) {
          double a = cs[j].atd(row);
          if (Double.isNaN(a)) continue;   // Skip missing observations in row

          // Calculate x_i * Y_j where Y_j is sub-matrix corresponding to categorical col j
          // double[] xy = new double[_dinfo._catLvls[j].length];
          double[] xy = new double[_dinfo._adaptedFrame.domains()[j].length];
          for(int level = 0; level < xy.length; level++) {
            for(int k = 0; k < _ncolX; k++) {
              xy[level] += chk_xnew(cs,k,_ncolA,_ncolX).atd(row) * _yt[idx_ycat(j,level,_dinfo)][k];
            }
          }
          _loss += _parms.mloss(xy, (int)a);
        }

        // Numeric columns
        for(int j = _dinfo._cats; j < _ncolA; j++) {
          double a = cs[j].atd(row);
          if (Double.isNaN(a)) continue;   // Skip missing observations in row

          // Inner product x_i * y_j
          double xy = 0;
          int js = j - _dinfo._cats;
          for(int k = 0; k < _ncolX; k++)
            xy += chk_xnew(cs,k,_ncolA,_ncolX).atd(row) * _yt[idx_ynum(js,_dinfo)][k];
          _loss += _parms.loss(xy, (a - _normSub[js]) * _normMul[js]);
        }

        // Calculate regularization term for old X if requested
        if(_regX) {
          int idx = 0;
          double[] xrow = new double[_ncolX];
          for(int j = _ncolA; j < _ncolA+_ncolX; j++) {
            // double x = cs[j].atd(row);
            // _xold_reg += _parms.regularize_x(x);
            xrow[idx] = cs[j].atd(row);
            idx++;
          }
          assert idx == _ncolX;
          _xold_reg += _parms.regularize_x(xrow);
        }
      }
    }
  }

  // Solves XD = AY' for X where A is n by p, Y is k by p, D is k by k, and n >> p > k
  // Resulting matrix X = (AY')D^(-1) will have dimensions n by k
  private static class CholMulTask extends MRTask<CholMulTask> {
    DataInfo _dinfo;
    GLRMParameters _parms;
    final double[][] _yt;     // _yt = Y' (transpose of Y)
    final int _ncolA;         // Number of cols in training frame
    final int _ncolX;         // Number of cols in X (k)
    final double[] _normSub;  // For standardizing training data
    final double[] _normMul;
    CholeskyDecomposition _chol;   // Cholesky decomposition of D = D', since we solve D'X' = DX' = AY'

    CholMulTask(DataInfo dinfo, GLRMParameters parms, CholeskyDecomposition chol, double[][] yt, double[] normSub, double[] normMul) {
      this(dinfo, parms, chol, yt, yt.length, yt[0].length, normSub, normMul);
    }

    CholMulTask(DataInfo dinfo, GLRMParameters parms, CholeskyDecomposition chol, double[][] yt, int ncolA, int ncolX, double[] normSub, double[] normMul) {
      assert yt != null && yt[0].length == ncolX;
      _parms = parms;
      _yt = yt;
      _ncolA = ncolA;
      _ncolX = ncolX;
      _chol = chol;

      assert dinfo._cats <= ncolA;
      _dinfo = dinfo;
      _normSub = normSub;
      _normMul = normMul;
    }

    // [A,X,W] A is read-only training data, X is left matrix in A = XY decomposition, W is working copy of X
    @Override public void map(Chunk[] cs) {
      assert (_ncolA + 2*_ncolX) == cs.length;
      double[] xrow = new double[_ncolX];

      for(int row = 0; row < cs[0]._len; row++) {
        // 1) Compute single row of AY'
        for (int k = 0; k < _ncolX; k++) {
          // Categorical columns
          double x = 0;
          for(int d = 0; d < _dinfo._cats; d++) {
            double a = cs[d].atd(row);
            if (Double.isNaN(a)) continue;
            x += _yt[idx_ycat(d, (int)a, _dinfo)][k];
          }

          // Numeric columns
          for (int d = _dinfo._cats; d < _ncolA; d++) {
            int ds = d - _dinfo._cats;
            double a = cs[d].atd(row);
            if (Double.isNaN(a)) continue;
            x += (a - _normSub[ds]) * _normMul[ds] * _yt[idx_ynum(ds,_dinfo)][k];
          }
          xrow[k] = x;
        }

        // 2) Cholesky solve for single row of X
        // _chol.solve(xrow);
        Matrix tmp = _chol.solve(new Matrix(new double[][] {xrow}).transpose());
        xrow = tmp.getColumnPackedCopy();

        // 3) Save row of solved values into X (and copy W = X)
        int i = 0;
        for(int d = _ncolA; d < _ncolA+_ncolX; d++) {
          cs[d].set(row, xrow[i]);
          cs[d+_ncolX].set(row, xrow[i++]);
        }
        assert i == xrow.length;
      }
    }
  }

  // Computes XY where X is n by k, Y is k by p, and k <= p
  // The resulting matrix Z = XY will have dimensions n by p
  private static class BMulTask extends FrameTask<BMulTask> {
    double[][] _yt;   // _yt = Y' (transpose of Y)

    BMulTask(Key jobKey, DataInfo dinfo, final double[][] yt) {
      super(jobKey, dinfo);
      _yt = yt;
    }

    @Override protected void processRow(long gid, DataInfo.Row row, NewChunk[] outputs) {
      assert row.nBins + _dinfo._nums == _yt[0].length;
      for(int p = 0; p < _yt.length; p++) {
        double x = row.innerProduct(_yt[p]);
        outputs[p].addNum(x);
      }
    }
  }
}
