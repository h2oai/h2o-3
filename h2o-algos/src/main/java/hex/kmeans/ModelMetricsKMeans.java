package hex.kmeans;

import hex.Model;
import hex.ModelMetrics;
import hex.ModelMetricsUnsupervised;
import water.fvec.Frame;

public class ModelMetricsKMeans extends ModelMetricsUnsupervised {

  public String _foo = null;

  public ModelMetricsKMeans(Model model, Frame frame) {
    super(model, frame);
  }

  public static class MetricBuilderKMeans extends ModelMetricsUnsupervised.MetricBuilderUnsupervised {

    public MetricBuilderKMeans(int dims) {
      _work = new float[dims];
    }

    @Override
    public float[] perRow(float[] dataRow, float[] preds, Model m) {
      assert m instanceof KMeansModel;
      return new float[0];
    }

    @Override
    public ModelMetrics makeModelMetrics(Model m, Frame f, double sigma) {
      ModelMetricsKMeans mm = new ModelMetricsKMeans( m, f);
      mm._foo = "f00!";
      return m.addMetrics(mm);
    }
  }
}
