package water.udf;

/**
 * Custom Distribution Function Interface to customize loss and prediction calculation in GBM algorithm
 * 
 * The function has six parts:
 * - link: link function transforms the probability of response variable to a continuous scale that is unbounded
 * - inversion: inversion of link function
 * - deviance: deviance of given distribution function at given predicted value. 
 * - init: computes numerator and denominator of the initial value.
 * - gradient: computes (Negative half) Gradient of deviance function at predicted value for actual response
 * - gamma: computes numerator and denominator of terminal node estimate - gamma 
 */
public interface CDistributionFunc extends CFunc {

    /**
     * Canonical link.
     * @param f value in original space, to be transformed to link space
     * @return link
     */
    double link(double f);

    /** 
     * Canonical link inverse.
     * @param f value in link space, to be transformed to original space
     * @return link inverse
     */
    double inversion(double f);
    
    /**
     * Deviance of given distribution function at predicted value f.
     * Important for calculation of regression metrics.
     * @param w observation weight
     * @param y (actual) response
     * @param f (predicted) response in original response space (including offset)
     * @return deviance
     */
    double deviance(double w, double y, double f);

    /**
     * Contribution for initial value computation (numerator and denominator).
     * @param w weight
     * @param o offset
     * @param y response
     * @return [weighted contribution to numerator,  weighted contribution to denominator]
     */
    double[] init(double w, double o, double y);
    
    /**
     * (Negative half) Gradient of deviance function at predicted value f, for actual response y.
     * Important for customized loss function.
     * @param y (actual) response
     * @param f (predicted) response in link space (including offset)
     * @return gradient
     */
    double gradient(double y, double f);

    /**
     * Contribution for GBM's leaf node prediction (numerator and denominator).
     * Important for customized loss function.
     * @param w weight
     * @param y response
     * @param z residual
     * @param f predicted value (including offset)
     * @return [weighted contribution to numerator, weighted contribution to denominator]
     */
    double[] gamma(double w, double y, double z, double f);
}
