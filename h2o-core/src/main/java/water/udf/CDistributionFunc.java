package water.udf;

/**
 * Custom Distribution Function Interface to customize loss and prediction calculation in GBM algorithm
 * 
 * The function has four parts:
 * - link: link function transforms the probability of response variable to a continuous scale that is unbounded
 * - init: computes numerator and denominator of the initial value.
 * - gradient: computes (Negative half) Gradient of deviance function at predicted value for actual response
 * - gamma: computes numerator and denominator of terminal node estimate
 */
public interface CDistributionFunc extends CFunc {

    /**
     * Type of Link function.
     * @return name of link function. Possible functions: log, logit, identity, inverse, ologit, ologlog, oprobit
     */
    String link();

    /**
     * Contribution for initial value computation (numerator and denominator).
     * @param w weight
     * @param o offset
     * @param y response
     * @return [weighted contribution to init numerator,  weighted contribution to init denominator]
     */
    double[] init(double w, double o, double y);
    
    /**
     * (Negative half) Gradient of deviance function at predicted value f, for actual response y.
     * Important for customization of a loss function.
     * @param y (actual) response
     * @param f (predicted) response in link space (including offset)
     * @return gradient
     */
    double gradient(double y, double f);

    /**
     * (Negative half) Gradient of deviance function at predicted value f, for actual response y.
     * Important for customization of a loss function.
     * @param y (actual) response
     * @param f (predicted) response in link space (including offset)
     * @param l (class label) label of a class (converted lexicographically from original labels to 0-number of class - 1)  
     * @return gradient
     */
    double gradient(double y, double f, int l);

    /**
     * Contribution for GBM's leaf node prediction (numerator and denominator).
     * Important for customization of a loss function.
     * @param w weight
     * @param y response
     * @param z residual
     * @param f predicted value (including offset)
     * @return [weighted contribution to gamma numerator, weighted contribution to gamma denominator]
     */
    double[] gamma(double w, double y, double z, double f);
}
