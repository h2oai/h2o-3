package hex;

import water.H2O;
import water.Iced;

/**
 * Distribution functions to be used by ML Algos
 */
//TODO: Separate into family/link
public class Distributions extends Iced {
  public enum Family {AUTO, multinomial, gaussian, bernoulli, poisson, gamma, tweedie}

  /**
   * Short constructor for non-Tweedie distributions
   * @param distribution
   */
  public Distributions(Family distribution) {
    assert(distribution != Family.tweedie);
    this.distribution = distribution;
    this.tweediePower = 0;
  }

  /**
   * Constructor to be used for Tweedie (and if uncertain)
   * @param distribution
   * @param tweediePower Tweedie Power
   */
  public Distributions(Family distribution, double tweediePower) {
    this.distribution = distribution;
    assert(tweediePower >1 && tweediePower <2);
    this.tweediePower = tweediePower;
  }

  public final Family distribution;
  public final double tweediePower; //tweedie power

  // helper - sanitized exponential function
  public static double exp(double x) {
    return Math.max(1e-19, Math.min(1e19, Math.exp(x)));
  }

  // helper - sanitized log function
  public static double log(double x) {
    return x == 0 ? -19 : Math.max(-19, Math.min(19, Math.log(x)));
  }

  // helper - string version of sanititized exp(x)
  public static String expString(String x) {
    return "Math.max(1e-19, Math.min(1e19, Math.exp(" + x + ")))";
  }

   /**
   * Deviance of given distribution function at predicted value f
   * @param w observation weight
   * @param y (actual) response
   * @param f (predicted) response in original response space
   * @return value of gradient
   */
  public double deviance(double w, double y, double f) {
    f = link(f); //bring back f to link space
    switch (distribution) {
      case AUTO:
      case gaussian:
        return w * (y - f) * (y - f);
      case bernoulli:
        return -2 * w * (y * f - log(1 + exp(f)));
      case poisson:
        return -2 * w * (y * f - exp(f));
      case gamma:
        return 2 * w * (y * exp(-f) + f);
      case tweedie:
        assert (tweediePower > 1 && tweediePower < 2);
        return 2 * w * (Math.pow(y, 2 - tweediePower) / ((1 - tweediePower) * (2 - tweediePower)) - y * exp(f * (1 - tweediePower)) / (1 - tweediePower) + exp(f * (2 - tweediePower)) / (2 - tweediePower));
      default:
        throw H2O.unimpl();
    }
  }

  /**
   * Gradient of given distribution function at predicted value f
   * @param y (actual) response
   * @param f (predicted) response in link space
   * @return value of gradient
   */
  public double gradient(double y, double f) {
    switch (distribution) {
      case AUTO:
      case gaussian:
      case bernoulli:
      case poisson:
        return y - linkInv(f);
      case gamma:
        return y * exp(-f) - 1;
      case tweedie:
        assert (tweediePower > 1 && tweediePower < 2);
        return y * exp(f * (1 - tweediePower)) - exp(f * (2 - tweediePower));
      default:
        throw H2O.unimpl();
    }
  }

  /**
   * Canonical link
   * @param f value in original space, to be transformed to link space
   * @return link(f)
   */
  public double link(double f) {
    switch (distribution) {
      case AUTO:
      case gaussian:
        return f;
      case bernoulli:
        return log(f/(1-f));
      case multinomial:
      case poisson:
      case gamma:
      case tweedie:
        assert(0<f);
        return log(f);
      default:
        throw H2O.unimpl();
    }
  }

  /**
   * Canonical link inverse
   * @param f value in link space, to be transformed back to original space
   * @return linkInv(f)
   */
  public double linkInv(double f) {
    switch (distribution) {
      case AUTO:
      case gaussian:
        return f;
      case bernoulli:
        return 1 / (1 + exp(-f));
      case multinomial:
      case poisson:
      case gamma:
      case tweedie:
        return exp(f);
      default:
        throw H2O.unimpl();
    }
  }

  /**
   * String version of link inverse (for POJO scoring code generation)
   * @param f value to be transformed by link inverse
   * @return String that turns into compilable expression of linkInv(f)
   */
  public String linkInvString(String f) {
    switch (distribution) {
      case AUTO:
      case gaussian:
        return f;
      case bernoulli:
        return "1/(1+" + expString("-" + f) + ")";
      case multinomial:
      case poisson:
      case gamma:
      case tweedie:
        return expString(f);
      default:
        throw H2O.unimpl();
    }
  }

  /**
   * Contribution to numerator for initial value computation
   * @param w weight
   * @param o offset
   * @param y response
   * @return weighted contribution to numerator
   */
  public double initFNum(double w, double o, double y) {
    switch (distribution) {
      case AUTO:
      case gaussian:
      case bernoulli:
      case multinomial:
        return w*(y-o);
      case poisson:
        return w*y;
      case gamma:
        return w*y*linkInv(-o);
      case tweedie:
        return w*y*exp(o*(1- tweediePower));
      default:
        throw H2O.unimpl();
    }
  }

  /**
   * Contribution to denominator for initial value computation
   * @param w weight
   * @param o offset
   * @return weighted contribution to denominator
   */
  public double initFDenom(double w, double o) {
    switch (distribution) {
      case AUTO:
      case gaussian:
      case bernoulli:
      case multinomial:
      case gamma:
        return w;
      case poisson:
        return w*linkInv(o);
      case tweedie:
        return w*exp(o*(2- tweediePower));
      default:
        throw H2O.unimpl();
    }
  }

  /**
   * Contribution to numerator for GBM's leaf node prediction
   * @param w weight
   * @param y response
   * @param z residual
   * @param f predicted value (including offset)
   * @return weighted contribution to numerator
   */
  public double gammaNum(double w, double y, double z, double f) {
    switch (distribution) {
      case gaussian:
      case bernoulli:
      case multinomial:
        return w * z;
      case poisson:
        return w * y;
      case gamma:
        return w * (z+1); //z+1 == y*exp(-f)
      case tweedie:
        return w * y * exp(f*(1- tweediePower));
      default:
        throw H2O.unimpl();
    }
  }

  /**
   * Contribution to denominator for GBM's leaf node prediction
   * @param w weight
   * @param y response
   * @param z residual
   * @param f predicted value (including offset)
   * @return weighted contribution to denominator
   */
  public double gammaDenom(double w, double y, double z, double f) {
    switch (distribution) {
      case gaussian:
      case gamma:
        return w;
      case bernoulli:
        double ff = y-z;
        return w * ff*(1-ff);
      case multinomial:
        double absz = Math.abs(z);
        return w * (absz*(1-absz));
      case poisson:
        return w * (y-z);
      case tweedie:
        return w * exp(f*(2- tweediePower));
      default:
        throw H2O.unimpl();
    }
  }
}
