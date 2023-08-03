package hex;

import hex.genmodel.utils.DistributionFamily;
import water.H2O;
import water.udf.CDistributionFunc;
import water.udf.CFuncObject;
import water.udf.CFuncRef;

/**
 * Factory to get distribution based on Model.Parameters or DistributionFamily.
 * 
 */
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
                return CustomDistribution.getCustomDistribution(params);
            default:
                throw H2O.unimpl("Try to get "+family+" which is not supported.");
        }
    }

    /**
     * Util class to calculate log and exp function for distribution and link function identically 
     */
    final public static class LogExpUtil {
        final static public double MIN_LOG = -19;
        final static public double MAX = 1e19;

        /**
         * Sanitized exponential function - helper function.
         *
         * @param x value to be transform
         * @return result of exp function
         */
        public static double exp(double x) { return Math.min(MAX, Math.exp(x)); }

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

    public BernoulliDistribution(DistributionFamily family){ super(family, new LogitFunction()); }

    @Override
    public double deviance(double w, double y, double f) { return -2 * w * (y * DistributionFactory.LogExpUtil.log(f) + (1 - y) * DistributionFactory.LogExpUtil.log(1 - f)); }

    @Override
    public double negHalfGradient(double y, double f) { return y - linkInv(f); }

    @Override
    public double initFNum(double w, double o, double y) { return w * (y - o); }

    @Override
    public double initFDenom(double w, double o, double y) { return w; }

    @Override
    public double gammaNum(double w, double y, double z, double f) { return w * z; }
    
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
            return -2 * w * y * DistributionFactory.LogExpUtil.log(f);
        else if (f < 0)
            return -2 * w * (1 - y) * DistributionFactory.LogExpUtil.log(1 - f);
        else
            return -2 * w * (y * DistributionFactory.LogExpUtil.log(f) + (1 - y) * DistributionFactory.LogExpUtil.log(1 - f));
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

    public MultinomialDistribution(DistributionFamily family){ super(family, new LogFunction()); }

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

    @Override
    public double negHalfGradient(double y, double f, int l) {
        return ((int) y == l ? 1f : 0f) - f;
    }
}

class PoissonDistribution extends Distribution {

    public PoissonDistribution(DistributionFamily family){
        super(family, new LogFunction());
    }

    @Override
    public double deviance(double w, double y, double f) {
        return 2 * w * (y * DistributionFactory.LogExpUtil.log(y / f) - y + f);
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
    public double gammaDenom(double w, double y, double z, double f) { return w * (y - z); } // y - z == LogExpUtil.exp(f) 
}

class GammaDistribution extends Distribution {

    public GammaDistribution(DistributionFamily family){
        super(family, new LogFunction());
    }

    @Override
    public double deviance(double w, double y, double f) {
        return 2 * w * (DistributionFactory.LogExpUtil.log(f / y) + ((y == 0 && f == 0) ? 1 : y / f) - 1);
    }

    @Override
    public double negHalfGradient(double y, double f) {
        return y * DistributionFactory.LogExpUtil.exp(-f) - 1;
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
    public double gammaNum(double w, double y, double z, double f) { return w * (z + 1); } // z + 1 == y * LogExpUtil.exp(-f) 

    @Override
    public double gammaDenom(double w, double y, double z, double f) { return w; }
}

class TweedieDistribution extends Distribution {

    public TweedieDistribution(Model.Parameters params){
        super(params, new LogFunction());
    }

    @Override
    public double deviance(double w, double y, double f) {
        f = link(f); // bring back f to link space
        assert (_tweediePower > 1 && _tweediePower < 2);
        return 2 * w * (Math.pow(y, 2 - _tweediePower) / ((1 - _tweediePower) * (2 - _tweediePower)) -
                y * DistributionFactory.LogExpUtil.exp(f * (1 - _tweediePower)) / (1 - _tweediePower) + 
                DistributionFactory.LogExpUtil.exp(f * (2 - _tweediePower)) / (2 - _tweediePower));
    }

    @Override
    public double negHalfGradient(double y, double f) {
        assert (_tweediePower > 1 && _tweediePower < 2);
        return y * DistributionFactory.LogExpUtil.exp(f * (1 - _tweediePower)) - DistributionFactory.LogExpUtil.exp(f * (2 - _tweediePower));
    }

    @Override
    public double initFNum(double w, double o, double y) {
        return w * y * DistributionFactory.LogExpUtil.exp(o * (1 - _tweediePower));
    }

    @Override
    public double initFDenom(double w, double o, double y) {
        return w * DistributionFactory.LogExpUtil.exp(o * (2 - _tweediePower));
    }

    @Override
    public double gammaNum(double w, double y, double z, double f) { return w * y * DistributionFactory.LogExpUtil.exp(f * (1 - _tweediePower)); }

    @Override
    public double gammaDenom(double w, double y, double z, double f) { return w * DistributionFactory.LogExpUtil.exp(f * (2 - _tweediePower)); }
}

class HuberDistribution extends Distribution {


    public HuberDistribution(Model.Parameters params){
        super(params);
    }

    @Override
    public double deviance(double w, double y, double f) {
        if (Math.abs(y - f) <= _huberDelta) {
            return w * (y - f) * (y - f); // same as wMSE
        } else {
            return w * (2 * Math.abs(y - f) - _huberDelta) * _huberDelta; // w * (2 * MAE - delta) * delta
        }
    }

    @Override
    public double negHalfGradient(double y, double f) {
        if (Math.abs(y - f) <= _huberDelta) {
            return y - f;
        } else {
            return f >= y ? -_huberDelta : _huberDelta;
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
    public double deviance(double w, double y, double f) { return y > f ? w * _quantileAlpha * (y - f) : w * (1 - _quantileAlpha) * (f - y); }

    @Override
    public double negHalfGradient(double y, double f) { return y > f ? 0.5 * _quantileAlpha : 0.5 * (_quantileAlpha - 1); }
}

/**
 * Custom distribution class to customized loss and prediction calculation.
 * Currently supported only for GBM algorithm.
 */
class CustomDistribution extends Distribution {
    
    private CustomDistributionWrapper _wrapper;
    private static CustomDistribution _instance;
    private String _distributionDef;
    
    private CustomDistribution(Model.Parameters params){
        super(params);
        _distributionDef = params._custom_distribution_func;
        _wrapper = new CustomDistributionWrapper(CFuncRef.from(params._custom_distribution_func));
        assert _wrapper != null;
        assert _wrapper.getFunc() != null;
        super._linkFunction = LinkFunctionFactory.getLinkFunction(_wrapper.getFunc().link());
    }
    
    public static CustomDistribution getCustomDistribution(Model.Parameters params){
        if(_instance == null || !params._custom_distribution_func.equals(_instance._distributionDef)){
            _instance = new CustomDistribution(params);
        }
        return _instance;
    }

    @Override
    public double deviance(double w, double y, double f) { throw H2O.unimpl("Deviance is not supported in Custom Distribution."); }

    @Override
    public double negHalfGradient(double y, double f) { return _wrapper.getFunc().gradient(y, f); }

    @Override
    public double negHalfGradient(double y, double f, int l) { return _wrapper.getFunc().gradient(y, f, l); }

    @Override
    public double initFNum(double w, double o, double y) {
        double[] init = _wrapper.getFunc().init(w, o, y);
        assert init.length == 2;
        return init[0];
    }

    @Override
    public double initFDenom(double w, double o, double y) {
        double[] init = _wrapper.getFunc().init(w, o, y);
        assert init.length == 2;
        return init[1];
    }

    @Override
    public double gammaNum(double w, double y, double z, double f) {
        double[] gamma = _wrapper.getFunc().gamma(w, y, z, f);
        assert gamma.length == 2;
        return gamma[0];
    }

    @Override
    public double gammaDenom(double w, double y, double z, double f) {
        double[] gamma = _wrapper.getFunc().gamma(w, y, z, f);
        assert gamma.length == 2;
        return gamma[1];
    }

    @Override
    public void reset() { _wrapper.setupLocal(); }
}

/**
 * Custom distribution wrapper to get user custom functions to H2O Java code.
 */
class CustomDistributionWrapper extends CFuncObject<CDistributionFunc> {

    CustomDistributionWrapper(CFuncRef ref){
        super(ref);
    }

    @Override
    protected Class<CDistributionFunc> getFuncType() {
        return CDistributionFunc.class;
    }

    @Override
    protected void setupLocal() { super.setupLocal(); }
}
