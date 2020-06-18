package hex.tree.isofor;

import hex.ModelCategory;
import hex.ModelMetrics;
import hex.ModelMetricsBinomial;
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

    public IsolationForest.VarSplits _var_splits;
    public TwoDimTable _variable_splits;

    public IsolationForestOutput(IsolationForest b) { super(b); }

    @Override
    public ModelCategory getModelCategory() {
      return ModelCategory.AnomalyDetection;
    }

    @Override
    public double defaultThreshold() {
      return _defaultThreshold;
    }
  }

  public IsolationForestModel(Key<IsolationForestModel> selfKey, IsolationForestParameters parms, IsolationForestOutput output ) { super(selfKey, parms, output); }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    if (domain == null)
      return new ModelMetricsAnomaly.MetricBuilderAnomaly("Isolation Forest Metrics", outputAnomalyFlag());
    else
      return new ModelMetricsBinomial.MetricBuilderBinomial(domain); // FIXME
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
    return new String[names.length][];
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
    if (_output._max_path_length > _output._min_path_length) {
      return  (_output._max_path_length - pathLength) / (_output._max_path_length - _output._min_path_length);
    } else {
      return 1;
    }
  }

  @Override protected void toJavaUnifyPreds(SBPrintStream body) {
    throw new UnsupportedOperationException("Isolation Forest support only MOJOs.");
  }

  @Override
  public IsolationForestMojoWriter getMojo() {
    return new IsolationForestMojoWriter(this);
  }

  final boolean outputAnomalyFlag() {
    return _output._defaultThreshold > 0;
  }

}
