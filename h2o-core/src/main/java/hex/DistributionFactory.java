package hex;

import hex.genmodel.utils.DistributionFamily;
import water.H2O;
import water.udf.CDistributionFunc;
import water.udf.CFuncObject;
import water.udf.CFuncRef;

public class DistributionFactory {

    public static Distribution getDistribution(DistributionFamily family) {
        switch (family) {
            case bernoulli:
                return new BernoulliDistribution(family);
            case quasibinomial:
                return new QuasibinomialDistribution(family);
            case modified_huber:
                return new ModifiedHuberDistribution(family);
            case multinomial:
                return new MultinomialDistribution(family);
            case AUTO: 
            case gaussian:
                return new GaussianDistribution(family);
            case poisson:
                return new PoissonDistribution(family);
            case gamma:
                return new GammaDistribution(family);
            case laplace:
                return new LaplaceDistribution(family);
            default:
                throw H2O.unimpl("Try to get "+family+" which is not supported.");
        }
    }

    public static Distribution getDistribution(Model.Parameters params) {
        DistributionFamily family = params._distribution;
        switch (family) {
            case bernoulli:
                return new BernoulliDistribution(family);
            case quasibinomial:
                return new QuasibinomialDistribution(family);
            case modified_huber:
                return new ModifiedHuberDistribution(family);
            case multinomial:
                return new MultinomialDistribution(family);
            case AUTO:    
            case gaussian:
                return new GaussianDistribution(family);
            case poisson:
                return new PoissonDistribution(family);
            case gamma:
                return new GammaDistribution(family);
            case laplace:
                return new LaplaceDistribution(family);
            case tweedie:
                return new TweedieDistribution(params);
            case huber:
                return new HuberDistribution(params);
            case quantile:
                return new QuantileDistribution(params);
            case custom:
                return new CustomDistribution(params);
            default:
                throw H2O.unimpl("Try to get "+family+" which is not supported.");
        }
    }
}

class GaussianDistribution extends Distribution {

    public GaussianDistribution(DistributionFamily family){
        super(family);
    }

    @Override
    public double deviance(double w, double y, double f) {
        return w * (y - f) * (y - f); // leads to wMSE
    }

    @Override
    public double negHalfGradient(double y, double f) {
        return y - linkInv(f);
    }

    @Override
    public double initFNum(double w, double o, double y) {
        return w * (y - o);
    }

    @Override
    public double initFDenom(double w, double o, double y) {
        return w;
    }

    @Override
    public double gammaNum(double w, double y, double z, double f) {
        return w * z;
    }

    @Override
    public double gammaDenom(double w, double y, double z, double f) {
        return w;
    }
}

class BernoulliDistribution extends Distribution {

    public BernoulliDistribution(DistributionFamily family){
        super(family, new LogitFunction());
    }

    @Override
    public double deviance(double w, double y, double f) {
        return -2 * w * (y * LogExpUtil.log(f) + (1 - y) * LogExpUtil.log(1 - f));
    }

    @Override
    public double negHalfGradient(double y, double f) {
        return y - linkInv(f);
    }

    @Override
    public double initFNum(double w, double o, double y) {
        return w * (y - o);
    }

    @Override
    public double initFDenom(double w, double o, double y) {
        return w;
    }

    @Override
    public double gammaNum(double w, double y, double z, double f) {
        return w * z;
    }

    @Override
    public double gammaDenom(double w, double y, double z, double f) {
        double ff = y - z;
        return w * ff * (1 - ff);
    }
}


class QuasibinomialDistribution extends Distribution {

    public QuasibinomialDistribution(DistributionFamily family){
        super(family, new LogitFunction());
    }

    @Override
    public double deviance(double w, double y, double f) {
        if (y == f) return 0;
        if (f > 1)
            return -2 * w * y * LogExpUtil.log(f);
        else if (f < 0)
            return -2 * w * (1 - y) * LogExpUtil.log(1 - f);
        else
            return -2 * w * (y * LogExpUtil.log(f) + (1 - y) * LogExpUtil.log(1 - f));
    }

    @Override
    public double negHalfGradient(double y, double f) {
        double ff = linkInv(f);
        if (ff == y)
            return 0;
        else if (ff > 1)
            return y / ff;
        else if (ff < 0)
            return (1 - y) / (ff - 1);
        else
            return y - ff;
    }

    @Override
    public double initFNum(double w, double o, double y) {
        return w * (y - o);
    }

    @Override
    public double initFDenom(double w, double o, double y) {
        return w;
    }

    @Override
    public double gammaNum(double w, double y, double z, double f) {
        return w * z;
    }

    @Override
    public double gammaDenom(double w, double y, double z, double f) {
        double ff = y - z;
        return w * ff * (1 - ff);
    }
}

class ModifiedHuberDistribution extends Distribution {

    public ModifiedHuberDistribution(DistributionFamily family){
        super(family, new LogitFunction());
    }

    @Override
    public double deviance(double w, double y, double f) {
        double yf = (2 * y - 1) * f;
        if (yf < -1)
            return -w * 4 * yf;
        else if (yf > 1)
            return 0;
        else
            return w * yf * yf;
    }

    @Override
    public double negHalfGradient(double y, double f) {
        double yf = (2 * y - 1) * f;
        if (yf < -1)
            return 2 * (2 * y - 1);
        else if (yf > 1)
            return 0;
        else
            return -f * (2 * y - 1) * (2 * y - 1);
    }

    @Override
    public double initFNum(double w, double o, double y) {
        return y == 1 ? w : 0;
    }

    @Override
    public double initFDenom(double w, double o, double y) {
        return y == 1 ? 0 : w;
    }

    @Override
    public double gammaNum(double w, double y, double z, double f) {
        double yf = (2 * y - 1) * f;
        if (yf < -1) return w * 4 * (2 * y - 1);
        else if (yf > 1) return 0;
        else return w * 2 * (2 * y - 1) * (1 - yf);
    }

    @Override
    public double gammaDenom(double w, double y, double z, double f) {
        double yf = (2 * y - 1) * f;
        if (yf < -1) return -w * 4 * yf;
        else if (yf > 1) return 0;
        else return w * (1 - yf) * (1 - yf);
    }
}

class MultinomialDistribution extends Distribution {

    public MultinomialDistribution(DistributionFamily family){
        super(family, new LogFunction());
    }

    @Override
    public double initFNum(double w, double o, double y) {
        return w * (y - o);
    }

    @Override
    public double initFDenom(double w, double o, double y) {
        return w;
    }

    @Override
    public double gammaNum(double w, double y, double z, double f) {
        return w * z;
    }

    @Override
    public double gammaDenom(double w, double y, double z, double f) {
        double absz = Math.abs(z);
        return w * (absz * (1 - absz));
    }
}


class PoissonDistribution extends Distribution {

    public PoissonDistribution(DistributionFamily family){
        super(family, new LogFunction());
    }

    @Override
    public double deviance(double w, double y, double f) {
        f = link(f); //bring back f to link space
        return -2 * w * (y * f - LogExpUtil.exp(f));
    }

    @Override
    public double negHalfGradient(double y, double f) {
        return y - linkInv(f);
    }

    @Override
    public double initFNum(double w, double o, double y) {
        return w * y;
    }

    @Override
    public double initFDenom(double w, double o, double y) {
        return w * linkInv(o);
    }

    @Override
    public double gammaNum(double w, double y, double z, double f) {
        return w * y;
    }

    @Override
    public double gammaDenom(double w, double y, double z, double f) {
        return w * (y - z); // y - z == LogExpUtil.exp(f)
    }
}


class GammaDistribution extends Distribution {

    public GammaDistribution(DistributionFamily family){
        super(family, new LogFunction());
    }

    @Override
    public double deviance(double w, double y, double f) {
        f = link(f); // bring back f to link space
        return 2 * w * (y * LogExpUtil.exp(-f) + f);
    }

    @Override
    public double negHalfGradient(double y, double f) {
        return y * LogExpUtil.exp(-f) - 1;
    }

    @Override
    public double initFNum(double w, double o, double y) {
        return w * y * linkInv(-o);
    }

    @Override
    public double initFDenom(double w, double o, double y) {
        return w;
    }

    @Override
    public double gammaNum(double w, double y, double z, double f) {
        return w * (z + 1); // z + 1 == y * LogExpUtil.exp(-f)
    }

    @Override
    public double gammaDenom(double w, double y, double z, double f) {
        return w;
    }
}

class TweedieDistribution extends Distribution {

    public TweedieDistribution(Model.Parameters params){
        super(params, new LogFunction());
    }

    @Override
    public double deviance(double w, double y, double f) {
        f = link(f); // bring back f to link space
        assert (tweediePower > 1 && tweediePower < 2);
        return 2 * w * (Math.pow(y, 2 - tweediePower) / ((1 - tweediePower) * (2 - tweediePower)) - y * LogExpUtil.exp(f * (1 - tweediePower)) / (1 - tweediePower) + LogExpUtil.exp(f * (2 - tweediePower)) / (2 - tweediePower));
    }

    @Override
    public double negHalfGradient(double y, double f) {
        assert (tweediePower > 1 && tweediePower < 2);
        return y * LogExpUtil.exp(f * (1 - tweediePower)) - LogExpUtil.exp(f * (2 - tweediePower));
    }

    @Override
    public double initFNum(double w, double o, double y) {
        return w * y * LogExpUtil.exp(o * (1 - tweediePower));
    }

    @Override
    public double initFDenom(double w, double o, double y) {
        return w * LogExpUtil.exp(o * (2 - tweediePower));
    }

    @Override
    public double gammaNum(double w, double y, double z, double f) {
        return w * y * LogExpUtil.exp(f * (1 - tweediePower));
    }

    @Override
    public double gammaDenom(double w, double y, double z, double f) {
        return w * LogExpUtil.exp(f * (2 - tweediePower));
    }
}

class HuberDistribution extends Distribution {


    public HuberDistribution(Model.Parameters params){
        super(params);
    }

    @Override
    public double deviance(double w, double y, double f) {
        if (Math.abs(y - f) <= huberDelta) {
            return w * (y - f) * (y - f); // same as wMSE
        } else {
            return 2 * w * (Math.abs(y - f) - huberDelta) * huberDelta; // note quite the same as wMAE
        }
    }

    @Override
    public double negHalfGradient(double y, double f) {
        if (Math.abs(y - f) <= huberDelta) {
            return y - f;
        } else {
            return f >= y ? -huberDelta : huberDelta;
        }
    }
}

class LaplaceDistribution extends Distribution {

    public LaplaceDistribution(DistributionFamily family){
        super(family);
    }

    @Override
    public double deviance(double w, double y, double f) {
        return w * Math.abs(y - f);
    }

    @Override
    public double negHalfGradient(double y, double f) {
        return f > y ? -0.5 : 0.5;
    }
}

class QuantileDistribution extends Distribution {

    public QuantileDistribution(Model.Parameters params){
        super(params);
    }

    @Override
    public double deviance(double w, double y, double f) {
        return y > f ? w * quantileAlpha * (y - f) : w * (1 - quantileAlpha) * (f - y);
    }

    @Override
    public double negHalfGradient(double y, double f) {
        return y > f ? 0.5 * quantileAlpha : 0.5 * (quantileAlpha - 1);
    }
}

class CustomDistribution extends Distribution {
    
    final CustomDistributionWrapper customDistribution;
    
    public CustomDistribution(Model.Parameters params){
        super(params); 
        customDistribution = new CustomDistributionWrapper(CFuncRef.from(params._custom_distribution_func));
    }

    @Override
    public double link(double f) {
        return customDistribution.getFunc().link(f);
    }

    @Override
    public double linkInv(double f) {
        return customDistribution.getFunc().linkInv(f);
    }

    @Override
    public String linkInvString(String f) {
        return customDistribution.getFunc().linkInvString(f);
    }

    @Override
    public double deviance(double w, double y, double f) {
        return customDistribution.getFunc().deviance(w, y, f);
    }

    @Override
    public double negHalfGradient(double y, double f) {
        return customDistribution.getFunc().negHalfGradient(y, f);
    }

    @Override
    public double initFNum(double w, double o, double y) {
        return customDistribution.getFunc().initFNum(w, o, y);
    }

    @Override
    public double initFDenom(double w, double o, double y) {
        return customDistribution.getFunc().initFDenom(w, o, y);
    }

    @Override
    public double gammaNum(double w, double y, double z, double f) {
        return customDistribution.getFunc().gammaNum(w, y, z, f);
    }

    @Override
    public double gammaDenom(double w, double y, double z, double f) {
        return customDistribution.getFunc().gammaDenom(w, y, z, f);
    }
}

class CustomDistributionWrapper extends CFuncObject<CDistributionFunc> {

    CustomDistributionWrapper(CFuncRef ref){
        super(ref);
    }

    @Override
    protected Class<CDistributionFunc> getFuncType() {
        return CDistributionFunc.class;
    }
}

class LogExpUtil {
    static public double MIN_LOG = -19;
    static public double MAX = 1e19;

    /**
     * Sanitized exponential function - helper function.
     *
     * @param x value to be transform
     * @return result of exp function
     */
    public static double exp(double x) {
        return Math.min(MAX, Math.exp(x));
    }

    /**
     * Sanitized log function - helper function
     *
     * @param x value to be transform
     * @return result of log function
     */
    public static double log(double x) {
        x = Math.max(0, x);
        return x == 0 ? MIN_LOG : Math.max(MIN_LOG, Math.log(x));
    }
}
