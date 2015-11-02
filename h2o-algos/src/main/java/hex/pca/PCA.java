package hex.pca;

import Jama.Matrix;
import Jama.SingularValueDecomposition;
import hex.DataInfo;

import hex.ModelBuilder;
import hex.ModelMetrics;
import hex.ModelCategory;
import hex.glrm.EmbeddedGLRM;
import hex.glrm.GLRM;
import hex.glrm.GLRMModel;
import hex.gram.Gram;
import hex.gram.Gram.GramTask;
import hex.schemas.ModelBuilderSchema;
import hex.schemas.PCAV3;

import hex.pca.PCAModel.PCAParameters;
import hex.svd.EmbeddedSVD;
import hex.svd.SVD;
import hex.svd.SVDModel;
import water.*;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.PrettyPrint;
import water.util.TwoDimTable;

import java.util.Arrays;

/**
 * Principal Components Analysis
 * It computes the principal components from the singular value decomposition using the power method.
 * <a href = "http://www.cs.yale.edu/homes/el327/datamining2013aFiles/07_singular_value_decomposition.pdf">SVD via Power Method Algorithm</a>
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

  @Override protected Job<PCAModel> trainModelImpl(long work, boolean restartTimer) {
    return start(new PCADriver(), work, restartTimer);
  }

  @Override
  public long progressUnits() {
    return _parms._pca_method == PCAParameters.Method.GramSVD ? 5 : 3;
  }

  @Override
  public ModelCategory[] can_build() {
    return new ModelCategory[]{ ModelCategory.Clustering };
  }

  @Override public BuilderVisibility builderVisibility() { return BuilderVisibility.Stable; };

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

  // Called from an http request
  public PCA(PCAParameters parms) {
    super("PCA", parms);
    init(false);
  }

  @Override
  public void init(boolean expensive) {
    super.init(expensive);
    if (_parms._max_iterations < 1 || _parms._max_iterations > 1e6)
      error("_max_iterations", "max_iterations must be between 1 and 1e6 inclusive");

    if (_train == null) return;
    _ncolExp = _train.numColsExp(_parms._use_all_factor_levels, false);
    // if (_ncolExp < 2) error("_train", "_train must have more than one column when categoricals are expanded");

    // TODO: Initialize _parms._k = min(ncolExp(_train), nrow(_train)) if not set
    int k_min = (int)Math.min(_ncolExp, _train.numRows());
    if (_parms._k < 1 || _parms._k > k_min) error("_k", "_k must be between 1 and " + k_min);
    if (!_parms._use_all_factor_levels && _parms._pca_method == PCAParameters.Method.GLRM)
      error("_use_all_factor_levels", "GLRM only implemented for _use_all_factor_levels = true");

    if (_parms._pca_method != PCAParameters.Method.GLRM && expensive && error_count() == 0) checkMemoryFootPrint();
  }

  class PCADriver extends H2O.H2OCountedCompleter<PCADriver> {
    protected PCADriver() { super(true); } // bump driver priority

    protected void buildTables(PCAModel pca, String[] rowNames) {
      // Eigenvectors are just the V matrix
      String[] colTypes = new String[_parms._k];
      String[] colFormats = new String[_parms._k];
      String[] colHeaders = new String[_parms._k];
      Arrays.fill(colTypes, "double");
      Arrays.fill(colFormats, "%5f");

      assert rowNames.length == pca._output._eigenvectors_raw.length;
      for (int i = 0; i < colHeaders.length; i++) colHeaders[i] = "PC" + String.valueOf(i + 1);
      pca._output._eigenvectors = new TwoDimTable("Rotation", null, rowNames, colHeaders, colTypes, colFormats, "",
              new String[pca._output._eigenvectors_raw.length][], pca._output._eigenvectors_raw);

      double[] vars = new double[pca._output._std_deviation.length];
      for (int i = 0; i < vars.length; i++)
        vars[i] = pca._output._std_deviation[i] * pca._output._std_deviation[i];

      // Importance of principal components
      double[] prop_var = new double[vars.length];    // Proportion of total variance
      double[] cum_var = new double[vars.length];    // Cumulative proportion of total variance
      for (int i = 0; i < vars.length; i++) {
        prop_var[i] = vars[i] / pca._output._total_variance;
        cum_var[i] = i == 0 ? prop_var[0] : cum_var[i-1] + prop_var[i];
      }
      pca._output._importance = new TwoDimTable("Importance of components", null,
              new String[]{"Standard deviation", "Proportion of Variance", "Cumulative Proportion"},
              colHeaders, colTypes, colFormats, "", new String[3][], new double[][]{pca._output._std_deviation, prop_var, cum_var});
      pca._output._model_summary = pca._output._importance;
    }

    protected void computeStatsFillModel(PCAModel pca, SVDModel svd) {
      // Fill PCA model with additional info needed for scoring
      pca._output._normSub = svd._output._normSub;
      pca._output._normMul = svd._output._normMul;
      pca._output._permutation = svd._output._permutation;
      pca._output._nnums = svd._output._nnums;
      pca._output._ncats = svd._output._ncats;
      pca._output._catOffsets = svd._output._catOffsets;
      pca._output._nobs = svd._output._nobs;

      // Fill model with eigenvectors and standard deviations
      pca._output._std_deviation = ArrayUtils.mult(svd._output._d, 1.0 / Math.sqrt(svd._output._nobs - 1.0));
      pca._output._eigenvectors_raw = svd._output._v;
      pca._output._total_variance = svd._output._total_variance;
      buildTables(pca, svd._output._names_expanded);
    }

    protected void computeStatsFillModel(PCAModel pca, GLRMModel glrm) {
      assert glrm._parms._recover_svd;

      // Fill model with additional info needed for scoring
      pca._output._normSub = glrm._output._normSub;
      pca._output._normMul = glrm._output._normMul;
      pca._output._permutation = glrm._output._permutation;
      pca._output._nnums = glrm._output._nnums;
      pca._output._ncats = glrm._output._ncats;
      pca._output._catOffsets = glrm._output._catOffsets;
      pca._output._objective = glrm._output._objective;

      // Fill model with eigenvectors and standard deviations
      double dfcorr = 1.0 / Math.sqrt(_train.numRows() - 1.0);
      pca._output._std_deviation = new double[_parms._k];
      pca._output._eigenvectors_raw = glrm._output._eigenvectors_raw;
      pca._output._total_variance = 0;
      for(int i = 0; i < glrm._output._singular_vals.length; i++) {
        pca._output._std_deviation[i] = dfcorr * glrm._output._singular_vals[i];
        pca._output._total_variance += pca._output._std_deviation[i] * pca._output._std_deviation[i];
      }
      buildTables(pca, glrm._output._names_expanded);
    }

    protected void computeStatsFillModel(PCAModel pca, DataInfo dinfo, SingularValueDecomposition svd, Gram gram, long nobs) {
      // Save adapted frame info for scoring later
      pca._output._normSub = dinfo._normSub == null ? new double[dinfo._nums] : dinfo._normSub;
      if(dinfo._normMul == null) {
        pca._output._normMul = new double[dinfo._nums];
        Arrays.fill(pca._output._normMul, 1.0);
      } else
        pca._output._normMul = dinfo._normMul;
      pca._output._permutation = dinfo._permutation;
      pca._output._nnums = dinfo._nums;
      pca._output._ncats = dinfo._cats;
      pca._output._catOffsets = dinfo._catOffsets;

      double dfcorr = nobs / (nobs - 1.0);
      double[] sval = svd.getSingularValues();
      pca._output._std_deviation = new double[_parms._k];    // Only want first k standard deviations
      for(int i = 0; i < _parms._k; i++) {
        sval[i] = dfcorr * sval[i];   // Degrees of freedom = n-1, where n = nobs = # row observations processed
        pca._output._std_deviation[i] = Math.sqrt(sval[i]);
      }

      double[][] eigvec = svd.getV().getArray();
      pca._output._eigenvectors_raw = new double[eigvec.length][_parms._k];   // Only want first k eigenvectors
      for(int i = 0; i < eigvec.length; i++)
        System.arraycopy(eigvec[i], 0, pca._output._eigenvectors_raw[i], 0, _parms._k);
      pca._output._total_variance = dfcorr * gram.diagSum();  // Since gram = X'X/n, but variance requires n-1 in denominator
      buildTables(pca, dinfo.coefNames());
    }

    // Main worker thread
    @Override protected void compute2() {
      PCAModel model = null;
      DataInfo dinfo = null;

      try {
        init(true);   // Initialize parameters
        _parms.read_lock_frames(PCA.this); // Fetch & read-lock input frames
        if (error_count() > 0) throw new IllegalArgumentException("Found validation errors: " + validationErrors());

        // The model to be built
        model = new PCAModel(dest(), _parms, new PCAModel.PCAOutput(PCA.this));
        model.delete_and_lock(_key);

        if(_parms._pca_method == PCAParameters.Method.GramSVD) {
          dinfo = new DataInfo(Key.make(), _train, _valid, 0, _parms._use_all_factor_levels, _parms._transform, DataInfo.TransformType.NONE, /* skipMissing */ !_parms._impute_missing, /* imputeMissing */ _parms._impute_missing, /* missingBucket */ false, /* weights */ false, /* offset */ false, /* fold */ false, /* intercept */ false);
          DKV.put(dinfo._key, dinfo);
          
          // Calculate and save Gram matrix of training data
          // NOTE: Gram computes A'A/n where n = nrow(A) = number of rows in training set (excluding rows with NAs)
          update(1, "Begin distributed calculation of Gram matrix");
          GramTask gtsk = new GramTask(self(), dinfo).doAll(dinfo._adaptedFrame);
          Gram gram = gtsk._gram;   // TODO: This ends up with all NaNs if training data has too many missing values
          assert gram.fullN() == _ncolExp;
          model._output._nobs = gtsk._nobs;

          // Compute SVD of Gram A'A/n using JAMA library
          // Note: Singular values ordered in weakly descending order by algorithm
          update(1, "Calculating SVD of Gram matrix locally");
          Matrix gramJ = new Matrix(gtsk._gram.getXX());
          SingularValueDecomposition svdJ = gramJ.svd();
          update(1, "Computing stats from SVD");
          computeStatsFillModel(model, dinfo, svdJ, gram, gtsk._nobs);

        } else if(_parms._pca_method == PCAParameters.Method.Power || _parms._pca_method == PCAParameters.Method.Randomized) {
          SVDModel.SVDParameters parms = new SVDModel.SVDParameters();
          parms._train = _parms._train;
          parms._valid = _parms._valid;
          parms._ignored_columns = _parms._ignored_columns;
          parms._ignore_const_cols = _parms._ignore_const_cols;
          parms._score_each_iteration = _parms._score_each_iteration;
          parms._use_all_factor_levels = _parms._use_all_factor_levels;
          parms._transform = _parms._transform;
          parms._nv = _parms._k;
          parms._max_iterations = _parms._max_iterations;
          parms._seed = _parms._seed;

          // Set method for computing SVD accordingly
          if(_parms._pca_method == PCAParameters.Method.Power)
            parms._svd_method = SVDModel.SVDParameters.Method.Power;
          else if(_parms._pca_method == PCAParameters.Method.Randomized)
            parms._svd_method = SVDModel.SVDParameters.Method.Randomized;

          // Calculate standard deviation, but not projection
          parms._only_v = false;
          parms._keep_u = false;
          parms._save_v_frame = false;

          SVDModel svd = null;
          SVD job = null;
          try {
            job = new EmbeddedSVD(_key, _progressKey, parms);
            svd = job.trainModel().get();
            if (job.isCancelledOrCrashed())
              PCA.this.cancel();
          } finally {
            if (job != null) job.remove();
            if (svd != null) svd.remove();
          }
          // Recover PCA results from SVD model
          update(1, "Computing stats from SVD");
          computeStatsFillModel(model, svd);

        } else if(_parms._pca_method == PCAParameters.Method.GLRM) {
          GLRMModel.GLRMParameters parms = new GLRMModel.GLRMParameters();
          parms._train = _parms._train;
          parms._valid = _parms._valid;
          parms._ignored_columns = _parms._ignored_columns;
          parms._ignore_const_cols = _parms._ignore_const_cols;
          parms._score_each_iteration = _parms._score_each_iteration;
          parms._transform = _parms._transform;
          parms._k = _parms._k;
          parms._max_iterations = _parms._max_iterations;
          parms._seed = _parms._seed;
          parms._recover_svd = true;

          parms._loss = GLRMModel.GLRMParameters.Loss.Quadratic;
          parms._gamma_x = parms._gamma_y = 0;
          parms._regularization_x = GLRMModel.GLRMParameters.Regularizer.None;
          parms._regularization_y = GLRMModel.GLRMParameters.Regularizer.None;
          parms._init = GLRM.Initialization.PlusPlus;

          GLRMModel glrm = null;
          GLRM job = null;
          try {
            job = new EmbeddedGLRM(_key, _progressKey, parms);
            glrm = job.trainModel().get();
            if (job.isCancelledOrCrashed())
              PCA.this.cancel();
          } finally {
            if (job != null) job.remove();
            if (glrm != null) {
              // glrm._parms._representation_key.get().delete();
              glrm._output._representation_key.get().delete();
              glrm.remove();
            }
          }
          // Recover PCA results from GLRM model
          update(1, "Computing stats from GLRM decomposition");
          computeStatsFillModel(model, glrm);
        }
        update(1, "Scoring and computing metrics on training data");
        if (_parms._compute_metrics) {
          model.score(_parms.train()).delete(); // This scores on the training data and appends a ModelMetrics
          ModelMetrics mm = ModelMetrics.getFromDKV(model,_parms.train());
          model._output._training_metrics = mm;
        }

        // At the end: validation scoring (no need to gather scoring history)
        update(1, "Scoring and computing metrics on validation data");
        if (_valid != null) {
          model.score(_parms.valid()).delete(); //this appends a ModelMetrics on the validation set
          model._output._validation_metrics = ModelMetrics.getFromDKV(model,_parms.valid());
        }
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
        updateModelOutput();
        _parms.read_unlock_frames(PCA.this);
        if (model != null) model.unlock(_key);
        if (dinfo != null) dinfo.remove();
      }
      tryComplete();
    }

    Key self() {
      return _key;
    }
  }
}
