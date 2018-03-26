package hex.tree.xgboost;

import hex.DataInfo;
import hex.ModelBuilder;
import hex.ModelCategory;
import hex.ScoreKeeper;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLMTask;
import hex.tree.xgboost.rabit.RabitTrackerH2O;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

import static hex.tree.SharedTree.createModelSummaryTable;
import static hex.tree.SharedTree.createScoringHistoryTable;
import static water.H2O.technote;

/** Gradient Boosted Trees
 *
 *  Based on "Elements of Statistical Learning, Second Edition, page 387"
 */
public class XGBoost extends ModelBuilder<XGBoostModel,XGBoostModel.XGBoostParameters,XGBoostOutput> {
  @Override public boolean haveMojo() { return true; }

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

  @Override protected int nModelsInParallel() {
    // TODO should this be only 2?!
    return 2;
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
      if (hasOffsetCol()) {
        error("_offset_column", "Offset is not supported for XGBoost.");
      }
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
  }

  static DataInfo makeDataInfo(Frame train, Frame valid, XGBoostModel.XGBoostParameters parms, int nClasses) {
    DataInfo dinfo = new DataInfo(
            train,
            valid,
            1, //nResponses
            true, //all factor levels
            DataInfo.TransformType.NONE, //do not standardize
            DataInfo.TransformType.NONE, //do not standardize response
            parms._missing_values_handling == XGBoostModel.XGBoostParameters.MissingValuesHandling.Skip, //whether to skip missing
            false, // do not replace NAs in numeric cols with mean
            true,  // always add a bucket for missing values
            parms._weights_column != null, // observation weights
            parms._offset_column != null,
            parms._fold_column != null
    );
    // Checks and adjustments:
    // 1) observation weights (adjust mean/sigmas for predictors and response)
    // 2) NAs (check that there's enough rows left)
    GLMTask.YMUTask ymt = new GLMTask.YMUTask(dinfo, nClasses,nClasses == 1, parms._missing_values_handling == XGBoostModel.XGBoostParameters.MissingValuesHandling.Skip, true, true).doAll(dinfo._adaptedFrame);
    if (ymt.wsum() == 0 && parms._missing_values_handling == XGBoostModel.XGBoostParameters.MissingValuesHandling.Skip)
      throw new H2OIllegalArgumentException("No rows left in the dataset after filtering out rows with missing values. Ignore columns with many NAs or set missing_values_handling to 'MeanImputation'.");
    if (parms._weights_column != null && parms._offset_column != null) {
      Log.warn("Combination of offset and weights can lead to slight differences because Rollupstats aren't weighted - need to re-calculate weighted mean/sigma of the response including offset terms.");
    }
    if (parms._weights_column != null && parms._offset_column == null /*FIXME: offset not yet implemented*/) {
      dinfo.updateWeightedSigmaAndMean(ymt.predictorSDs(), ymt.predictorMeans());
      if (nClasses == 1)
        dinfo.updateWeightedSigmaAndMeanForResponse(ymt.responseSDs(), ymt.responseMeans());
    }
    return dinfo;
  }

  public static byte[] getRawArray(Booster booster) {
    if(null == booster) {
      return null;
    }

    byte[] rawBooster;
    try {
      Map<String, String> localRabitEnv = new HashMap<>();
      Rabit.init(localRabitEnv);
      rawBooster = booster.toByteArray();
      Rabit.shutdown();
    } catch (XGBoostError xgBoostError) {
      throw new IllegalStateException("Failed to initialize Rabit or serialize the booster.", xgBoostError);
    }
    return rawBooster;
  }

  // ----------------------
  class XGBoostDriver extends Driver {

    // Per driver instance
    final private String featureMapFileName = "featureMap" + UUID.randomUUID().toString() + ".txt";
    // Shared file to write list of features
    private File featureMapFile = null;

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
      XGBoostModel model = new XGBoostModel(_result, _parms, new XGBoostOutput(XGBoost.this), _train, _valid);
      model.write_lock(_job);

      if (_parms._dmatrix_type == XGBoostModel.XGBoostParameters.DMatrixType.sparse) {
        model._output._sparse = true;
      } else if (_parms._dmatrix_type == XGBoostModel.XGBoostParameters.DMatrixType.dense) {
        model._output._sparse = false;
      } else {
        float fillRatio = 0;
        int col = 0;
        for (int i = 0; i < _train.numCols(); ++i) {
          if (_train.name(i).equals(_parms._response_column)) continue;
          if (_train.name(i).equals(_parms._weights_column)) continue;
          if (_train.name(i).equals(_parms._fold_column)) continue;
          if (_train.name(i).equals(_parms._offset_column)) continue;
          fillRatio += _train.vec(i).nzCnt() / _train.numRows();
          col++;
        }
        fillRatio /= col;
        Log.info("fill ratio: " + fillRatio);
        model._output._sparse = fillRatio < 0.5 || ((_train.numRows() * (long) _train.numCols()) > Integer.MAX_VALUE);
      }

      // Single Rabit tracker per job. Manages the node graph for Rabit.
      final IRabitTracker rt;
      XGBoostSetupTask setupTask = null;
      try {
        // Count on how many nodes the data resides
        Set<H2ONode> nodesHoldingFrame = new HashSet<>();
        Vec vec = train().anyVec();
        for(int chunkNr = 0; chunkNr < vec.nChunks(); chunkNr++) {
          nodesHoldingFrame.add(vec.chunkKey(chunkNr).home_node());
        }

        // Prepare Rabit tracker for this job
        // This cannot be H2O.getCloudSize() as a frame might not be distributed on all the nodes
        // In such a case we'll perform training only on a subset of nodes while XGBoost/Rabit would keep waiting
        // for H2O.getCloudSize() number of requests/responses.
        rt = new RabitTrackerH2O(nodesHoldingFrame.size());

        if (!startRabitTracker(rt)) {
          throw new IllegalArgumentException("Cannot start XGboost rabit tracker, please, "
                                             + "make sure you have python installed!");
        }

        // Create a temporary file with "feature map"
        String featureMap = XGBoostUtils.makeFeatureMap(_train, model.model_info()._dataInfoKey.get());
        featureMapFile = createFeatureMapFile(featureMap);

        setupTask = new XGBoostSetupTask(model.model_info(), _parms, model._output._sparse).doAll(_train);

        BoosterParms boosterParms = XGBoostModel.createParams(_parms, model._output.nclasses());

        try {
          model.model_info().setBooster(new XGBoostUpdateTask(
                  setupTask,
                  model.model_info().getBooster(),
                  boosterParms,
                  0,
                  getWorkerEnvs(rt)).doAll(_train).getBooster());
          // Wait for results
          waitOnRabitWorkers(rt);
        } catch (RuntimeException e) {
          throw e;
        } finally {
          rt.stop();
        }

        // train the model
        scoreAndBuildTrees(setupTask, boosterParms, model, rt);

        // save the model to DKV
        model.model_info().nativeToJava();
        model._output._boosterBytes = model.model_info()._boosterBytes;
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

    // For feature importances - write out column info
    private File createFeatureMapFile(String featureMap) {
      OutputStream os = null;
      try {
        File tmpModelDir = java.nio.file.Files.createTempDirectory("xgboost-model-" + _result.toString()).toFile();
        File fmFile = new File(tmpModelDir, featureMapFileName);
        os = new FileOutputStream(fmFile);
        os.write(featureMap.getBytes());
        os.close();
        return fmFile;
      } catch (IOException e) {
        throw new RuntimeException("Cannot generate feature map file " + featureMapFileName, e);
      } finally {
        FileUtils.close(os);
      }
    }

    private void scoreAndBuildTrees(XGBoostSetupTask setupTask, BoosterParms boosterParms,
                                    XGBoostModel model, IRabitTracker rt) throws XGBoostError {
      for( int tid=0; tid< _parms._ntrees; tid++) {
        // During first iteration model contains 0 trees, then 1-tree, ...
        boolean scored = doScoring(model, model.model_info().getBooster(), false);
        if (scored && ScoreKeeper.stopEarly(model._output.scoreKeepers(), _parms._stopping_rounds,
                _nclass > 1, _parms._stopping_method, _parms._stopping_metric, _parms._stopping_tolerance,
                "model's last", true)) {
          doScoring(model, model.model_info().getBooster(), true);
          _job.update(_parms._ntrees-model._output._ntrees); //finish
          return;
        }

        Timer kb_timer = new Timer();
        startRabitTracker(rt);
        try {
          model.model_info().setBooster(new XGBoostUpdateTask(
              setupTask,
              model.model_info().getBooster(),
              boosterParms,
              tid,
              getWorkerEnvs(rt)).doAll(_train).getBooster());
          // Wait for successful completion
          waitOnRabitWorkers(rt);
        } finally {
          rt.stop();
        }

        Log.info((tid + 1) + ". tree was built in " + kb_timer.toString());
        _job.update(1);
        // Optional: for convenience
//          model.update(_job);
//          model.model_info().nativeToJava();
        model._output._ntrees++;
        model._output._scored_train = ArrayUtils.copyAndFillOf(model._output._scored_train, model._output._ntrees+1, new ScoreKeeper());
        model._output._scored_valid = model._output._scored_valid != null ? ArrayUtils.copyAndFillOf(model._output._scored_valid, model._output._ntrees+1, new ScoreKeeper()) : null;
        model._output._training_time_ms = ArrayUtils.copyAndFillOf(model._output._training_time_ms, model._output._ntrees+1, System.currentTimeMillis());
        if (stop_requested() && !timeout()) throw new Job.JobCancelledException();
        if (timeout()) { //stop after scoring
          if (!scored) {
            doScoring(model, model.model_info().getBooster(), true);
          }
          _job.update(_parms._ntrees-model._output._ntrees); //finish
          break;
        }
      }
      doScoring(model, model.model_info().getBooster(), true);
    }

    // Don't start the tracker for 1 node clouds -> the GPU plugin fails in such a case
    private boolean startRabitTracker(IRabitTracker rt) {
      if (H2O.CLOUD.size() > 1) {
        return rt.start(0);
      }
      return true;
    }

    // RT should not be started for 1 node clouds
    private void waitOnRabitWorkers(IRabitTracker rt) {
      if(H2O.CLOUD.size() > 1) {
        rt.waitFor(0);
      }
    }

    // XGBoost seems to manipulate its frames in case of a 1 node distributed version in a way the GPU plugin can't handle
    // Therefore don't use RabitTracker envs for 1 node
    private Map<String, String> getWorkerEnvs(IRabitTracker rt) {
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
                              Booster booster,
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
        model.doScoring(booster, _train, _parms.train(), _valid, _parms.valid());
        _timeLastScoreEnd = System.currentTimeMillis();
        model.computeVarImp(booster.getFeatureScore(featureMapFile.getAbsolutePath()));
        XGBoostOutput out = model._output;
        out._model_summary = createModelSummaryTable(out._ntrees, null);
        out._scoring_history = createScoringHistoryTable(out, model._output._scored_train, out._scored_valid, _job, out._training_time_ms, _parms._custom_metric_func != null);
        out._variable_importances = hex.ModelMetrics.calcVarImp(out._varimp);
        model.update(_job);
        Log.info(model);
        scored = true;
      }

      return scored;
    }
  }

  private double effective_learning_rate(XGBoostModel model) {
    return _parms._learn_rate * Math.pow(_parms._learn_rate_annealing, (model._output._ntrees-1));
  }

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

  // helper
  static synchronized boolean hasGPU(int gpu_id) {
    if (! XGBoostExtension.isGpuSupportEnabled()) {
      return false;
    }

    if(GPUS.contains(gpu_id)) {
      return true;
    }

    DMatrix trainMat = null;
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
}