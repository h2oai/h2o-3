package hex.genmodel.utils;


/**
 * Used to be `hex.Distribution.Family`.
 */
public enum DistributionFamily {
  AUTO,  // model-specific behavior
  bernoulli {
    @Override public double link(double f) { return log(f/(1 - f)); }
    @Override public double linkInv(double f) { return 1 / (1 + exp(-f)); }
    @Override public String linkInvString(String f) { return "1./(1. + " + expString("-("+f+")") + ")"; }
  },
  modified_huber {
    // FIXME
    @Override public double link(double f) { return log(f/(1 - f)); }
    @Override public double linkInv(double f) { return 1 / (1 + exp(-f)); }
    @Override public String linkInvString(String f) { return "1./(1. + " + expString("-("+f+")") + ")"; }
  },
  multinomial {
    @Override public double link(double f) { return log(f); }
    @Override public double linkInv(double f) { return exp(f); }
    @Override public String linkInvString(String f) { return expString(f); }
  },
  gaussian {
  },
  poisson {
    @Override public double link(double f) { return log(f); }
    @Override public double linkInv(double f) { return exp(f); }
    @Override public String linkInvString(String f) { return expString(f); }
  },
  gamma {
    @Override public double link(double f) { return log(f); }
    @Override public double linkInv(double f) { return exp(f); }
    @Override public String linkInvString(String f) { return expString(f); }
  },
  tweedie {
    @Override public double link(double f) { return log(f); }
    @Override public double linkInv(double f) { return exp(f); }
    @Override public String linkInvString(String f) { return expString(f); }
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


  public static double exp(double x) {
    return Math.min(1e19, Math.exp(x));
  }

  public static String expString(String x) {
    return "Math.min(1e19, Math.exp(" + x + "))";
  }

  public static double log(double x) {
    return x <= 0 ? -19 : Math.max(-19, Math.log(x));
  }
}
