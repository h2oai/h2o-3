package hex;

import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

public class ModelMetricsSupervised extends ModelMetrics {
  public final String[] _domain;// Name of classes
  public final double _sigma;   // stddev of the response (if any)

  public ModelMetricsSupervised(Model model, Frame frame, long nobs, double mse, String[] domain, double sigma, CustomMetric customMetric) {
    super(model, frame, nobs, mse, null, customMetric);
    _domain = domain;
    _sigma = sigma;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString());
    return sb.toString();
  }

  public final double r2() {
    double var = _sigma*_sigma;
    return 1.0-_MSE /var;
  }

  abstract public static class MetricBuilderSupervised<T extends MetricBuilderSupervised<T>> extends MetricBuilder<T> {
    protected final String[] _domain;
    protected final int _nclasses;

    public MetricBuilderSupervised(int nclasses, String[] domain) {
      assert domain==null || domain.length >= nclasses; // Domain can be larger than the number of classes, if the score set includes "junk" levels
      _nclasses = nclasses;
      _domain = domain;
      _work = new double[_nclasses+1];
    }

  }
}
