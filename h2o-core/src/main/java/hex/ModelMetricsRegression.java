package hex;

import water.fvec.Frame;

public class ModelMetricsRegression extends ModelMetrics {
  public final double _sigma;   // stddev of the response (if any)
  public final double _mse;     // Mean Squared Error
  public ModelMetricsRegression(Model model, Frame frame) {
    super(model, frame);
    _sigma = Double.NaN;
    _mse = Double.NaN;
  }
  public ModelMetricsRegression(Model model, Frame frame, double sigma, double mse) {
    super(model, frame);
    _sigma = sigma;
    _mse = mse;
  }
  @Override public double r2() {
    double var = _sigma*_sigma;
    return 1.0-(_mse/var);
  }
}
