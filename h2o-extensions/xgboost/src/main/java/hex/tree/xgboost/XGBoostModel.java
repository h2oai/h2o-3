package hex.tree.xgboost;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.gbm.GBTree;
import biz.k11i.xgboost.gbm.GradBooster;
import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.RegTreeNode;
import biz.k11i.xgboost.tree.RegTreeNodeStat;
import hex.*;
import hex.genmodel.algos.tree.*;
import hex.genmodel.algos.xgboost.XGBoostJavaMojoModel;
import hex.genmodel.algos.xgboost.XGBoostMojoModel;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.FriedmanPopescusH;
import hex.tree.CalibrationHelper;
import hex.tree.xgboost.predict.*;
import hex.tree.xgboost.util.PredictConfiguration;
import hex.util.EffectiveParametersUtils;
import org.apache.log4j.Logger;
import water.*;
import water.codegen.CodeGeneratorPipeline;
import water.fvec.Frame;
import water.fvec.Vec;
import water.udf.CFuncRef;
import water.util.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static hex.genmodel.algos.xgboost.XGBoostMojoModel.ObjectiveType;
import static hex.tree.xgboost.XGBoost.makeDataInfo;
import static hex.tree.xgboost.util.GpuUtils.hasGPU;
import static water.H2O.OptArgs.SYSTEM_PROP_PREFIX;

public class XGBoostModel extends Model<XGBoostModel, XGBoostModel.XGBoostParameters, XGBoostOutput> 
        implements SharedTreeGraphConverter, Model.LeafNodeAssignment, Model.Contributions, FeatureInteractionsCollector, Model.UpdateAuxTreeWeights, FriedmanPopescusHCollector {

  private static final Logger LOG = Logger.getLogger(XGBoostModel.class);

  private static final String PROP_VERBOSITY = H2O.OptArgs.SYSTEM_PROP_PREFIX + "xgboost.verbosity";
  private static final String PROP_NTHREAD = SYSTEM_PROP_PREFIX + "xgboost.nthreadMax";

  private XGBoostModelInfo model_info;

  public XGBoostModelInfo model_info() { return model_info; }

  public static class XGBoostParameters extends Model.Parameters implements Model.GetNTrees, CalibrationHelper.ParamsWithCalibration {
    public enum TreeMethod {
      auto, exact, approx, hist
    }
    public enum GrowPolicy {
      depthwise, lossguide
    }
    public enum Booster {
      gbtree, gblinear, dart
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
    public enum FeatureSelector {
      cyclic, shuffle, random, greedy, thrifty
    }
    public enum Updater {
      gpu_hist, shotgun, coord_descent, gpu_coord_descent,
    }

    // H2O GBM options
    public boolean _quiet_mode = true;

    public int _ntrees = 50; // Number of trees in the final model. Grid Search, comma sep values:50,100,150,200
    /**
     * @deprecated will be removed in 3.30.0.1, use _ntrees
     */
    public int _n_estimators; // This doesn't seem to be used anywhere... (not in clients)

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
    public double _colsample_bynode = 1.0;

    public double _col_sample_rate_per_tree = 1.0; //fraction of columns to sample for each tree
    public double _colsample_bytree = 1.0;

    public KeyValue[] _monotone_constraints;
    public String[][] _interaction_constraints;

    public float _max_abs_leafnode_pred = 0;
    public float _max_delta_step = 0;

    public int _score_tree_interval = 0; // score every so many trees (no matter what)
    public int _initial_score_interval = 4000; //Adding this parameter to take away the hard coded value of 4000 for scoring the first  4 secs
    public int _score_interval = 4000; //Adding this parameter to take away the hard coded value of 4000 for scoring each iteration every 4 secs
    public float _min_split_improvement = 0;
    public float _gamma;

    // Runtime options
    public int _nthread = -1;
    public String _save_matrix_directory; // dump the xgboost matrix to this directory
    public boolean _build_tree_one_node = false; // force to run on single node

    // LightGBM specific (only for grow_policy == lossguide)
    public int _max_bins = 256;
    public int _max_leaves = 0;

    // XGBoost specific options
    public TreeMethod _tree_method = TreeMethod.auto;
    public GrowPolicy _grow_policy = GrowPolicy.depthwise;
    public Booster _booster = Booster.gbtree;
    public DMatrixType _dmatrix_type = DMatrixType.auto;
    public float _reg_lambda = 1;
    public float _reg_alpha = 0;
    public float _scale_pos_weight = 1;

    // Platt scaling (by default)
    public boolean _calibrate_model;
    public Key<Frame> _calibration_frame;
    public CalibrationHelper.CalibrationMethod _calibration_method = CalibrationHelper.CalibrationMethod.AUTO;

    // Dart specific (booster == dart)
    public DartSampleType _sample_type = DartSampleType.uniform;
    public DartNormalizeType _normalize_type = DartNormalizeType.tree;
    public float _rate_drop = 0;
    public boolean _one_drop = false;
    public float _skip_drop = 0;
    public int[] _gpu_id; // which GPU to use
    public Backend _backend = Backend.auto;

    // GBLiner specific (booster == gblinear)
    // lambda, alpha support also for gbtree
    public FeatureSelector _feature_selector = FeatureSelector.cyclic;
    public int _top_k;
    public Updater _updater;

    public String _eval_metric;
    public boolean _score_eval_metric_only;

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
      if (!(TreeMethod.auto == _tree_method || TreeMethod.hist == _tree_method) && Booster.gblinear != _booster) {
        incompat.put("tree_method", "Only auto and hist are supported tree_method on GPU backend.");
      } 
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

    @Override
    public int getNTrees() {
      return _ntrees;
    }

    @Override
    public Frame getCalibrationFrame() {
      return _calibration_frame != null ? _calibration_frame.get() : null;
    }

    @Override
    public boolean calibrateModel() {
      return _calibrate_model;
    }

    @Override
    public CalibrationHelper.CalibrationMethod getCalibrationMethod() {
      return _calibration_method;
    }

    @Override
    public void setCalibrationMethod(CalibrationHelper.CalibrationMethod calibrationMethod) {
      _calibration_method = calibrationMethod;
    }

    @Override
    public Parameters getParams() {
      return this;
    }

    static String[] CHECKPOINT_NON_MODIFIABLE_FIELDS = { 
        "_tree_method", "_grow_policy", "_booster", "_sample_rate", "_max_depth", "_min_rows" 
    };

  }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    switch(_output.getModelCategory()) {
      case Binomial:    return new ModelMetricsBinomial.MetricBuilderBinomial(domain);
      case Multinomial: return new ModelMetricsMultinomial.MetricBuilderMultinomial(_output.nclasses(), domain, _parms._auc_type);
      case Regression:  return new ModelMetricsRegression.MetricBuilderRegression();
      default: throw H2O.unimpl();
    }
  }

  public XGBoostModel(Key<XGBoostModel> selfKey, XGBoostParameters parms, XGBoostOutput output, Frame train, Frame valid) {
    super(selfKey,parms,output);
    final DataInfo dinfo = makeDataInfo(train, valid, _parms);
    DKV.put(dinfo);
    setDataInfoToOutput(dinfo);
    model_info = new XGBoostModelInfo(parms, dinfo);
  }

  @Override
  public void initActualParamValues() {
    super.initActualParamValues();
    EffectiveParametersUtils.initFoldAssignment(_parms);
    _parms._backend = getActualBackend(_parms, true);
    _parms._tree_method = getActualTreeMethod(_parms);
    EffectiveParametersUtils.initCalibrationMethod(_parms);
  }

  public static XGBoostParameters.TreeMethod getActualTreeMethod(XGBoostParameters p) {
    // tree_method parameter is evaluated according to:
    // https://github.com/h2oai/xgboost/blob/96f61fb3be8c4fa0e160dd6e82677dfd96a5a9a1/src/gbm/gbtree.cc#L127 
    // + we don't use external-memory data matrix feature in h2o 
    // + https://github.com/h2oai/h2o-3/blob/b68e544d8dac3c5c0ed16759e6bf7e8288573ab5/h2o-extensions/xgboost/src/main/java/hex/tree/xgboost/XGBoostModel.java#L348
    if ( p._tree_method == XGBoostModel.XGBoostParameters.TreeMethod.auto) {
      if (p._backend == XGBoostParameters.Backend.gpu) {
        return XGBoostParameters.TreeMethod.hist;
      } else if (H2O.getCloudSize() > 1) {
        if (p._monotone_constraints != null && p._booster != XGBoostParameters.Booster.gblinear && p._backend != XGBoostParameters.Backend.gpu) {
          return XGBoostParameters.TreeMethod.hist;
        } else {
          return XGBoostModel.XGBoostParameters.TreeMethod.approx;
        }
      } else if (p.train() != null && p.train().numRows() >= (4 << 20)) {
        return XGBoostModel.XGBoostParameters.TreeMethod.approx;
      } else {
        return XGBoostModel.XGBoostParameters.TreeMethod.exact;
      }
    } else {
      return p._tree_method;
    }
  }

  public void initActualParamValuesAfterOutputSetup(boolean isClassifier, int nclasses) {
    EffectiveParametersUtils.initStoppingMetric(_parms, isClassifier);
    EffectiveParametersUtils.initCategoricalEncoding(_parms, Parameters.CategoricalEncodingScheme.OneHotInternal);
    EffectiveParametersUtils.initDistribution(_parms, nclasses);
    _parms._dmatrix_type = _output._sparse ? XGBoostModel.XGBoostParameters.DMatrixType.sparse : XGBoostModel.XGBoostParameters.DMatrixType.dense;
  }
  
  public static XGBoostParameters.Backend getActualBackend(XGBoostParameters p, boolean verbose) {
    Consumer<String> log = verbose ? LOG::info : LOG::debug;
    if ( p._backend == XGBoostParameters.Backend.auto || p._backend == XGBoostParameters.Backend.gpu ) {
      if (H2O.getCloudSize() > 1 && !p._build_tree_one_node && !XGBoost.allowMultiGPU()) {
        log.accept("GPU backend not supported in distributed mode. Using CPU backend.");
        return XGBoostParameters.Backend.cpu;
      } else if (! p.gpuIncompatibleParams().isEmpty()) {
        log.accept("GPU backend not supported for the choice of parameters (" + p.gpuIncompatibleParams() + "). Using CPU backend.");
        return XGBoostParameters.Backend.cpu;
      } else if (hasGPU(H2O.CLOUD.members()[0], p._gpu_id)) {
        log.accept("Using GPU backend (gpu_id: " + Arrays.toString(p._gpu_id) + ").");
        return XGBoostParameters.Backend.gpu;
      } else {
        log.accept("No GPU (gpu_id: " + Arrays.toString(p._gpu_id) + ") found. Using CPU backend.");
        return XGBoostParameters.Backend.cpu;
      }
    } else {
      log.accept("Using CPU backend.");
      return XGBoostParameters.Backend.cpu;
    }
  }
  
  public static Map<String, Object> createParamsMap(XGBoostParameters p, int nClasses, String[] coefNames) {
    Map<String, Object> params = new HashMap<>();

    // Common parameters with H2O GBM
    if (p._n_estimators != 0) {
      LOG.info("Using user-provided parameter n_estimators instead of ntrees.");
      params.put("nround", p._n_estimators);
      p._ntrees = p._n_estimators;
    } else {
      params.put("nround", p._ntrees);
      p._n_estimators = p._ntrees;
    }
    if (p._eta != 0.3) {
      params.put("eta", p._eta);
      p._learn_rate = p._eta;
    } else {
      params.put("eta", p._learn_rate);
      p._eta = p._learn_rate;
    }
    params.put("max_depth", p._max_depth);
    if (System.getProperty(PROP_VERBOSITY) != null) {
      params.put("verbosity", System.getProperty(PROP_VERBOSITY));
    } else {
      params.put("silent", p._quiet_mode);
    }
    if (p._subsample != 1.0) {
      params.put("subsample", p._subsample);
      p._sample_rate = p._subsample;
    } else {
      params.put("subsample", p._sample_rate);
      p._subsample = p._sample_rate;
    }
    if (p._colsample_bytree != 1.0) {
      params.put("colsample_bytree", p._colsample_bytree);
      p._col_sample_rate_per_tree = p._colsample_bytree;
    } else {
      params.put("colsample_bytree", p._col_sample_rate_per_tree);
      p._colsample_bytree = p._col_sample_rate_per_tree;
    }
    if (p._colsample_bylevel != 1.0) {
      params.put("colsample_bylevel", p._colsample_bylevel);
      p._col_sample_rate = p._colsample_bylevel;
    } else {
      params.put("colsample_bylevel", p._col_sample_rate);
      p._colsample_bylevel = p._col_sample_rate;
    }
    if (p._colsample_bynode != 1.0) {
      params.put("colsample_bynode", p._colsample_bynode);
    }    
    if (p._max_delta_step != 0) {
      params.put("max_delta_step", p._max_delta_step);
      p._max_abs_leafnode_pred = p._max_delta_step;
    } else {
      params.put("max_delta_step", p._max_abs_leafnode_pred);
      p._max_delta_step = p._max_abs_leafnode_pred;
    }
    params.put("seed", (int)(p._seed % Integer.MAX_VALUE));

    // XGBoost specific options
    params.put("grow_policy", p._grow_policy.toString());
    if (p._grow_policy == XGBoostParameters.GrowPolicy.lossguide) {
      params.put("max_bin", p._max_bins);
      params.put("max_leaves", p._max_leaves);
    }
    params.put("booster", p._booster.toString());
    if (p._booster == XGBoostParameters.Booster.dart) {
      params.put("sample_type", p._sample_type.toString());
      params.put("normalize_type", p._normalize_type.toString());
      params.put("rate_drop", p._rate_drop);
      params.put("one_drop", p._one_drop ? "1" : "0");
      params.put("skip_drop", p._skip_drop);
    }
    if (p._booster == XGBoostParameters.Booster.gblinear) {
      params.put("feature_selector", p._feature_selector.toString());
      params.put("top_k", p._top_k);
    }
    XGBoostParameters.Backend actualBackend = getActualBackend(p, true);
    XGBoostParameters.TreeMethod actualTreeMethod = getActualTreeMethod(p);
    if (actualBackend == XGBoostParameters.Backend.gpu) {
      if (p._gpu_id != null && p._gpu_id.length > 0) {
        params.put("gpu_id", p._gpu_id[0]);
      } else {
        params.put("gpu_id", 0);
      }
      // we are setting updater rather than tree_method here to keep CPU predictor, which is faster
      if (p._booster == XGBoostParameters.Booster.gblinear && p._updater == null) {
        LOG.info("Using gpu_coord_descent updater."); 
        params.put("updater", XGBoostParameters.Updater.gpu_coord_descent.toString());
      } else {
        LOG.info("Using gpu_hist tree method.");
        params.put("max_bin", p._max_bins);
        params.put("tree_method", XGBoostParameters.Updater.gpu_hist.toString());
      }
    } else if (p._booster == XGBoostParameters.Booster.gblinear && p._updater == null) {
      LOG.info("Using coord_descent updater.");
      params.put("updater", XGBoostParameters.Updater.coord_descent.toString());
    } else if (H2O.CLOUD.size() > 1 && p._tree_method == XGBoostParameters.TreeMethod.auto &&
        p._monotone_constraints != null) {
      LOG.info("Using hist tree method for distributed computation with monotone_constraints.");
      params.put("tree_method", actualTreeMethod.toString());
      params.put("max_bin", p._max_bins);
    } else {
      LOG.info("Using " + p._tree_method.toString() + " tree method.");
      params.put("tree_method", actualTreeMethod.toString());
      if (p._tree_method == XGBoostParameters.TreeMethod.hist) {
        params.put("max_bin", p._max_bins);
      }
    }
    if (p._updater != null) {
      LOG.info("Using user-provided updater.");
      params.put("updater", p._updater.toString());
    }
    if (p._min_child_weight != 1) {
      LOG.info("Using user-provided parameter min_child_weight instead of min_rows.");
      params.put("min_child_weight", p._min_child_weight);
      p._min_rows = p._min_child_weight;
    } else {
      params.put("min_child_weight", p._min_rows);
      p._min_child_weight = p._min_rows;
    }
    if (p._gamma != 0) {
      LOG.info("Using user-provided parameter gamma instead of min_split_improvement.");
      params.put("gamma", p._gamma);
      p._min_split_improvement = p._gamma;
    } else {
      params.put("gamma", p._min_split_improvement);
      p._gamma = p._min_split_improvement;
    }

    params.put("lambda", p._reg_lambda);
    params.put("alpha", p._reg_alpha);
    if (p._scale_pos_weight != 1)
      params.put("scale_pos_weight", p._scale_pos_weight);

    // objective function
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
      } else if (p._distribution == DistributionFamily.gaussian || p._distribution == DistributionFamily.AUTO) {
        params.put("objective", ObjectiveType.REG_SQUAREDERROR.getId());
      } else {
        throw new UnsupportedOperationException("No support for distribution=" + p._distribution.toString());
      }
    } else {
      params.put("objective", ObjectiveType.MULTI_SOFTPROB.getId());
      params.put("num_class", nClasses);
    }
    assert ObjectiveType.fromXGBoost((String) params.get("objective")) != null;

    // evaluation metric
    if (p._eval_metric != null) {
      params.put("eval_metric", p._eval_metric);
    }

    final int nthreadMax = getMaxNThread();
    final int nthread = p._nthread != -1 ? Math.min(p._nthread, nthreadMax) : nthreadMax;
    if (nthread < p._nthread) {
      LOG.warn("Requested nthread=" + p._nthread + " but the cluster has only " + nthreadMax + " available." +
              "Training will use nthread=" + nthread + " instead of the user specified value.");
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
    
    String[][] interactionConstraints = p._interaction_constraints;
    if(interactionConstraints != null && interactionConstraints.length > 0) {
      if(!p._categorical_encoding.equals(Parameters.CategoricalEncodingScheme.OneHotInternal)){
        throw new IllegalArgumentException("No support interaction constraint for categorical encoding = " + p._categorical_encoding.toString()+". Constraint interactions are available only for ``AUTO`` (``one_hot_internal`` or ``OneHotInternal``) categorical encoding.");
      }
      params.put("interaction_constraints", createInteractions(interactionConstraints, coefNames, p));
    }
    
    LOG.info("XGBoost Parameters:");
    for (Map.Entry<String,Object> s : params.entrySet()) {
      LOG.info(" " + s.getKey() + " = " + s.getValue());
    }
    LOG.info("");
    return Collections.unmodifiableMap(params);
  }
  
  private static String createInteractions(String[][] interaction_constraints, String[] coefNames, XGBoostParameters params){
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (String[] list : interaction_constraints) {
      sb.append("[");
      for (String item : list) {
        if(item.equals(params._response_column)){
          throw new IllegalArgumentException("'interaction_constraints': Column with the name '" + item + "'is used as response column and cannot be used in interaction.");
        }
        if(item.equals(params._weights_column)){
          throw new IllegalArgumentException("'interaction_constraints': Column with the name '" + item + "'is used as weights column and cannot be used in interaction.");
        }
        if(item.equals(params._fold_column)){
          throw new IllegalArgumentException("'interaction_constraints': Column with the name '" + item + "'is used as fold column and cannot be used in interaction.");
        }
        if(params._ignored_columns != null && ArrayUtils.find(params._ignored_columns, item) != -1) {
          throw new IllegalArgumentException("'interaction_constraints': Column with the name '" + item + "'is set in ignored columns and cannot be used in interaction.");
        }
        // first find only name
        int start = ArrayUtils.findWithPrefix(coefNames, item);
        // find start index and add indices until end index
        if (start == -1) {
          throw new IllegalArgumentException("'interaction_constraints': Column with name '" + item + "' is not in the frame.");
        } else if(start > -1){               // find exact position - no encoding  
          sb.append(start).append(",");
        } else {              // find first occur of the name with prefix - encoding
          start = -start - 2;
          assert coefNames[start].startsWith(item): "The column name should be find correctly.";
          // iterate until find all encoding indices
          int end = start;
          while (end < coefNames.length && coefNames[end].startsWith(item)) {
            sb.append(end).append(",");
            end++;
          }
        }
      }
      sb.replace(sb.length() - 1, sb.length(), "],");
    }
    sb.replace(sb.length() - 1, sb.length(), "]");
    return sb.toString();
  }

  public static BoosterParms createParams(XGBoostParameters p, int nClasses, String[] coefNames) {
    return BoosterParms.fromMap(createParamsMap(p, nClasses, coefNames));
  }

  /** Performs deep clone of given model.  */
  protected XGBoostModel deepClone(Key<XGBoostModel> result) {
    XGBoostModel newModel = IcedUtils.deepCopy(this);
    newModel._key = result;
    // Do not clone model metrics
    newModel._output.clearModelMetrics(false);
    newModel._output._training_metrics = null;
    newModel._output._validation_metrics = null;
    return newModel;
  }
  
  static int getMaxNThread() {
    if (System.getProperty(PROP_NTHREAD) != null) {
      return Integer.getInteger(PROP_NTHREAD);
    } else {
      int maxNodesPerHost = 1;
      Set<String> checkedNodes = new HashSet<>();
      for (H2ONode node : H2O.CLOUD.members()) {
        String nodeHost = node.getIp();
        if (!checkedNodes.contains(nodeHost)) {
          checkedNodes.add(nodeHost);
          long cnt = Stream.of(H2O.CLOUD.members()).filter(h -> h.getIp().equals(nodeHost)).count();
          if (cnt > maxNodesPerHost) {
            maxNodesPerHost = (int) cnt;
          }
        }
      }
      return Math.max(1, H2O.ARGS.nthreads / maxNodesPerHost);
    }
  }

  @Override protected AutoBuffer writeAll_impl(AutoBuffer ab) {
    ab.putKey(model_info.getDataInfoKey());
    ab.putKey(model_info.getAuxNodeWeightsKey());
    return super.writeAll_impl(ab);
  }

  @Override protected Keyed readAll_impl(AutoBuffer ab, Futures fs) {
    ab.getKey(model_info.getDataInfoKey(), fs);
    ab.getKey(model_info.getAuxNodeWeightsKey(), fs);
    return super.readAll_impl(ab, fs);
  }

  @Override
  public XGBoostMojoWriter getMojo() {
    return new XGBoostMojoWriter(this);
  }

  private ModelMetrics makeMetrics(Frame data, Frame originalData, boolean isTrain, String description) {
    LOG.debug("Making metrics: " + description);
    return new XGBoostModelMetrics(_output, data, originalData, isTrain, this, CFuncRef.from(_parms._custom_metric_func)).compute();
  }

  final void doScoring(Frame train, Frame trainOrig, CustomMetric trainCustomMetric,
                       Frame valid, Frame validOrig, CustomMetric validCustomMetric) {
    ModelMetrics mm = makeMetrics(train, trainOrig, true, "Metrics reported on training frame");
    _output._training_metrics = mm;
    if (trainCustomMetric == null) {
      _output._scored_train[_output._ntrees].fillFrom(mm, mm._custom_metric);
    } else {
      _output._scored_train[_output._ntrees].fillFrom(mm, trainCustomMetric);
    }
    addModelMetrics(mm);
    // Optional validation part
    if (valid != null) {
      mm = makeMetrics(valid, validOrig, false, "Metrics reported on validation frame");
      _output._validation_metrics = mm;
      if (validCustomMetric == null) {
        _output._scored_valid[_output._ntrees].fillFrom(mm, mm._custom_metric);
      } else {
        _output._scored_valid[_output._ntrees].fillFrom(mm, validCustomMetric);
      }
      addModelMetrics(mm);
    }
  }

  @Override
  protected Frame postProcessPredictions(Frame adaptedFrame, Frame predictFr, Job j) {
    return CalibrationHelper.postProcessPredictions(predictFr, j, _output);
  }

  @Override
  protected double[] score0(double[] data, double[] preds) {
    return score0(data, preds, 0.0);
  }

  @Override // per row scoring is slow and should be avoided!
  public double[] score0(final double[] data, final double[] preds, final double offset) {
    final DataInfo di = model_info.dataInfo();
    assert di != null;
    MutableOneHotEncoderFVec row = new MutableOneHotEncoderFVec(di, _output._sparse);
    row.setInput(data);
    Predictor predictor = makePredictor(true);
    float[] out;
    if (_output.hasOffset()) {
      out = predictor.predict(row, (float) offset);
    } else if (offset != 0) {
      throw new UnsupportedOperationException("Unsupported: offset != 0");
    } else {
      out = predictor.predict(row);
    }
    return XGBoostMojoModel.toPreds(data, out, preds, _output.nclasses(), _output._priorClassDist, defaultThreshold());
  }

  @Override
  protected XGBoostBigScorePredict setupBigScorePredict(BigScore bs) {
    return setupBigScorePredict(false);
  }

  public XGBoostBigScorePredict setupBigScorePredict(boolean isTrain) {
    DataInfo di = model_info().scoringInfo(isTrain); // always for validation scoring info for scoring (we are not in the training phase)
    return PredictConfiguration.useJavaScoring() ? setupBigScorePredictJava(di) : setupBigScorePredictNative(di);
  }

  private XGBoostBigScorePredict setupBigScorePredictNative(DataInfo di) {
    BoosterParms boosterParms = XGBoostModel.createParams(_parms, _output.nclasses(), di.coefNames());
    return new XGBoostNativeBigScorePredict(model_info, _parms, _output, di, boosterParms, defaultThreshold());
  }

  private XGBoostBigScorePredict setupBigScorePredictJava(DataInfo di) {
    return new XGBoostJavaBigScorePredict(model_info, _output, di, _parms, defaultThreshold());
  }
  
  public XGBoostVariableImportance setupVarImp() {
    if (PredictConfiguration.useJavaScoring()) {
      return new XGBoostJavaVariableImportance(model_info);
    } else {
      return new XGBoostNativeVariableImportance(_key, model_info.getFeatureMap());
    }
  }

  @Override
  public Frame scoreContributions(Frame frame, Key<Frame> destination_key) {
    return scoreContributions(frame, destination_key, null, new ContributionsOptions());
  }

  @Override
  public Frame scoreContributions(Frame frame, Key<Frame> destination_key, Job<Frame> j, ContributionsOptions options) {
    Frame adaptFrm = new Frame(frame);
    adaptTestForTrain(adaptFrm, true, false);

    DataInfo di = model_info().dataInfo();
    assert di != null;
    final String[] featureContribNames = ContributionsOutputFormat.Compact.equals(options._outputFormat) ? 
            _output.features() : di.coefNames();
    final String[] outputNames = ArrayUtils.append(featureContribNames, "BiasTerm");

    if (options.isSortingRequired()) {
      final ContributionComposer contributionComposer = new ContributionComposer();
      int topNAdjusted = contributionComposer.checkAndAdjustInput(options._topN, featureContribNames.length);
      int bottomNAdjusted = contributionComposer.checkAndAdjustInput(options._bottomN, featureContribNames.length);

      int outputSize = Math.min((topNAdjusted+bottomNAdjusted)*2, featureContribNames.length*2);
      String[] names = new String[outputSize+1];
      byte[] types = new byte[outputSize+1];
      String[][] domains = new String[outputSize+1][outputNames.length];

      composeScoreContributionTaskMetadata(names, types, domains, featureContribNames, options);

      return new PredictTreeSHAPSortingTask(di, model_info(), _output, options)
              .withPostMapAction(JobUpdatePostMap.forJob(j))
              .doAll(types, adaptFrm)
              .outputFrame(destination_key, names, domains);
    }

    return new PredictTreeSHAPTask(di, model_info(), _output, options)
            .withPostMapAction(JobUpdatePostMap.forJob(j))
            .doAll(outputNames.length, Vec.T_NUM, adaptFrm)
            .outputFrame(destination_key, outputNames, null);
  }


  @Override
  public Frame scoreContributions(Frame frame, Key<Frame> destination_key, Job<Frame> j, ContributionsOptions options, Frame backgroundFrame) {
    Log.info("Starting contributions calculation for " + this._key + "...");
    try (Scope.Safe s = Scope.safe(frame, backgroundFrame)) {
      Frame contributions;
      if (null == backgroundFrame) {
        contributions = scoreContributions(frame, destination_key, j, options);
      } else {
        Frame adaptedFrame = adaptFrameForScore(frame, false);
        DKV.put(adaptedFrame);
        Frame adaptedBgFrame = adaptFrameForScore(backgroundFrame, false);
        DKV.put(adaptedBgFrame);

        DataInfo di = model_info().dataInfo();
        assert di != null;
        final String[] featureContribNames = ContributionsOutputFormat.Compact.equals(options._outputFormat) ?
                _output.features() : di.coefNames();
        final String[] outputNames = ArrayUtils.append(featureContribNames, "BiasTerm");


        contributions = new PredictTreeSHAPWithBackgroundTask(di, model_info(), _output, options,
                adaptedFrame, adaptedBgFrame, options._outputPerReference, options._outputSpace)
                .runAndGetOutput(j, destination_key, outputNames);
      }
      return Scope.untrack(contributions);
    } finally {
      Log.info("Finished contributions calculation for " + this._key + "...");
    }
  }
  
  @Override
  public UpdateAuxTreeWeightsReport updateAuxTreeWeights(Frame frame, String weightsColumn) {
    if (weightsColumn == null) {
      throw new IllegalArgumentException("Weights column name is not defined");
    }
    Frame adaptFrm = new Frame(frame);
    Vec weights = adaptFrm.remove(weightsColumn);
    if (weights == null) {
      throw new IllegalArgumentException("Input frame doesn't contain weights column `" + weightsColumn + "`");
    }
    adaptTestForTrain(adaptFrm, true, false);
    // keep features only and re-introduce weights column at the end of the frame
    Frame featureFrm = new Frame(_output.features(), frame.vecs(_output.features()));
    featureFrm.add(weightsColumn, weights);

    DataInfo di = model_info().dataInfo();
    assert di != null;

    double[][] nodeWeights = new UpdateAuxTreeWeightsTask(_parms._distribution, di, model_info(), _output)
            .doAll(featureFrm)
            .getNodeWeights();
    AuxNodeWeights auxNodeWeights = new AuxNodeWeights(model_info().getAuxNodeWeightsKey(), nodeWeights);
    DKV.put(auxNodeWeights);

    UpdateAuxTreeWeightsReport report = new UpdateAuxTreeWeightsReport();
    report._warn_classes = new int[0];
    report._warn_trees = new int[0];
    for (int treeId = 0; treeId < nodeWeights.length; treeId++) {
      if (nodeWeights[treeId] == null)
        continue;
      for (double w : nodeWeights[treeId]) {
        if (w == 0) {
          report._warn_trees = ArrayUtils.append(report._warn_trees, treeId);
          report._warn_classes = ArrayUtils.append(report._warn_classes, 0);
          break;
        }
      }
    }
    return report;
  }

  @Override
  public Frame scoreLeafNodeAssignment(
      Frame frame, LeafNodeAssignmentType type, Key<Frame> destination_key
  ) {
    AssignLeafNodeTask task = AssignLeafNodeTask.make(model_info.scoringInfo(false), _output, model_info._boosterBytes, type);
    Frame adaptFrm = new Frame(frame);
    adaptTestForTrain(adaptFrm, true, false);
    return task.execute(adaptFrm, destination_key);
  }

  private void setDataInfoToOutput(DataInfo dinfo) {
    _output.setNames(dinfo._adaptedFrame.names(), dinfo._adaptedFrame.typesStr());
    _output._domains = dinfo._adaptedFrame.domains();
    _output._nums = dinfo._nums;
    _output._cats = dinfo._cats;
    _output._catOffsets = dinfo._catOffsets;
    _output._useAllFactorLevels = dinfo._useAllFactorLevels;
  }

  @Override
  protected Futures remove_impl(Futures fs, boolean cascade) {
    DataInfo di = model_info().dataInfo();
    if (di != null) {
      di.remove(fs);
    }
    AuxNodeWeights anw = model_info().auxNodeWeights();
    if (anw != null) {
      anw.remove(fs);
    }
    if (_output._calib_model != null)
      _output._calib_model.remove(fs);
    return super.remove_impl(fs, cascade);
  }

  @Override
  public SharedTreeGraph convert(final int treeNumber, final String treeClassName) {
    GradBooster booster = XGBoostJavaMojoModel
            .makePredictor(model_info._boosterBytes, model_info.auxNodeWeightBytes())
            .getBooster();
    if (!(booster instanceof GBTree)) {
      throw new IllegalArgumentException("XGBoost model is not backed by a tree-based booster. Booster class is " + 
              booster.getClass().getCanonicalName());
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
    final RegTreeNodeStat[] treeNodeStats = treesInGroup[treeNumber].getStats();
    assert treeNodes.length >= 1;

    SharedTreeGraph sharedTreeGraph = new SharedTreeGraph();
    final SharedTreeSubgraph sharedTreeSubgraph = sharedTreeGraph.makeSubgraph(_output._training_metrics._description);

    final XGBoostUtils.FeatureProperties featureProperties = XGBoostUtils.assembleFeatureNames(model_info.dataInfo()); // XGBoost's usage of one-hot encoding assumed
    constructSubgraph(treeNodes, treeNodeStats, sharedTreeSubgraph.makeRootNode(), 0, sharedTreeSubgraph, featureProperties, true); // Root node is at index 0
    return sharedTreeGraph;
  }

  private static void constructSubgraph(final RegTreeNode[] xgBoostNodes, final RegTreeNodeStat[] xgBoostNodeStats, final SharedTreeNode sharedTreeNode,
                                        final int nodeIndex, final SharedTreeSubgraph sharedTreeSubgraph,
                                        final XGBoostUtils.FeatureProperties featureProperties, boolean inclusiveNA) {
    final RegTreeNode xgBoostNode = xgBoostNodes[nodeIndex];
    final RegTreeNodeStat xgBoostNodeStat = xgBoostNodeStats[nodeIndex];
    // Not testing for NaNs, as SharedTreeNode uses NaNs as default values.
    //No domain set, as the structure mimics XGBoost's tree, which is numeric-only
    if (featureProperties._oneHotEncoded[xgBoostNode.getSplitIndex()]) {
      //Shared tree model uses < to the left and >= to the right. Transforiming one-hot encoded categoricals
      // from 0 to 1 makes it fit the current split description logic
      sharedTreeNode.setSplitValue(1.0F);
    } else {
      sharedTreeNode.setSplitValue(xgBoostNode.getSplitCondition());
    }
    sharedTreeNode.setPredValue(xgBoostNode.getLeafValue());
    sharedTreeNode.setInclusiveNa(inclusiveNA);
    sharedTreeNode.setNodeNumber(nodeIndex);
    sharedTreeNode.setGain(xgBoostNodeStat.getGain());
    sharedTreeNode.setWeight(xgBoostNodeStat.getCover());
    
    if (!xgBoostNode.isLeaf()) {
      sharedTreeNode.setCol(xgBoostNode.getSplitIndex(), featureProperties._names[xgBoostNode.getSplitIndex()]);
      constructSubgraph(xgBoostNodes, xgBoostNodeStats, sharedTreeSubgraph.makeLeftChildNode(sharedTreeNode),
              xgBoostNode.getLeftChildIndex(), sharedTreeSubgraph, featureProperties, xgBoostNode.default_left());
      constructSubgraph(xgBoostNodes, xgBoostNodeStats, sharedTreeSubgraph.makeRightChildNode(sharedTreeNode),
          xgBoostNode.getRightChildIndex(), sharedTreeSubgraph, featureProperties, !xgBoostNode.default_left());
    }
  }

  @Override
  public SharedTreeGraph convert(int treeNumber, String treeClass, ConvertTreeOptions options) {
    return convert(treeNumber, treeClass); // options are currently not applicable to in-H2O conversion
  }

  private int getXGBoostClassIndex(final String treeClass) {
    final ModelCategory modelCategory = _output.getModelCategory();
    if(ModelCategory.Regression.equals(modelCategory) && (treeClass != null && !treeClass.isEmpty())){
      throw new IllegalArgumentException("There should be no tree class specified for regression.");
    }
    if ((treeClass == null || treeClass.isEmpty())) {
      // Binomial & regression problems do not require tree class to be specified, as there is only one available.
      // Such class is selected automatically for the user.
      switch (modelCategory) {
        case Binomial:
        case Regression:
          return 0;
        default:
          // If the user does not specify tree class explicitely and there are multiple options to choose from,
          // throw an error.
          throw new IllegalArgumentException(String.format("Model category '%s' requires tree class to be specified.",
                  modelCategory));
      }
    }

    final String[] domain = _output._domains[_output._domains.length - 1];
    final int treeClassIndex = ArrayUtils.find(domain, treeClass);

    if (ModelCategory.Binomial.equals(modelCategory) && treeClassIndex != 0) {
      throw new IllegalArgumentException(String.format("For binomial XGBoost model, only one tree for class %s has been built.", domain[0]));
    } else if (treeClassIndex < 0) {
      throw new IllegalArgumentException(String.format("No such class '%s' in tree.", treeClass));
    }

    return treeClassIndex;
  }

  @Override
  public boolean isFeatureUsedInPredict(String featureName) {
    int featureIdx = ArrayUtils.find(_output._varimp._names, featureName);
    if (featureIdx == -1 && _output._catOffsets.length > 1) { // feature is possibly categorical
      featureIdx = ArrayUtils.find(_output._names, featureName);
      if (featureIdx == -1 || !_output._column_types[featureIdx].equals("Enum")) return false;
      for (int i = 0; i < _output._varimp._names.length; i++) {
        if (_output._varimp._names[i].startsWith(featureName.concat(".")) && _output._varimp._varimp[i] != 0){
          return true;
        }
      }
      return false;
    }
    return featureIdx != -1 && _output._varimp._varimp[featureIdx] != 0d;
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Serialization into a POJO
  //--------------------------------------------------------------------------------------------------------------------

  @Override
  protected boolean toJavaCheckTooBig() {
    return _output == null || _output._ntrees * _parms._max_depth > 1000;
  }

  @Override protected SBPrintStream toJavaInit(SBPrintStream sb, CodeGeneratorPipeline fileCtx) {
    sb.nl();
    sb.ip("public boolean isSupervised() { return true; }").nl();
    sb.ip("public int nclasses() { return ").p(_output.nclasses()).p("; }").nl();
    return sb;
  }
  
  @Override
  protected void toJavaPredictBody(
      SBPrintStream sb, CodeGeneratorPipeline classCtx, CodeGeneratorPipeline fileCtx, boolean verboseCode
  ) {
    final String namePrefix = JCodeGen.toJavaId(_key.toString());
    Predictor p = makePredictor(false);
    XGBoostPojoWriter.make(p, namePrefix, _output, defaultThreshold()).renderJavaPredictBody(sb, fileCtx);
  }

  public FeatureInteractions getFeatureInteractions(int maxInteractionDepth, int maxTreeDepth, int maxDeepening) {

    FeatureInteractions featureInteractions = new FeatureInteractions();
    
    for (int i = 0; i < this._parms._ntrees; i++) {
      FeatureInteractions currentTreeFeatureInteractions = new FeatureInteractions();
      SharedTreeGraph sharedTreeGraph = convert(i, null);
      assert sharedTreeGraph.subgraphArray.size() == 1;
      SharedTreeSubgraph tree = sharedTreeGraph.subgraphArray.get(0);
      List<SharedTreeNode> interactionPath = new ArrayList<>();
      Set<String> memo = new HashSet<>();
      
      FeatureInteractions.collectFeatureInteractions(tree.rootNode, interactionPath, 0, 0, 1, 0, 0,
              currentTreeFeatureInteractions, memo, maxInteractionDepth, maxTreeDepth, maxDeepening, i, false);
      featureInteractions.mergeWith(currentTreeFeatureInteractions);
    }
    
    return featureInteractions;
  }

  @Override
  public TwoDimTable[][] getFeatureInteractionsTable(int maxInteractionDepth, int maxTreeDepth, int maxDeepening) {
    return FeatureInteractions.getFeatureInteractionsTable(this.getFeatureInteractions(maxInteractionDepth,maxTreeDepth,maxDeepening));
  }

  Predictor makePredictor(boolean scoringOnly) {
    return PredictorFactory.makePredictor(model_info._boosterBytes, model_info.auxNodeWeightBytes(), scoringOnly);
  }

  protected Frame removeSpecialNNonNumericColumns(Frame frame) {
    Frame adaptFrm = new Frame(frame);
    adaptTestForTrain(adaptFrm, true, false);
    // remove non-feature columns
    adaptFrm.remove(_parms._response_column);
    adaptFrm.remove(_parms._fold_column);
    adaptFrm.remove(_parms._weights_column);
    adaptFrm.remove(_parms._offset_column);
    // remove non-numeric columns
    int numCols = adaptFrm.numCols()-1;
    for (int index=numCols; index>=0; index--) {
      if (!adaptFrm.vec(index).isNumeric())
        adaptFrm.remove(index);
    }
    return adaptFrm;
  }
  
  @Override
  public double getFriedmanPopescusH(Frame frame, String[] vars) {
    Frame adaptFrm = removeSpecialNNonNumericColumns(frame);

    for(int colId = 0; colId < adaptFrm.numCols(); colId++) {
      Vec col = adaptFrm.vec(colId);
      if (col.isBad()) {
        throw new UnsupportedOperationException(
                "Calculating of H statistics error: column " + adaptFrm.name(colId) + " is missing.");
      }
      if(!col.isNumeric()) {
        throw new UnsupportedOperationException(
                "Calculating of H statistics error: column " + adaptFrm.name(colId) + " is not numeric.");
      }
    }

    int nclasses = this._output.nclasses() > 2 ? this._output.nclasses() : 1;
    SharedTreeSubgraph[][] sharedTreeSubgraphs = new SharedTreeSubgraph[this._parms._ntrees][nclasses];
    for (int i = 0; i < this._parms._ntrees; i++) {
      for (int j = 0; j < nclasses; j++) {
        SharedTreeGraph graph = this.convert(i, this._output.classNames()[j]);
        assert graph.subgraphArray.size() == 1;
        sharedTreeSubgraphs[i][j] = graph.subgraphArray.get(0);
      }
    }

    return FriedmanPopescusH.h(adaptFrm, vars, this._parms._learn_rate, sharedTreeSubgraphs);
  }


}
