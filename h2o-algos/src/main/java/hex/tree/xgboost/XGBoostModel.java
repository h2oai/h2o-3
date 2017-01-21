package hex.tree.xgboost;

import hex.*;
import hex.genmodel.utils.DistributionFamily;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;
import water.H2O;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.HashMap;
import java.util.Map;

public class XGBoostModel extends Model<XGBoostModel, XGBoostModel.XGBoostParameters, XGBoostOutput> {

  public static class XGBoostParameters extends Model.Parameters {
    public double _learn_rate = 0.1;
    public double _learn_rate_annealing;
    public double _col_sample_rate = 1.0;
    public double _max_abs_leafnode_pred;
    public double _pred_noise_bandwidth;
    public int _ntrees=50; // Number of trees in the final model. Grid Search, comma sep values:50,100,150,200
    public int _max_depth = 5; // Maximum tree depth. Grid Search, comma sep values:5,7
    public double _min_rows = 10; // Fewest allowed observations in a leaf (in R called 'nodesize'). Grid Search, comma sep values
//    public int _nbins = 20; // Numerical (real/int) cols: Build a histogram of this many bins, then split at the best point
//    public int _nbins_cats = 1024; // Categorical (factor) cols: Build a histogram of this many bins, then split at the best point
//public int _nbins_top_level = 1<<10; //hardcoded maximum top-level number of bins for real-valued columns
    public double _min_split_improvement = 1e-5; // Minimum relative improvement in squared error reduction for a split to happen
//    public double _r2_stopping = Double.MAX_VALUE; // Stop when the r^2 metric equals or exceeds this value
//    public boolean _build_tree_one_node = false;
    public int _score_tree_interval = 0; // score every so many trees (no matter what)
    public int _initial_score_interval = 4000; //Adding this parameter to take away the hard coded value of 4000 for scoring the first  4 secs
    public int _score_interval = 4000; //Adding this parameter to take away the hard coded value of 4000 for scoring each iteration every 4 secs
    public double _sample_rate = 0.632; //fraction of rows to sample for each tree
//    public double[] _sample_rate_per_class; //fraction of rows to sample for each tree, per class
//    public double _col_sample_rate_change_per_level = 1.0f; //relative change of the column sampling rate for every level
    public double _col_sample_rate_per_tree = 1.0f; //fraction of columns to sample for each tree

    public XGBoostParameters() {
      super();
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

  public XGBoostModel(Key<XGBoostModel> selfKey, XGBoostParameters parms, XGBoostOutput output) {
    super(selfKey,parms,output);
  }


  HashMap<String, Object> createParams() {
    XGBoostParameters p = _parms;
    HashMap<String, Object> params = new HashMap<>();
    params.put("eta", p._learn_rate);
    params.put("max_depth", p._max_depth);
    params.put("silent", 1);
    if (_output.nclasses()==2)
      params.put("objective", "binary:logistic");
    else if (_output.nclasses()==1)
      params.put("objective", "reg:linear");
    else {
      params.put("objective", "multi:softprob");
      params.put("num_class", _output.nclasses());
    }
    return params;
  }

//  @Override
//  public Frame score(Frame fr) throws IllegalArgumentException {
//    //FIXME
//    try {
//      DMatrix trainMat = convertFrametoDMatrix(fr, _parms._response_column, _parms._weights_column, _parms._fold_column, null);
//    } catch (XGBoostError xgBoostError) {
//      xgBoostError.printStackTrace();
//    }
//    return fr;
//  }

  @Override
  protected double[] score0(double[] data, double[] preds) {
    // FIXME
    return new double[0];
  }


  @Override
  public XGBoostMojoWriter getMojo() {
    return new XGBoostMojoWriter(this);
  }

  // Fast scoring using the C++ data structures
  // However, we need to bring the data back to Java to compute the metrics
  // For multinomial, we also need to transpose the data - which is slow
  private ModelMetrics makeMetrics(Booster booster, DMatrix data) throws XGBoostError {
    float[][] preds = booster.predict(data);
    Vec resp = Vec.makeVec(data.getLabel(), Vec.newKey());
    ModelMetrics mm;
    if (_output.nclasses()<=2) {
      double[] dpreds = new double[preds.length];
      for (int j = 0; j < dpreds.length; ++j)
        dpreds[j] = preds[j][0];
      for (int j = 0; j < dpreds.length; ++j)
        assert (data.getWeight()[j] == 1.0);
      Vec pred = Vec.makeVec(dpreds, Vec.newKey());
      if (_output.nclasses() == 1) {
        mm = ModelMetricsRegression.make(pred, resp, DistributionFamily.gaussian);
      } else {
        mm = ModelMetricsBinomial.make(pred, resp);
      }
      pred.remove();
    }
    else {
      // ugly: need to transpose the data to put it into a Frame to score -> could be sped up
      double[][] dpreds = new double[_output.nclasses()][preds.length];
      for (int i = 0; i < dpreds.length; ++i) {
        for (int j = 0; j < dpreds[i].length; ++j) {
          dpreds[i][j] = preds[j][i];
        }
      }
      Vec[] pred = new Vec[_output.nclasses()];
      for (int i = 0; i < pred.length; ++i) {
        pred[i] = Vec.makeVec(dpreds[i], Vec.newKey());
      }
      Frame predFrame = new Frame(Key.<Frame>make(),pred,true);
      Scope.enter();
      mm = ModelMetricsMultinomial.make(predFrame, resp, resp.toCategoricalVec().domain());
      Scope.exit();
      predFrame.remove();
    }
    resp.remove();
    return mm;
  }

  /**
   * Score an XGBoost model on training and validation data (optional)
   * Note: every row is scored, all observation weights are assumed to be equal
   * @param booster xgboost model
   * @param train training data
   * @param valid validation data (optional, can be null)
   * @throws XGBoostError
   */
  public void doScoring(Booster booster, DMatrix train, DMatrix valid) throws XGBoostError {
    ModelMetrics mm = makeMetrics(booster, train);
    mm._description = "Metrics reported on training frame";
    _output._training_metrics = mm;
    _output._scored_train[_output._ntrees].fillFrom(mm);
    if (valid!=null) {
      mm = makeMetrics(booster, valid);
      mm._description = "Metrics reported on validation frame";
      _output._validation_metrics = mm;
      _output._scored_valid[_output._ntrees].fillFrom(mm);
    }
  }

  public void computeVarImp(Map<String,Integer> varimp) {
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

}
