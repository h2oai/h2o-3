package hex;

import water.H2O;
import water.fvec.Frame;

public class ModelMetricsSupervised extends ModelMetrics {
  public final String[] _domain;// Name of classes
  public final double _sigma;   // stddev of the response (if any)

  public ModelMetricsSupervised(Model model, Frame frame, long nobs, double mse, String[] domain, double sigma) {
    super(model, frame, nobs, mse, null);
    _domain = domain;
    _sigma = sigma;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString());
    sb.append(" R^2: " + (float)r2() + "\n");
    return sb.toString();
  }

  public final double r2() {
    double var = _sigma*_sigma;
    double r2 = 1.0-(_MSE /var);
    return r2 >= 0 ? r2 : Double.NaN;
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

    /**
     * Create a model metrics object
     * @param m Model
     * @param f Frame
     * @param adaptedFrame
     *@param preds Optional predictions (can be null), only used to compute Gains/Lift table for binomial problems  @return
     */
    @Override public ModelMetrics makeModelMetrics(Model m, Frame f, Frame adaptedFrame, Frame preds) { return null; }
  }
}
