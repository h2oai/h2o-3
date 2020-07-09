package ai.h2o.targetencoding;

import water.Iced;

public class BlendingParams extends Iced<BlendingParams> {
  private double _k;
  private double _f;

  public BlendingParams(double k, double f) {
    _k = k;
    _f = f;
  }

  /**
   * k represents the inflection point of the sigmoid used as a shrinking factor.
   * For a given category, if the sample size is greater than k, then the posterior weight is greater than the prior.
   * @return the inflection point of the sigmoid function.
   */
  public double getK() {
    return _k;
  }

  /**
   * f represents the smoothing, i.e. the slope of the sigmoid at the inflection point.
   * When f tends to 0, the sigmoid becomes a step function.
   * @return the smoothing factor of the sigmoid function.
   */
  public double getF() {
    return _f;
  }
}
