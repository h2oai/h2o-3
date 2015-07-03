package hex;

import water.H2O;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.FrameUtils;

public class ModelMetricsSupervised extends ModelMetrics {
  public final String[] _domain;// Name of classes
  public final double _sigma;   // stddev of the response (if any)

  public ModelMetricsSupervised(Model model, Frame frame, double mse, String[] domain, double sigma) {
    super(model, frame, mse, null);
    _domain = domain;
    _sigma = sigma;
  }
  public final double r2() {
    double var = _sigma*_sigma;
    return 1.0-(_MSE /var);
  }

  public static class MetricBuilderSupervised<T extends MetricBuilderSupervised<T>> extends MetricBuilder<T> {
    protected final String[] _domain;
    protected final int _nclasses;

    public MetricBuilderSupervised(int nclasses, String[] domain) {
      assert domain==null || domain.length >= nclasses; // Domain can be larger than the number of classes, if the score set includes "junk" levels
      _nclasses = nclasses;
      _domain = domain; 
      _work = new double[_nclasses+1];
    }

    @Override public double[] perRow(double[] ds, float[] yact, Model m) {
      throw H2O.fail("Subclasses must implement perRow.");
    }

    @Override public ModelMetrics makeModelMetrics(Model m, Frame f) { return null; }
  }
}
