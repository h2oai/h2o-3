package hex.tree.xgboost;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.gbm.GBTree;
import biz.k11i.xgboost.gbm.GradBooster;
import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.RegTreeNode;
import biz.k11i.xgboost.util.FVec;
import hex.*;
import hex.genmodel.GenModel;
import hex.genmodel.algos.tree.SharedTreeGraph;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.genmodel.algos.tree.SharedTreeGraphConverter;
import hex.genmodel.algos.xgboost.XGBoostMojoModel;
import hex.genmodel.algos.xgboost.XGBoostNativeMojoModel;
import hex.genmodel.utils.DistributionFamily;
import ml.dmlc.xgboost4j.java.*;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.Log;
import hex.ModelMetrics;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static hex.tree.xgboost.XGBoost.makeDataInfo;
import static hex.genmodel.algos.xgboost.XGBoostMojoModel.ObjectiveType;

public class XGBoostModel extends Model<XGBoostModel, XGBoostModel.XGBoostParameters, XGBoostOutput> implements SharedTreeGraphConverter {

  private XGBoostModelInfo model_info;

  public XGBoostModelInfo model_info() { return model_info; }

  public static class XGBoostParameters extends Model.Parameters {
    public enum TreeMethod {
      auto, exact, approx, hist
    }
    public enum GrowPolicy {
      depthwise, lossguide
    }
    public enum Booster {
      gbtree, gblinear, dart
    }
    public enum MissingValuesHandling {
      MeanImputation, Skip
    }
    public enum DartSampleType {
      uniform, weighted
    }
    public enum DartNormalizeType {
      tree, forest
    }
    public enum DMatrixType {
      auto, dense, sparse
    }
    public enum Backend {
      auto, gpu, cpu
    }

    // H2O GBM options
    public boolean _quiet_mode = true;
    public MissingValuesHandling _missing_values_handling;

    public int _ntrees=50; // Number of trees in the final model. Grid Search, comma sep values:50,100,150,200
    public int _n_estimators;  // This doesn't seem to be used anywhere... (not in clients)

    public int _max_depth = 6; // Maximum tree depth. Grid Search, comma sep values:5,7

    public double _min_rows = 1;
    public double _min_child_weight = 1;

    public double _learn_rate = 0.3;
    public double _eta = 0.3;

    public double _learn_rate_annealing = 1;

    public double _sample_rate = 1.0;
    public double _subsample = 1.0;

    public double _col_sample_rate = 1.0;
    public double _colsample_bylevel = 1.0;

    public double _col_sample_rate_per_tree = 1.0; //fraction of columns to sample for each tree
    public double _colsample_bytree = 1.0;

    public KeyValue[] _monotone_constraints;

    public float _max_abs_leafnode_pred = 0;
    public float _max_delta_step = 0;

    public int _score_tree_interval = 0; // score every so many trees (no matter what)
    public int _initial_score_interval = 4000; //Adding this parameter to take away the hard coded value of 4000 for scoring the first  4 secs
    public int _score_interval = 4000; //Adding this parameter to take away the hard coded value of 4000 for scoring each iteration every 4 secs
    public float _min_split_improvement = 0;
    public float _gamma;

    // Runtime options
    public int _nthread = -1;

    // LightGBM specific (only for grow_policy == lossguide)
    public int _max_bins = 256;
    public int _max_leaves = 0;
    public float _min_sum_hessian_in_leaf = 100;
    public float _min_data_in_leaf = 0;

    // XGBoost specific options
    public TreeMethod _tree_method = TreeMethod.auto;
    public GrowPolicy _grow_policy = GrowPolicy.depthwise;
    public Booster _booster = Booster.gbtree;
    public DMatrixType _dmatrix_type = DMatrixType.auto;
    public float _reg_lambda = 0;
    public float _reg_alpha = 0;

    // Dart specific (booster == dart)
    public DartSampleType _sample_type = DartSampleType.uniform;
    public DartNormalizeType _normalize_type = DartNormalizeType.tree;
    public float _rate_drop = 0;
    public boolean _one_drop = false;
    public float _skip_drop = 0;
    public int _gpu_id = 0; // which GPU to use
    public Backend _backend = Backend.auto;

    public String algoName() { return "XGBoost"; }
    public String fullName() { return "XGBoost"; }
    public String javaName() { return XGBoostModel.class.getName(); }

    @Override
    public long progressUnits() {
      return _ntrees;
    }

    /**
     * Finds parameter settings that are not available on GPU backend.
     * In this case the CPU backend should be used instead of GPU.
     * @return map of parameter name -> parameter value
     */
    Map<String, Object> gpuIncompatibleParams() {
      Map<String, Object> incompat = new HashMap<>();
      if (_max_depth > 15 || _max_depth < 1) {
        incompat.put("max_depth",  _max_depth + " . Max depth must be greater than 0 and lower than 16 for GPU backend.");
      }
      if (_grow_policy == GrowPolicy.lossguide)
        incompat.put("grow_policy", GrowPolicy.lossguide); // See PUBDEV-5302 (param.grow_policy != TrainParam::kLossGuide Loss guided growth policy not supported. Use CPU algorithm.)
      return incompat;
    }

    Map<String, Integer> monotoneConstraints() {
      if (_monotone_constraints == null || _monotone_constraints.length == 0) {
        return Collections.emptyMap();
      }
      Map<String, Integer> constraints = new HashMap<>(_monotone_constraints.length);
      for (KeyValue constraint : _monotone_constraints) {
        final double val = constraint.getValue();
        if (val == 0) {
          continue;
        }
        if (constraints.containsKey(constraint.getKey())) {
          throw new IllegalStateException("Duplicate definition of constraint for feature '" + constraint.getKey() + "'.");
        }
        final int direction = val < 0 ? -1 : 1;
        constraints.put(constraint.getKey(), direction);
      }
      return constraints;
    }

  }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    switch(_output.getModelCategory()) {
      case Binomial:    return new ModelMetricsBinomial.MetricBuilderBinomial(domain);
      case Multinomial: return new ModelMetricsMultinomial.MetricBuilderMultinomial(_output.nclasses(),domain);
      case Regression:  return new ModelMetricsRegression.MetricBuilderRegression();
      default: throw H2O.unimpl();
    }
  }

  public XGBoostModel(Key<XGBoostModel> selfKey, XGBoostParameters parms, XGBoostOutput output, Frame train, Frame valid) {
    super(selfKey,parms,output);
    final DataInfo dinfo = makeDataInfo(train, valid, _parms, output.nclasses());
    DKV.put(dinfo);
    setDataInfoToOutput(dinfo);
    model_info = new XGBoostModelInfo(parms);
    model_info._dataInfoKey = dinfo._key;
  }

  public static BoosterParms createParams(XGBoostParameters p, int nClasses, String[] coefNames) {
    Map<String, Object> params = new HashMap<>();

    // Common parameters with H2O GBM
    if (p._n_estimators != 0) {
      Log.info("Using user-provided parameter n_estimators instead of ntrees.");
      params.put("nround", p._n_estimators);
      p._ntrees = p._n_estimators;
    } else {
      params.put("nround", p._ntrees);
    }
    if (p._eta != 0.3) {
      Log.info("Using user-provided parameter eta instead of learn_rate.");
      params.put("eta", p._eta);
      p._learn_rate = p._eta;
    } else {
      params.put("eta", p._learn_rate);
    }
    params.put("max_depth", p._max_depth);
    params.put("silent", p._quiet_mode);
    if (p._subsample!=1.0) {
      Log.info("Using user-provided parameter subsample instead of sample_rate.");
      params.put("subsample", p._subsample);
      p._sample_rate = p._subsample;
    } else {
      params.put("subsample", p._sample_rate);
    }
    if (p._colsample_bytree!=1.0) {
      Log.info("Using user-provided parameter colsample_bytree instead of col_sample_rate_per_tree.");
      params.put("colsample_bytree", p._colsample_bytree);
      p._col_sample_rate_per_tree = p._colsample_bytree;
    } else {
      params.put("colsample_bytree", p._col_sample_rate_per_tree);
    }
    if (p._colsample_bylevel!=1.0) {
      Log.info("Using user-provided parameter colsample_bylevel instead of col_sample_rate.");
      params.put("colsample_bylevel", p._colsample_bylevel);
      p._col_sample_rate = p._colsample_bylevel;
    } else {
      params.put("colsample_bylevel", p._col_sample_rate);
    }
    if (p._max_delta_step!=0) {
      Log.info("Using user-provided parameter max_delta_step instead of max_abs_leafnode_pred.");
      params.put("max_delta_step", p._max_delta_step);
      p._max_abs_leafnode_pred = p._max_delta_step;
    } else {
      params.put("max_delta_step", p._max_abs_leafnode_pred);
    }
    params.put("seed", (int)(p._seed % Integer.MAX_VALUE));

    // XGBoost specific options
    params.put("tree_method", p._tree_method.toString());
    params.put("grow_policy", p._grow_policy.toString());
    if (p._grow_policy== XGBoostParameters.GrowPolicy.lossguide) {
      params.put("max_bins", p._max_bins);
      params.put("max_leaves", p._max_leaves);
      params.put("min_sum_hessian_in_leaf", p._min_sum_hessian_in_leaf);
      params.put("min_data_in_leaf", p._min_data_in_leaf);
    }
    params.put("booster", p._booster.toString());
    if (p._booster== XGBoostParameters.Booster.dart) {
      params.put("sample_type", p._sample_type.toString());
      params.put("normalize_type", p._normalize_type.toString());
      params.put("rate_drop", p._rate_drop);
      params.put("one_drop", p._one_drop ? "1" : "0");
      params.put("skip_drop", p._skip_drop);
    }
    if ( p._backend == XGBoostParameters.Backend.auto || p._backend == XGBoostParameters.Backend.gpu ) {
      if (H2O.getCloudSize() > 1) {
        Log.info("GPU backend not supported in distributed mode. Using CPU backend.");
      } else if (! p.gpuIncompatibleParams().isEmpty()) {
        Log.info("GPU backend not supported for the choice of parameters (" + p.gpuIncompatibleParams() + "). Using CPU backend.");
      } else if (XGBoost.hasGPU(H2O.CLOUD.members()[0], p._gpu_id)) {
        Log.info("Using GPU backend (gpu_id: " + p._gpu_id + ").");
        params.put("gpu_id", p._gpu_id);
        if (p._tree_method == XGBoostParameters.TreeMethod.exact) {
          Log.info("Using grow_gpu (exact) updater.");
          params.put("tree_method", "exact");
          params.put("updater", "grow_gpu");
        }
        else {
          Log.info("Using grow_gpu_hist (approximate) updater.");
          params.put("max_bins", p._max_bins);
          params.put("tree_method", "exact");
          params.put("updater", "grow_gpu_hist");
        }
      } else {
        Log.info("No GPU (gpu_id: "+p._gpu_id + ") found. Using CPU backend.");
      }
    } else {
      assert p._backend == XGBoostParameters.Backend.cpu;
      Log.info("Using CPU backend.");
    }
    if (p._min_child_weight!=1) {
      Log.info("Using user-provided parameter min_child_weight instead of min_rows.");
      params.put("min_child_weight", p._min_child_weight);
      p._min_rows = p._min_child_weight;
    } else {
      params.put("min_child_weight", p._min_rows);
    }
    if (p._gamma!=0) {
      Log.info("Using user-provided parameter gamma instead of min_split_improvement.");
      params.put("gamma", p._gamma);
      p._min_split_improvement = p._gamma;
    } else {
      params.put("gamma", p._min_split_improvement);
    }

    params.put("lambda", p._reg_lambda);
    params.put("alpha", p._reg_alpha);

    if (nClasses==2) {
      params.put("objective", ObjectiveType.BINARY_LOGISTIC.getId());
    } else if (nClasses==1) {
      if (p._distribution == DistributionFamily.gamma) {
        params.put("objective", ObjectiveType.REG_GAMMA.getId());
      } else if (p._distribution == DistributionFamily.tweedie) {
        params.put("objective", ObjectiveType.REG_TWEEDIE.getId());
        params.put("tweedie_variance_power", p._tweedie_power);
      } else if (p._distribution == DistributionFamily.poisson) {
        params.put("objective", ObjectiveType.COUNT_POISSON.getId());
      } else if (p._distribution == DistributionFamily.gaussian || p._distribution==DistributionFamily.AUTO) {
        params.put("objective", ObjectiveType.REG_LINEAR.getId());
      } else {
        throw new UnsupportedOperationException("No support for distribution=" + p._distribution.toString());
      }
    } else {
      params.put("objective", ObjectiveType.MULTI_SOFTPROB.getId());
      params.put("num_class", nClasses);
    }
    assert ObjectiveType.fromXGBoost((String) params.get("objective")) != null;

    final int nthreadMax = getMaxNThread();
    final int nthread = p._nthread != -1 ? Math.min(p._nthread, nthreadMax) : nthreadMax;
    if (nthread < p._nthread) {
      Log.warn("Requested nthread=" + p._nthread + " but the cluster has only " + nthreadMax + " available." +
              "Training will use nthread=" + nthreadMax + " instead of the user specified value.");
    }
    params.put("nthread", nthread);

    Map<String, Integer> monotoneConstraints = p.monotoneConstraints();
    if (! monotoneConstraints.isEmpty()) {
      int constraintsUsed = 0;
      StringBuilder sb = new StringBuilder();
      sb.append("(");
      for (String coef : coefNames) {
        final String direction;
        if (monotoneConstraints.containsKey(coef)) {
          direction = monotoneConstraints.get(coef).toString();
          constraintsUsed++;
        } else {
          direction = "0";
        }
        sb.append(direction);
        sb.append(",");
      }
      sb.replace(sb.length()-1, sb.length(), ")");
      params.put("monotone_constraints", sb.toString());
      assert constraintsUsed == monotoneConstraints.size();
    }

    Log.info("XGBoost Parameters:");
    for (Map.Entry<String,Object> s : params.entrySet()) {
      Log.info(" " + s.getKey() + " = " + s.getValue());
    }
    Log.info("");

    return BoosterParms.fromMap(Collections.unmodifiableMap(params));
  }

  private static int getMaxNThread() {
    return Integer.getInteger(H2O.OptArgs.SYSTEM_PROP_PREFIX + "xgboost.nthread", H2O.ARGS.nthreads);
  }

  @Override protected AutoBuffer writeAll_impl(AutoBuffer ab) {
    ab.putKey(model_info._dataInfoKey);
    return super.writeAll_impl(ab);
  }

  @Override protected Keyed readAll_impl(AutoBuffer ab, Futures fs) {
    ab.getKey(model_info._dataInfoKey, fs);
    return super.readAll_impl(ab, fs);
  }

  @Override
  public XGBoostMojoWriter getMojo() {
    return new XGBoostMojoWriter(this);
  }

  private ModelMetrics makeMetrics(Frame data, Frame originalData, String description) {
    Log.debug("Making metrics: " + description);
    XGBoostScoreTask.XGBoostScoreTaskResult score = XGBoostScoreTask.runScoreTask(
            model_info(), _output, _parms, null, data, originalData, true, this);
    score.preds.remove();
    return score.mm;
  }

  /**
   * Score an XGBoost model on training and validation data (optional)
   * Note: every row is scored, all observation weights are assumed to be equal
   * @param _train training data in the form of matrix
   * @param _valid validation data (optional, can be null)
   */
  final void doScoring(Frame _train, Frame _trainOrig, Frame _valid, Frame _validOrig) {
    ModelMetrics mm = makeMetrics(_train, _trainOrig, "Metrics reported on training frame");
    _output._training_metrics = mm;
    _output._scored_train[_output._ntrees].fillFrom(mm);
    addModelMetrics(mm);
    // Optional validation part
    if (_valid!=null) {
      mm = makeMetrics(_valid, _validOrig, "Metrics reported on validation frame");
      _output._validation_metrics = mm;
      _output._scored_valid[_output._ntrees].fillFrom(mm);
      addModelMetrics(mm);
    }
  }

  VarImp computeVarImp(Map<String, Integer> varimp) {
    if (varimp.isEmpty())
      return null;
    // compute variable importance
    float[] viFloat = new float[varimp.size()];
    String[] names = new String[varimp.size()];
    int j=0;
    for (Map.Entry<String, Integer> it : varimp.entrySet()) {
      viFloat[j] = it.getValue();
      names[j] = it.getKey();
      j++;
    }
    return new VarImp(viFloat, names);
  }

  @Override
  protected boolean needsPostProcess() {
    return false; // scoring functions return final predictions
  }

  @Override
  protected double[] score0(double[] data, double[] preds) {
    return score0(data, preds, 0.0);
  }

  @Override // per row scoring is slow and should be avoided!
  public double[] score0(final double[] data, final double[] preds, final double offset) {
    final DataInfo di = model_info._dataInfoKey.get();
    assert di != null;
    final double threshold = defaultThreshold();
    Booster booster = null;
    try {
      booster = model_info.deserializeBooster();
      return XGBoostNativeMojoModel.score0(data, offset, preds,
              model_info.deserializeBooster(), di._nums, di._cats, di._catOffsets, di._useAllFactorLevels,
              _output.nclasses(), _output._priorClassDist, threshold, _output._sparse);
    } finally {
      if (booster != null)
        BoosterHelper.dispose(booster);
    }
  }

  private boolean useJavaScoring() {
    return Boolean.getBoolean("sys.ai.h2o.xgboost.predict.java.enable");
  }

  @Override
  protected BigScorePredict setupBigScorePredict(BigScore bs) {
    return useJavaScoring() ? setupBigScorePredictJava() : setupBigScorePredictNative();
  }

  private BigScorePredict setupBigScorePredictNative() {
    DataInfo di = model_info()._dataInfoKey.get();
    assert di != null;
    BoosterParms boosterParms = XGBoostModel.createParams(_parms, _output.nclasses(), di.coefNames());
    return new XGBoostBigScorePredict(boosterParms);
  }

  private BigScorePredict setupBigScorePredictJava() {
    final DataInfo di = model_info._dataInfoKey.get();
    assert di != null;
    return new XGBoostJavaBigScorePredict(di, _output, defaultThreshold(), model_info()._boosterBytes);
  }

  private class XGBoostBigScorePredict implements BigScorePredict {
    private final BoosterParms _boosterParms;

    private XGBoostBigScorePredict(BoosterParms boosterParms) {
      _boosterParms = boosterParms;
    }

    @Override
    public BigScoreChunkPredict initMap(Frame fr, Chunk[] chks) {
      float[][] preds = scoreChunk(fr, chks);
      return new XGBoostBigScoreChunkPredict(_output.nclasses(), preds, defaultThreshold());
    }

    private float[][] scoreChunk(Frame fr, Chunk[] chks) {
      return XGBoostScoreTask.scoreChunk(model_info(), _parms, _boosterParms, _output, fr, chks);
    }
  }

  private static class XGBoostBigScoreChunkPredict implements BigScoreChunkPredict {
    private final int _nclasses;
    private final float[][] _preds;
    private final double _threshold;

    private XGBoostBigScoreChunkPredict(int nclasses, float[][] preds, double threshold) {
      _nclasses = nclasses;
      _preds = preds;
      _threshold = threshold;
    }

    @Override
    public double[] score0(Chunk[] chks, double offset, int row_in_chunk, double[] tmp, double[] preds) {
      for (int i = 0; i < tmp.length; i++) {
        tmp[i] = chks[i].atd(row_in_chunk);
      }
      return XGBoostMojoModel.toPreds(tmp, _preds[row_in_chunk], preds, _nclasses, null, _threshold);
    }

    @Override
    public void close() {}
  }

  private class XGBoostJavaBigScorePredict implements BigScorePredict {
    private final DataInfo _di;
    private final XGBoostOutput _output;
    private final double _threshold;
    private final Predictor _predictor;

    XGBoostJavaBigScorePredict(DataInfo di, XGBoostOutput output, double threshold, byte[] boosterBytes) {
      _di = di;
      _output = output;
      _threshold = threshold;
      _predictor = PredictorFactory.makePredictor(boosterBytes);
    }

    @Override
    public BigScoreChunkPredict initMap(Frame fr, Chunk[] chks) {
      return new XGboostJavaBigScoreChunkPredict(_di, _output, _threshold, _predictor);
    }

  }

  private static class XGboostJavaBigScoreChunkPredict implements BigScoreChunkPredict {
    private final XGBoostOutput _output;
    private final double _threshold;
    private final Predictor _predictor;
    private final MutableOneHotEncoderFVec _row;

    public XGboostJavaBigScoreChunkPredict(DataInfo di, XGBoostOutput output, double threshold, Predictor predictor) {
      _output = output;
      _threshold = threshold;
      _predictor = predictor;
      _row = new MutableOneHotEncoderFVec(di, _output._sparse);
    }

    @Override
    public double[] score0(Chunk[] chks, double offset, int row_in_chunk, double[] tmp, double[] preds) {
      if (offset != 0) throw new UnsupportedOperationException("Unsupported: offset != 0");

      assert _output.nfeatures() == tmp.length;
      for (int i = 0; i < tmp.length; i++) {
        tmp[i] = chks[i].atd(row_in_chunk);
      }

      _row.setInput(tmp);

      float[] out = _predictor.predict(_row);

      return XGBoostMojoModel.toPreds(tmp, out, preds, _output.nclasses(), _output._priorClassDist, _threshold);
    }

    @Override
    public void close() {}
  }

  private static class MutableOneHotEncoderFVec implements FVec {
    private final DataInfo _di;
    private final boolean _treatsZeroAsNA;
    private final int[] _catMap;
    private final int[] _catValues;
    private final float[] _numValues;
    private final float _notHot;

    MutableOneHotEncoderFVec(DataInfo di, boolean treatsZeroAsNA) {
      _di = di;
      _catValues = new int[_di._cats];
      _treatsZeroAsNA = treatsZeroAsNA;
      _notHot = _treatsZeroAsNA ? Float.NaN : 0;
      if (_di._catOffsets == null) {
        _catMap = new int[0];
      } else {
        _catMap = new int[_di._catOffsets[_di._cats]];
        for (int c = 0; c < _di._cats; c++) {
          for (int j = _di._catOffsets[c]; j < _di._catOffsets[c+1]; j++)
            _catMap[j] = c;
        }
      }
      _numValues = new float[_di._nums];
    }

    void setInput(double[] input) {
      GenModel.setCats(input, _catValues, _di._cats, _di._catOffsets, _di._useAllFactorLevels);
      for (int i = 0; i < _numValues.length; i++) {
        float val = (float) input[_di._cats + i];
        _numValues[i] = _treatsZeroAsNA && (val == 0) ? Float.NaN : val;
      }
    }

    @Override
    public final float fvalue(int index) {
      if (index >= _catMap.length)
        return _numValues[index - _catMap.length];

      final boolean isHot = _catValues[_catMap[index]] == index;
      return isHot ? 1 : _notHot;
    }
  }

  private void setDataInfoToOutput(DataInfo dinfo) {
    _output._names = dinfo._adaptedFrame.names();
    _output._domains = dinfo._adaptedFrame.domains();
    _output._origNames = _parms._train.get().names();
    _output._origDomains = _parms._train.get().domains();
    _output._nums = dinfo._nums;
    _output._cats = dinfo._cats;
    _output._catOffsets = dinfo._catOffsets;
    _output._useAllFactorLevels = dinfo._useAllFactorLevels;
  }

  @Override
  protected Futures remove_impl(Futures fs) {
    if (model_info()._dataInfoKey !=null)
      model_info()._dataInfoKey.get().remove(fs);
    return super.remove_impl(fs);
  }

  @Override
  public SharedTreeGraph convert(final int treeNumber, final String treeClassName) {
    GradBooster booster = null;
    try {
      booster = new Predictor(new ByteArrayInputStream(model_info._boosterBytes)).getBooster();
    } catch (IOException e) {
      Log.err(e);
      throw new IllegalStateException("Booster bytes inaccessible. Not able to extract the predictor and construct tree graph.");
    }

    if (!(booster instanceof GBTree)) {
      throw new IllegalArgumentException(String.format("Given XGBoost model is not backed by a tree-based booster. Booster class is %d",
              booster.getClass().getCanonicalName()));
    }

    final RegTree[][] groupedTrees = ((GBTree) booster).getGroupedTrees();
    final int treeClass = getXGBoostClassIndex(treeClassName);
    if (treeClass >= groupedTrees.length) {
      throw new IllegalArgumentException(String.format("Given XGBoost model does not have given class '%s'.", treeClassName));
    }

    final RegTree[] treesInGroup = groupedTrees[treeClass];

    if (treeNumber >= treesInGroup.length || treeNumber < 0) {
      throw new IllegalArgumentException(String.format("There is no such tree number for given class. Total number of trees is %d.", treesInGroup.length));
    }

    final RegTreeNode[] treeNodes = treesInGroup[treeNumber].getNodes();
    assert treeNodes.length >= 1;

    SharedTreeGraph sharedTreeGraph = new SharedTreeGraph();
    final SharedTreeSubgraph sharedTreeSubgraph = sharedTreeGraph.makeSubgraph(_output._training_metrics._description);

    final XGBoostUtils.FeatureProperties featureProperties = XGBoostUtils.assembleFeatureNames(model_info._dataInfoKey.get()); // XGBoost's usage of one-hot encoding assumed
    constructSubgraph(treeNodes, sharedTreeSubgraph.makeRootNode(), 0, sharedTreeSubgraph, featureProperties, true); // Root node is at index 0
    return sharedTreeGraph;
  }

  private static void constructSubgraph(final RegTreeNode[] xgBoostNodes, final SharedTreeNode sharedTreeNode,
                                        final int nodeIndex, final SharedTreeSubgraph sharedTreeSubgraph,
                                        final XGBoostUtils.FeatureProperties featureProperties, boolean inclusiveNA) {
    final RegTreeNode xgBoostNode = xgBoostNodes[nodeIndex];
    // Not testing for NaNs, as SharedTreeNode uses NaNs as default values.
    //No domain set, as the structure mimics XGBoost's tree, which is numeric-only
    if (featureProperties._oneHotEncoded[xgBoostNode.split_index()]) {
      //Shared tree model uses < to the left and >= to the right. Transforiming one-hot encoded categoricals
      // from 0 to 1 makes it fit the current split description logic
      sharedTreeNode.setSplitValue(1.0F);
    } else {
      sharedTreeNode.setSplitValue(xgBoostNode.getSplitCondition());
    }
    sharedTreeNode.setPredValue(xgBoostNode.getLeafValue());
    sharedTreeNode.setCol(xgBoostNode.split_index(), featureProperties._names[xgBoostNode.split_index()]);
    sharedTreeNode.setInclusiveNa(inclusiveNA);
    sharedTreeNode.setNodeNumber(nodeIndex);

    if (xgBoostNode.getLeftChildIndex() != -1) {
      constructSubgraph(xgBoostNodes, sharedTreeSubgraph.makeLeftChildNode(sharedTreeNode),
              xgBoostNode.getLeftChildIndex(), sharedTreeSubgraph, featureProperties, xgBoostNode.default_left());
    }

    if (xgBoostNode.getRightChildIndex() != -1) {
      constructSubgraph(xgBoostNodes, sharedTreeSubgraph.makeRightChildNode(sharedTreeNode),
              xgBoostNode.getRightChildIndex(), sharedTreeSubgraph, featureProperties, !xgBoostNode.default_left());
    }
  }


  private final int getXGBoostClassIndex(final String treeClass) {
    final ModelCategory modelCategory = _output.getModelCategory();
    if (treeClass == null && ModelCategory.Regression.equals(modelCategory)) return 0;
    if (treeClass == null && !ModelCategory.Regression.equals(modelCategory)) {
      throw new IllegalArgumentException("Non-regressional models require tree class specified.");
    }

    final String[] domain = _output._domains[_output._domains.length - 1];
    if(ModelCategory.Regression.equals(modelCategory) && treeClass != null){
      throw new IllegalArgumentException("There should be no tree class specified for regression.");
    }
    final int treeClassIndex = ArrayUtils.find(domain, treeClass);

    if (ModelCategory.Binomial.equals(modelCategory) && treeClassIndex != 0) {
      throw new IllegalArgumentException(String.format("For binomial XGBoost model, only one tree for class %s has been built.", domain[0]));
    } else if (treeClassIndex < 0) {
      throw new IllegalArgumentException(String.format("No such class '%s' in tree.", treeClass));
    }

    return treeClassIndex;
  }

}
