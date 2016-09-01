package hex.genmodel.utils;

import static java.lang.Math.exp;
import static java.lang.Math.log;

/**
 * Used to be `hex.Distribution.Family`.
 */
public enum DistributionFamily {
  AUTO,  // model-specific behavior
  bernoulli {
    @Override public double link(double f) { return log(f/(1 - f)); }
    @Override public double linkInv(double f) { return 1 / (1 + exp(-f)); }
    @Override public String linkInvString(String f) { return "1/(1+Math.min(1e19, Math.exp(-"+f+")))"; }
  },
  modified_huber {
    @Override public double link(double f) { return log(f/(1 - f)); }
    @Override public double linkInv(double f) { return 1 / (1 + exp(-f)); }
    @Override public String linkInvString(String f) { return "1/(1+Math.min(1e19, Math.exp(-"+f+")))"; }
  },
  multinomial {
    @Override public double link(double f) { return log(f); }
    @Override public double linkInv(double f) { return exp(f); }
    @Override public String linkInvString(String f) { return "Math.min(1e19, Math.exp("+f+")"; }
  },
  gaussian {
  },
  poisson {
    @Override public double link(double f) { return log(f); }
    @Override public double linkInv(double f) { return exp(f); }
    @Override public String linkInvString(String f) { return "Math.min(1e19, Math.exp("+f+")"; }
  },
  gamma {
    @Override public double link(double f) { return log(f); }
    @Override public double linkInv(double f) { return exp(f); }
    @Override public String linkInvString(String f) { return "Math.min(1e19, Math.exp("+f+")"; }
  },
  tweedie {
    @Override public double link(double f) { return log(f); }
    @Override public double linkInv(double f) { return exp(f); }
    @Override public String linkInvString(String f) { return "Math.min(1e19, Math.exp("+f+")"; }
  },
  huber,
  laplace,
  quantile;

  /**
   * Canonical link inverse
   * @param f value in link space, to be transformed back to original space
   * @return linkInv(f)
   */
  public double linkInv(double f) {
    return f;
  }

  /**
   * Canonical link
   * @param f value in original space, to be transformed to link space
   * @return link(f)
   */
  public double link(double f) {
    return f;
  }

  /**
   * String version of link inverse (for POJO scoring code generation)
   * @param f value to be transformed by link inverse
   * @return String that turns into compilable expression of linkInv(f)
   */
  public String linkInvString(String f) {
    return f;
  }

}
