package hex.tree.xgboost;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.gbm.GBTree;
import biz.k11i.xgboost.gbm.GradBooster;
import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.RegTreeNode;
import hex.*;
import hex.genmodel.GenModel;
import hex.genmodel.algos.tree.SharedTreeGraph;
import hex.genmodel.algos.tree.SharedTreeGraphConverter;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.genmodel.algos.xgboost.XGBoostNativeMojoModel;
import hex.genmodel.utils.DistributionFamily;
import hex.genmodel.utils.LinkFunctionType;
import hex.tree.xgboost.predict.*;
import hex.tree.xgboost.util.PredictConfiguration;
import ml.dmlc.xgboost4j.java.*;
import water.*;
import water.codegen.CodeGenerator;
import water.codegen.CodeGeneratorPipeline;
import water.exceptions.JCodeSB;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.JCodeGen;
import water.util.Log;
import water.util.SBPrintStream;

import java.io.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static hex.genmodel.algos.xgboost.XGBoostMojoModel.ObjectiveType;
import static hex.tree.xgboost.XGBoost.makeDataInfo;
import static water.H2O.OptArgs.SYSTEM_PROP_PREFIX;

public class XGBoostModel extends Model<XGBoostModel, XGBoostModel.XGBoostParameters, XGBoostOutput> 
        implements SharedTreeGraphConverter, Model.LeafNodeAssignment, Model.Contributions {

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
    public String _save_matrix_directory; // dump the xgboost matrix to this directory

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
    public float _reg_lambda = 1;
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
    model_info = new XGBoostModelInfo(parms, dinfo);
  }

  // useful for debugging
  @SuppressWarnings("unused")
  public void dump(String format) {
    File fmFile = null;
    try {
      Booster b = BoosterHelper.loadModel(new ByteArrayInputStream(this.model_info._boosterBytes));
      fmFile = File.createTempFile("xgboost-feature-map", ".bin");
      FileOutputStream os = new FileOutputStream(fmFile);
      os.write(this.model_info._featureMap.getBytes());
      os.close();
      String fmFilePath = fmFile.getAbsolutePath();
      String[] d = b.getModelDump(fmFilePath, true, format);
      for (String l : d) {
        System.out.println(l);
      }
    } catch (Exception e) {
      Log.err(e);
    } finally {
      if (fmFile != null) {
        fmFile.delete();
      }
    }
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
        if (p._booster == XGBoostParameters.Booster.gblinear) {
          Log.info("Using gpu_coord_descent updater."); 
          params.put("updater", "gpu_coord_descent");
        } else  if (p._tree_method == XGBoostParameters.TreeMethod.exact) {
          Log.info("Using grow_gpu (exact) updater.");
          params.put("tree_method", "exact");
          params.put("updater", "grow_gpu");
        } else {
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
    return Integer.getInteger(SYSTEM_PROP_PREFIX + "xgboost.nthread", H2O.ARGS.nthreads);
  }

  @Override protected AutoBuffer writeAll_impl(AutoBuffer ab) {
    ab.putKey(model_info.getDataInfoKey());
    return super.writeAll_impl(ab);
  }

  @Override protected Keyed readAll_impl(AutoBuffer ab, Futures fs) {
    ab.getKey(model_info.getDataInfoKey(), fs);
    return super.readAll_impl(ab, fs);
  }

  @Override
  public XGBoostMojoWriter getMojo() {
    return new XGBoostMojoWriter(this);
  }

  private ModelMetrics makeMetrics(Frame data, Frame originalData, boolean isTrain, String description) {
    Log.debug("Making metrics: " + description);
    XGBoostScoreTask.XGBoostScoreTaskResult score = XGBoostScoreTask.runScoreTask(
            model_info(), _output, _parms, null, data, originalData, isTrain, true, this);
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
    ModelMetrics mm = makeMetrics(_train, _trainOrig, true, "Metrics reported on training frame");
    _output._training_metrics = mm;
    _output._scored_train[_output._ntrees].fillFrom(mm);
    addModelMetrics(mm);
    // Optional validation part
    if (_valid!=null) {
      mm = makeMetrics(_valid, _validOrig, false, "Metrics reported on validation frame");
      _output._validation_metrics = mm;
      _output._scored_valid[_output._ntrees].fillFrom(mm);
      addModelMetrics(mm);
    }
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
    final DataInfo di = model_info.dataInfo();
    assert di != null;
    final double threshold = defaultThreshold();
    Booster booster = null;
    try {
      booster = model_info.deserializeBooster();
      return XGBoostNativeMojoModel.score0(data, offset, preds, _parms._booster.toString(), _parms._ntrees,
              model_info.deserializeBooster(), di._nums, di._cats, di._catOffsets, di._useAllFactorLevels,
              _output.nclasses(), _output._priorClassDist, threshold, _output._sparse);
    } finally {
      if (booster != null)
        BoosterHelper.dispose(booster);
    }
  }

  @Override
  protected BigScorePredict setupBigScorePredict(BigScore bs) {
    DataInfo di = model_info().scoringInfo(false); // always for validation scoring info for scoring (we are not in the training phase)
    return PredictConfiguration.useJavaScoring() ? setupBigScorePredictJava(di) : setupBigScorePredictNative(di);
  }

  private BigScorePredict setupBigScorePredictNative(DataInfo di) {
    BoosterParms boosterParms = XGBoostModel.createParams(_parms, _output.nclasses(), di.coefNames());
    return new XGBoostBigScorePredict(model_info, _parms, _output, di, boosterParms, defaultThreshold());
  }

  private BigScorePredict setupBigScorePredictJava(DataInfo di) {
    return new XGBoostJavaBigScorePredict(di, _output, defaultThreshold(), model_info()._boosterBytes);
  }

  @Override
  public Frame scoreContributions(Frame frame, Key<Frame> destination_key) {
    return scoreContributions(frame, destination_key, false);
  }

  public Frame scoreContributions(Frame frame, Key<Frame> destination_key, boolean approx) {
    Frame adaptFrm = new Frame(frame);
    adaptTestForTrain(adaptFrm, true, false);

    DataInfo di = model_info().dataInfo();
    assert di != null;
    final String[] outputNames = ArrayUtils.append(di.coefNames(), "BiasTerm");

    return makePredictContribTask(di, approx)
            .doAll(outputNames.length, Vec.T_NUM, adaptFrm)
            .outputFrame(destination_key, outputNames, null);
  }
  
  private MRTask<?> makePredictContribTask(DataInfo di, boolean approx) {
    return approx ? new PredictContribApproxTask(_parms, model_info, _output, di) : new PredictTreeSHAPTask(di, model_info(), _output);
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
    _output._origNames = _parms._train.get().names();
    _output._origDomains = _parms._train.get().domains();
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
    return super.remove_impl(fs, cascade);
  }

  @Override
  public SharedTreeGraph convert(final int treeNumber, final String treeClassName) {
    GradBooster booster;
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

    final XGBoostUtils.FeatureProperties featureProperties = XGBoostUtils.assembleFeatureNames(model_info.dataInfo()); // XGBoost's usage of one-hot encoding assumed
    constructSubgraph(treeNodes, sharedTreeSubgraph.makeRootNode(), 0, sharedTreeSubgraph, featureProperties, true); // Root node is at index 0
    return sharedTreeGraph;
  }

  private static void constructSubgraph(final RegTreeNode[] xgBoostNodes, final SharedTreeNode sharedTreeNode,
                                        final int nodeIndex, final SharedTreeSubgraph sharedTreeSubgraph,
                                        final XGBoostUtils.FeatureProperties featureProperties, boolean inclusiveNA) {
    final RegTreeNode xgBoostNode = xgBoostNodes[nodeIndex];
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
    sharedTreeNode.setCol(xgBoostNode.getSplitIndex(), featureProperties._names[xgBoostNode.getSplitIndex()]);
    sharedTreeNode.setInclusiveNa(inclusiveNA);
    sharedTreeNode.setNodeNumber(nodeIndex);

    if (!xgBoostNode.isLeaf()) {
      constructSubgraph(xgBoostNodes, sharedTreeSubgraph.makeLeftChildNode(sharedTreeNode),
          xgBoostNode.getLeftChildIndex(), sharedTreeSubgraph, featureProperties, xgBoostNode.default_left());
      constructSubgraph(xgBoostNodes, sharedTreeSubgraph.makeRightChildNode(sharedTreeNode),
          xgBoostNode.getRightChildIndex(), sharedTreeSubgraph, featureProperties, !xgBoostNode.default_left());
    }
  }


  private int getXGBoostClassIndex(final String treeClass) {
    final ModelCategory modelCategory = _output.getModelCategory();
    if(ModelCategory.Regression.equals(modelCategory) && (treeClass != null && !treeClass.isEmpty())){
      throw new IllegalArgumentException("There should be no tree class specified for regression.");
    }
    if ((treeClass == null || treeClass.isEmpty())) {
      if (ModelCategory.Regression.equals(modelCategory)) return 0;
      else throw new IllegalArgumentException("Non-regressional models require tree class specified.");
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
  
  private void renderTree(JCodeSB sb, RegTree tree, int nidx) {
    RegTreeNode node = tree.getNodes()[nidx];
    if (node.isLeaf()) {
      sb.ip("").pj(node.getLeafValue());
    } else {
      String accessor;
      if (node.getSplitIndex() >= _output._catOffsets[_output._cats]) {
        int colIdx = node.getSplitIndex() - _output._catOffsets[_output._cats] + _output._cats;
        accessor = "data[" + colIdx + "]";
      } else {
        int colIdx = 0;
        while (node.getSplitIndex() >= _output._catOffsets[colIdx+1]) colIdx++;
        int colValue = node.getSplitIndex() - _output._catOffsets[colIdx];
        accessor = "(data[" + colIdx + "] == " + colValue + " ? 1 : " + (_output._sparse?"NaN":"0") + ")";
      }
      String operator;
      int trueChild;
      int falseChild;
      if (node.default_left()) {
        operator = " < ";
        trueChild = node.getLeftChildIndex();
        falseChild = node.getRightChildIndex();
      } else {
        operator = " >= ";
        trueChild = node.getRightChildIndex();
        falseChild = node.getLeftChildIndex();
      }
      sb.ip("((Double.isNaN(").p(accessor).p(") || ").p(accessor).p(operator).pj(node.getSplitCondition()).p(") ?").nl();
      sb.ii(1);
      renderTree(sb, tree, trueChild);
      sb.nl().ip(":").nl();
      renderTree(sb, tree, falseChild);
      sb.di(1);
      sb.nl().ip(")");
    }
  }
  
  private String renderTreeClass(String namePrefix, RegTree[][] trees, final int gidx, final int tidx, CodeGeneratorPipeline fileCtx) {
    final RegTree tree = trees[gidx][tidx];
    final String className = namePrefix + "_Tree_g_" + gidx + "_t_" + tidx;
    fileCtx.add(new CodeGenerator() {
      @Override
      public void generate(JCodeSB sb) {
        sb.nl().p("class ").p(className).p(" {").nl();
        sb.ii(1);
        sb.ip("static float score0(double[] data) {").nl();
        sb.ii(1);
        sb.ip("return ");
        renderTree(sb, tree, 0);
        sb.p(";").nl();
        sb.di(1);
        sb.ip("}").nl();
        sb.di(1);
        sb.ip("}").nl();
      }
    });
    return className;
  }
  
  private void renderPredTransformViaLinkFunction(LinkFunctionType type, SBPrintStream sb) {
    LinkFunction lf = LinkFunctionFactory.getLinkFunction(type);
    sb.ip("preds[0] = (float) ").p(lf.linkInvString("preds[0]")).p(";").nl();
  }
  
  private void renderMultiClassPredTransform(SBPrintStream sb) {
    sb.ip("double max = preds[0];").nl();
    sb.ip("for (int i = 1; i < preds.length-1; i++) max = Math.max(preds[i], max); ").nl();
    sb.ip("double sum = 0.0D;").nl();
    sb.ip("for (int i = 0; i < preds.length-1; i++) {").nl();
    sb.ip("  preds[i] = Math.exp(preds[i] - max);").nl();
    sb.ip("  sum += preds[i];").nl();
    sb.ip("}").nl();
    sb.ip("for (int i = 0; i < preds.length-1; i++) {").nl();
    sb.ip("  preds[i] /= (float) sum;").nl();
    sb.ip("}").nl();
  }
  
  private void renderPredTransform(String objFunction, SBPrintStream sb) {
    if (ObjectiveType.REG_GAMMA.getId().equals(objFunction) ||
        ObjectiveType.REG_TWEEDIE.getId().equals(objFunction) ||
        ObjectiveType.COUNT_POISSON.getId().equals(objFunction)) {
      renderPredTransformViaLinkFunction(LinkFunctionType.log, sb);
    } else if (ObjectiveType.BINARY_LOGISTIC.getId().equals(objFunction)) {
      renderPredTransformViaLinkFunction(LinkFunctionType.logit, sb);
    } else if(ObjectiveType.REG_LINEAR.getId().equals(objFunction) ||
        ObjectiveType.RANK_PAIRWISE.getId().equals(objFunction)) {
      renderPredTransformViaLinkFunction(LinkFunctionType.identity, sb);
    } else if (ObjectiveType.MULTI_SOFTPROB.getId().equals(objFunction)) {
      renderMultiClassPredTransform(sb);
    } else {
      throw new IllegalArgumentException("Unexpected objFunction " + objFunction);
    }
  }

  @Override
  protected void toJavaPredictBody(
      SBPrintStream sb, CodeGeneratorPipeline classCtx, CodeGeneratorPipeline fileCtx, boolean verboseCode
  ) {
    final String namePrefix = JCodeGen.toJavaId(_key.toString());
    Predictor p = PredictorFactory.makePredictor(model_info._boosterBytes, false);
    GBTree booster = ((GBTree) p.getBooster());
    RegTree[][] trees = booster.getGroupedTrees();
    for (int gidx = 0; gidx < trees.length; gidx++) {
      sb.ip("preds[").p(gidx).p("] = ").nl();
      sb.ii(1);
      for (int tidx = 0; tidx < trees[gidx].length; tidx++) {
        String treeClassName = renderTreeClass(namePrefix, trees, gidx, tidx, fileCtx);
        sb.ip(treeClassName).p(".score0(data)").p(" + ").nl();
      }
      sb.ip("").pj(p.getBaseScore()).p(";").nl();
      sb.di(1);
    }
    renderPredTransform(p.getObjName(), sb);
    if (_output.nclasses() > 2) {
      sb.ip("for (int i = preds.length-2; i >= 0; i--)").nl();
      sb.ip("  preds[1 + i] = preds[i];").nl();
      sb.ip("preds[0] = GenModel.getPrediction(preds, PRIOR_CLASS_DISTRIB, data, ").p(defaultThreshold()).p(");").nl();
    } else if (_output.nclasses() == 2) {
      sb.ip("preds[1] = 1f - preds[0];").nl();
      sb.ip("preds[2] = preds[0];").nl();
      sb.ip("preds[0] = GenModel.getPrediction(preds, PRIOR_CLASS_DISTRIB, data, ").p(defaultThreshold()).p(");").nl();
    }
  }

}
