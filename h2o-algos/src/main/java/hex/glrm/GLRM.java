package hex.glrm;

import hex.DataInfo;
import hex.Model;
import hex.ModelBuilder;
import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel;
import hex.schemas.GLRMV2;
import hex.schemas.ModelBuilderSchema;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;

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
      DataInfo xinfo = null;
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
        dinfo = new DataInfo(Key.make(), fr, null, 0, false, DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true);
        DKV.put(dinfo._key, dinfo);

        // 0) Initialize Y matrix using k-means++
        double nobs = _train.numRows() * _train.numCols();
        double[][] yt = ArrayUtils.transpose(initialY());
        model._output._iterations = 0;
        model._output._avg_change_obj = 2 * TOLERANCE;    // Run at least 1 iteration

        while (!isDone(model)) {
          // 1) Update X matrix given fixed Y

          // 2) Update Y matrix given fixed X

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
        if (xinfo != null) xinfo.remove();
        _parms.read_unlock_frames(GLRM.this);
      }
      tryComplete();
    }

    Key self() {
      return _key;
    }
  }

  private static class UpdateX extends MRTask<UpdateX> {
    int _ncolA;
    int _ncolX;
    double _alpha;
    double[][] _yt;

    UpdateX(DataInfo dinfo, final double[][] yt, final int ncolA, final int ncolX, final double alpha) {
      assert yt != null && yt.length == ncolA && yt[0].length == ncolX;
      _ncolA = ncolA;
      _ncolX = ncolX;
      _alpha = alpha;
      _yt = yt;
    }

    // In chunk, first _ncolA cols are A, next _ncolX cols are X
    @Override public void map(Chunk[] cs) {
      assert (_ncolX + _ncolA) == cs.length;
      double[] grad = new double[_ncolX];

      for(int row = 0; row < cs[0]._len; row++) {
        // Compute gradient of objective at row
        for(int d = 0; d < _ncolA; d++) {
          double a = cs[d].atd(row);
          if(Double.isNaN(a)) continue;
          // Sum over \grad L(x_i*y_j, a)*y_j
        }

        // Update row x_i in place with new values
        int i = 0;
        for(int d = _ncolA; d < cs.length; d++) {
          double xold = cs[d].atd(row);
          cs[d].set(row, xold - _alpha * grad[i]);
          i++;
        }
        assert i == grad.length;
      }
    }
  }
}
