package hex.tree.isofor;

import hex.ModelCategory;
import hex.ModelMetrics;
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
  }

  public IsolationForestModel(Key<IsolationForestModel> selfKey, IsolationForestParameters parms, IsolationForestOutput output ) { super(selfKey, parms, output); }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    return new ModelMetricsAnomaly.MetricBuilderAnomaly("Isolation Forest Metrics");
  }

  @Override
  protected String[] makeScoringNames(){
    return new String[]{"predict", "mean_length"};
  }

  @Override
  protected String[][] makeScoringDomains(Frame adaptFrm, boolean computeMetrics, String[] names) {
    assert names.length == 2;
    return new String[2][];
  }

  /** Bulk scoring API for one row.  Chunks are all compatible with the model,
   *  and expect the last Chunks are for the final distribution and prediction.
   *  Default method is to just load the data into the tmp array, then call
   *  subclass scoring logic. */
  @Override protected double[] score0(double[] data, double[] preds, double offset, int ntrees) {
    super.score0(data, preds, offset, ntrees);
    if (ntrees >= 1) preds[1] = preds[0] / ntrees;
    preds[0] = normalizePathLength(preds[0]);
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

}
