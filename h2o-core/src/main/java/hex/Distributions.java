package hex;

/**
 * Distribution functions to be used by ML Algos
 * Note: Not using interface or inheritance for performance reasons - let the user call these directly
 */
public class Distributions {

  public static class Gaussian {
    public static double deviance(double w, double y, double f) {
      return w * (y - f) * (y - f);
    }

    public static double gradient(double y, double f) {
      return y - linkInv(f);
    }

    public static double linkInv(double f) {
      return f;
    }

    public static String linkInvString(String f) {
      return f;
    }
  }

  public static class Bernoulli {
    public static double deviance(double w, double y, double f) {
      return w * (y * f - Math.log(1 + exp(f)));
    }

    public static double gradient(double y, double f) {
      return y - linkInv(f);
    }

    public static double linkInv(double f) {
      return 1. / (1. + exp(-f));
    }

    public static String linkInvString(String f) {
      return "1.0/(1.0+Math.exp(-"+f+"))";
    }
  }

  public static class Poisson {
    public static double deviance(double w, double y, double f) {
      return w * (y * f - linkInv(f));
    }

    public static double gradient(double y, double f) {
      return y - linkInv(f);
    }

    public static double linkInv(double f) {
      return exp(f);
    }

    public static String linkInvString(String f) {
      return expString(f);
    }
  }

  public static class Gamma {
    public static double deviance(double w, double y, double f) {
      return w * (y * linkInv(-f) + f);
    }

    public static double gradient(double y, double f) {
      return y * linkInv(-f) - 1;
    }

    public static double linkInv(double f) {
      return exp(f);
    }

    public static String linkInvString(String f) {
      return expString(f);
    }
  }

  public static class Tweedie {
    public static double deviance(double w, double y, double f, double p) {
      return w * (Math.pow(y, 2 - p) / ((1 - p) * (2 - p)) - y * exp(f * (1 - p)) / (1 - p) + exp(f * (2 - p)) / (2 - p));
    }

    public static double gradient(double y, double f, double p) {
      return y * exp(f * (1 - p)) - exp(f * (2 - p));
    }

    public static double linkInv(double f) {
      return exp(f);
    }

    public static String linkInvString(String f) {
      return expString(f);
    }
  }

  // sanitized exponential function
  public static double exp(double x)  { return Math.max(1e-19, Math.min(1e19, Math.exp(x))); }

  public static String expString(String x)  { return "Math.max(1e-19, Math.min(1e19, Math.exp(" + x + ")))"; }
}

