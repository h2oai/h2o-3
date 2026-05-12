package hex.tree.xgboost;

import biz.k11i.xgboost.gbm.GBTree;
import biz.k11i.xgboost.gbm.GradBooster;
import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.RegTreeNode;
import hex.*;
import hex.genmodel.algos.xgboost.XGBoostJavaMojoModel;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.CalibrationHelper;
import hex.tree.TreeUtils;
import hex.tree.xgboost.exec.LocalXGBoostExecutor;
import hex.tree.xgboost.exec.RemoteXGBoostExecutor;
import hex.tree.xgboost.exec.XGBoostExecutor;
import hex.tree.xgboost.predict.XGBoostVariableImportance;
import hex.tree.xgboost.remote.SteamExecutorStarter;
import hex.tree.xgboost.util.FeatureScore;
import hex.util.CheckpointUtils;
import org.apache.log4j.Logger;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.RebalanceDataSet;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.Timer;
import water.util.TwoDimTable;

import java.io.IOException;
import java.util.*;

import static hex.tree.SharedTree.createModelSummaryTable;
import static hex.tree.SharedTree.createScoringHistoryTable;
import static hex.tree.xgboost.util.GpuUtils.*;
import static water.H2O.technote;

/** 
 * H2O XGBoost
 */
public class XGBoost extends ModelBuilder<XGBoostModel,XGBoostModel.XGBoostParameters,XGBoostOutput> 
    implements CalibrationHelper.ModelBuilderWithCalibration<XGBoostModel, XGBoostModel.XGBoostParameters, XGBoostOutput> {

  private static final Logger LOG = Logger.getLogger(XGBoost.class);
  
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

  // Calibration frame for Platt scaling
  private transient Frame _calib;

  @Override protected int nModelsInParallel(int folds) {
    /*
      Concept of XGBoost CV parallelization:
        - for CPU backend use regular strategy with defaultParallelization = 2
        - for GPU backend:
            - running on GPU in parallel might not be faster in all cases - but H2O currently has overhead in scoring,
              and scoring is done always on CPU - we want to keep GPU busy the whole training, the idea is when one model
              is being scored (on CPU) the other one is running on GPU and the GPU is never idle
            - data up to a certain limit can run 2 models parallel per GPU
            - big data take the whole GPU
     */
    if (_parms._parallelize_cross_validation &&
            XGBoostModel.getActualBackend(_parms, false) == XGBoostModel.XGBoostParameters.Backend.gpu) {
      int numGPUs = _parms._gpu_id != null && _parms._gpu_id.length > 0 ? _parms._gpu_id.length : numGPUs(H2O.CLOUD.members()[0]);
      int parallelizationPerGPU = _train.byteSize() < parallelTrainingSizeLimit() ? 2 : 1;
      return numGPUs * parallelizationPerGPU;
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
   *  Validate the learning rate and distribution family. */
  @Override public void init(boolean expensive) {
    super.init(expensive);
    if (H2O.CLOUD.size() > 1 && H2O.SELF.getSecurityManager().securityEnabled) {
      if (H2O.ARGS.allow_insecure_xgboost) {
        LOG.info("Executing XGBoost on an secured cluster might compromise security.");
      } else {
        throw new H2OIllegalArgumentException("Cannot run XGBoost on an SSL enabled cluster larger than 1 node. XGBoost does not support SSL encryption.");
      }
    }
    if (H2O.ARGS.client && _parms._build_tree_one_node)
      error("_build_tree_one_node", "Cannot run on a single node in client mode.");
    if (expensive) {
      if (_response != null && _response.naCnt() > 0) {
        error("_response_column", "Response contains missing values (NAs) - not supported by XGBoost.");
      }
      if(!new XGBoostExtensionCheck().doAllNodes().enabled) {
        error("XGBoost", "XGBoost is not available on all nodes!");
      }
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

    if (_parms._max_depth < 0) error("_max_depth", "_max_depth must be >= 0.");
    if (_parms._max_depth == 0) _parms._max_depth = Integer.MAX_VALUE;

    if (expensive) {
      if (error_count() > 0)
        throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(XGBoost.this);
    }

    if ( _parms._backend == XGBoostModel.XGBoostParameters.Backend.gpu) {
      if (! hasGPU(_parms._gpu_id))
        error("_backend", "GPU backend (gpu_id: " + Arrays.toString(_parms._gpu_id) + ") is not functional. Check CUDA_PATH and/or GPU installation.");

      if (H2O.getCloudSize() > 1 && !_parms._build_tree_one_node && !allowMultiGPU())
        error("_backend", "GPU backend is not supported in distributed mode.");

      Map<String, Object> incompats = _parms.gpuIncompatibleParams();
      if (! incompats.isEmpty())
        for (Map.Entry<String, Object> incompat : incompats.entrySet())
          error("_backend", "GPU backend is not available for parameter setting '" + incompat.getKey() + " = " + incompat.getValue() + "'. Use CPU backend instead.");
    }
    DistributionFamily[] allowed_distributions = new DistributionFamily[] {
            DistributionFamily.AUTO,
            DistributionFamily.bernoulli,
            DistributionFamily.multinomial,
            DistributionFamily.gaussian,
            DistributionFamily.poisson,
            DistributionFamily.gamma,
            DistributionFamily.tweedie,
    };
    if (!ArrayUtils.contains(allowed_distributions, _parms._distribution))
      error("_distribution", _parms._distribution.name() + " is not supported for XGBoost in current H2O.");

    if (unsupportedCategoricalEncoding()) {
      error("_categorical_encoding", _parms._categorical_encoding + " encoding is not supported for XGBoost in current H2O.");
    }
    
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

    checkPositiveRate("learn_rate", _parms._learn_rate);
    checkPositiveRate("sample_rate", _parms._sample_rate);
    checkPositiveRate("subsample", _parms._subsample);
    checkPositiveRate("col_sample_rate", _parms._col_sample_rate);
    checkPositiveRate("col_sample_rate_per_tree", _parms._col_sample_rate_per_tree);
    checkPositiveRate("colsample_bylevel", _parms._colsample_bylevel);
    checkPositiveRate("colsample_bynode", _parms._colsample_bynode);
    checkPositiveRate("colsample_bytree", _parms._colsample_bytree);
    checkColumnAlias("col_sample_rate", _parms._col_sample_rate, "colsample_bylevel", _parms._colsample_bylevel, 1);
    checkColumnAlias("col_sample_rate_per_tree", _parms._col_sample_rate_per_tree, "colsample_bytree", _parms._colsample_bytree, 1);
    checkColumnAlias("sample_rate", _parms._sample_rate, "subsample", _parms._subsample, 1);
    checkColumnAlias("learn_rate", _parms._learn_rate, "eta", _parms._eta, 0.3);
    checkColumnAlias("max_abs_leafnode_pred", _parms._max_abs_leafnode_pred, "max_delta_step", _parms._max_delta_step,0);
    checkColumnAlias("ntrees", _parms._ntrees, "n_estimators", _parms._n_estimators, 0);
    
    if(_parms._tree_method.equals(XGBoostModel.XGBoostParameters.TreeMethod.approx) && (_parms._col_sample_rate < 1 || _parms._colsample_bylevel < 1)){
        error("_tree_method", "approx is not supported with _col_sample_rate or _colsample_bylevel, use exact/hist instead or disable column sampling.");
    }

    if (_parms._scale_pos_weight != 1) {
      if (_nclass != 2)
        error("_scale_pos_weight", "scale_pos_weight can only be used for binary classification");
      if (_parms._scale_pos_weight <= 0)
        error("_scale_pos_weight", "scale_pos_weight must be a positive number");
    }

    if (_parms._grow_policy== XGBoostModel.XGBoostParameters.GrowPolicy.lossguide && 
        _parms._tree_method!= XGBoostModel.XGBoostParameters.TreeMethod.hist)
      error("_grow_policy", "must use tree_method=hist for grow_policy=lossguide");

    if ((_train != null) && !_parms.monotoneConstraints().isEmpty()) {
      if (_parms._tree_method == XGBoostModel.XGBoostParameters.TreeMethod.approx) {
        error("_tree_method", "approx is not supported with _monotone_constraints, use auto/exact/hist instead");
      } else {
        assert _parms._tree_method == XGBoostModel.XGBoostParameters.TreeMethod.auto ||
            _parms._tree_method == XGBoostModel.XGBoostParameters.TreeMethod.exact ||
            _parms._tree_method == XGBoostModel.XGBoostParameters.TreeMethod.hist :
            "Unexpected tree method used " + _parms._tree_method;
      }
      TreeUtils.checkMonotoneConstraints(this, _train, _parms._monotone_constraints);
    }

    if ((_train != null) && (H2O.CLOUD.size() > 1) &&
        (_parms._tree_method == XGBoostModel.XGBoostParameters.TreeMethod.exact) &&    
        !_parms._build_tree_one_node)
      error("_tree_method", "exact is not supported in distributed environment, set build_tree_one_node to true to use exact");

    CalibrationHelper.initCalibration(this, _parms, expensive);

    if (_parms.hasCustomMetricFunc() && _parms._eval_metric != null) {
      error("custom_metric_func", "Custom metric is not supported together with eval_metric parameter. Please use only one of them.");
    }

    if (_parms._score_eval_metric_only && _parms._eval_metric == null) {
      warn("score_eval_metric_only", "score_eval_metric_only is set but eval_metric parameter is not defined");
    }
  }

  protected void checkCustomMetricForEarlyStopping() {
    if (_parms._eval_metric == null && !_parms.hasCustomMetricFunc()) {
      error("_eval_metric", "Evaluation metric needs to be defined in order to use it for early stopping.");
      super.checkCustomMetricForEarlyStopping();
    }
  }

  private void checkPositiveRate(String paramName, double rateValue) {
    if (rateValue <= 0 || rateValue > 1)
      error("_" + paramName, paramName + " must be between 0 (exclusive) and 1 (inclusive)");
  }

  private void checkColumnAlias(String paramName, double paramValue, String aliasName, double aliasValue, double defaultValue) {
    if (paramValue != defaultValue && aliasValue != defaultValue && paramValue != aliasValue) {
      error("_" + paramName, paramName + " and its alias " + aliasName + " are both set to different value than default value. Set " + aliasName + " to default value (" + defaultValue + "), to use " + paramName + " actual value.");
    } else if (aliasValue != defaultValue){
      warn("_"+paramName, "Using user-provided parameter "+aliasName+" instead of "+paramName+".\"");
    }
  }

  @Override
  protected void checkEarlyStoppingReproducibility() {
    if (_parms._score_tree_interval == 0 && !_parms._score_each_iteration) {
      warn("_stopping_rounds", "early stopping is enabled but neither score_tree_interval or score_each_iteration are defined. Early stopping will not be reproducible!");
    }
  }

  static boolean allowMultiGPU() {
    return H2O.getSysBoolProperty("xgboost.multinode.gpu.enabled", false);
  }

  static long parallelTrainingSizeLimit() {
    long defaultLimit = (long) 1e9; // 1GB; current GPUs typically have at least 8GB of memory - plenty of buffer left
    String limitSpec = H2O.getSysProperty("xgboost.gpu.parallelTrainingSizeLimit", Long.toString(defaultLimit));
    return Long.parseLong(limitSpec);
  }

  static boolean prestartExternalClusterForCV() {
    return H2O.getSysBoolProperty("xgboost.external.cv.prestart", false);
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

  @Override
  protected boolean canLearnFromNAs() {
    return true;
  }

  static DataInfo makeDataInfo(Frame train, Frame valid, XGBoostModel.XGBoostParameters parms) {
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
    assert !dinfo._predictor_transform.isMeanAdjusted() : "Unexpected predictor transform, it shouldn't be mean adjusted";
    assert !dinfo._predictor_transform.isSigmaScaled() : "Unexpected predictor transform, it shouldn't be sigma scaled";
    assert !dinfo._response_transform.isMeanAdjusted() : "Unexpected response transform, it shouldn't be mean adjusted";
    assert !dinfo._response_transform.isSigmaScaled() : "Unexpected response transform, it shouldn't be sigma scaled";
    dinfo.coefNames(); // cache the coefficient names
    dinfo.coefOriginalColumnIndices(); // cache the original column indices
    assert dinfo._coefNames != null && dinfo._coefOriginalIndices != null;
    return dinfo;
  }

  @Override
  protected Frame rebalance(Frame original_fr, boolean local, String name) {
    if (original_fr == null) return null;
    else if (_parms._build_tree_one_node) {
      int original_chunks = original_fr.anyVec().nChunks();
      if (original_chunks == 1)
        return original_fr;
      LOG.info("Rebalancing " + name.substring(name.length()-5) + " dataset onto a single node.");
      Key<Frame> newKey = Key.make(name + ".1chk");
      Frame singleChunkFr = RebalanceDataSet.toSingleChunk(original_fr, newKey);
      Scope.track(singleChunkFr);
      return singleChunkFr;
    } else {
      return super.rebalance(original_fr, local, name);
    }
  }

  // ----------------------
  class XGBoostDriver extends Driver {

    @Override
    public void computeImpl() {
      init(true); //this can change the seed if it was set to -1
      // Something goes wrong
      if (error_count() > 0)
        throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(XGBoost.this);
      buildModel();
    }
    
    private XGBoostExecutor makeExecutor(XGBoostModel model, boolean useValidFrame) throws IOException {
      final Frame valid = useValidFrame ? _valid : null;
      if (H2O.ARGS.use_external_xgboost) {
        return SteamExecutorStarter.getInstance().getRemoteExecutor(model, _train, valid, _job);
      } else {
        String remoteUriFromProp = H2O.getSysProperty("xgboost.external.address", null);
        if (remoteUriFromProp == null) {
          return new LocalXGBoostExecutor(model, _train, valid);
        } else {
          String userName = H2O.getSysProperty("xgboost.external.user", null);
          String password = H2O.getSysProperty("xgboost.external.password", null);
          return new RemoteXGBoostExecutor(model, _train, valid, remoteUriFromProp, userName, password);
        }
      }
    }

    final void buildModel() {
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
      
      if (model.evalAutoParamsEnabled) {
        model.initActualParamValuesAfterOutputSetup(isClassifier(), _nclass);
      }

      XGBoostUtils.createFeatureMap(model, _train);
      XGBoostVariableImportance variableImportance = model.setupVarImp();
      boolean scoreValidFrame = _valid != null && _parms._eval_metric != null;
      LOG.info("Need to score validation frame by XGBoost native backend: " + scoreValidFrame);
      try (XGBoostExecutor exec = makeExecutor(model, scoreValidFrame)) {
        model.model_info().updateBoosterBytes(exec.setup());
        scoreAndBuildTrees(model, exec, variableImportance);
      } catch (Exception e) {
        throw new RuntimeException("Error while training XGBoost model", e);
      } finally {
        variableImportance.cleanup();
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
      LOG.info("fill ratio: " + fillRatio);

      return fillRatio < FILL_RATIO_THRESHOLD
          || ((_train.numRows() * totalColumns) > Integer.MAX_VALUE);
    }

    private void scoreAndBuildTrees(final XGBoostModel model, final XGBoostExecutor exec, XGBoostVariableImportance varImp) {
      long scoringTime = 0;
      for (int tid = 0; tid < _ntrees; tid++) {
        if (_job.stop_requested() && tid > 0) break;
        // During first iteration model contains 0 trees, then 1-tree, ...
        long scoringStart = System.currentTimeMillis();
        boolean scored = doScoring(model, exec, varImp, false, _parms._score_eval_metric_only);
        scoringTime += System.currentTimeMillis() - scoringStart;
        if (scored && ScoreKeeper.stopEarly(model._output.scoreKeepers(), _parms._stopping_rounds, ScoreKeeper.ProblemType.forSupervised(_nclass > 1), _parms._stopping_metric, _parms._stopping_tolerance, "model's last", true)) {
          LOG.info("Early stopping triggered - stopping XGBoost training");
          LOG.info("Setting actual ntrees to the " + model._output._ntrees);
          _parms._ntrees = model._output._ntrees;
          break;
        }

        Timer kb_timer = new Timer();
        exec.update(tid);
        LOG.info((tid + 1) + ". tree was built in " + kb_timer.toString());
        _job.update(1);

        model._output._ntrees++;
        model._output._scored_train = ArrayUtils.copyAndFillOf(model._output._scored_train, model._output._ntrees+1, new ScoreKeeper());
        model._output._scored_valid = model._output._scored_valid != null ? ArrayUtils.copyAndFillOf(model._output._scored_valid, model._output._ntrees+1, new ScoreKeeper()) : null;
        model._output._training_time_ms = ArrayUtils.copyAndFillOf(model._output._training_time_ms, model._output._ntrees+1, System.currentTimeMillis());
        if (stop_requested() && !timeout()) throw new Job.JobCancelledException(_job);
        if (timeout()) {
          LOG.info("Stopping XGBoost training because of timeout");
          break;
        }
      }

      Map<String, Integer> monotoneConstraints = _parms.monotoneConstraints();
      if (!monotoneConstraints.isEmpty() &&
          _parms._booster != XGBoostModel.XGBoostParameters.Booster.gblinear &&
          monotonicityConstraintCheckEnabled()
      ) {
        _job.update(0, "Checking monotonicity constraints on the final model");
        model.model_info().updateBoosterBytes(exec.updateBooster());
        checkMonotonicityConstraints(model.model_info(), monotoneConstraints);
      }

      if (_parms._interaction_constraints != null &&
              interactionConstraintCheckEnabled()) {
        _job.update(0, "Checking interaction constraints on the final model");
        model.model_info().updateBoosterBytes(exec.updateBooster());
        checkInteractionConstraints(model.model_info(), _parms._interaction_constraints);
      }
      
      _job.update(0, "Scoring the final model");
      // Final scoring
      long scoringStart = System.currentTimeMillis();
      doScoring(model, exec, varImp, true, _parms._score_eval_metric_only);
      scoringTime += System.currentTimeMillis() - scoringStart;
      // Finish remaining work (if stopped early)
      _job.update(_parms._ntrees-model._output._ntrees);
      Log.info("In-training scoring took " + scoringTime + "ms.");
    }

    private boolean monotonicityConstraintCheckEnabled() {
      return Boolean.parseBoolean(getSysProperty("xgboost.monotonicity.checkEnabled", "true"));
    }

    private boolean interactionConstraintCheckEnabled() {
      return Boolean.parseBoolean(getSysProperty("xgboost.interactions.checkEnabled", "true"));
    }

    private void checkMonotonicityConstraints(XGBoostModelInfo model_info, Map<String, Integer> monotoneConstraints) {
      GradBooster booster = XGBoostJavaMojoModel.makePredictor(model_info._boosterBytes, null).getBooster();
      if (!(booster instanceof GBTree)) {
        throw new IllegalStateException("Expected booster object to be GBTree instead it is " + booster.getClass().getName());
      }
      final RegTree[][] groupedTrees = ((GBTree) booster).getGroupedTrees();
      final XGBoostUtils.FeatureProperties featureProperties = XGBoostUtils.assembleFeatureNames(model_info.dataInfo()); // XGBoost's usage of one-hot encoding assumed

      for (RegTree[] classTrees : groupedTrees) {
        for (RegTree tree : classTrees) {
          if (tree == null) continue;
          checkMonotonicityConstraints(tree.getNodes(), monotoneConstraints, featureProperties);
        }
      }
    }

    private void checkMonotonicityConstraints(RegTreeNode[] tree, Map<String, Integer> monotoneConstraints, XGBoostUtils.FeatureProperties featureProperties) {
      float[] mins = new float[tree.length];
      int[] min_ids = new int[tree.length];
      float[] maxs = new float[tree.length];
      int[] max_ids = new int[tree.length];
      rollupMinMaxPreds(tree, 0, mins, min_ids, maxs, max_ids);
      for (RegTreeNode node : tree) {
        if (node.isLeaf()) continue;
        String splitColumn = featureProperties._names[node.getSplitIndex()];
        if (!monotoneConstraints.containsKey(splitColumn)) continue;
        int constraint = monotoneConstraints.get(splitColumn);
        int left = node.getLeftChildIndex();
        int right = node.getRightChildIndex();
        if (constraint > 0) {
          if (maxs[left] > mins[right]) {
            throw new IllegalStateException("Monotonicity constraint " + constraint + " violated on column '" + splitColumn + "' (max(left) > min(right)): " +
                maxs[left] + " > " + mins[right] +
                "\nNode: " + node +
                "\nLeft Node (max): " + tree[max_ids[left]] +
                "\nRight Node (min): " + tree[min_ids[right]]);
          }
        } else if (constraint < 0) {
          if (mins[left] < maxs[right]) {
            throw new IllegalStateException("Monotonicity constraint " + constraint + " violated on column '" + splitColumn + "' (min(left) < max(right)): " +
                mins[left] + " < " + maxs[right] +
                "\nNode: " + node +
                "\nLeft Node (min): " + tree[min_ids[left]] +
                "\nRight Node (max): " + tree[max_ids[right]]);
          }
        }
      }
    }

    private void rollupMinMaxPreds(RegTreeNode[] tree, int nid, float[] mins, int min_ids[], float[] maxs, int[] max_ids) {
      RegTreeNode node = tree[nid];
      if (node.isLeaf()) {
        mins[nid] = node.getLeafValue();
        min_ids[nid] = nid;
        maxs[nid] = node.getLeafValue();
        max_ids[nid] = nid;
        return;
      }
      int left = node.getLeftChildIndex();
      int right = node.getRightChildIndex();
      rollupMinMaxPreds(tree, left, mins, min_ids, maxs, max_ids);
      rollupMinMaxPreds(tree, right, mins, min_ids, maxs, max_ids);
      final int min_id = mins[left] < mins[right] ? left : right;
      mins[nid] = mins[min_id];
      min_ids[nid] = min_ids[min_id];
      final int max_id = maxs[left] > maxs[right] ? left : right;
      maxs[nid] = maxs[max_id];
      max_ids[nid] = max_ids[max_id];
    }

    private void checkInteractionConstraints(XGBoostModelInfo model_info, String[][] interactionConstraints) {
      GradBooster booster = XGBoostJavaMojoModel.makePredictor(model_info._boosterBytes, null).getBooster();
      if (!(booster instanceof GBTree)) {
        throw new IllegalStateException("Expected booster object to be GBTree instead it is " + booster.getClass().getName());
      }
      final RegTree[][] groupedTrees = ((GBTree) booster).getGroupedTrees();
      final XGBoostUtils.FeatureProperties featureProperties = XGBoostUtils.assembleFeatureNames(model_info.dataInfo()); // XGBoost's usage of one-hot encoding assumed

      // create map of constraint unions
      Map<Integer, Set<Integer>> interactionUnions = new HashMap<>();
      for(String[] interaction : interactionConstraints){
        Integer[] mapOfIndices = featureProperties.mapOriginalNamesToIndices(interaction);
        for(int index : mapOfIndices){
          if(!interactionUnions.containsKey(index)) {
            interactionUnions.put(index, new HashSet<>());
          }
          interactionUnions.get(index).addAll(Arrays.asList(mapOfIndices));
        }
      }
      
      for (RegTree[] classTrees : groupedTrees) {
        for (RegTree tree : classTrees) {
          if (tree == null) continue;
          RegTreeNode[] treeNodes = tree.getNodes(); 
          checkInteractionConstraints(treeNodes, treeNodes[0], interactionUnions, featureProperties);
        }
      }
    }
    
    private void checkInteractionConstraints(RegTreeNode[] tree, RegTreeNode node, Map<Integer, Set<Integer>> interactionUnions, XGBoostUtils.FeatureProperties featureProperties){
      if (node.isLeaf()) {
        return;
      }
      int splitIndex = node.getSplitIndex();
      int splitIndexOriginal = featureProperties._originalColumnIndices[splitIndex];
      Set<Integer> interactionUnion = interactionUnions.get(splitIndexOriginal);
      RegTreeNode leftChildNode = tree[node.getLeftChildIndex()];
      // if left child node is not leaf - check left child
      if(!leftChildNode.isLeaf()) {
        int leftChildSplitIndex = leftChildNode.getSplitIndex();
        int leftChildSplitIndexOriginal =  featureProperties._originalColumnIndices[leftChildSplitIndex];
        // check left child split column is the same as parent or is in parent constrained union - if not violate constraint
        if (leftChildSplitIndex != splitIndex && (interactionUnion == null || !interactionUnion.contains(leftChildSplitIndexOriginal))) {
          String parentOriginalName = featureProperties._originalNames[splitIndexOriginal];
          String interactionString = generateInteractionConstraintUnionString(featureProperties._originalNames, splitIndexOriginal, interactionUnion);
          String leftOriginalName = featureProperties._originalNames[leftChildSplitIndexOriginal];
          throw new IllegalStateException("Interaction constraint violated on column '" + leftOriginalName+ "': The parent column '"+parentOriginalName+"' can interact only with "+interactionString+" columns.");
        }
      }
      RegTreeNode rightChildNode = tree[node.getRightChildIndex()];
      // if right child node is not leaf - check right child
      if(!rightChildNode.isLeaf()) {
        int rightChildSplitIndex = rightChildNode.getSplitIndex();
        int rightChildSplitIndexOriginal =  featureProperties._originalColumnIndices[rightChildSplitIndex];
        // check right child split column is the same as parent or is in parent constrained union - if not violate constraint
        if (rightChildSplitIndex != splitIndex && (interactionUnion == null || !interactionUnion.contains(rightChildSplitIndexOriginal))) {
          String parentOriginalName = featureProperties._originalNames[splitIndexOriginal];
          String interactionString = generateInteractionConstraintUnionString(featureProperties._originalNames, splitIndexOriginal, interactionUnion);
          String rightOriginalName = featureProperties._originalNames[rightChildSplitIndexOriginal];
          throw new IllegalStateException("Interaction constraint violated on column '" + rightOriginalName+ "': The parent column '"+parentOriginalName+"' can interact only with "+interactionString+" columns.");
        }
      }
      checkInteractionConstraints(tree, leftChildNode, interactionUnions, featureProperties);
      checkInteractionConstraints(tree, rightChildNode, interactionUnions, featureProperties);
    }
    
    private String generateInteractionConstraintUnionString(String[] originalNames, int splitIndexOriginal, Set<Integer> interactionUnion){
      String parentOriginalName = originalNames[splitIndexOriginal];
      String interaction = "['" + parentOriginalName + "']";
      if (interactionUnion != null) {
        StringBuilder sb = new StringBuilder("[");
        for(Integer i: interactionUnion){
          sb.append(originalNames[i]).append(",");
        }
        interaction = sb.replace(sb.length()-1, sb.length(), "]").toString();
      }
      return interaction;
    }

    long _firstScore = 0;
    long _timeLastScoreStart = 0;
    long _timeLastScoreEnd = 0;

    private boolean doScoring(final XGBoostModel model, final XGBoostExecutor exec, XGBoostVariableImportance varImp,
                              boolean finalScoring, boolean scoreEvalMetricOnly) {
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
        final XGBoostOutput out = model._output;
        final boolean boosterUpdated;
        _timeLastScoreStart = now;
        CustomMetric customMetricTrain = _parms._eval_metric != null ? toCustomMetricTrain(exec.getEvalMetric()) : null;
        CustomMetric customMetricValid = _parms._eval_metric != null && _valid != null ? toCustomMetricValid(exec.getEvalMetric()) : null;
        if (!finalScoring && scoreEvalMetricOnly && customMetricTrain != null) {
          out._scored_train[out._ntrees]._custom_metric = customMetricTrain.value;
          if (customMetricValid != null) {
            out._useValidForScoreKeeping = true;
            out._scored_valid[out._ntrees]._custom_metric = customMetricValid.value;
          }
          boosterUpdated = false;
        } else {
          model.model_info().updateBoosterBytes(exec.updateBooster());
          boosterUpdated = true;
          model.doScoring(_train, _parms.train(), customMetricTrain, _valid, _parms.valid(), customMetricValid);
        }
        _timeLastScoreEnd = System.currentTimeMillis();
        out._model_summary = createModelSummaryTable(out._ntrees, null);
        out._scoring_history = createScoringHistoryTable(out, model._output._scored_train, out._scored_valid, _job, out._training_time_ms, _parms._custom_metric_func != null || _parms._eval_metric != null, false);
        if (boosterUpdated) {
          final Map<String, FeatureScore> varimp = varImp.getFeatureScores(model.model_info()._boosterBytes);
          out._varimp = computeVarImp(varimp);
          if (out._varimp != null) {
            out._variable_importances = createVarImpTable(null, ArrayUtils.toDouble(out._varimp._varimp), out._varimp._names);
            out._variable_importances_cover = createVarImpTable("Cover", ArrayUtils.toDouble(out._varimp._covers), out._varimp._names);
            out._variable_importances_frequency = createVarImpTable("Frequency", ArrayUtils.toDouble(out._varimp._freqs), out._varimp._names);
          }
        }
        model.update(_job);
        LOG.info(model);
        scored = true;
      }

      // Model Calibration (only for the final model, not CV models)
      if (finalScoring && _parms.calibrateModel() && (!_parms._is_cv_model)) {
        model._output.setCalibrationModel(
                CalibrationHelper.buildCalibrationModel(XGBoost.this, _parms, _job, model)
        );
        model.update(_job);
      }

      return scored;
    }
  }

  static CustomMetric toCustomMetricTrain(EvalMetric evalMetric) {
    return toCustomMetric(evalMetric, true);
  }

  static CustomMetric toCustomMetricValid(EvalMetric evalMetric) {
    return toCustomMetric(evalMetric, false);
  }

  private static CustomMetric toCustomMetric(EvalMetric evalMetric, boolean isTrain) {
    if (evalMetric == null) {
      return null;
    }
    return CustomMetric.from(evalMetric._name, isTrain ? evalMetric._trainValue : evalMetric._validValue);
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

  @Override
  protected CVModelBuilder makeCVModelBuilder(ModelBuilder<?, ?, ?>[] modelBuilders, int parallelization) {
    if (XGBoostModel.getActualBackend(_parms, false) == XGBoostModel.XGBoostParameters.Backend.gpu && parallelization > 1) {
      return new XGBoostGPUCVModelBuilder(_job, modelBuilders, parallelization, _parms._gpu_id);      
    } else if (H2O.ARGS.use_external_xgboost && prestartExternalClusterForCV()) {
      return new XGBoostExternalCVModelBuilder(_job, modelBuilders, parallelization, SteamExecutorStarter.getInstance());
    } else {
      return super.makeCVModelBuilder(modelBuilders, parallelization);
    }
  }

  @Override public void cv_computeAndSetOptimalParameters(ModelBuilder<XGBoostModel,XGBoostModel.XGBoostParameters,XGBoostOutput>[] cvModelBuilders) {
    if( _parms._stopping_rounds == 0 && _parms._max_runtime_secs == 0) return; // No exciting changes to stopping conditions
    // Extract stopping conditions from each CV model, and compute the best stopping answer
    _parms._stopping_rounds = 0;
    setMaxRuntimeSecsForMainModel();
    int sum = 0;
    for (ModelBuilder mb : cvModelBuilders)
      sum += ((XGBoostOutput) DKV.<Model>getGet(mb.dest())._output)._ntrees;
    _parms._ntrees = (int)((double)sum/cvModelBuilders.length);
    warn("_ntrees", "Setting optimal _ntrees to " + _parms._ntrees + " for cross-validation main model based on early stopping of cross-validation models.");
    warn("_stopping_rounds", "Disabling convergence-based early stopping for cross-validation main model.");
    warn("_max_runtime_secs", "Disabling maximum allowed runtime for cross-validation main model.");
  }

  private boolean unsupportedCategoricalEncoding() {
    return _parms._categorical_encoding == Model.Parameters.CategoricalEncodingScheme.Enum ||
            _parms._categorical_encoding == Model.Parameters.CategoricalEncodingScheme.Eigen;
  }

}
