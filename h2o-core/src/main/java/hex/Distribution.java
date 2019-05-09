package hex;

import water.H2O;
import water.Iced;
import hex.genmodel.utils.DistributionFamily;
import water.udf.*;

/**
 * Distribution functions to be used by ML Algos
 */
<<<<<<< HEAD
public abstract class Distribution extends Iced<Distribution> {
    
    public final double tweediePower; // tweedie power
    public final double quantileAlpha; // for quantile regression
    public double huberDelta; // should be updated to huber_alpha quantile of absolute error of predictions via setter
    public final LinkFunction linkFunction; // link function to use mainly for GLM
    public final DistributionFamily distribution; // distribution name, important for some algos to decide what to do
    
    public Distribution(DistributionFamily family, LinkFunction lf) {
        tweediePower = 1.5;
        quantileAlpha = 0.5;
        huberDelta = Double.NaN;
        linkFunction = lf;
        distribution = family;
    }

    public Distribution(DistributionFamily family) {
        tweediePower = 1.5;
        quantileAlpha = 0.5;
        huberDelta = Double.NaN;
        linkFunction = new IdentityFunction();
        distribution = family;
    }

    public Distribution(Model.Parameters params, LinkFunction lf) {
        tweediePower = params._tweedie_power;
        quantileAlpha = params._quantile_alpha;
        huberDelta = 1;
        assert (tweediePower > 1 && tweediePower < 2);
        linkFunction = lf;
        distribution = params._distribution;
    }
    
    public Distribution(Model.Parameters params) {
        tweediePower = params._tweedie_power;
        quantileAlpha = params._quantile_alpha;
        huberDelta = 1;
        assert (tweediePower > 1 && tweediePower < 2);
        linkFunction = new IdentityFunction();
        distribution = params._distribution;
    }
    
    /**
     * Setter of huber delta. Required for Huber aka M-regression.
     *
     * @param huberDelta
     */
    public void setHuberDelta(double huberDelta) {
        this.huberDelta = huberDelta;
    }
    
    /**
     * Canonical link
     *
     * @param f value in original space, to be transformed to link space
     * @return link(f)
     */
    public double link(double f) {
        return linkFunction.link(f);
    }

    /**
     * Canonical link inverse
     *
     * @param f value in link space, to be transformed back to original space
     * @return linkInv(f)
     */
    public double linkInv(double f) {
        return linkFunction.linkInv(f);
    }

    /**
     * String version of link inverse (for POJO scoring code generation)
     *
     * @param f value to be transformed by link inverse
     * @return String that turns into compilable expression of linkInv(f)
     */
    public String linkInvString(String f) {
        return linkFunction.linkInvString(f);
    }

    /**
     * Deviance of given distribution function at predicted value f
     *
     * @param w observation weight
     * @param y (actual) response
     * @param f (predicted) response in original response space (including offset)
     * @return deviance
     */
    public double deviance(double w, double y, double f) {
        throw H2O.unimpl();
    }
    
    /**
     * (Negative half) Gradient of deviance function at predicted value f, for actual response y
     * This assumes that the deviance(w,y,f) is w*deviance(y,f), so the gradient is w * d/df deviance(y,f)
     *
     * @param y (actual) response
     * @param f (predicted) response in link space (including offset)
     * @return -1/2 * d/df deviance(w=1,y,f)
     */
    public double negHalfGradient(double y, double f) {
        throw H2O.unimpl();
    }
    
    /**
     * Contribution to numerator for initial value computation
     *
     * @param w weight
     * @param o offset
     * @param y response
     * @return weighted contribution to numerator
     */
    public double initFNum(double w, double o, double y) {
        throw H2O.unimpl();
    }
    
    /**
     * Contribution to denominator for initial value computation
     *
     * @param w weight
     * @param o offset
     * @param y response
     * @return weighted contribution to denominator
     */
    public double initFDenom(double w, double o, double y) {
=======
public class Distribution extends Iced<Distribution> implements CDistributionFunc {

  static public double MIN_LOG = -19;
  static public double MAX = 1e19;

  public final DistributionFamily distribution;
  public final double tweediePower; //tweedie power
  public final double quantileAlpha; //for quantile regression
  public double huberDelta; //should be updated to huber_alpha quantile of absolute error of predictions via setter
  public final CDistributionFunc custDist;
  
  public Distribution(DistributionFamily family) {
    distribution = family;
    tweediePower = 1.5;
    quantileAlpha = 0.5;
    huberDelta = Double.NaN;
    custDist = null;
  }

  public Distribution(Model.Parameters params) {
    distribution = params._distribution;
    if(distribution == DistributionFamily.custom){
      //String tmpName = params._custom_distribution_func.replaceFirst("python", "java");
      custDist = new CustomDistribution(CFuncRef.from(params._custom_distribution_func)).getFunc();
    } else{
      custDist = null;
    }
    tweediePower = params._tweedie_power;
    quantileAlpha = params._quantile_alpha;
    huberDelta = 1; 
    assert(tweediePower >1 && tweediePower <2);
  }

  /**
   * Setter of huber delta. Required for Huber aka M-regression.
   * @param huberDelta
   */
  public void setHuberDelta(double huberDelta) { this.huberDelta = huberDelta; }

  /**
   * Sanitized exponential function - helper function.
   * @param x value to be transform
   * @return result of exp function
   */
  public static double exp(double x) {
    double val = Math.min(MAX, Math.exp(x));
    //if (val == MAX) Log.warn("Exp overflow: exp(" + x + ") truncated to " + MAX);
    return val;
  }

  /**
   * Sanitized log function - helper function
   * @param x value to be transform
   * @return result of log function
   */
  public static double log(double x) {
    x = Math.max(0,x);
    double val = x == 0 ? MIN_LOG : Math.max(MIN_LOG, Math.log(x));
    //  if (val == MIN_LOG) Log.warn("Log underflow: log(" + x + ") truncated to " + MIN_LOG);
    return val;
  }

  /**
   * String version of sanititized exp(x) - helper function to use in POJO
   * @param x value to be transform to string
   * @return string representation of calculation
   */
  public static String expString(String x) {
    return "Math.min(" + MAX + ", Math.exp(" + x + "))";
  }

  @Override
  public double deviance(double w, double y, double f) {
    switch (distribution) {
      case AUTO:
      case gaussian:
        return w * (y - f) * (y - f); // leads to wMSE
      case huber:
        if (Math.abs(y-f) <= huberDelta) {
          return w * (y - f) * (y - f); // same as wMSE
        } else {
          return 2 * w * (Math.abs(y-f) - huberDelta)*huberDelta; // note quite the same as wMAE
        }
      case laplace:
        return w * Math.abs(y-f); // same as wMAE
      case quantile:
        return y > f ? w*quantileAlpha*(y-f) : w*(1-quantileAlpha)*(f-y);
      case bernoulli:  // unused
        return -2 * w * (y * log(f) + (1 - y) * log(1 - f));
      case quasibinomial:  // unused
        if (y==f) return 0;
        if (f > 1)
          return - 2 * w * y * log(f);
        else if (f < 0)
          return - 2 * w * (1 - y) * log(1 - f);
        else
          return -2 * w * (y * log(f) + (1 - y) * log(1 - f));
      case poisson:
        f = link(f); //bring back f to link space
        return -2 * w * (y * f - exp(f));
      case gamma:
        f = link(f); //bring back f to link space
        return 2 * w * (y * exp(-f) + f);
      case tweedie:
        f = link(f); //bring back f to link space
        assert (tweediePower > 1 && tweediePower < 2);
        return 2 * w * (Math.pow(y, 2 - tweediePower) / ((1 - tweediePower) * (2 - tweediePower)) - y * exp(f * (1 - tweediePower)) / (1 - tweediePower) + exp(f * (2 - tweediePower)) / (2 - tweediePower));
      case modified_huber:
        double yf = (2*y-1)*f;
        if (yf < -1)
          return -w*4*yf;
        else if (yf > 1)
          return 0;
        else
          return w* yf * yf;
      case custom:
        return custDist.deviance(w, y, f);
      default:
        throw H2O.unimpl();
    }
  }

  @Override
  public double negHalfGradient(double y, double f) {
    switch (distribution) {
      case AUTO:
      case gaussian:
      case bernoulli:
      case poisson:
        return y - linkInv(f);
      case quasibinomial:
        double ff = linkInv(f);
        if (ff==y)
          return 0;
        else if (ff > 1)
          return y/ff;
        else if (ff < 0)
          return (1-y)/(ff-1);
        else
          return y - ff;
      case gamma:
        return y * exp(-f) - 1;
      case tweedie:
        assert (tweediePower > 1 && tweediePower < 2);
        return y * exp(f * (1 - tweediePower)) - exp(f * (2 - tweediePower));
      case huber:
        if (Math.abs(y-f) <= huberDelta) {
          return y - f;
        } else {
          return f >= y ? -huberDelta : huberDelta;
        }
      case laplace:
        return f > y ? -0.5 : 0.5;
      case quantile:
        return y > f ? 0.5*quantileAlpha : 0.5*(quantileAlpha-1);
//        return y > f ? quantileAlpha : quantileAlpha-1;
      case modified_huber:
        double yf = (2*y-1)*f;
        if (yf < -1)
          return 2*(2*y-1);
        else if (yf > 1)
          return 0;
        else
          return -f*(2*y-1)*(2*y-1);
      case custom:
        return custDist.negHalfGradient(y, f);
      default:
        throw H2O.unimpl();
    }
  }

  @Override
  public double link(double f) {
    switch (distribution) {
      case bernoulli:
      case quasibinomial:  
      case modified_huber:
      case ordinal:
        return log(f / (1 - f));
      case multinomial:
      case poisson:
      case gamma:
      case tweedie:
        return log(f);
      case custom:
        return custDist.link(f);
      default:
        return f;
    }
  }

  @Override
  public double linkInv(double f) {
    switch (distribution) {
      case bernoulli:
      case quasibinomial:
      case modified_huber:
      case ordinal:
        return 1/(1+exp(-f));
      case multinomial:
      case poisson:
      case gamma:
      case tweedie:
        return exp(f);
      case custom:
        return custDist.linkInv(f);
      default:
        return f;
    }
  }

  @Override
  public String linkInvString(String f) {
    switch (distribution) {
      case bernoulli:
      case quasibinomial:
      case modified_huber:
      case ordinal:
        return "1./(1. + " + expString("-("+f+")") + ")";
      case multinomial:
      case poisson:
      case gamma:
      case tweedie:
        return expString(f);
      case custom:
        return custDist.linkInvString(f);
      default:
        return f;
    }
  }

  @Override
  public double initFNum(double w, double o, double y) {
    switch (distribution) {
      case AUTO:
      case gaussian:
      case bernoulli:
      case quasibinomial:
      case multinomial:
        return w*(y-o);
      case poisson:
        return w*y;
      case gamma:
        return w*y*linkInv(-o);
      case tweedie:
        return w*y*exp(o*(1- tweediePower));
      case modified_huber:
        return y==1 ? w : 0;
      case custom:
        return custDist.initFNum(w, o, y);
      default:
        throw H2O.unimpl();
    }
  }

  @Override
  public double initFDenom(double w, double o, double y) {
    switch (distribution) {
      case AUTO:
      case gaussian:
      case bernoulli:
      case quasibinomial:
      case multinomial:
      case gamma:
        return w;
      case poisson:
        return w*linkInv(o);
      case tweedie:
        return w*exp(o*(2- tweediePower));
      case modified_huber:
        return y==1 ? 0 : w;
      case custom:
        return custDist.initFDenom(w, o, y);
      default:
>>>>>>> PUBDEV-4076 custom distribution proof of concept
        throw H2O.unimpl();
    }

<<<<<<< HEAD
    /**
     * Contribution to numerator for GBM's leaf node prediction
     *
     * @param w weight
     * @param y response
     * @param z residual
     * @param f predicted value (including offset)
     * @return weighted contribution to numerator
     */
    public double gammaNum(double w, double y, double z, double f) {
=======
  @Override
  public double gammaNum(double w, double y, double z, double f) {
    switch (distribution) {
      case gaussian:
      case bernoulli:
      case quasibinomial:
      case multinomial:
        return w * z;
      case poisson:
        return w * y;
      case gamma:
        return w * (z+1); //z+1 == y*exp(-f)
      case tweedie:
        return w * y * exp(f*(1- tweediePower));
      case modified_huber:
        double yf = (2*y-1)*f;
        if (yf < -1) return w*4*(2*y-1);
        else if (yf > 1) return 0;
        else return w*2*(2*y-1)*(1-yf);
      case custom:
        return custDist.gammaNum(w, y, z, f);
      default:
>>>>>>> PUBDEV-4076 custom distribution proof of concept
        throw H2O.unimpl();
    }

<<<<<<< HEAD
    /**
     * Contribution to denominator for GBM's leaf node prediction
     *
     * @param w weight
     * @param y response
     * @param z residual
     * @param f predicted value (including offset)
     * @return weighted contribution to denominator
     */
    public double gammaDenom(double w, double y, double z, double f) {
        throw H2O.unimpl();
    }
=======
  @Override
  public double gammaDenom(double w, double y, double z, double f) {
    switch (distribution) {
      case gaussian:
      case gamma:
        return w;
      case bernoulli:
      case quasibinomial:
        double ff = y-z;
        return w * ff*(1-ff);
      case multinomial:
        double absz = Math.abs(z);
        return w * (absz*(1-absz));
      case poisson:
        return w * (y-z); //y-z == exp(f)
      case tweedie:
        return w * exp(f*(2- tweediePower));
      case modified_huber:
        double yf = (2*y-1)*f;
        if (yf < -1) return -w*4*yf;
        else if (yf > 1) return 0;
        else return w*(1-yf)*(1-yf);
      case custom:
        return custDist.gammaDenom(w, y, z, f);
      default:
        throw H2O.unimpl();
    }
  }

  class CustomDistribution extends CFuncObject<CDistributionFunc> {

    CustomDistribution(CFuncRef ref){
      super(ref);
    }

    @Override
    protected Class<CDistributionFunc> getFuncType() {
      return CDistributionFunc.class;
    }
  }

>>>>>>> PUBDEV-4076 custom distribution proof of concept
}

