package hex.pca;

import Jama.Matrix;
import Jama.SingularValueDecomposition;
import hex.DataInfo;
import hex.ModelBuilder;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.genmodel.algos.glrm.GlrmInitialization;
import hex.genmodel.algos.glrm.GlrmLoss;
import hex.genmodel.algos.glrm.GlrmRegularizer;
import hex.glrm.GLRM;
import hex.glrm.GLRMModel;
import hex.gram.Gram;
import hex.gram.Gram.GramTask;
import hex.gram.Gram.OuterGramTask;
import hex.pca.PCAModel.PCAParameters;
import hex.svd.SVD;
import hex.svd.SVDModel;
import hex.util.LinearAlgebraUtils.SMulTask;
import water.DKV;
import water.H2O;
import water.HeartBeat;
import water.Job;
import water.fvec.Frame;
import water.rapids.Rapids;
import water.util.PrettyPrint;
import water.util.TwoDimTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;

import static hex.util.DimensionReductionUtils.createScoringHistoryTableDR;
import static hex.util.DimensionReductionUtils.generateIPC;
import static water.util.ArrayUtils.*;

/**
 * Principal Components Analysis
 * It computes the principal components from the singular value decomposition using the power method.
 * <a href = "http://www.cs.yale.edu/homes/el327/datamining2013aFiles/07_singular_value_decomposition.pdf">SVD via Power Method Algorithm</a>
 * @author anqi_fu
 *
 */
public class PCA extends ModelBuilder<PCAModel,PCAModel.PCAParameters,PCAModel.PCAOutput> {
  // Number of columns in training set (p)
  private transient int _ncolExp;       // With categoricals expanded into 0/1 indicator cols
  boolean _wideDataset = false;         // default with wideDataset set to be false.
  @Override protected PCADriver trainModelImpl() { return new PCADriver(); }
  @Override public ModelCategory[] can_build() { return new ModelCategory[]{ ModelCategory.Clustering }; }

  @Override public boolean havePojo() { return true; }
  @Override public boolean haveMojo() { return false; }

  @Override protected void checkMemoryFootPrint() {
    HeartBeat hb = H2O.SELF._heartbeat; // todo: Add to H2O object memory information so we don't have to use heartbeat.
 //   int numCPUs= H2O.NUMCPUS;   // proper way to get number of CPUs.
    double p = hex.util.LinearAlgebraUtils.numColsExp(_train,true);
    double r = _train.numRows();
    long mem_usage =
            _parms._pca_method == PCAParameters.Method.GramSVD ? (long)(hb._cpus_allowed * p*p * 8/*doubles*/ *
                    Math.log((double)_train.lastVec().nChunks())/Math.log(2.)) : 1; //one gram per core
    long mem_usage_w = _parms._pca_method == PCAParameters.Method.GramSVD ? (long)(hb._cpus_allowed * r*r *
            8/*doubles*/ * Math.log((double)_train.lastVec().nChunks())/Math.log(2.)) : 1;
    long max_mem = hb.get_free_mem();
    if ((mem_usage > max_mem) && (mem_usage_w > max_mem))  {
      String msg = "Gram matrices (one per thread) won't fit in the driver node's memory ("
              + PrettyPrint.bytes(mem_usage) + " > " + PrettyPrint.bytes(max_mem)
              + ") - try reducing the number of columns and/or the number of categorical factors.";
      error("_train", msg);
    }
    if (mem_usage > max_mem) {
      _wideDataset = true;   // set to true if wide dataset is detected
    }
  }

  /*
    Set value of wideDataset.  Note that this routine is used for test purposes only and is not intended
    for users but more for developers for setting.
 */
  public void setWideDataset(boolean isWide) {
    _wideDataset = isWide;
  }

  // Called from an http request
  public PCA(PCAParameters parms) { super(parms); init(false); }
  public PCA(boolean startup_once) { super(new PCAParameters(),startup_once); }

  @Override
  public void init(boolean expensive) {
    super.init(expensive);
    if (_parms._max_iterations < 1 || _parms._max_iterations > 1e6) {
      error("_max_iterations", "max_iterations must be between 1 and 1e6 inclusive");
    }

    if (_train == null) {
      return;
    }
    _ncolExp = hex.util.LinearAlgebraUtils.numColsExp(_train,_parms._use_all_factor_levels);
    // if (_ncolExp < 2) error("_train", "_train must have more than one column when categoricals are expanded");

    // TODO: Initialize _parms._k = min(ncolExp(_train), nrow(_train)) if not set
    int k_min = (int)Math.min(_ncolExp, _train.numRows());
    if (_parms._k < 1 || _parms._k > k_min) {
      error("_k", "_k must be between 1 and " + k_min);
    }
    if (!_parms._use_all_factor_levels && _parms._pca_method == PCAParameters.Method.GLRM) {
      error("_use_all_factor_levels", "GLRM only implemented for _use_all_factor_levels = true");
    }

    if (_parms._pca_method != PCAParameters.Method.GLRM && expensive && error_count() == 0) {
      checkMemoryFootPrint();
    }
  }

  class PCADriver extends Driver {

    protected void buildTables(PCAModel pca, String[] rowNames) {
      // Eigenvectors are just the V matrix
      String[] colTypes = new String[_parms._k];
      String[] colFormats = new String[_parms._k];
      String[] colHeaders = new String[_parms._k];
      Arrays.fill(colTypes, "double");
      Arrays.fill(colFormats, "%5f");

      assert rowNames.length == pca._output._eigenvectors_raw.length;
      for (int i = 0; i < colHeaders.length; i++) {
        colHeaders[i] = "PC" + String.valueOf(i + 1);
      }
      pca._output._eigenvectors = new TwoDimTable("Rotation", null, rowNames, colHeaders,
              colTypes, colFormats, "",
              new String[pca._output._eigenvectors_raw.length][], pca._output._eigenvectors_raw);

      // Importance of principal components
      double[] vars = new double[pca._output._std_deviation.length];
      double[] prop_var = new double[pca._output._std_deviation.length];    // Proportion of total variance
      double[] cum_var = new double[pca._output._std_deviation.length];    // Cumulative proportion of total variance
      generateIPC(pca._output._std_deviation, pca._output._total_variance, vars, prop_var, cum_var);
      pca._output._importance = new TwoDimTable("Importance of components", null,
              new String[]{"Standard deviation", "Proportion of Variance", "Cumulative Proportion"},
              colHeaders, colTypes, colFormats, "", new String[3][],
              new double[][]{pca._output._std_deviation, prop_var, cum_var});
      pca._output._model_summary = pca._output._importance;
    }

    protected void computeStatsFillModel(PCAModel pca, SVDModel svd, Gram gram) {
      // Fill PCA model with additional info needed for scoring
      pca._output._normSub = svd._output._normSub;
      pca._output._normMul = svd._output._normMul;
      pca._output._permutation = svd._output._permutation;
      pca._output._nnums = svd._output._nnums;
      pca._output._ncats = svd._output._ncats;
      pca._output._catOffsets = svd._output._catOffsets;
      pca._output._nobs = svd._output._nobs;

      // Fill model with eigenvectors and standard deviations
      pca._output._std_deviation = mult(svd._output._d, 1.0 / Math.sqrt(svd._output._nobs - 1.0));
      pca._output._eigenvectors_raw = svd._output._v;
      // Since gram = X'X/n, but variance requires n-1 in denominator
      pca._output._total_variance = gram.diagSum()*pca._output._nobs/(pca._output._nobs-1.0);
      buildTables(pca, svd._output._names_expanded);
    }

    protected void computeStatsFillModel(PCAModel pca, GLRMModel glrm, Gram gram) {
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
      for(int i = 0; i < glrm._output._singular_vals.length; i++) {
        pca._output._std_deviation[i] = dfcorr * glrm._output._singular_vals[i];
      }
      pca._output._nobs = _train.numRows();
      // Since gram = X'X/n, but variance requires n-1 in denominator
      pca._output._total_variance = gram.diagSum()*pca._output._nobs/(pca._output._nobs-1.0);
      buildTables(pca, glrm._output._names_expanded);
    }

    protected void computeStatsFillModel(PCAModel pca, DataInfo dinfo, double[] sval,
                                         double[][] eigvec, Gram gram, long nobs) {
      // Save adapted frame info for scoring later
      pca._output._normSub = dinfo._normSub == null ? new double[dinfo._nums] : dinfo._normSub;
      if(dinfo._normMul == null) {
        pca._output._normMul = new double[dinfo._nums];
        Arrays.fill(pca._output._normMul, 1.0);
      } else {
        pca._output._normMul = dinfo._normMul;
      }
      pca._output._permutation = dinfo._permutation;
      pca._output._nnums = dinfo._nums;
      pca._output._ncats = dinfo._cats;
      pca._output._catOffsets = dinfo._catOffsets;

      double dfcorr = nobs / (nobs - 1.0);
      pca._output._std_deviation = new double[_parms._k];    // Only want first k standard deviations
      for(int i = 0; i < _parms._k; i++) {
        sval[i] = dfcorr * sval[i];   // Degrees of freedom = n-1, where n = nobs = # row observations processed
        pca._output._std_deviation[i] = Math.sqrt(sval[i]);
      }

      pca._output._eigenvectors_raw = new double[eigvec.length][_parms._k];   // Only want first k eigenvectors
      for(int i = 0; i < eigvec.length; i++) {
        System.arraycopy(eigvec[i], 0, pca._output._eigenvectors_raw[i], 0, _parms._k);
      }
      pca._output._total_variance = dfcorr * gram.diagSum();  // Since gram = X'X/n, but variance requires n-1 in denominator
      buildTables(pca, dinfo.coefNames());
    }

    protected void computeStatsFillModel(PCAModel pca, DataInfo dinfo, SingularValueDecomposition svd, Gram gram,
                                         long nobs) {
      computeStatsFillModel(pca, dinfo, svd.getSingularValues(), svd.getV().getArray(), gram, nobs);
    }

    // Main worker thread
    @Override
    public void computeImpl() {
      PCAModel model = null;
      DataInfo dinfo = null, tinfo = null;
      DataInfo AE = null;
      Gram gram = null;

      try {
        init(true);   // Initialize parameters
        if (error_count() > 0) {
          throw new IllegalArgumentException("Found validation errors: " + validationErrors());
        }

        // The model to be built
        model = new PCAModel(dest(), _parms, new PCAModel.PCAOutput(PCA.this));
        model.delete_and_lock(_job);

        // store (possibly) rebalanced input train to pass it to nested SVD job
        Frame tranRebalanced = new Frame(_train);

        if (!_parms._impute_missing) {    // added warning to user per request from Nidhi
          _job.warn("_train: Dataset used may contain fewer number of rows due to removal of rows with " +
                  "NA/missing values.  If this is not desirable, set impute_missing argument in pca call to " +
                  "TRUE/True/true/... depending on the client language.");
        }

        if (_wideDataset && (!_parms._impute_missing) && tranRebalanced.hasNAs()) { // remove NAs rows
          tinfo = new DataInfo(_train, _valid, 0, _parms._use_all_factor_levels, _parms._transform,
                  DataInfo.TransformType.NONE, /* skipMissing */ !_parms._impute_missing, /* imputeMissing */
                  _parms._impute_missing, /* missingBucket */ false, /* weights */ false,
                    /* offset */ false, /* fold */ false, /* intercept */ false);
          DKV.put(tinfo._key, tinfo);

          DKV.put(tranRebalanced._key, tranRebalanced);
          _train = Rapids.exec(String.format("(na.omit %s)", tranRebalanced._key)).getFrame(); // remove NA rows
          DKV.remove(tranRebalanced._key);
        }

        dinfo = new DataInfo(_train, _valid, 0, _parms._use_all_factor_levels, _parms._transform,
                DataInfo.TransformType.NONE, /* skipMissing */ !_parms._impute_missing, /* imputeMissing */
                _parms._impute_missing, /* missingBucket */ false, /* weights */ false,
                  /* offset */ false, /* fold */ false, /* intercept */ false);
        DKV.put(dinfo._key, dinfo);

        if(_parms._pca_method == PCAParameters.Method.GramSVD) {
          // Calculate and save Gram matrix of training data
          // NOTE: Gram computes A'A/n where n = nrow(A) = number of rows in training set (excluding rows with NAs)
          _job.update(1, "Begin distributed calculation of Gram matrix");
          OuterGramTask ogtsk = null;
          GramTask gtsk = null;

          if (_wideDataset) {
            if (!_parms._impute_missing && tranRebalanced.hasNAs()) {
              // fixed the std and mean of dinfo to that of the frame before removing NA rows
              dinfo._normMul = tinfo._normMul;
              dinfo._numMeans = tinfo._numMeans;
              dinfo._normSub = tinfo._normSub;
            }
            ogtsk = new OuterGramTask(_job._key, dinfo).doAll(dinfo._adaptedFrame);

            gram = ogtsk._gram;
            model._output._nobs = ogtsk._nobs;
          } else {
            gtsk = new GramTask(_job._key, dinfo).doAll(dinfo._adaptedFrame);
            gram = gtsk._gram;   // TODO: This ends up with all NaNs if training data has too many missing values
            assert gram.fullN() == _ncolExp;
            model._output._nobs = gtsk._nobs;
          }

          // Cannot calculate SVD if all rows contain missing value(s) and hence were skipped
          // and if the user specify k to be higher than min(number of columns, number of rows)
          if((model._output._nobs == 0) || (model._output._nobs < _parms._k )) {
            error("_train", "Number of row in _train is less than k. " +
                    "Consider setting impute_missing = TRUE or using pca_method = 'GLRM' instead or reducing the " +
                    "value of parameter k.");
          }
          if (error_count() > 0) {
            throw new IllegalArgumentException("Found validation errors: " + validationErrors());
          }

          // Compute SVD of Gram A'A/n using JAMA library
          // Note: Singular values ordered in weakly descending order by algorithm
          _job.update(1, "Calculating SVD of Gram matrix locally");
          Matrix gramJ = _wideDataset ? new Matrix(ogtsk._gram.getXX()) : new Matrix(gtsk._gram.getXX());
          SingularValueDecomposition svdJ = gramJ.svd();
          _job.update(1, "Computing stats from SVD");
          // correct for the eigenvector by t(A)*eigenvector for wide dataset
          if (_wideDataset) {
            // squeeze dataset A and eigenVector matirx U into one frame, tempFrame and use SMulTask to
            // perform the multiplication of Transpose(A) * U
            Frame tempFrame = new Frame(dinfo._adaptedFrame);
            Frame eigFrame = new water.util.ArrayUtils().frame(svdJ.getV().getArray());
            tempFrame.add(eigFrame);

            SMulTask stsk = new SMulTask(dinfo, svdJ.getV().getArray().length,
                    dinfo._numOffsets[dinfo._numOffsets.length-1]);
            double[][] eigenVecs = stsk.doAll(tempFrame)._atq;

            if (eigFrame != null) { // delete frame to prevent leak keys.
              eigFrame.delete();
            }

            // need to normalize eigenvectors after multiplication by transpose(A) so that they have unit norm
            double[][] eigenVecsTranspose = transpose(eigenVecs);
            double[] eigenNormsI = new double[eigenVecsTranspose.length];
            for (int vecIndex = 0; vecIndex < eigenVecsTranspose.length; vecIndex++) {
              eigenNormsI[vecIndex] = 1.0/l2norm(eigenVecsTranspose[vecIndex]);
            }
            eigenVecs = transpose(mult(eigenVecsTranspose, eigenNormsI));
            computeStatsFillModel(model, dinfo, svdJ.getSingularValues(), eigenVecs, gram, model._output._nobs);
          } else {
            computeStatsFillModel(model, dinfo, svdJ, gram, model._output._nobs);
          }
          model._output._training_time_ms.add(System.currentTimeMillis());

          // generate variables for scoring_history generation
          LinkedHashMap<String, ArrayList> scoreTable = new LinkedHashMap<String, ArrayList>();
          scoreTable.put("Timestamp", model._output._training_time_ms);
          model._output._scoring_history = createScoringHistoryTableDR(scoreTable, "Scoring History for GramSVD",
                  _job.start_time());
        //  model._output._scoring_history.tableHeader = "Scoring history from GLRM";

        } else if(_parms._pca_method == PCAParameters.Method.Power ||
                _parms._pca_method == PCAParameters.Method.Randomized) {
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
          if(_parms._pca_method == PCAParameters.Method.Power) {
            parms._svd_method = SVDModel.SVDParameters.Method.Power;
          } else if(_parms._pca_method == PCAParameters.Method.Randomized) {
            parms._svd_method = SVDModel.SVDParameters.Method.Randomized;
          }

          // Calculate standard deviation, but not projection
          parms._only_v = false;
          parms._keep_u = false;
          parms._save_v_frame = false;

          // Build an SVD model
          SVDModel svd = new SVD(parms, _job).trainModelNested(tranRebalanced);
          if (stop_requested()) {
            return;
          }
          svd.remove(); // Remove from DKV

          // Recover PCA results from SVD model
          _job.update(1, "Computing stats from SVD");
          GramTask gtsk = new GramTask(_job._key, dinfo).doAll(dinfo._adaptedFrame);
          gram = gtsk._gram;   // TODO: This ends up with all NaNs if training data has too many missing values
          computeStatsFillModel(model, svd, gram);
          model._output._scoring_history = svd._output._scoring_history;
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

          parms._loss = GlrmLoss.Quadratic;
          parms._gamma_x = parms._gamma_y = 0;
          parms._regularization_x = GlrmRegularizer.None;
          parms._regularization_y = GlrmRegularizer.None;
          parms._init = GlrmInitialization.SVD; // changed from PlusPlus to SVD.  Seems to give better result

          // Build an SVD model
          // Hack: we have to resort to unsafe type casts because _job is of Job<PCAModel> type, whereas a GLRM
          // model requires a Job<GLRMModel> _job. If anyone knows how to avoid this hack, please fix it!
          GLRMModel glrm = new GLRM(parms, (Job)_job).trainModelNested(tranRebalanced);
          if (stop_requested()) {
            return;
          }
          glrm._output._representation_key.get().delete();
          glrm.remove(); // Remove from DKV

          // Recover PCA results from GLRM model
          _job.update(1, "Computing stats from GLRM decomposition");
          GramTask gtsk = new GramTask(_job._key, dinfo).doAll(dinfo._adaptedFrame);
          gram = gtsk._gram;   // TODO: This ends up with all NaNs if training data has too many missing values
          computeStatsFillModel(model, glrm, gram);
          model._output._scoring_history = glrm._output._scoring_history;
          model._output._scoring_history.setTableHeader("Scoring history from GLRM");
        }
        _job.update(1, "Scoring and computing metrics on training data");
        if (_parms._compute_metrics) {
          model.score(_parms.train()).delete(); // This scores on the training data and appends a ModelMetrics
          ModelMetrics mm = ModelMetrics.getFromDKV(model,_parms.train());
          model._output._training_metrics = mm;
        }

        // At the end: validation scoring (no need to gather scoring history)
        _job.update(1, "Scoring and computing metrics on validation data");
        if (_valid != null) {
          model.score(_parms.valid()).delete(); //this appends a ModelMetrics on the validation set
          model._output._validation_metrics = ModelMetrics.getFromDKV(model,_parms.valid());
        }
        model.update(_job);


      } finally {
        if (model != null) {
          model.unlock(_job);
        }
        if (dinfo != null) {
          dinfo.remove();
        }
        if (tinfo != null) {
          tinfo.remove();
        }
        if (AE != null) {
          AE.remove();
        }
      }
    }
  }
}
