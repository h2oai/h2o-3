package hex;

/**
 * Distribution functions to be used by ML Algos
 */
//TODO: Separate into family/link
public class Distributions {

  interface Dist {
    double deviance(double w, double y, double f);
    double gradient(double y, double f);
    double linkInv(double f);
    double link(double f);
    String linkInvString(String f);
  }

  public enum Family implements Dist {
    AUTO {
      @Override
      public double deviance(double w, double y, double f) {
        return Double.NaN;
      }

      @Override
      public double gradient(double y, double f) {
        throw new IllegalArgumentException();
      }

      @Override
      public double linkInv(double f) {
        throw new IllegalArgumentException();
      }

      @Override
      public double link(double f) {
        throw new IllegalArgumentException();
      }

      @Override
      public String linkInvString(String f) {
        throw new IllegalArgumentException();
      }
    },

    multinomial {
      @Override
      public double deviance(double w, double y, double f) {
        throw new IllegalArgumentException();
      }

      @Override
      public double gradient(double y, double f) {
        throw new IllegalArgumentException();
      }

      @Override
      public double linkInv(double f) {
        return exp(f);
      }

      @Override
      public double link(double f) {
        return log(f);
      }

      @Override
      public String linkInvString(String f) {
        return expString(f);
      }
    },

    gaussian {
      @Override
      public double deviance(double w, double y, double f) {
        f = link(f);
        return w * (y - f) * (y - f);
      }

      @Override
      public double gradient(double y, double f) {
        return y - f;
      }

      @Override
      public double linkInv(double f) {
        return f;
      }

      @Override
      public double link(double f) {
        return f;
      }

      @Override
      public String linkInvString(String f) {
        return f;
      }
    },

    bernoulli {
      @Override
      public double deviance(double w, double y, double f) {
        f = link(f);
        return - 2 * w * (y * f - log(1 + exp(f)));
      }

      @Override
      public double gradient(double y, double f) {
        return y - linkInv(f);
      }

      @Override
      public double linkInv(double f) {
        return 1. / (1. + exp(-f));
      }

      @Override
      public double link(double f) {
        return log(f / (1 - f));
      }

      @Override
      public String linkInvString(String f) {
        return "1.0/(1.0+" + expString("-" + f)+")";
      }
    },

    poisson {
      @Override
      public double deviance(double w, double y, double f) {
        f = link(f);
        return - 2 * w * (y * f - exp(f));
      }

      @Override
      public double gradient(double y, double f) {
        return y - exp(f);
      }

      @Override
      public double linkInv(double f) {
        return exp(f);
      }

      @Override
      public double link(double f) {
        return log(f);
      }

      @Override
      public String linkInvString(String f) {
        return expString(f);
      }
    },

    gamma {
      @Override
      public double deviance(double w, double y, double f) {
        f = link(f);
        return 2 * w * (y * exp(-f) + f);
      }

      @Override
      public double gradient(double y, double f) {
        return y * exp(-f) - 1;
      }

      @Override
      public double linkInv(double f) {
        return exp(f);
      }

      @Override
      public double link(double f) {
        return log(f);
      }

      @Override
      public String linkInvString(String f) {
        return expString(f);
      }
    },

    tweedie {
      @Override
      public double deviance(double w, double y, double f) {
        assert(p>1 && p<2);
        f = link(f);
        return 2 * w * (Math.pow(y, 2 - p) / ((1 - p) * (2 - p)) - y * exp(f * (1 - p)) / (1 - p) + exp(f * (2 - p)) / (2 - p));
      }

      @Override
      public double gradient(double y, double f) {
        assert(p>1 && p<2);
        return y * exp(f * (1 - p)) - exp(f * (2 - p));
      }

      @Override
      public double linkInv(double f) {
        return exp(f);
      }

      @Override
      public double link(double f) {
        return log(f);
      }

      @Override
      public String linkInvString(String f) {
        return expString(f);
      }
    };
    public double p; //tweedie power //FIXME PUBDEV-1670: This isn't getting serialized by the Icer
  }

  // sanitized exponential function
  public static double exp(double x)  { return Math.max(1e-19, Math.min(1e19, Math.exp(x))); }
  // sanitized log function
  public static double log(double x)  {
    return x == 0 ? -19 : Math.max(-19,Math.min(19, Math.log(x)));
  }

  public static String expString(String x)  { return "Math.max(1e-19, Math.min(1e19, Math.exp(" + x + ")))"; }
}

