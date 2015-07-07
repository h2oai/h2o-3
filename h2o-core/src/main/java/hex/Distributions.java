package hex;

/**
 * Distribution functions to be used by ML Algos
 * Note: Not using interface or inheritance for performance reasons - let the user call these directly
 */
public class Distributions {

  public static class Gaussian {
    public static double deviance(double w, double y, double f) { return w*(y-f)*(y-f); }
    public static double gradient(double y, double f) { return y-invLink(f); }
    public static double invLink(double f) { return f; }
  }

  public static class Bernoulli {
    public static double deviance(double w, double y, double f) { return w*(y*f - Math.log(1+Math.exp(f))); }
    public static double gradient(double y, double f) { return y-linkInv(f); }
    public static double linkInv(double f) { return 1./(1.+Math.exp(-f)); } //logit
  }

  public static class Poisson {
    public static double deviance(double w, double y, double f) { return w*(y*f - invLink(f)); }
    public static double gradient(double y, double f) { return y-invLink(f); }
    public static double invLink(double f) { return Math.max(1e-19,Math.min(1e19,Math.exp(f))); }
  }

  public static class Gamma {
    public static double deviance(double w, double y, double f) { return w*(y*Math.exp(-f)+f); }
    public static double gradient(double y, double f) { return y*Math.exp(-f)-1; }
    public static double linkInv(double f) { return -1/f; }
  }
}
