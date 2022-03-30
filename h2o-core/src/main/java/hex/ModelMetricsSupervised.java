package hex;

import water.fvec.Frame;
import water.util.ComparisonUtils;

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

  @Override
  public boolean isEqualUpToTolerance(ComparisonUtils.MetricComparator comparator, ModelMetrics other) {
    super.isEqualUpToTolerance(comparator, other);

    comparator.compareUpToTolerance("r2", this.r2(), ((ModelMetricsSupervised)other).r2());
    
    return comparator.isEqual();
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
}
