package hex;

import water.fvec.Frame;

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

  public double r2() { // TODO: Override for GLM Regression  - create new Generic & Generic V3 versions
    double var = _sigma*_sigma;
    return 1.0-_MSE /var;
  }

  abstract public static class MetricBuilderSupervised<T extends MetricBuilderSupervised<T>> extends MetricBuilder<T> {
    protected final String[] _domain;
    protected final int _nclasses;

    public MetricBuilderSupervised(int nclasses, String[] domain) {
      _nclasses = nclasses;
      _domain = domain;
      _work = new double[_nclasses+1];
    }
  }

  abstract public static class IndependentMetricBuilderSupervised<T extends IndependentMetricBuilderSupervised<T>> extends IndependentMetricBuilder<T> {
    protected final String[] _domain;
    protected final int _nclasses;

    public IndependentMetricBuilderSupervised(int nclasses, String[] domain) {
      _nclasses = nclasses;
      _domain = domain;
      _work = new double[_nclasses+1];
    }
  }
}
