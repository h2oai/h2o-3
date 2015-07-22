package hex;

import water.H2O;
import water.Iced;

/**
 * Distribution functions to be used by ML Algos
 */
//TODO: Separate into family/link
public class Distributions extends Iced {
  public enum Family {AUTO, multinomial, gaussian, bernoulli, poisson, gamma, tweedie}

  public Distributions(Family distribution) {
    this.distribution = distribution;
  }

  public Distributions(Family distribution, double p) {
    this.distribution = distribution;
    this.p = p;
  }

  public Family distribution;
  public double p; //tweedie power

  // sanitized exponential function
  public static double exp(double x) {
    return Math.max(1e-19, Math.min(1e19, Math.exp(x)));
  }

  // sanitized log function
  public static double log(double x) {
    return x == 0 ? -19 : Math.max(-19, Math.min(19, Math.log(x)));
  }

  public static String expString(String x) {
    return "Math.max(1e-19, Math.min(1e19, Math.exp(" + x + ")))";
  }

  public double deviance(double w, double y, double f) {
    switch (distribution) {
      case AUTO:
      case gaussian:
        f = link(f);
        return w * (y - f) * (y - f);
      case bernoulli:
        f = link(f);
        return -2 * w * (y * f - log(1 + exp(f)));
      case poisson:
        f = link(f);
        return -2 * w * (y * f - exp(f));
      case gamma:
        f = link(f);
        return 2 * w * (y * exp(-f) + f);
      case tweedie:
        assert (p > 1 && p < 2);
        f = link(f);
        return 2 * w * (Math.pow(y, 2 - p) / ((1 - p) * (2 - p)) - y * exp(f * (1 - p)) / (1 - p) + exp(f * (2 - p)) / (2 - p));
      default:
        throw H2O.unimpl();
    }
  }

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
        assert (p > 1 && p < 2);
        return y * exp(f * (1 - p)) - exp(f * (2 - p));
      default:
        throw H2O.unimpl();
    }
  }

  // Canonical link
  public double link(double f) {
    switch (distribution) {
      case AUTO:
      case gaussian:
        return f;
      case bernoulli:
        assert(0<f && f<1);
        return log(f / (1 - f));
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

  // Canonical link inverse
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
        return w*y*exp(o*(1-p));
      default:
        throw H2O.unimpl();
    }
  }

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
        return w*exp(o*(2-p));
      default:
        throw H2O.unimpl();
    }
  }
}
