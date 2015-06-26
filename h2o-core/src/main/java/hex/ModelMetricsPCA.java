package hex;

import water.fvec.Frame;

public class ModelMetricsPCA extends ModelMetricsUnsupervised {
  public ModelMetricsPCA(Model model, Frame frame) {
    super(model, frame, Double.NaN);
  }

  // PCA currently does not have any model metrics to compute during scoring
  public static class PCAModelMetrics extends MetricBuilderUnsupervised {
    public PCAModelMetrics(int dims) {
      _work = new double[dims];
    }

    @Override
    public double[] perRow(double[] dataRow, float[] preds, Model m) { return dataRow; }

    @Override
    public ModelMetrics makeModelMetrics(Model m, Frame f) {
      return m._output.addModelMetrics(new ModelMetricsPCA(m, f));
    }
  }
}