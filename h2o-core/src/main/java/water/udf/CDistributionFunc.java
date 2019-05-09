package water.udf;

import java.io.Serializable;

/**
 * Custom Distribution Function
 */
public interface CDistributionFunc extends CFunc, Serializable {

    /**
     * Canonical link
     * @param f value in original space, to be transformed to link space
     * @return link(f)
     */
    public double link(double f);

    /**
     * Canonical link inverse
     * @param f value in link space, to be transformed back to original space
     * @return linkInv(f)
     */
    public double linkInv(double f);

    /**
     * String version of link inverse (for POJO scoring code generation)
     * @param f value to be transformed by link inverse
     * @return String that turns into compilable expression of linkInv(f)
     */
    public String linkInvString(String f);

    /**
     * Deviance of given distribution function at predicted value f
     * @param w observation weight
     * @param y (actual) response
     * @param f (predicted) response in original response space (including offset)
     * @return deviance
     */
    public double deviance(double w, double y, double f);

    /**
     * (Negative half) Gradient of deviance function at predicted value f, for actual response y
     * This assumes that the deviance(w,y,f) is w*deviance(y,f), so the gradient is w * d/df deviance(y,f)
     * @param y (actual) response
     * @param f (predicted) response in link space (including offset)
     * @return -1/2 * d/df deviance(w=1,y,f)
     */
    public double negHalfGradient(double y, double f);

    /**
     * Contribution to numerator for initial value computation
     * @param w weight
     * @param o offset
     * @param y response
     * @return weighted contribution to numerator
     */
    public double initFNum(double w, double o, double y);

    /**
     * Contribution to denominator for initial value computation
     * @param w weight
     * @param o offset
     * @param y response
     * @return weighted contribution to denominator
     */
    public double initFDenom(double w, double o, double y);

    /**
     * Contribution to numerator for GBM's leaf node prediction
     * @param w weight
     * @param y response
     * @param z residual
     * @param f predicted value (including offset)
     * @return weighted contribution to numerator
     */
    public double gammaNum(double w, double y, double z, double f);

    /**
     * Contribution to denominator for GBM's leaf node prediction
     * @param w weight
     * @param y response
     * @param z residual
     * @param f predicted value (including offset)
     * @return weighted contribution to denominator
     */
    public double gammaDenom(double w, double y, double z, double f);
}
