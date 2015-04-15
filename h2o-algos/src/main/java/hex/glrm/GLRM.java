package hex.glrm;

import Jama.Matrix;
import Jama.QRDecomposition;
import Jama.SingularValueDecomposition;
import hex.DataInfo;
import hex.FrameTask;
import hex.Model;
import hex.ModelBuilder;
import hex.gram.Gram;
import hex.gram.Gram.*;
import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel;
import hex.schemas.GLRMV2;
import hex.glrm.GLRMModel.GLRMParameters;
import hex.schemas.ModelBuilderSchema;
import hex.svd.SVD;
import hex.svd.SVDModel;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.TwoDimTable;

import java.util.Arrays;

/**
 * Generalized Low Rank Models
 * <a href = "http://web.stanford.edu/~boyd/papers/pdf/glrm.pdf">Generalized Low Rank Models</a>
 * @author anqi_fu
 */
public class GLRM extends ModelBuilder<GLRMModel,GLRMModel.GLRMParameters,GLRMModel.GLRMOutput> {
  // Convergence tolerance
  private final double TOLERANCE = 1e-8;

  // Number of columns in training set (p)
  private transient int _ncolA;

  // Number of columns in fitted X matrix (k)
  private transient int _ncolX;

  // For standardizing training data
  private transient double[] _normSub;
  private transient double[] _normMul;

  @Override public ModelBuilderSchema schema() {
    return new GLRMV2();
  }

  @Override public Job<GLRMModel> trainModel() {
    return start(new GLRMDriver(), 0);
  }

  @Override public Model.ModelCategory[] can_build() {
    return new Model.ModelCategory[]{Model.ModelCategory.Clustering};
  }

  public enum Initialization {
    SVD, PlusPlus, User
  }

  // Called from an http request
  public GLRM(GLRMParameters parms) {
    super("GLRM", parms);
    init(false);
  }

  @Override public void init(boolean expensive) {
    super.init(expensive);
    if (_parms._loading_key == null) _parms._loading_key = Key.make("GLRMLoading_" + Key.rand());
    if (_parms._gamma < 0) error("_gamma", "gambda must be a non-negative number");
    if (_parms._max_iterations < 1) error("_max_iterations", "max_iterations must be at least 1");

    if (_train == null) return;
    if (_train.numCols() < 2) error("_train", "_train must have more than one column");

    // TODO: Initialize _parms._k = min(ncol(_train), nrow(_train)) if not set
    int k_min = (int) Math.min(_train.numCols(), _train.numRows());
    if (_parms._k < 1 || _parms._k > k_min) error("_k", "_k must be between 1 and " + k_min);
    if (null != _parms._user_points) { // Check dimensions of user-specified centers
      if (_parms._user_points.get().numCols() != _train.numCols())
        error("_user_points", "The user-specified points must have the same number of columns (" + _train.numCols() + ") as the training observations");
      else if (_parms._user_points.get().numRows() != _parms._k)
        error("_user_points", "The user-specified points must have k = " + _parms._k + " rows");
      else {
        int zero_vec = 0;
        Vec[] centersVecs = _parms._user_points.get().vecs();
        for (int c = 0; c < _ncolA; c++) {
          if(centersVecs[c].isConst() && centersVecs[c].max() == 0)
            zero_vec++;
        }
        if (zero_vec == _ncolA)
          error("_user_points", "The user-specified points cannot all be zero");
      }
    }

    // Currently, does not work on categorical data
    Vec[] vecs = _train.vecs();
    for (int i = 0; i < vecs.length; i++) {
      if (!vecs[i].isNumeric()) throw H2O.unimpl();
    }

    _ncolA = _train.numCols();
    _ncolX = _parms._k;
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

    for (int clu = 0; clu < K; clu++) {
      System.arraycopy(centers[clu], 0, value[clu], 0, N);
      for (int col = ncats; col < N; col++)
        value[clu][col] = (value[clu][col] - means[col]) * mults[col];
    }
    return value;
  }

  class GLRMDriver extends H2O.H2OCountedCompleter<GLRMDriver> {

    // Initialize Y matrix
    public double[][] initialY() {
      double[][] centers;

      if (null != _parms._user_points) { // User-specified starting points
        Vec[] centersVecs = _parms._user_points.get().vecs();
        centers = new double[_parms._k][_ncolA];

        // Get the centers and put into array
        for (int r = 0; r < _parms._k; r++) {
          for (int c = 0; c < _ncolA; c++)
            centers[r][c] = centersVecs[c].at(r);
        }
      } else if (_parms._init == Initialization.SVD) {  // Run SVD and use right singular vectors as initial Y

        SVDModel.SVDParameters parms = new SVDModel.SVDParameters();
        parms._train = _parms._train;
        parms._nv = _parms._k;
        parms._max_iterations = _parms._max_iterations;
        parms._transform = _parms._transform;
        parms._seed = _parms._seed;

        SVDModel svd = null;
        SVD job = null;
        try {
          job = new SVD(parms);
          svd = job.trainModel().get();
        } finally {
          if (job != null) job.remove();
          if (svd != null) svd.remove();
        }
        centers = ArrayUtils.transpose(svd._output._v);
      } else {  // Run k-means++ and use resulting cluster centers as initial Y

        KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
        parms._train = _parms._train;
        parms._ignored_columns = _parms._ignored_columns;
        parms._dropConsCols = _parms._dropConsCols;
        parms._dropNA20Cols = _parms._dropNA20Cols;
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

      // If all centers entries are zero or any is NaN, initialize to standard normal random matrix
      double frob = frobenius2(centers);
      if(frob == 0 || Double.isNaN(frob)) {
        warn("_init", "Initialization failed. Setting initial Y to standard normal random matrix instead...");
        centers = ArrayUtils.gaussianArray(_parms._k, _ncolA);
      }
      return centers;
    }

    // Stopping criteria
    private boolean isDone(GLRMModel model) {
      if (!isRunning()) return true; // Stopped/cancelled
      // Stopped for running out of iterations
      if (model._output._iterations > _parms._max_iterations) return true;

      // Stopped when average decrease in objective per iteration < TOLERANCE
      if( Math.abs(model._output._avg_change_obj) < TOLERANCE ) return true;
      return false;             // Not stopping
    }

    public Cholesky regularizedCholesky(Gram gram, int max_attempts) {
      int attempts = 0;
      double addedL2 = 0;   // TODO: Should I report this to the user?
      Cholesky chol = gram.cholesky(null);
      while(!chol.isSPD() && attempts < max_attempts) {
        if(addedL2 == 0) addedL2 = 1e-5;
        else addedL2 *= 10;
        ++attempts;
        gram.addDiag(addedL2); // try to add L2 penalty to make the Gram SPD
        Log.info("Added L2 regularization = " + addedL2 + " to diagonal of X Gram matrix");
        gram.cholesky(chol);
      }
      if(!chol.isSPD())
        throw new Gram.NonSPDMatrixException();
      return chol;
    }
    public Cholesky regularizedCholesky(Gram gram) {
      return regularizedCholesky(gram, 10);
    }

    // Recover eigenvalues and eigenvectors of XY
    public void recoverPCA(GLRMModel model, DataInfo xinfo) {
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
      model._output._eigenvectors_raw = eigvec.getArray();

      String[] colTypes = new String[_parms._k];
      String[] colFormats = new String[_parms._k];
      String[] colHeaders = new String[_parms._k];
      Arrays.fill(colTypes, "double");
      Arrays.fill(colFormats, "%5f");
      for(int i = 0; i < colHeaders.length; i++) colHeaders[i] = "PC" + String.valueOf(i+1);
      model._output._eigenvectors = new TwoDimTable("Rotation", null, _train.names(),
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
      model._output._pc_importance = new TwoDimTable("Importance of components", null,
              new String[] { "Standard deviation", "Proportion of Variance", "Cumulative Proportion" },
              colHeaders, colTypes, colFormats, "", new String[3][], new double[][] { sdev, prop_var, cum_var });
    }

    @Override protected void compute2() {
      GLRMModel model = null;
      DataInfo dinfo = null, xinfo = null;
      Frame fr = null, x = null;

      try {
        _parms.read_lock_frames(GLRM.this); // Fetch & read-lock input frames
        init(true);
        if (error_count() > 0) throw new IllegalArgumentException("Found validation errors: " + validationErrors());

        // The model to be built
        model = new GLRMModel(dest(), _parms, new GLRMModel.GLRMOutput(GLRM.this));
        model.delete_and_lock(_key);
        _train.read_lock(_key);

        // 0) a) Initialize Y matrix
        double nobs = _train.numRows() * _train.numCols();
        // for(int i = 0; i < _train.numCols(); i++) nobs -= _train.vec(i).naCnt();   // TODO: Should we count NAs?
        double[][] yt = ArrayUtils.transpose(initialY());

        // 0) b) Initialize X matrix to random numbers
        // Jam A and X into a single frame for distributed computation
        // [A,X,W] A is read-only training data, X is matrix from prior iteration, W is working copy of X this iteration
        Vec[] vecs = new Vec[_ncolA + 2*_ncolX];
        for (int i = 0; i < _ncolA; i++) vecs[i] = _train.vec(i);
        for (int i = _ncolA; i < vecs.length; i++) vecs[i] = _train.anyVec().makeRand(_parms._seed);
        fr = new Frame(null, vecs);
        dinfo = new DataInfo(Key.make(), fr, null, 0, false, _parms._transform, DataInfo.TransformType.NONE, true);
        DKV.put(dinfo._key, dinfo);

        // Save standardization vectors for use in scoring later
        model._output._normSub = _normSub = dinfo._normSub == null ? new double[_ncolA] : Arrays.copyOf(dinfo._normSub, _ncolA);
        if(dinfo._normMul == null) {
          model._output._normMul = _normMul = new double[_ncolA];
          Arrays.fill(model._output._normMul, 1.0);
        } else
          model._output._normMul = _normMul = Arrays.copyOf(dinfo._normMul, _ncolA);

        // Compute initial objective function
        ObjCalc objtsk = new ObjCalc(_parms, yt).doAll(dinfo._adaptedFrame);
        model._output._objective = objtsk._loss + _parms._gamma * _parms.regularize(yt);
        model._output._iterations = 0;
        model._output._avg_change_obj = 2 * TOLERANCE;    // Run at least 1 iteration

        boolean overwriteX = false;
        double step = 1.0;        // Initial step size
        double steps_in_row = 0;  // Keep track of number of steps taken that decrease objective

        while (!isDone(model)) {
          // 1) Update X matrix given fixed Y
          UpdateX xtsk = new UpdateX(_parms, yt, step/_ncolA, overwriteX);
          xtsk.doAll(dinfo._adaptedFrame);

          // 2) Update Y matrix given fixed X
          UpdateY ytsk = new UpdateY(_parms, yt, step/_ncolA);
          double[][] ytnew = ytsk.doAll(dinfo._adaptedFrame)._ytnew;

          // 3) Compute average change in objective function
          objtsk = new ObjCalc(_parms, ytnew).doAll(dinfo._adaptedFrame);
          double obj_new = objtsk._loss + _parms._gamma * (xtsk._xreg + ytsk._yreg);
          model._output._avg_change_obj = (model._output._objective - obj_new) / nobs;
          model._output._iterations++;

          // step = 1.0 / model._output._iterations;   // Step size \alpha_k = 1/iters
          if(model._output._avg_change_obj > 0) {   // Objective decreased this iteration
            yt = ytnew;
            model._output._objective = obj_new;
            step *= 1.05;
            steps_in_row = Math.max(1, steps_in_row+1);
            overwriteX = true;
          } else {    // If objective increased, re-run with smaller step size
            step = step / Math.max(1.5, -steps_in_row);
            steps_in_row = Math.min(0, steps_in_row-1);
            overwriteX = false;
            // Log.info("Iteration " + model._output._iterations + ": Objective increased to " + model._output._objective);
          }
          model.update(_key); // Update model in K/V store
          update(1);          // One unit of work
        }

        // 4) Save solution to model output
        // Save X frame for user reference later
        Vec[] xvecs = new Vec[_ncolX];
        for (int i = 0; i < _ncolX; i++) xvecs[i] = fr.vec(idx_xnew(i));
        x = new Frame(_parms._loading_key, null, xvecs);
        xinfo = new DataInfo(Key.make(), x, null, 0, false, DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true);
        DKV.put(x._key, x);
        DKV.put(xinfo._key, xinfo);

        model._output._archetypes = yt;
        if (_parms._recover_pca) recoverPCA(model, xinfo);

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

        // Clean up old copy of X matrix
        if (fr != null) {
          for(int i = 0; i < _ncolX; i++)
            fr.vec(idx_xold(i)).remove();
        }
        // if (x != null && !_parms._keep_loading) x.delete();
        _parms.read_unlock_frames(GLRM.this);
      }
      tryComplete();
    }

    Key self() {
      return _key;
    }
  }

  // In chunk, first _ncolA cols are A, next _ncolX cols are X
  protected int idx_xold(int c) { return _ncolA+c; }
  protected int idx_xnew(int c) { return _ncolA+_ncolX+c; }

  protected Chunk chk_xold(Chunk chks[], int c) { return chks[_ncolA+c]; }
  protected Chunk chk_xnew(Chunk chks[], int c) { return chks[_ncolA+_ncolX+c]; }

  private class UpdateX extends MRTask<UpdateX> {
    // Input
    GLRMParameters _parms;
    double _alpha;      // Step size
    boolean _update;    // Should we update X from working copy?
    double[][] _yt;     // _yt = Y' (transpose of Y)

    // Output
    double _loss;    // Loss evaluated on A - XY using new X (and current Y)
    double _xreg;    // Regularization evaluated on new X

    public UpdateX(GLRMParameters parms, final double[][] yt, final double alpha) { this(parms, yt, alpha, true); }
    public UpdateX(GLRMParameters parms, final double[][] yt, final double alpha, final boolean update) {
      assert yt != null && yt.length == _ncolA && yt[0].length == _ncolX;
      _parms = parms;
      _alpha = alpha;
      _update = update;
      _yt = yt;
    }

    @Override public void map(Chunk[] cs) {
      assert (_ncolA + 2*_ncolX) == cs.length;
      double[] a = new double[_ncolA];
      _loss = _xreg = 0;

      for(int row = 0; row < cs[0]._len; row++) {
        double[] grad = new double[_ncolX];
        double[] xnew = new double[_ncolX];

        // Copy old working copy of X to current X if requested
        if(_update) {
          for(int k = 0; k < _ncolX; k++)
            chk_xold(cs,k).set(row, chk_xnew(cs,k).atd(row));
        }

        // Compute gradient of objective at row
        for(int j = 0; j < _ncolA; j++) {
          a[j] = cs[j].atd(row);
          if(Double.isNaN(a[j])) continue;   // Skip missing observations in row

          // Inner product x_i * y_j
          double xy = 0;
          for(int k = 0; k < _ncolX; k++)
            xy += chk_xold(cs, k).atd(row) * _yt[j][k];

          // Sum over y_j weighted by gradient of loss \grad L_{i,j}(x_i * y_j, A_{i,j})
          double weight = _parms.lgrad(xy, (a[j] - _normSub[j]) * _normMul[j]);
          for(int i = 0; i < _ncolX; i++)
            grad[i] += weight * _yt[j][i];
        }

        // Update row x_i of working copy with new values
        for(int k = 0; k < _ncolX; k++) {
          double xold = chk_xold(cs, k).atd(row);   // Old value of x_i
          xnew[k] = _parms.rproxgrad(xold - _alpha * grad[k], _alpha);  // Proximal gradient
          chk_xnew(cs, k).set(row, xnew[k]);
          _xreg += _parms.regularize(xnew[k]);
        }

        // Compute loss function using new x_i
        for(int j = 0; j < _ncolA; j++) {
          if(Double.isNaN(a[j])) continue;   // Skip missing observations in row
          double xy = ArrayUtils.innerProduct(xnew, _yt[j]);
          _loss += _parms.loss(xy, a[j]);
        }
      }
    }

    @Override public void reduce(UpdateX other) {
      _loss += other._loss;
      _xreg += other._xreg;
    }
  }

  private class UpdateY extends MRTask<UpdateY> {
    // Input
    GLRMParameters _parms;
    double _alpha;      // Step size
    double[][] _ytold;  // Old Y matrix

    // Output
    double[][] _ytnew;  // New Y matrix
    double _yreg;    // Regularization evaluated on new Y

    public UpdateY(GLRMParameters parms, final double[][] yt, final double alpha) {
      assert yt != null && yt.length == _ncolA && yt[0].length == _ncolX;
      _parms = parms;
      _alpha = alpha;
      _ytold = yt;
      _yreg = 0;
      // _ytnew = new double[_ncolA][_ncolX];
    }

    @Override public void map(Chunk[] cs) {
      assert (_ncolA + 2*_ncolX) == cs.length;
      _ytnew = new double[_ncolA][_ncolX];

      for(int j = 0; j < _ncolA; j++) {
        // Compute gradient of objective at column
        for(int row = 0; row < cs[0]._len; row++) {
          double a = cs[j].atd(row);
          if(Double.isNaN(a)) continue;   // Skip missing observations in column

          // Inner product x_i * y_j
          double xy = 0;
          for(int k = 0; k < _ncolX; k++)
            xy += chk_xnew(cs,k).atd(row) * _ytold[j][k];

          // Sum over x_i weighted by gradient of loss \grad L_{i,j}(x_i * y_j, A_{i,j})
          double weight = _parms.lgrad(xy, (a - _normSub[j]) * _normMul[j]);
          for(int k = 0; k < _ncolX; k++)
            _ytnew[j][k] += weight * chk_xnew(cs,k).atd(row);
        }
      }
    }

    @Override public void reduce(UpdateY other) {
      ArrayUtils.add(_ytnew, other._ytnew);
    }

    @Override protected void postGlobal() {
      // Compute new y_j values using proximal gradient
      for(int j = 0; j < _ncolA; j++) {
        for(int k = 0; k < _ncolX; k++) {
          double u = _ytold[j][k] - _alpha * _ytnew[j][k];
          _ytnew[j][k] = _parms.rproxgrad(u, _alpha);
          _yreg += _parms.regularize(_ytnew[j][k]);
        }
      }
    }
  }

  private class ObjCalc extends MRTask<ObjCalc> {
    // Input
    GLRMParameters _parms;
    double[][] _yt;

    // Output
    double _loss;

    public ObjCalc(GLRMParameters parms, final double[][] yt) {
      assert yt != null && yt.length == _ncolA && yt[0].length == _ncolX;
      _parms = parms;
      _yt = yt;
      _loss = 0;
    }

    @Override public void map(Chunk[] cs) {
      assert (_ncolA + 2*_ncolX) == cs.length;

      for(int row = 0; row < cs[0]._len; row++) {
        for (int j = 0; j < _ncolA; j++) {
          double a = cs[j].atd(row);
          if (Double.isNaN(a)) continue;   // Skip missing observations in row

          // Inner product x_i * y_j
          double xy = 0;
          for (int k = 0; k < _ncolX; k++)
            xy += chk_xnew(cs, k).atd(row) * _yt[j][k];
          _loss += _parms.loss(xy, (a - _normSub[j]) * _normMul[j]);
        }
      }
    }
  }

  // Computes XY where X is n by k, Y is k by p, and k <= p
  //  Resulting matrix Z = XY will have dimensions n by k
  private static class BMulTask extends FrameTask<BMulTask> {
    double[][] _yt;   // _yt = Y' (transpose of Y)

    public BMulTask(Key jobKey, DataInfo dinfo, final double[][] yt) {
      super(jobKey, dinfo);
      _yt = yt;
    }

    @Override protected void processRow(long gid, DataInfo.Row row, NewChunk[] outputs) {
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
}
