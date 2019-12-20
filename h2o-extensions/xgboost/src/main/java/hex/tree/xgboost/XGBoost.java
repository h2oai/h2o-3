package hex.tree.xgboost;

import hex.*;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLM;
import hex.glm.GLMTask;
import hex.tree.PlattScalingHelper;
import hex.tree.SharedTree;
import hex.tree.TreeUtils;
import hex.tree.xgboost.rabit.RabitTrackerH2O;
import hex.tree.xgboost.util.FeatureScore;
import hex.util.CheckpointUtils;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import water.*;
import ml.dmlc.xgboost4j.java.*;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.*;
import water.util.Timer;

import java.io.*;
import java.util.*;

import static hex.tree.SharedTree.createModelSummaryTable;
import static hex.tree.SharedTree.createScoringHistoryTable;
import static water.H2O.technote;

/** Gradient Boosted Trees
 *
 *  Based on "Elements of Statistical Learning, Second Edition, page 387"
 */
public class XGBoost extends ModelBuilder<XGBoostModel,XGBoostModel.XGBoostParameters,XGBoostOutput> 
    implements PlattScalingHelper.ModelBuilderWithCalibration<XGBoostModel, XGBoostModel.XGBoostParameters, XGBoostOutput> {

  private static final double FILL_RATIO_THRESHOLD = 0.25D;

  @Override public boolean haveMojo() { return true; }
  @Override public boolean havePojo() { return true; }

  @Override public BuilderVisibility builderVisibility() {
    if(ExtensionManager.getInstance().isCoreExtensionsEnabled(XGBoostExtension.NAME)){
      return BuilderVisibility.Stable;
    } else {
      return BuilderVisibility.Experimental;
    }
  }

  @Override public ModelCategory[] can_build() {
    return new ModelCategory[]{
            ModelCategory.Regression,
            ModelCategory.Binomial,
            ModelCategory.Multinomial,
    };
  }

  // Called from an http request
  public XGBoost(XGBoostModel.XGBoostParameters parms                   ) { super(parms     ); init(false); }
  public XGBoost(XGBoostModel.XGBoostParameters parms, Key<XGBoostModel> key) { super(parms, key); init(false); }
  public XGBoost(boolean startup_once) { super(new XGBoostModel.XGBoostParameters(),startup_once); }
  public boolean isSupervised(){return true;}

  // Number of trees requested, including prior trees from a checkpoint
  private int _ntrees;
  
  // Back-end used for the build
  private XGBoostModel.XGBoostParameters.Backend _backend;

  // Calibration frame for Platt scaling
  private transient Frame _calib;

  @Override protected int nModelsInParallel(int folds) {
    if (_backend == XGBoostModel.XGBoostParameters.Backend.gpu) {
      return 1;
    } else {
      return nModelsInParallel(folds, 2);
    }
  }

  /** Start the XGBoost training Job on an F/J thread. */
  @Override protected XGBoostDriver trainModelImpl() {
    return new XGBoostDriver();
  }

  /** Initialize the ModelBuilder, validating all arguments and preparing the
   *  training frame.  This call is expected to be overridden in the subclasses
   *  and each subclass will start with "super.init();".  This call is made
   *  by the front-end whenever the GUI is clicked, and needs to be fast;
   *  heavy-weight prep needs to wait for the trainModel() call.
   *
   *  Validate the learning rate and distribution family. */
  @Override public void init(boolean expensive) {
    super.init(expensive);
    if (H2O.CLOUD.size() > 1) {
      if(H2O.SELF.getSecurityManager().securityEnabled) {
        throw new H2OIllegalArgumentException("Cannot run XGBoost on an SSL enabled cluster larger than 1 node. XGBoost does not support SSL encryption.");
      }
    }
    if (expensive) {
      if (_response.naCnt() > 0) {
        error("_response_column", "Response contains missing values (NAs) - not supported by XGBoost.");
      }
      if(!new XGBoostExtensionCheck().doAllNodes().enabled) {
        error("XGBoost", "XGBoost is not available on all nodes!");
      }
    }
    if (H2O.CLOUD.members().length == 0) {
      // during rest-api registration we do not care about the actual back-end
      _backend = XGBoostModel.XGBoostParameters.Backend.cpu;
    } else {
      _backend = XGBoostModel.getActualBackend(_parms);
    }

    if (_parms.hasCheckpoint()) {  // Asking to continue from checkpoint?
      Value cv = DKV.get(_parms._checkpoint);
      if (cv != null) { // Look for prior model
        XGBoostModel checkpointModel = CheckpointUtils.getAndValidateCheckpointModel(this, XGBoostModel.XGBoostParameters.CHECKPOINT_NON_MODIFIABLE_FIELDS, cv);
        // Compute number of trees to build for this checkpoint
        _ntrees = _parms._ntrees - checkpointModel._output._ntrees; // Needed trees
      }
    } else {
      _ntrees = _parms._ntrees;
    }

    // Initialize response based on given distribution family.
    // Regression: initially predict the response mean
    // Binomial: just class 0 (class 1 in the exact inverse prediction)
    // Multinomial: Class distribution which is not a single value.

    // However there is this weird tension on the initial value for
    // classification: If you guess 0's (no class is favored over another),
    // then with your first GBM tree you'll typically move towards the correct
    // answer a little bit (assuming you have decent predictors) - and
    // immediately the Confusion Matrix shows good results which gradually
    // improve... BUT the Means Squared Error will suck for unbalanced sets,
    // even as the CM is good.  That's because we want the predictions for the
    // common class to be large and positive, and the rare class to be negative
    // and instead they start around 0.  Guessing initial zero's means the MSE
    // is so bad, that the R^2 metric is typically negative (usually it's
    // between 0 and 1).

    // If instead you guess the mean (reversed through the loss function), then
    // the zero-tree XGBoost model reports an MSE equal to the response variance -
    // and an initial R^2 of zero.  More trees gradually improves the R^2 as
    // expected.  However, all the minority classes have large guesses in the
    // wrong direction, and it takes a long time (lotsa trees) to correct that
    // - so your CM sucks for a long time.
    if (expensive) {
      if (error_count() > 0)
        throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(XGBoost.this);
    }

    if ( _parms._backend == XGBoostModel.XGBoostParameters.Backend.gpu) {
      if (! hasGPU(_parms._gpu_id))
        error("_backend", "GPU backend (gpu_id: " + _parms._gpu_id + ") is not functional. Check CUDA_PATH and/or GPU installation.");

      if (H2O.getCloudSize() > 1)
        error("_backend", "GPU backend is not supported in distributed mode.");

      Map<String, Object> incompats = _parms.gpuIncompatibleParams();
      if (! incompats.isEmpty())
        for (Map.Entry<String, Object> incompat : incompats.entrySet())
          error("_backend", "GPU backend is not available for parameter setting '" + incompat.getKey() + " = " + incompat.getValue() + "'. Use CPU backend instead.");
    }

    if (_parms._distribution == DistributionFamily.quasibinomial)
      error("_distribution", "Quasibinomial is not supported for XGBoost in current H2O.");

    switch( _parms._distribution) {
      case bernoulli:
        if( _nclass != 2 /*&& !couldBeBool(_response)*/)
          error("_distribution", technote(2, "Binomial requires the response to be a 2-class categorical"));
        break;
      case modified_huber:
        if( _nclass != 2 /*&& !couldBeBool(_response)*/)
          error("_distribution", technote(2, "Modified Huber requires the response to be a 2-class categorical."));
        break;
      case multinomial:
        if (!isClassifier()) error("_distribution", technote(2, "Multinomial requires an categorical response."));
        break;
      case huber:
        if (isClassifier()) error("_distribution", technote(2, "Huber requires the response to be numeric."));
        break;
      case poisson:
        if (isClassifier()) error("_distribution", technote(2, "Poisson requires the response to be numeric."));
        break;
      case gamma:
        if (isClassifier()) error("_distribution", technote(2, "Gamma requires the response to be numeric."));
        break;
      case tweedie:
        if (isClassifier()) error("_distribution", technote(2, "Tweedie requires the response to be numeric."));
        break;
      case gaussian:
        if (isClassifier()) error("_distribution", technote(2, "Gaussian requires the response to be numeric."));
        break;
      case laplace:
        if (isClassifier()) error("_distribution", technote(2, "Laplace requires the response to be numeric."));
        break;
      case quantile:
        if (isClassifier()) error("_distribution", technote(2, "Quantile requires the response to be numeric."));
        break;
      case AUTO:
        break;
      default:
        error("_distribution","Invalid distribution: " + _parms._distribution);
    }

    if( !(0. < _parms._learn_rate && _parms._learn_rate <= 1.0) )
      error("_learn_rate", "learn_rate must be between 0 and 1");
    if( !(0. < _parms._col_sample_rate && _parms._col_sample_rate <= 1.0) )
      error("_col_sample_rate", "col_sample_rate must be between 0 and 1");
    if (_parms._grow_policy== XGBoostModel.XGBoostParameters.GrowPolicy.lossguide && _parms._tree_method!= XGBoostModel.XGBoostParameters.TreeMethod.hist)
      error("_grow_policy", "must use tree_method=hist for grow_policy=lossguide");

    if ((_train != null) && (_parms._monotone_constraints != null)) {
      TreeUtils.checkMonotoneConstraints(this, _train, _parms._monotone_constraints);
    }

    PlattScalingHelper.initCalibration(this, _parms, expensive);
  }

  @Override
  public XGBoost getModelBuilder() {
    return this;
  }

  @Override
  public Frame getCalibrationFrame() {
    return _calib;
  }

  @Override
  public void setCalibrationFrame(Frame f) {
    _calib = f;
  }

  static DataInfo makeDataInfo(Frame train, Frame valid, XGBoostModel.XGBoostParameters parms, int nClasses) {
    DataInfo dinfo = new DataInfo(
            train,
            valid,
            1, //nResponses
            true, //all factor levels
            DataInfo.TransformType.NONE, //do not standardize
            DataInfo.TransformType.NONE, //do not standardize response
            false, //whether to skip missing
            false, // do not replace NAs in numeric cols with mean
            true,  // always add a bucket for missing values
            parms._weights_column != null, // observation weights
            parms._offset_column != null,
            parms._fold_column != null
    );
    // Checks and adjustments:
    // 1) observation weights (adjust mean/sigmas for predictors and response)
    // 2) NAs (check that there's enough rows left)
    GLMTask.YMUTask ymt = new GLMTask.YMUTask(dinfo, nClasses,nClasses == 1, false, true, true).doAll(dinfo._adaptedFrame);
    if (parms._weights_column != null && parms._offset_column != null) {
      Log.warn("Combination of offset and weights can lead to slight differences because Rollupstats aren't weighted - need to re-calculate weighted mean/sigma of the response including offset terms.");
    }
    if (parms._weights_column != null && parms._offset_column == null) {
      dinfo.updateWeightedSigmaAndMean(ymt.predictorSDs(), ymt.predictorMeans());
      if (nClasses == 1)
        dinfo.updateWeightedSigmaAndMeanForResponse(ymt.responseSDs(), ymt.responseMeans());
    }
    dinfo.coefNames(); // cache the coefficient names
    assert dinfo._coefNames != null;
    return dinfo;
  }

  // ----------------------
  class XGBoostDriver extends Driver {

    // Per driver instance
    final private String featureMapFileName = "featureMap" + UUID.randomUUID().toString() + ".txt";
    // Shared file to write list of features
    private String featureMapFileAbsolutePath = null;

    @Override
    public void computeImpl() {
      init(true); //this can change the seed if it was set to -1
      // Something goes wrong
      if (error_count() > 0)
        throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(XGBoost.this);
      buildModel();
    }

    final void buildModel() {
      if ((XGBoostModel.XGBoostParameters.Backend.auto.equals(_parms._backend) || XGBoostModel.XGBoostParameters.Backend.gpu.equals(_parms._backend)) &&
              hasGPU(_parms._gpu_id) && H2O.getCloudSize() == 1 && _parms.gpuIncompatibleParams().isEmpty()) {
        synchronized (XGBoostGPULock.lock(_parms._gpu_id)) {
          buildModelImpl();
        }
      } else {
        buildModelImpl();
      }
    }

    final void buildModelImpl() {
      final XGBoostModel model;
      if (_parms.hasCheckpoint()) {
        XGBoostModel checkpoint = DKV.get(_parms._checkpoint).<XGBoostModel>get().deepClone(_result);
        checkpoint._parms = _parms;
        model = checkpoint.delete_and_lock(_job);
      } else {
        model = new XGBoostModel(_result, _parms, new XGBoostOutput(XGBoost.this), _train, _valid);
        model.write_lock(_job);
      }

      if (_parms._dmatrix_type == XGBoostModel.XGBoostParameters.DMatrixType.sparse) {
        model._output._sparse = true;
      } else if (_parms._dmatrix_type == XGBoostModel.XGBoostParameters.DMatrixType.dense) {
        model._output._sparse = false;
      } else {
        model._output._sparse = isTrainDatasetSparse();
      }

      XGBoostSetupTask setupTask = null;
      try {
        XGBoostSetupTask.FrameNodes trainFrameNodes = XGBoostSetupTask.findFrameNodes(_train);

        // Prepare Rabit tracker for this job
        // This cannot be H2O.getCloudSize() as a frame might not be distributed on all the nodes
        // In such a case we'll perform training only on a subset of nodes while XGBoost/Rabit would keep waiting
        // for H2O.getCloudSize() number of requests/responses.
        final RabitTrackerH2O rt = new RabitTrackerH2O(trainFrameNodes.getNumNodes());
        startRabitTracker(rt);

        // Create a "feature map" and store in a temporary file (for Variable Importance, MOJO, ...)
        DataInfo dataInfo = model.model_info().dataInfo();
        assert dataInfo != null;
        String featureMap = XGBoostUtils.makeFeatureMap(_train, dataInfo);
        model.model_info().setFeatureMap(featureMap);
        featureMapFileAbsolutePath = createFeatureMapFile(featureMap);

        BoosterParms boosterParms = XGBoostModel.createParams(_parms, model._output.nclasses(), dataInfo.coefNames());
        model._output._native_parameters = boosterParms.toTwoDimTable();

        byte[] checkpointBytes = null;
        if (_parms.hasCheckpoint()) {
          checkpointBytes = model.model_info()._boosterBytes;
        }
        setupTask = new XGBoostSetupTask(model, _parms, boosterParms, checkpointBytes, getWorkerEnvs(rt), trainFrameNodes).run();
        try {
          // initial iteration
          XGBoostUpdateTask nullModelTask = new XGBoostUpdateTask(setupTask, 0).run();
          BoosterProvider boosterProvider = new BoosterProvider(model.model_info(), nullModelTask);

          // train the model
          scoreAndBuildTrees(setupTask, boosterProvider, model);

          // shutdown rabit & XGB native resources
          XGBoostCleanupTask.cleanUp(setupTask);
          setupTask = null;

          waitOnRabitWorkers(rt);
        } finally {
          stopRabitTracker(rt);
        }
      } catch (XGBoostError xgBoostError) {
        xgBoostError.printStackTrace();
        throw new RuntimeException("XGBoost failure", xgBoostError);
      } finally {
        if (setupTask != null) {
          try {
            XGBoostCleanupTask.cleanUp(setupTask);
          } catch (Exception e) {
            Log.err("XGBoost clean-up failed - this could leak memory!", e);
          }
        }
        // Unlock & save results
        model.unlock(_job);
      }
    }

    /**
     * @return True if train dataset is sparse, otherwise false.
     */
    private boolean isTrainDatasetSparse() {
      long nonZeroCount = 0;
      int nonCategoricalColumns = 0;
      long oneHotEncodedColumns = 0;
      for (int i = 0; i < _train.numCols(); ++i) {
        if (_train.name(i).equals(_parms._response_column)) continue;
        if (_train.name(i).equals(_parms._weights_column)) continue;
        if (_train.name(i).equals(_parms._fold_column)) continue;
        if (_train.name(i).equals(_parms._offset_column)) continue;
        final Vec vector = _train.vec(i);
        if (vector.isCategorical()) {
          nonZeroCount += _train.numRows();
        } else {
          nonZeroCount += vector.nzCnt();
        }
        if (vector.isCategorical()) {
          oneHotEncodedColumns += vector.cardinality();
        } else {
          nonCategoricalColumns++;
        }
      }
      final long totalColumns = oneHotEncodedColumns + nonCategoricalColumns;
      final double denominator = (double) totalColumns * _train.numRows();
      final double fillRatio = (double) nonZeroCount / denominator;
      Log.info("fill ratio: " + fillRatio);

      return fillRatio < FILL_RATIO_THRESHOLD
          || ((_train.numRows() * totalColumns) > Integer.MAX_VALUE);
    }

    // For feature importances - write out column info
    private String createFeatureMapFile(String featureMap) {
      OutputStream os = null;
      try {
        File tmpModelDir = java.nio.file.Files.createTempDirectory("xgboost-model-" + _result.toString()).toFile();
        File fmFile = new File(tmpModelDir, featureMapFileName);
        os = new FileOutputStream(fmFile);
        os.write(featureMap.getBytes());
        os.close();
        return fmFile.getAbsolutePath();
      } catch (IOException e) {
        throw new RuntimeException("Cannot generate feature map file " + featureMapFileName, e);
      } finally {
        FileUtils.close(os);
      }
    }

    private void scoreAndBuildTrees(final XGBoostSetupTask setupTask, final BoosterProvider boosterProvider,
                                    final XGBoostModel model) throws XGBoostError {
      for( int tid=0; tid< _ntrees; tid++) {
        // During first iteration model contains 0 trees, then 1-tree, ...
        boolean scored = doScoring(model, boosterProvider, false);
        if (scored && ScoreKeeper.stopEarly(model._output.scoreKeepers(), _parms._stopping_rounds, ScoreKeeper.ProblemType.forSupervised(_nclass > 1), _parms._stopping_metric, _parms._stopping_tolerance, "model's last", true)) {
          Log.info("Early stopping triggered - stopping XGBoost training");
          break;
        }

        Timer kb_timer = new Timer();
        XGBoostUpdateTask t = new XGBoostUpdateTask(setupTask, tid).run();
        boosterProvider.reset(t);
        Log.info((tid + 1) + ". tree was built in " + kb_timer.toString());
        _job.update(1);

        model._output._ntrees++;
        model._output._scored_train = ArrayUtils.copyAndFillOf(model._output._scored_train, model._output._ntrees+1, new ScoreKeeper());
        model._output._scored_valid = model._output._scored_valid != null ? ArrayUtils.copyAndFillOf(model._output._scored_valid, model._output._ntrees+1, new ScoreKeeper()) : null;
        model._output._training_time_ms = ArrayUtils.copyAndFillOf(model._output._training_time_ms, model._output._ntrees+1, System.currentTimeMillis());
        if (stop_requested() && !timeout()) throw new Job.JobCancelledException();
        if (timeout()) {
          Log.info("Stopping XGBoost training because of timeout");
          break;
        }
      }
      _job.update(0, "Scoring the final model");
      // Final scoring
      doScoring(model, boosterProvider, true);
      // Finish remaining work (if stopped early)
      _job.update(_parms._ntrees-model._output._ntrees);
    }

    // Don't start the tracker for 1 node clouds -> the GPU plugin fails in such a case
    private void startRabitTracker(RabitTrackerH2O rt) {
      if (H2O.CLOUD.size() > 1) {
        rt.start(0);
      }
    }

    // RT should not be started for 1 node clouds
    private void waitOnRabitWorkers(RabitTrackerH2O rt) {
      if(H2O.CLOUD.size() > 1) {
        rt.waitFor(0);
      }
    }

    /**
     *
     * @param rt Rabit tracker to stop
     */
    private void stopRabitTracker(RabitTrackerH2O rt) {
      if(H2O.CLOUD.size() > 1) {
        rt.stop();
      }
    }

    // XGBoost seems to manipulate its frames in case of a 1 node distributed version in a way the GPU plugin can't handle
    // Therefore don't use RabitTracker envs for 1 node
    private Map<String, String> getWorkerEnvs(RabitTrackerH2O rt) {
      if(H2O.CLOUD.size() > 1) {
        return rt.getWorkerEnvs();
      } else {
        return new HashMap<>();
      }
    }

    long _firstScore = 0;
    long _timeLastScoreStart = 0;
    long _timeLastScoreEnd = 0;

    private boolean doScoring(XGBoostModel model,
                              BoosterProvider boosterProvider,
                              boolean finalScoring) throws XGBoostError {
      boolean scored = false;
      long now = System.currentTimeMillis();
      if (_firstScore == 0) _firstScore = now;
      long sinceLastScore = now - _timeLastScoreStart;
      _job.update(0, "Built " + model._output._ntrees + " trees so far (out of " + _parms._ntrees + ").");

      boolean timeToScore = (now - _firstScore < _parms._initial_score_interval) || // Score every time for 4 secs
              // Throttle scoring to keep the cost sane; limit to a 10% duty cycle & every 4 secs
              (sinceLastScore > _parms._score_interval && // Limit scoring updates to every 4sec
                      (double) (_timeLastScoreEnd - _timeLastScoreStart) / sinceLastScore < 0.1); //10% duty cycle

      boolean manualInterval = _parms._score_tree_interval > 0 && model._output._ntrees % _parms._score_tree_interval == 0;

      // Now model already contains tid-trees in serialized form
      if (_parms._score_each_iteration || finalScoring || // always score under these circumstances
              (timeToScore && _parms._score_tree_interval == 0) || // use time-based duty-cycle heuristic only if the user didn't specify _score_tree_interval
              manualInterval) {
        _timeLastScoreStart = now;
        boosterProvider.updateBooster(); // retrieve booster, expensive!
        model.doScoring(_train, _parms.train(), _valid, _parms.valid());
        _timeLastScoreEnd = System.currentTimeMillis();
        XGBoostOutput out = model._output;
        final Map<String, FeatureScore> varimp;
        Booster booster = null;
        try {
          booster = model.model_info().deserializeBooster();
          varimp = BoosterHelper.doWithLocalRabit(new BoosterHelper.BoosterOp<Map<String, FeatureScore>>() {
            @Override
            public Map<String, FeatureScore> apply(Booster booster) throws XGBoostError {
              final String[] modelDump = booster.getModelDump(featureMapFileAbsolutePath, true);
              return XGBoostUtils.parseFeatureScores(modelDump);
            }
          }, booster);
        } finally {
          if (booster != null)
            BoosterHelper.dispose(booster);
        }
        out._varimp = computeVarImp(varimp);
        out._model_summary = createModelSummaryTable(out._ntrees, null);
        out._scoring_history = createScoringHistoryTable(out, model._output._scored_train, out._scored_valid, _job, out._training_time_ms, _parms._custom_metric_func != null, false);
        if (out._varimp != null) {
          out._variable_importances = createVarImpTable(null, ArrayUtils.toDouble(out._varimp._varimp), out._varimp._names);
          out._variable_importances_cover = createVarImpTable("Cover", ArrayUtils.toDouble(out._varimp._covers), out._varimp._names);
          out._variable_importances_frequency = createVarImpTable("Frequency", ArrayUtils.toDouble(out._varimp._freqs), out._varimp._names);
        }
        model.update(_job);
        Log.info(model);
        scored = true;
      }

      // Model Calibration (only for the final model, not CV models)
      if (finalScoring && _parms.calibrateModel() && (!_parms._is_cv_model)) {
        model._output._calib_model = PlattScalingHelper.buildCalibrationModel(XGBoost.this, _parms, _job, model);
        model.update(_job);
      }

      return scored;
    }
  }

  private static TwoDimTable createVarImpTable(String name, double[] rel_imp, String[] coef_names) {
    return hex.ModelMetrics.calcVarImp(rel_imp, coef_names, "Variable Importances" + (name != null ? " - " + name : ""),
            new String[]{"Relative Importance", "Scaled Importance", "Percentage"});
  }

  private static XgbVarImp computeVarImp(Map<String, FeatureScore> varimp) {
    if (varimp.isEmpty())
      return null;
    float[] gains = new float[varimp.size()];
    float[] covers = new float[varimp.size()];
    int[] freqs = new int[varimp.size()];
    String[] names = new String[varimp.size()];
    int j = 0;
    for (Map.Entry<String, FeatureScore> it : varimp.entrySet()) {
      gains[j] = it.getValue()._gain;
      covers[j] = it.getValue()._cover;
      freqs[j] = it.getValue()._frequency;
      names[j] = it.getKey();
      j++;
    }
    return new XgbVarImp(names, gains, covers, freqs);
  }

  private static final class BoosterProvider {
    XGBoostModelInfo _modelInfo;
    XGBoostUpdateTask _updateTask;

    BoosterProvider(XGBoostModelInfo modelInfo, XGBoostUpdateTask updateTask) {
      _modelInfo = modelInfo;
      _updateTask = updateTask;
      _modelInfo.setBoosterBytes(_updateTask.getBoosterBytes());
    }

    final void reset(XGBoostUpdateTask updateTask) {
      _updateTask = updateTask;
    }

    final void updateBooster() {
      if (_updateTask == null) {
        throw new IllegalStateException("Booster can be retrieved only once!");
      }
      final byte[] boosterBytes = _updateTask.getBoosterBytes();
      _modelInfo.setBoosterBytes(boosterBytes);
    }
  }

  private static volatile boolean DEFAULT_GPU_BLACKLISTED = false;
  private static Set<Integer> GPUS = new HashSet<>();

  static boolean hasGPU(H2ONode node, int gpu_id) {
    final boolean hasGPU;
    if (H2O.SELF.equals(node)) {
      hasGPU = hasGPU(gpu_id);
    } else {
      HasGPUTask t = new HasGPUTask(gpu_id);
      new RPC<>(node, t).call().get();
      hasGPU = t._hasGPU;
    }
    Log.debug("Availability of GPU (id=" + gpu_id + ") on node " + node + ": " + hasGPU);
    return hasGPU;
  }

  private static class HasGPUTask extends DTask<HasGPUTask> {
    private final int _gpu_id;
    // OUT
    private boolean _hasGPU;

    private HasGPUTask(int gpu_id) { _gpu_id = gpu_id; }

    @Override
    public void compute2() {
      _hasGPU = hasGPU(_gpu_id);
      tryComplete();
    }
  }

  private static boolean hasGPU(int gpu_id) {
    if (gpu_id == 0 && DEFAULT_GPU_BLACKLISTED) // quick default path & no synchronization - if we already know we don't have the default GPU, let's not to find out again
      return false;
    boolean hasGPU = hasGPU_impl(gpu_id);
    if (gpu_id == 0 && !hasGPU) {
      DEFAULT_GPU_BLACKLISTED = true; // this can never change back
    }
    return hasGPU;
  }

  // helper
  private static synchronized boolean hasGPU_impl(int gpu_id) {
    if (! XGBoostExtension.isGpuSupportEnabled()) {
      return false;
    }

    if(GPUS.contains(gpu_id)) {
      return true;
    }

    DMatrix trainMat;
    try {
      trainMat = new DMatrix(new float[]{1,2,1,2},2,2);
      trainMat.setLabel(new float[]{1,0});
    } catch (XGBoostError xgBoostError) {
      throw new IllegalStateException("Couldn't prepare training matrix for XGBoost.", xgBoostError);
    }

    HashMap<String, Object> params = new HashMap<>();
    params.put("updater", "grow_gpu_hist");
    params.put("silent", 1);

    params.put("gpu_id", gpu_id);
    HashMap<String, DMatrix> watches = new HashMap<>();
    watches.put("train", trainMat);
    try {
      Map<String, String> localRabitEnv = new HashMap<>();
      Rabit.init(localRabitEnv);
      ml.dmlc.xgboost4j.java.XGBoost.train(trainMat, params, 1, watches, null, null);
      GPUS.add(gpu_id);
      return true;
    } catch (XGBoostError xgBoostError) {
      return false;
    } finally {
      try {
        Rabit.shutdown();
      } catch (XGBoostError e) {
        Log.warn("Cannot shutdown XGBoost Rabit for current thread.");
      }
    }
  }

  @Override public void cv_computeAndSetOptimalParameters(ModelBuilder<XGBoostModel,XGBoostModel.XGBoostParameters,XGBoostOutput>[] cvModelBuilders) {
    if( _parms._stopping_rounds == 0 && _parms._max_runtime_secs == 0) return; // No exciting changes to stopping conditions
    // Extract stopping conditions from each CV model, and compute the best stopping answer
    _parms._stopping_rounds = 0;
    _parms._max_runtime_secs = 0;
    int sum = 0;
    for (ModelBuilder mb : cvModelBuilders)
      sum += ((XGBoostOutput) DKV.<Model>getGet(mb.dest())._output)._ntrees;
    _parms._ntrees = (int)((double)sum/cvModelBuilders.length);
    warn("_ntrees", "Setting optimal _ntrees to " + _parms._ntrees + " for cross-validation main model based on early stopping of cross-validation models.");
    warn("_stopping_rounds", "Disabling convergence-based early stopping for cross-validation main model.");
    warn("_max_runtime_secs", "Disabling maximum allowed runtime for cross-validation main model.");
  }

}
