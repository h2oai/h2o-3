package hex;

import water.H2O;
import water.fvec.Frame;

public class ModelMetricsAutoEncoder extends ModelMetricsUnsupervised {
  public final double _mse;
  public ModelMetricsAutoEncoder(Model model, Frame frame) {
    super(model, frame);
    _mse = Double.NaN;
  }
  public ModelMetricsAutoEncoder(Model model, Frame frame, double mse) {
    super(model, frame);
    _mse = mse;
  }

  public static class MetricBuilderAutoEncoder extends MetricBuilderUnsupervised {
    public MetricBuilderAutoEncoder(int dims) {
      _work = new double[dims];
    }

    @Override public double[] perRow(double ds[], float yact[], Model m, int row) {
      throw H2O.unimpl();
    }

    // Having computed a MetricBuilder, this method fills in a ModelMetrics
    public ModelMetrics makeModelMetrics( Model m, Frame f, double mse) {
      return m._output.addModelMetrics(new ModelMetricsAutoEncoder(m, f, mse));
    }
  }
}
