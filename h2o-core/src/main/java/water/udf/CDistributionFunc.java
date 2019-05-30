package water.udf;

/**
 * Custom Distribution Function
 */
public interface CDistributionFunc extends CFunc {

    /**
     * Canonical link, Canonical link inverse
     * @param f value in original space, to be transformed to link space
     * @return [link, link inverse]
     */
    public double[] link(double f);
    
    /**
     * Deviance of given distribution function at predicted value f
     * @param w observation weight
     * @param y (actual) response
     * @param f (predicted) response in original response space (including offset)
     * @return deviance
     */
    public double deviance(double w, double y, double f);

    /**
     * Contribution for initial value computation
     * @param w weight
     * @param o offset
     * @param y response
     * @return [weighted contribution to numerator,  weighted contribution to denominator]
     */
    public double[] init(double w, double o, double y);


    /**
     * (Negative half) Gradient of deviance function at predicted value f, for actual response y
     * This assumes that the deviance(w,y,f) is w*deviance(y,f), so the gradient is w * d/df deviance(y,f)
     * @param y (actual) response
     * @param f (predicted) response in link space (including offset)
     * @return -1/2 * d/df deviance(w=1,y,f)
     */
    public double gradient(double y, double f);
    

    /**
     * Contribution for GBM's leaf node prediction
     * @param w weight
     * @param y response
     * @param z residual
     * @param f predicted value (including offset)
     * @return [weighted contribution to numerator, weighted contribution to denominator]
     */
    public double[] gamma(double w, double y, double z, double f);
}
