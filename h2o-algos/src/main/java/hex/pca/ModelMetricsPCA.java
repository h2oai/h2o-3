package hex.pca;

import com.google.gson.JsonObject;
import hex.CustomMetric;
import hex.Model;
import hex.ModelMetrics;
import hex.ModelMetricsUnsupervised;
import hex.genmodel.IMetricBuilder;
import hex.genmodel.algos.pca.PCAMojoModel;
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

  public static class PCAMetricBuilderFactory extends ModelMetrics.MetricBuilderFactory<PCAModel, PCAMojoModel> {
    @Override
    public IMetricBuilder createBuilder(PCAMojoModel mojoModel, JsonObject extraInfo) {
      return new ModelMetricsPCA.IndependentPCAMetricBuilder();
    }
  }
}
