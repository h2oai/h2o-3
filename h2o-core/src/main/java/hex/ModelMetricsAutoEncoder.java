package hex;

import water.H2O;
import water.fvec.Frame;

public class ModelMetricsAutoEncoder extends ModelMetricsUnsupervised {
  public ModelMetricsAutoEncoder(Model model, Frame frame, CustomMetric customMetric) {
    super(model, frame, 0, Double.NaN, customMetric);
  }
  public ModelMetricsAutoEncoder(Model model, Frame frame, long nobs, double mse, CustomMetric customMetric) {
    super(model, frame, nobs, mse, customMetric);
  }

  public static class MetricBuilderAutoEncoder extends MetricBuilderUnsupervised<MetricBuilderAutoEncoder> {
    public MetricBuilderAutoEncoder(int dims) {
      _work = new double[dims];
    }

    @Override public double[] perRow(double ds[], float yact[], Model m) {
      throw H2O.unimpl();
    }

    // Having computed a MetricBuilder, this method fills in a ModelMetrics
    public ModelMetrics makeModelMetrics(Model m, Frame f) {
      return m.addModelMetrics(new ModelMetricsAutoEncoder(m, f, _customMetric));
    }
    
    @Override public ModelMetrics makeModelMetricsWithoutRuntime(Model m) {
      return new ModelMetricsAutoEncoder(m, null, _customMetric);
    }
  }
}
