package hex.tree.isofor;

import hex.ModelCategory;
import hex.ModelMetrics;
import hex.genmodel.CategoricalEncoding;
import hex.genmodel.utils.ArrayUtils;
import hex.ScoreKeeper;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.SharedTreeModel;
import water.Key;
import water.fvec.Frame;
import water.util.SBPrintStream;
import water.util.TwoDimTable;


public class IsolationForestModel extends SharedTreeModel<IsolationForestModel, IsolationForestModel.IsolationForestParameters, IsolationForestModel.IsolationForestOutput> {

  public static class IsolationForestParameters extends SharedTreeModel.SharedTreeParameters {
    public String algoName() { return "IsolationForest"; }
    public String fullName() { return "Isolation Forest"; }
    public String javaName() { return IsolationForestModel.class.getName(); }
    public int _mtries;
    public long _sample_size;
    public double _contamination;

    public IsolationForestParameters() {
      super();
      _mtries = -1;
      _sample_size = 256;
      _max_depth = 8; // log2(_sample_size)
      _sample_rate = -1;
      _min_rows = 1;
      _min_split_improvement = 0;
      _nbins = 2;
      _nbins_cats = 2;
      // _nbins_top_level = 2;
      _histogram_type = HistogramType.Random;
      _distribution = DistributionFamily.gaussian;

      // early stopping
      _stopping_tolerance = 0.01; // (default 0.001 is too low for the default criterion anomaly_score)

      // IF specific
      _contamination = -1; // disabled
    }
  }

  public static class IsolationForestOutput extends SharedTreeModel.SharedTreeOutput {
    public int _max_path_length;
    public int _min_path_length;

    public String _response_column;
    public String[] _response_domain;
    
    public IsolationForest.VarSplits _var_splits;
    public TwoDimTable _variable_splits;

    public IsolationForestOutput(IsolationForest b) { 
      super(b);
      if (b.vresponse() != null) {
        _response_column = b._parms._response_column;
        _response_domain = b.vresponse().domain();
      }
    }

    @Override
    public ModelCategory getModelCategory() {
      return ModelCategory.AnomalyDetection;
    }

    @Override
    public double defaultThreshold() {
      return _defaultThreshold;
    }

    @Override
    public String responseName() {
      return _response_column;
    }

    @Override
    public boolean hasResponse() {
      return _response_column != null;
    }

    @Override
    public int responseIdx() {
      return _names.length;
    }
  }

  public IsolationForestModel(Key<IsolationForestModel> selfKey, IsolationForestParameters parms, IsolationForestOutput output ) { 
    super(selfKey, parms, output);
  }

  @Override
  public void initActualParamValues() {
    super.initActualParamValues();
    if (_parms._stopping_metric == ScoreKeeper.StoppingMetric.AUTO){
      if (_parms._stopping_rounds == 0) {
        _parms._stopping_metric = null;
      } else {
        _parms._stopping_metric = ScoreKeeper.StoppingMetric.anomaly_score;
      }
    }
    if (_parms._categorical_encoding == Parameters.CategoricalEncodingScheme.AUTO) {
      _parms._categorical_encoding = Parameters.CategoricalEncodingScheme.Enum;
    }
  }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    // note: in the context of scoring on a training frame during model building domain will be null
    if (domain != null && _output.hasResponse()) {
      return new MetricBuilderAnomalySupervised(domain);
    } else {
      return new ModelMetricsAnomaly.MetricBuilderAnomaly("Isolation Forest Metrics", outputAnomalyFlag());
    }
  }

  @Override
  protected String[] makeScoringNames() {
    if (outputAnomalyFlag()) {
      return new String[]{"predict", "score", "mean_length"};
    } else {
      return new String[]{"predict", "mean_length"};
    }
  }

  @Override
  protected String[][] makeScoringDomains(Frame adaptFrm, boolean computeMetrics, String[] names) {
    assert outputAnomalyFlag() ? names.length == 3 : names.length == 2;
    String[][] domains = new String[names.length][];
    if (outputAnomalyFlag()) {
      domains[0] = _output._response_domain != null ? _output._response_domain : new String[]{"0", "1"};
    }
    return domains;
  }

  /** Bulk scoring API for one row.  Chunks are all compatible with the model,
   *  and expect the last Chunks are for the final distribution and prediction.
   *  Default method is to just load the data into the tmp array, then call
   *  subclass scoring logic. */
  @Override protected double[] score0(double[] data, double[] preds, double offset, int ntrees) {
    super.score0(data, preds, offset, ntrees);
    boolean outputAnomalyFlag = outputAnomalyFlag();
    int off = outputAnomalyFlag ? 1 : 0;
    if (ntrees >= 1) 
      preds[off + 1] = preds[0] / ntrees;
    preds[off] = normalizePathLength(preds[0]);
    if (outputAnomalyFlag)
      preds[0] = preds[1] >= _output._defaultThreshold ? 1 : 0; 
    return preds;
  }

  final double normalizePathLength(double pathLength) {
    return normalizePathLength(pathLength, _output._min_path_length, _output._max_path_length);
  }

  static double normalizePathLength(double pathLength, int minPathLength, int maxPathLength) {
    if (maxPathLength > minPathLength) {
      return  (maxPathLength - pathLength) / (maxPathLength - minPathLength);
    } else {
      return 1;
    }
  }

  @Override protected void toJavaUnifyPreds(SBPrintStream body) {
    throw new UnsupportedOperationException("Isolation Forest support only MOJOs.");
  }

  @Override protected CategoricalEncoding getGenModelEncoding() {
    return super.getGenModelEncoding();
  }
  
  @Override
  public IsolationForestMojoWriter getMojo() {
    return new IsolationForestMojoWriter(this);
  }

  @Override
  public String[] adaptTestForTrain(Frame test, boolean expensive, boolean computeMetrics) {
    if (!computeMetrics || _output._response_column == null) {
      return super.adaptTestForTrain(test, expensive, computeMetrics);
    } else {
      return adaptTestForTrain(
              test,
              _output._origNames,
              _output._origDomains,
              ArrayUtils.append(_output._names, _output._response_column),
              ArrayUtils.append(_output._domains, _output._response_domain),
              _parms,
              expensive,
              true,
              _output.interactionBuilder(),
              getToEigenVec(),
              _toDelete,
              false
      );
    }
  }

  final boolean outputAnomalyFlag() {
    return _output._defaultThreshold >= 0;
  }

}
