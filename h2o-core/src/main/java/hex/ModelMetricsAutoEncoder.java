package hex;

import water.fvec.Frame;

public class ModelMetricsAutoEncoder extends ModelMetrics {
  public final double _mse;
  public ModelMetricsAutoEncoder(Model model, Frame frame) {
    super(model, frame);
    _mse = Double.NaN;
  }
  public ModelMetricsAutoEncoder(Model model, Frame frame, double mse) {
    super(model, frame);
    _mse = mse;
  }
}
