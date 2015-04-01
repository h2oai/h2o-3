package hex.glrm;

import hex.DataInfo;
import hex.Model;
import hex.ModelBuilder;
import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel;
import hex.schemas.GLRMV2;
import hex.glrm.GLRMModel.GLRMParameters;
import hex.schemas.ModelBuilderSchema;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.Arrays;

/**
 * Generalized Low Rank Models
 * <a href = "http://web.stanford.edu/~boyd/papers/pdf/glrm.pdf">Generalized Low Rank Models</a>
 * @author anqi_fu
 */
public class GLRM extends ModelBuilder<GLRMModel,GLRMModel.GLRMParameters,GLRMModel.GLRMOutput> {
  // Convergence tolerance
  final private double TOLERANCE = 1e-8;

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
  public GLRM(GLRMModel.GLRMParameters parms) {
    super("GLRM", parms);
    init(false);
  }

  @Override public void init(boolean expensive) {
    super.init(expensive);
    if (_parms._loading_key == null) _parms._loading_key = Key.make("GLRMLoading_" + Key.rand());
    if (_parms._gamma < 0) error("_gamma", "lambda must be a non-negative number");

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
    }

    // Currently, SVD initialization is unimplemented
    if (_parms._init == Initialization.SVD) throw H2O.unimpl();

    // Currently, does not work on categorical data
    Vec[] vecs = _train.vecs();
    for (int i = 0; i < vecs.length; i++) {
      if (!vecs[i].isNumeric()) throw H2O.unimpl();
    }
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
    // Initialize Y to be the k centers from k-means++
    public double[][] initialY() {
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

    // Stopping criteria
    private boolean isDone(GLRMModel model) {
      if (!isRunning()) return true; // Stopped/cancelled
      // Stopped for running out of iterations
      if (model._output._iterations > _parms._max_iterations) return true;

      // Stopped when average decrease in objective per iteration < TOLERANCE
      if( model._output._avg_change_obj < TOLERANCE ) return true;
      return false;             // Not stopping
    }

    @Override protected void compute2() {
      GLRMModel model = null;
      DataInfo dinfo = null;
      Frame x = null;

      try {
        _parms.read_lock_frames(GLRM.this); // Fetch & read-lock input frames
        init(true);
        if (error_count() > 0) throw new IllegalArgumentException("Found validation errors: " + validationErrors());

        // The model to be built
        model = new GLRMModel(dest(), _parms, new GLRMModel.GLRMOutput(GLRM.this));
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

        // Output standardization vectors for use in scoring later
        model._output._normSub = dinfo._normSub == null ? new double[_train.numCols()] : Arrays.copyOf(dinfo._normSub, _train.numCols());
        if(dinfo._normMul == null) {
          model._output._normMul = new double[_train.numCols()];
          Arrays.fill(model._output._normMul, 1.0);
        } else
          model._output._normMul = Arrays.copyOf(dinfo._normMul, _train.numCols());

        // Save X frame for user reference later
        x = new Frame(_parms._loading_key, null, xvecs);
        DKV.put(x._key, x);

        // 0) Initialize Y matrix using k-means++
        double nobs = _train.numRows() * _train.numCols();
        double[][] yt = ArrayUtils.transpose(initialY());
        model._output._iterations = 0;
        model._output._avg_change_obj = 2 * TOLERANCE;    // Run at least 1 iteration

        while (!isDone(model)) {
          double step = 1/(model._output._iterations+1);  // Step size \alpha_k = 1/(iters + 1)

          // 1) Update X matrix given fixed Y
          UpdateX xtsk = new UpdateX(dinfo, _parms, yt, _train.numCols(), step);
          xtsk.doAll(dinfo._adaptedFrame);

          // 2) Update Y matrix given fixed X
          UpdateY ytsk = new UpdateY(dinfo, _parms, yt, _train.numCols(), step);
          yt = ytsk.doAll(dinfo._adaptedFrame)._ytnew;

          // TODO: Compute average change in objective function each iteration
          model._output._avg_change_obj /= nobs;
          model._output._iterations++;
          model.update(_key); // Update model in K/V store
          update(1);          // One unit of work
        }

        // 4) Save solution to model output
        model._output._archetypes = yt;
        model._output._parameters = _parms;

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
        // if (x != null && !_parms._keep_loading) x.delete();
        _parms.read_unlock_frames(GLRM.this);
      }
      tryComplete();
    }

    Key self() {
      return _key;
    }
  }

  private static class UpdateX extends MRTask<UpdateX> {
    double[] _normSub;  // For standardizing A only
    double[] _normMul;
    int _ncolA, _ncolX;
    GLRMParameters _parms;
    double _alpha;
    double[][] _yt;

    UpdateX(DataInfo dinfo, GLRMParameters parms, final double[][] yt, final int ncolA, final double alpha) {
      assert yt != null && yt.length == ncolA && yt[0].length == parms._k;
      _normSub = dinfo._normSub == null ? MemoryManager.malloc8d(ncolA) : dinfo._normSub;
      if(dinfo._normMul == null) {
        _normMul = MemoryManager.malloc8d(ncolA);
        Arrays.fill(_normMul, 1.0);
      } else _normMul = dinfo._normMul;
      _ncolA = ncolA; _ncolX = parms._k;
      _parms = parms;
      _alpha = alpha;
      _yt = yt;
    }

    // In chunk, first _ncolA cols are A, next _ncolX cols are X
    @Override public void map(Chunk[] cs) {
      assert (_ncolX + _ncolA) == cs.length;
      double[] grad = new double[_ncolX];

      for(int row = 0; row < cs[0]._len; row++) {
        // Compute gradient of objective at row
        for(int j = 0; j < _ncolA; j++) {
          double a = cs[j].atd(row);
          if(Double.isNaN(a)) continue;   // Skip missing observations in row

          // Inner product x_i * y_j
          int idx = 0;
          double xy = 0;
          for(int k = _ncolA; k < cs.length; k++)
            xy += cs[k].atd(row) * _yt[j][idx++];
          assert idx == _ncolX;

          // Sum over y_j weighted by gradient of loss \grad L_{i,j}(x_i * y_j, A_{i,j})
          double weight = _parms.lgrad(xy, (a - _normSub[j]) * _normMul[j]);
          for(int i = 0; i < _ncolX; i++)
            grad[i] += weight * _yt[j][i];
        }

        // Update row x_i in place with new values
        int idx = 0;
        for(int k = _ncolA; k < cs.length; k++) {
          double xold = cs[k].atd(row);   // Old value of x_i
          double prox = _parms.rproxgrad(xold - _alpha * grad[idx], _alpha);  // Proximal gradient
          cs[k].set(row, prox);
          idx++;
        }
        assert idx == grad.length;
      }
    }
  }

  private static class UpdateY extends MRTask<UpdateY> {
    double[] _normSub;  // For standardizing A only
    double[] _normMul;
    int _ncolA, _ncolX;
    GLRMParameters _parms;
    double _alpha;      // Step size this iteration
    double[][] _ytold;  // Old Y matrix

    double[][] _ytnew;  // New Y matrix

    UpdateY(DataInfo dinfo, GLRMParameters parms, final double[][] yt, final int ncolA, final double alpha) {
      assert yt != null && yt.length == ncolA && yt[0].length == parms._k;
      _normSub = dinfo._normSub == null ? MemoryManager.malloc8d(ncolA) : dinfo._normSub;
      if(dinfo._normMul == null) {
        _normMul = MemoryManager.malloc8d(ncolA);
        Arrays.fill(_normMul, 1.0);
      } else _normMul = dinfo._normMul;
      _ncolA = ncolA; _ncolX = parms._k;
      _parms = parms;
      _alpha = alpha;
      _ytold = yt;

      _ytnew = new double[_ncolA][_ncolX];
    }

    // In chunk, first _ncolA cols are A, next _ncolX cols are X
    @Override public void map(Chunk[] cs) {
      assert (_ncolX + _ncolA) == cs.length;

      for(int j = 0; j < _ncolA; j++) {
        // Compute gradient of objective at column
        for(int row = 0; row < cs[0]._len; row++) {
          double a = cs[j].atd(row);
          if (Double.isNaN(a)) continue;   // Skip missing observations in column

          // Inner product x_i * y_j
          int idx = 0;
          double xy = 0;
          for (int k = _ncolA; k < cs.length; k++)
            xy += cs[k].atd(row) * _ytold[j][idx++];
          assert idx == _ncolX;

          // Sum over x_i weighted by gradient of loss \grad L_{i,j}(x_i * y_j, A_{i,j})
          idx = 0;
          double weight = _parms.lgrad(xy, (a - _normSub[j]) * _normMul[j]);
          for(int k = _ncolA; k < cs.length; k++)
            _ytnew[j][idx++] += weight * cs[k].atd(row);
        }
      }
    }

    @Override public void reduce(UpdateY other) { ArrayUtils.add(_ytnew, other._ytnew); }

    @Override protected void postGlobal() {
      // Compute new y_j values using proximal gradient
      for(int j = 0; j < _ncolA; j++) {
        for(int k = 0; k < _ncolX; k++) {
          double u = _ytold[j][k] - _alpha * _ytnew[j][k];
          _ytnew[j][k] = _parms.rproxgrad(u, _alpha);
        }
      }
    }
  }
}
