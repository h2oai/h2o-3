package hex.tree.xgboost;

import com.sun.jna.Memory;
import hex.*;
import hex.genmodel.algos.xgboost.XGBoostMojoModel;
import hex.genmodel.utils.DistributionFamily;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.Log;
import hex.ModelMetrics;
import java.util.HashMap;
import java.util.Map;

import static hex.tree.xgboost.XGBoost.convertFrametoDMatrix;
import static hex.tree.xgboost.XGBoost.makeDataInfo;

public class XGBoostModel extends Model<XGBoostModel, XGBoostModel.XGBoostParameters, XGBoostOutput> {

  private XGBoostModelInfo model_info;

  XGBoostModelInfo model_info() { return model_info; }

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

    public float _max_abs_leafnode_pred = 0;
    public float _max_delta_step = 0;

    public int _score_tree_interval = 0; // score every so many trees (no matter what)
    public int _initial_score_interval = 4000; //Adding this parameter to take away the hard coded value of 4000 for scoring the first  4 secs
    public int _score_interval = 4000; //Adding this parameter to take away the hard coded value of 4000 for scoring each iteration every 4 secs
    public float _min_split_improvement = 0;
    public float _gamma;

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

    public XGBoostParameters() {
      super();
      _categorical_encoding = CategoricalEncodingScheme.LabelEncoder;
    }

    public String algoName() { return "XGBoost"; }
    public String fullName() { return "XGBoost"; }
    public String javaName() { return XGBoostModel.class.getName(); }

    @Override
    public long progressUnits() {
      return _ntrees;
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
    model_info = new XGBoostModelInfo(parms,output.nclasses());
    model_info._dataInfoKey = dinfo._key;
  }

  HashMap<String, Object> createParams() {
    XGBoostParameters p = _parms;
    HashMap<String, Object> params = new HashMap<>();

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
      if (XGBoost.hasGPU(_parms._gpu_id)) {
        Log.info("Using GPU backend (gpu_id: " + _parms._gpu_id + ").");
        params.put("gpu_id", _parms._gpu_id);
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
        Log.info("No GPU (gpu_id: "+_parms._gpu_id + ") found. Using CPU backend.");
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

    if (_output.nclasses()==2) {
      params.put("objective", "binary:logistic");
    } else if (_output.nclasses()==1) {
      if (p._distribution == DistributionFamily.gamma) {
        params.put("objective", "reg:gamma");
      } else if (p._distribution == DistributionFamily.tweedie) {
        params.put("objective", "reg:tweedie");
        params.put("tweedie_variance_power", p._tweedie_power);
      } else if (p._distribution == DistributionFamily.poisson) {
        params.put("objective", "count:poisson");
      } else if (p._distribution == DistributionFamily.gaussian || p._distribution==DistributionFamily.AUTO) {
        params.put("objective", "reg:linear");
      } else {
        throw new UnsupportedOperationException("No support for distribution=" + p._distribution.toString());
      }
    } else {
      params.put("objective", "multi:softprob");
      params.put("num_class", _output.nclasses());
    }
    Log.info("XGBoost Parameters:");
    for (Map.Entry<String,Object> s : params.entrySet()) {
      Log.info(" " + s.getKey() + " = " + s.getValue());
    }
    Log.info("");
    return params;
  }

  @Override
  protected double[] score0(double[] data, double[] preds) {
    return score0(data, preds, 0.0);
  }


  @Override
  public XGBoostMojoWriter getMojo() {
    return new XGBoostMojoWriter(this);
  }

  // Fast scoring using the C++ data structures
  // However, we need to bring the data back to Java to compute the metrics
  // For multinomial, we also need to transpose the data - which is slow
  private ModelMetrics makeMetrics(Booster booster, DMatrix data, Frame dataFrame, String description) throws XGBoostError {
    return makeMetrics(booster, data, dataFrame, description, null);
  }

  private ModelMetrics makeMetrics(Booster booster, DMatrix data, Frame dataFrame, String description,
                                   Key<Frame> predFrameKey) throws XGBoostError {
    ModelMetrics[] mms = new ModelMetrics[1];
    Frame predictions = makePreds(booster, data, mms, true, predFrameKey);
    if (predFrameKey == null) {
        predictions.remove();
    } else {
      DKV.put(predictions);
    }
    ModelMetrics mm = mms[0]
        .withModelAndFrame(this, dataFrame)
        .withDescription(description);
    return mm;
  }

  private Frame makePredsOnly(Booster booster, DMatrix data, Key<Frame> destinationKey) throws XGBoostError {
    Frame preds = makePreds(booster, data, null, false, destinationKey);
    DKV.put(preds);
    return preds;
  }

  private Frame makePreds(Booster booster, DMatrix data, ModelMetrics[] mms, boolean computeMetrics,
                          Key<Frame> destinationKey) throws XGBoostError {
    assert (! computeMetrics) || (mms != null && mms.length == 1);

    // Calculate predictions
    final float[][] preds = booster.predict(data);

    // Convert to expected output structure and optionally calculate metrics
    Frame predFrame;
    ModelMetrics mm = null;
    Vec resp = computeMetrics ? Vec.makeVec(data.getLabel(), Vec.newKey()) : null;
    float[] weights = data.getWeight();
    if (_output.nclasses()==1) {
      double[] dpreds = new double[preds.length];
      for (int j = 0; j < dpreds.length; ++j) {
        dpreds[j] = preds[j][0];
        assert(!Double.isNaN(dpreds[j])) : "Error: XGBoost predicted NAs.";
      }
//      if (weights.length>0)
//        for (int j = 0; j < dpreds.length; ++j)
//          assert weights[j] == 1.0;
      Vec pred = Vec.makeVec(dpreds, Vec.newKey());
      if (computeMetrics)
        mm = ModelMetricsRegression.make(pred, resp, DistributionFamily.gaussian);
      predFrame = new Frame(destinationKey, makeScoringNames(), new Vec[]{pred});
    }
    else if (_output.nclasses()==2) {
      double[] dpreds = new double[preds.length];
      for (int j = 0; j < dpreds.length; ++j)
        dpreds[j] = preds[j][0];
      if (weights.length>0)
        for (int j = 0; j < dpreds.length; ++j)
          assert weights[j] == 1.0;
      Vec p1 = Vec.makeVec(dpreds, Vec.newKey());
      Vec p0 = p1.makeCon(0);
      Vec label = p1.makeCon(0., Vec.T_CAT);
      new MRTask() {
        public void map(Chunk l, Chunk p0, Chunk p1) {
          for (int i=0; i<l._len; ++i) {
            double p = p1.atd(i);
            p0.set(i, 1. - p);
            double[] row = new double[]{0, 1-p, p};
            l.set(i, hex.genmodel.GenModel.getPrediction(row, _output._priorClassDist, null, defaultThreshold()));
          }
        }
      }.doAll(label,p0,p1);
      if (computeMetrics)
        mm = ModelMetricsBinomial.make(p1, resp);
      label.setDomain(new String[]{"N","Y"}); // ignored
      predFrame = new Frame(destinationKey, makeScoringNames(), new Vec[]{label,p0,p1});
    } else {
      String[] names = makeScoringNames();
      String[][] domains = new String[names.length][];
      domains[0] = _output.classNames();
      Frame input = null;
      try {
        input = new Frame(Vec.makeVec(MemoryManager.malloc4f(preds.length), Vec.newKey())); // dummy frame of the right size
        predFrame = new MRTask() {
          @Override
          public void map(Chunk[] chk, NewChunk[] nc) {
            assert chk.length == 1;
            for (int i = 0; i < chk[0]._len; ++i) {
              double[] row = new double[nc.length];
              for (int j = 1; j < row.length; ++j) {
                double val = preds[i][j - 1];
                nc[j].addNum(val);
                row[j] = val;
              }
              nc[0].addNum(hex.genmodel.GenModel.getPrediction(row, _output._priorClassDist, null, defaultThreshold()));
            }
          }
        }.doAll(_output.nclasses() + 1, Vec.T_NUM, input).outputFrame(destinationKey, names, domains);
      } finally {
        if (input != null)
          input.remove();
      }
      if (computeMetrics) {
        Frame pp = new Frame(predFrame);
        pp.remove(0);
        Scope.enter();
        mm = ModelMetricsMultinomial.make(pp, resp, resp.toCategoricalVec().domain());
        Scope.exit();
      }
    }
    if (resp != null)
      resp.remove();

    if (computeMetrics) {
      assert mm != null;
      mms[0] = mm;
    }
    assert predFrame != null && "predict".equals(predFrame.name(0));
    return predFrame;
  }

  /**
   * Score an XGBoost model on training and validation data (optional)
   * Note: every row is scored, all observation weights are assumed to be equal
   * @param booster xgboost model
   * @param train training data in the form of matrix
   * @param trainFrame original training frame
   * @param valid validation data (optional, can be null)
   * @param validFrame original validation frame, can be null
   * @throws XGBoostError
   */
  public void doScoring(Booster booster, DMatrix train, Frame trainFrame, DMatrix valid, Frame validFrame) throws XGBoostError {
    ModelMetrics mm = makeMetrics(booster, train, trainFrame, "Metrics reported on training frame");
    _output._training_metrics = mm;
    _output._scored_train[_output._ntrees].fillFrom(mm);
    addModelMetrics(mm);
    // Optional validation part
    if (valid!=null) {
      assert validFrame != null : "Validation frame (source of validation matrix) has to be not null!";
      mm = makeMetrics(booster, valid, validFrame, "Metrics reported on validation frame");
      _output._validation_metrics = mm;
      _output._scored_valid[_output._ntrees].fillFrom(mm);
      addModelMetrics(mm);
    }
  }

  public void computeVarImp(Map<String,Integer> varimp) {
    if (varimp.isEmpty()) return;
    // compute variable importance
    float[] viFloat = new float[varimp.size()];
    String[] names = new String[varimp.size()];
    int j=0;
    for (Map.Entry<String, Integer> it : varimp.entrySet()) {
      viFloat[j] = it.getValue();
      names[j] = it.getKey();
      j++;
    }
    _output._varimp = new VarImp(viFloat, names);
  }

  @Override
  public double[] score0(double[] data, double[] preds, double offset) {
    DataInfo di = model_info._dataInfoKey.get();
    return XGBoostMojoModel.score0(data, offset, preds,
            model_info.booster(), di._nums, di._cats, di._catOffsets, di._useAllFactorLevels,
            _output.nclasses(), _output._priorClassDist, defaultThreshold(), _output._sparse);
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
    model_info().nukeBackend();
    if (model_info()._dataInfoKey !=null)
      model_info()._dataInfoKey.get().remove(fs);
    return super.remove_impl(fs);
  }

  @Override
  public Frame score(Frame fr, String destination_key, Job j, boolean computeMetrics) throws IllegalArgumentException {
    Frame adaptFr = new Frame(fr);
    computeMetrics = computeMetrics && (!isSupervised() || (adaptFr.vec(_output.responseName()) != null && !adaptFr.vec(_output.responseName()).isBad()));
    String[] msg = adaptTestForTrain(adaptFr,true, computeMetrics);   // Adapt
    if (msg.length > 0) {
      for (String s : msg)
        Log.warn(s);
    }
    try {
      DMatrix trainMat = convertFrametoDMatrix( model_info()._dataInfoKey, adaptFr,
          _parms._response_column, _parms._weights_column, _parms._fold_column, null, _output._sparse);
      Key<Frame> destFrameKey = Key.make(destination_key);
      if (computeMetrics){
        ModelMetrics mm = makeMetrics(model_info().booster(), trainMat, fr, "Prediction on frame " + fr._key, destFrameKey);
        // Update model with newly computed model metrics
        this.addModelMetrics(mm);
        DKV.put(this);
      } else
        makePredsOnly(model_info().booster(), trainMat, destFrameKey);
      return destFrameKey.get();
    } catch (XGBoostError xgBoostError) {
      throw new RuntimeException(xgBoostError);
    }
  }
}
