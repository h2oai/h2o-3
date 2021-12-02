package hex.pca;

import hex.CustomMetric;
import hex.Model;
import hex.ModelMetrics;
import hex.ModelMetricsUnsupervised;
import water.fvec.Frame;

public class ModelMetricsPCA extends ModelMetricsUnsupervised {
  public ModelMetricsPCA(Model model, Frame frame, CustomMetric customMetric) {
    super(model, frame, 0, Double.NaN, customMetric);
  }

  // PCA currently does not have any model metrics to compute during scoring
  public static class PCAModelMetrics extends MetricBuilderUnsupervised<PCAModelMetrics> {
    public PCAModelMetrics(int dims) {
      _work = new double[dims];
    }

    @Override
    public double[] perRow(double[] preds, float[] dataRow, Model m) { return preds; }

    @Override
    public ModelMetrics makeModelMetrics(Model m, Frame f) {
      return m.addModelMetrics(new ModelMetricsPCA(m, f, _customMetric));
    }
  }

  // Builder calculates just as H2O-runtime
  public static class IndependentPCAMetricBuilder
    extends IndependentMetricBuilderUnsupervised<IndependentPCAMetricBuilder> {

    @Override
    public double[] perRow(double[] preds, float[] dataRow) { return preds; }

    @Override
    public ModelMetrics makeModelMetrics() {
      return new ModelMetricsPCA(null, null, _customMetric);
    }
  }
}
