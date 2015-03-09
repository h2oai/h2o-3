package hex;

import water.H2O;
import water.fvec.Frame;

public class ModelMetricsSupervised extends ModelMetrics {
  public double _sigma;   // stddev of the response (if any)

  public ModelMetricsSupervised(Model model, Frame frame) {
    super(model, frame);
    _sigma = Double.NaN;
  }
  public ModelMetricsSupervised(Model model, Frame frame, double sigma, double mse) {
    super(model, frame, mse);
    _sigma = sigma;
  }
  public final double r2() {
    double var = _sigma*_sigma;
    return 1.0-(_mse/var);
  }

  public static class MetricBuilderSupervised extends MetricBuilder {
    protected final String[] _domain;
    protected final int _nclasses;

    public MetricBuilderSupervised(int nclasses, String[] domain) {
      assert domain.length >= nclasses; // Domain can be larger than the number of classes, if the score set includes "junk" levels
      _nclasses = nclasses;
      _domain = domain; 
      _work = new float[_nclasses+1];
    }

    @Override public float[] perRow(float[] ds, float[] yact, Model m) {
      throw H2O.unimpl("Subclasses must implement perRow.");
    }

    @Override public ModelMetrics makeModelMetrics(Model m, Frame f, double sigma) { return null; }
  }
}
