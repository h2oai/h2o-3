package ai.h2o.targetencoding;

import water.Iced;

public class BlendingParams extends Iced<BlendingParams> {
  private double _inflectionPoint;
  private double _smoothing;

  public BlendingParams(double inflectionPoint, double smoothing) {
    _inflectionPoint = inflectionPoint;
    _smoothing =  smoothing;
  }

  /**
   * The inflection point of the sigmoid used as a shrinking factor.
   * For a given category, if the sample size is greater than this value, then the posterior weight is greater than the prior.
   * @return the inflection point of the sigmoid function.
   */
  public double getInflectionPoint() {
    return _inflectionPoint;
  }

  /**
   * The smoothing factor, i.e. the inverse of the slope of the sigmoid at the inflection point.
   * When it tends to 0, the sigmoid becomes a step function.
   * @return the smoothing factor of the sigmoid function.
   */
  public double getSmoothing() {
    return _smoothing;
  }
}
