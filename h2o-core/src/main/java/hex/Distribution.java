package hex;

import water.H2O;
import water.Iced;

/**
 * Distribution functions to be used by ML Algos
 */
//TODO: Separate into family/link
public class Distribution extends Iced {
  public enum Family {
    AUTO,         //model-specific behavior
    bernoulli,    //binomial classification (nclasses == 2)
    multinomial,  //classification (nclasses >= 2)
    gaussian, poisson, gamma, tweedie, huber, laplace, quantile //regression
  }

  // Default constructor for non-Tweedie and non-Quantile families
  public Distribution(Family family) {
    distribution = family;
    assert(family != Family.tweedie);
    assert(family != Family.quantile);
    tweediePower = 1.5;
    quantileAlpha = 0.5;
  }

  /**
   * @param params
   */
  public Distribution(Model.Parameters params) {
    distribution = params._distribution;
    tweediePower = params._tweedie_power;
    quantileAlpha = params._quantile_alpha;
    assert(tweediePower >1 && tweediePower <2);
  }
  static public double MIN_LOG = -19;
  static public double MAX = 1e19;

  public final Family distribution;
  public final double tweediePower; //tweedie power
  public final double quantileAlpha; //for quantile regression

  // helper - sanitized exponential function
  public static double exp(double x) {
    double val = Math.min(MAX, Math.exp(x));
//    if (val == MAX) Log.warn("Exp overflow: exp(" + x + ") truncated to " + MAX);
    return val;
  }

  // helper - sanitized log function
  public static double log(double x) {
    x = Math.max(0,x);
    double val = x == 0 ? MIN_LOG : Math.max(MIN_LOG, Math.log(x));
//    if (val == MIN_LOG) Log.warn("Log underflow: log(" + x + ") truncated to " + MIN_LOG);
    return val;
  }

  // helper - string version of sanititized exp(x)
  public static String expString(String x) {
    return "Math.min(" + MAX + ", Math.exp(" + x + "))";
  }

   /**
   * Deviance of given distribution function at predicted value f
   * @param w observation weight
   * @param y (actual) response
   * @param f (predicted) response in original response space (including offset)
   * @return value of gradient
   */
  public double deviance(double w, double y, double f) {
    f = link(f); //bring back f to link space
    switch (distribution) {
      case AUTO:
      case gaussian:
        return w * (y - f) * (y - f); // 2x as big as what the gradient (y-f) would suggest: we want the full squared error
      case huber:
        if (Math.abs(y-f) < 1) {
          return w * (y - f) * (y - f);
        } else {
          return 2 * w * Math.abs(y-f) - 1;
        }
      case laplace:
        return w * Math.abs(y-f); // weighted absolute deviance == weighted absolute error
      case quantile:
        return y > f ? w*quantileAlpha*(y-f) : w*(1-quantileAlpha)*(f-y);
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
   * Gradient of deviance function at predicted value f, for actual response y
   * @param y (actual) response
   * @param f (predicted) response in link space (including offset)
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
      case huber:
        if (Math.abs(y-f) < 1) {
          return y - f;
        } else {
          return f - 1 >= y ? -1 : 1;
        }
      case laplace:
        return f > y ? -1 : 1;
      case quantile:
        return y > f ? quantileAlpha : quantileAlpha-1;
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
      case huber:
      case laplace:
      case quantile:
        return f;
      case bernoulli:
        return log(f/(1-f));
      case multinomial:
      case poisson:
      case gamma:
      case tweedie:
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
      case huber:
      case laplace:
      case quantile:
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
      case huber:
      case laplace:
      case quantile:
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
        return w * (y-z); //y-z == exp(f)
      case tweedie:
        return w * exp(f*(2- tweediePower));
      default:
        throw H2O.unimpl();
    }
  }
}
