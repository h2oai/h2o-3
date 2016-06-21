package hex;

import water.H2O;
import water.fvec.Frame;

public class ModelMetricsAutoEncoder extends ModelMetricsUnsupervised {
  public ModelMetricsAutoEncoder(Model model, Frame frame) {
    super(model, frame, 0, Double.NaN);
  }
  public ModelMetricsAutoEncoder(Model model, Frame frame, long nobs, double mse) {
    super(model, frame, nobs, mse);
  }

  public static class MetricBuilderAutoEncoder extends MetricBuilderUnsupervised {
    public MetricBuilderAutoEncoder(int dims) {
      _work = new double[dims];
    }

    @Override public double[] perRow(double ds[], float yact[], Model m) {
      throw H2O.unimpl();
    }

    // Having computed a MetricBuilder, this method fills in a ModelMetrics
    public ModelMetrics makeModelMetrics(Model m, Frame f, Frame adaptedFrame, Frame preds) {
      return m._output.addModelMetrics(new ModelMetricsAutoEncoder(m, f));
    }
  }
}
