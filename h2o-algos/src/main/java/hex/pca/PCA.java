package hex.pca;

import hex.DataInfo;

import hex.ModelBuilder;
import hex.ModelCategory;
import hex.schemas.ModelBuilderSchema;
import hex.schemas.PCAV3;

import hex.svd.SVD;
import hex.svd.SVDModel;
import water.*;
import water.fvec.Frame;
import water.util.Log;
import water.util.PrettyPrint;
import water.util.TwoDimTable;

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
  // Number of columns in training set (p)
  private transient int _ncolExp;    // With categoricals expanded into 0/1 indicator cols

  @Override
  public ModelBuilderSchema schema() {
    return new PCAV3();
  }

  @Override
  public Job<PCAModel> trainModel() {
    return start(new PCADriver(), 0);
  }

  @Override
  public ModelCategory[] can_build() {
    return new ModelCategory[]{ModelCategory.Clustering};
  }

  @Override public BuilderVisibility builderVisibility() { return BuilderVisibility.Experimental; };

  @Override
  protected void checkMemoryFootPrint() {
    HeartBeat hb = H2O.SELF._heartbeat;
    double p = _train.degreesOfFreedom();
    long mem_usage = (long)(hb._cpus_allowed * p*p * 8/*doubles*/ * Math.log((double)_train.lastVec().nChunks())/Math.log(2.)); //one gram per core
    long max_mem = hb.get_max_mem();
    if (mem_usage > max_mem) {
      String msg = "Gram matrices (one per thread) won't fit in the driver node's memory ("
              + PrettyPrint.bytes(mem_usage) + " > " + PrettyPrint.bytes(max_mem)
              + ") - try reducing the number of columns and/or the number of categorical factors.";
      error("_train", msg);
      cancel(msg);
    }
  }

  public enum Initialization {
    SVD, PlusPlus, User
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
    if (_parms._max_iterations < 1 || _parms._max_iterations > 1e6)
      error("_max_iterations", "max_iterations must be between 1 and 1e6 inclusive");

    if (_train == null) return;
    if (_train.numCols() < 2) error("_train", "_train must have more than one column");
    _ncolExp = _train.numColsExp(_parms._useAllFactorLevels, false);

    // TODO: Initialize _parms._k = min(ncolExp(_train), nrow(_train)) if not set
    int k_min = (int)Math.min(_ncolExp, _train.numRows());
    if (_parms._k < 1 || _parms._k > k_min) error("_k", "_k must be between 1 and " + k_min);

    if (expensive && error_count() == 0) checkMemoryFootPrint();
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

  class PCADriver extends H2O.H2OCountedCompleter<PCADriver> {

    protected void computeStatsFillModel(PCAModel pca, SVDModel svd) {
      // Eigenvectors are just the V matrix
      String[] colTypes = new String[_parms._k];
      String[] colFormats = new String[_parms._k];
      String[] colHeaders = new String[_parms._k];
      Arrays.fill(colTypes, "double");
      Arrays.fill(colFormats, "%5f");
      for (int i = 0; i < colHeaders.length; i++) colHeaders[i] = "PC" + String.valueOf(i + 1);
      pca._output._eigenvectors_raw = svd._output._v;
      pca._output._eigenvectors = new TwoDimTable("Rotation", null, _train.names(),
              colHeaders, colTypes, colFormats, "", new String[_train.numCols()][], svd._output._v);

      // Compute standard deviation
      double[] sdev = new double[svd._output._d.length];
      double[] vars = new double[svd._output._d.length];
      double totVar = 0;
      double dfcorr = 1.0 / Math.sqrt(_train.numRows() - 1.0);
      for (int i = 0; i < sdev.length; i++) {
        sdev[i] = dfcorr * svd._output._d[i];
        vars[i] = sdev[i] * sdev[i];
        totVar += vars[i];
      }
      pca._output._std_deviation = sdev;

      // Importance of principal components
      double[] prop_var = new double[vars.length];    // Proportion of total variance
      double[] cum_var = new double[vars.length];    // Cumulative proportion of total variance
      for (int i = 0; i < vars.length; i++) {
        prop_var[i] = vars[i] / totVar;
        cum_var[i] = i == 0 ? prop_var[0] : cum_var[i - 1] + prop_var[i];
      }
      pca._output._pc_importance = new TwoDimTable("Importance of components", null,
              new String[]{"Standard deviation", "Proportion of Variance", "Cumulative Proportion"},
              colHeaders, colTypes, colFormats, "", new String[3][], new double[][]{sdev, prop_var, cum_var});

      // Fill PCA model with additional info needed for scoring
      if(_parms._keep_loading) pca._output._loading_key = svd._output._u_key;
      pca._output._normSub = svd._output._normSub;
      pca._output._normMul = svd._output._normMul;
      pca._output._permutation = svd._output._permutation;
      pca._output._nnums = svd._output._nnums;
      pca._output._ncats = svd._output._ncats;
      pca._output._catOffsets = svd._output._catOffsets;
    }

    // Main worker thread
    @Override protected void compute2() {
      PCAModel model = null;
      DataInfo dinfo = null;
      DataInfo xinfo = null;
      Frame x = null;

      try {
        _parms.read_lock_frames(PCA.this); // Fetch & read-lock input frames
        init(true);
        if (error_count() > 0) throw new IllegalArgumentException("Found validation errors: " + validationErrors());

        // The model to be built
        model = new PCAModel(dest(), _parms, new PCAModel.PCAOutput(PCA.this));
        model.delete_and_lock(_key);

        SVDModel.SVDParameters parms = new SVDModel.SVDParameters();
        parms._train = _parms._train;
        parms._ignored_columns = _parms._ignored_columns;
        parms._ignore_const_cols = _parms._ignore_const_cols;
        parms._score_each_iteration = _parms._score_each_iteration;
        parms._useAllFactorLevels = _parms._useAllFactorLevels;
        parms._transform = _parms._transform;
        parms._nv = _parms._k;
        parms._max_iterations = _parms._max_iterations;
        parms._seed = _parms._seed;

        // Calculate standard deviation and projection as well
        parms._only_v = false;
        parms._u_key = _parms._loading_key;
        parms._keep_u = _parms._keep_loading;

        SVDModel svd = null;
        SVD job = null;
        try {
          job = new SVD(parms);
          svd = job.trainModel().get();
        } finally {
          if (job != null) job.remove();
          if (svd != null) svd.remove();
        }

        // Recover PCA results from SVD model
        computeStatsFillModel(model, svd);
        model.update(_key);
        update(1);

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
        _parms.read_unlock_frames(PCA.this);
        if (model != null) model.unlock(_key);
        if (dinfo != null) dinfo.remove();
        if (xinfo != null) xinfo.remove();
        if (x != null && !_parms._keep_loading) x.delete();
      }
      tryComplete();
    }

    Key self() {
      return _key;
    }
  }
}
