package hex;

import water.H2O;
import water.Iced;
import hex.genmodel.utils.DistributionFamily;
import water.udf.*;

/**
 * Distribution functions to be used by ML Algos
 */
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
        throw H2O.unimpl();
    }

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
        throw H2O.unimpl();
    }
    
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
  }
