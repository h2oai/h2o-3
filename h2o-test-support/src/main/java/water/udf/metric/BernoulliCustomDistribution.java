package water.udf.metric;

import org.junit.Ignore;
import water.udf.CDistributionFunc;

@Ignore("Support for tests, but no actual tests here")
public class BernoulliCustomDistribution implements CDistributionFunc {

    public double MIN_LOG = -19;
    public double MAX = 1e19;
    
    public double exp(double x) { return Math.min(MAX, Math.exp(x)); }
    
    public double log(double x) {
        x = Math.max(0, x);
        return x == 0 ? MIN_LOG : Math.max(MIN_LOG, Math.log(x));
    }

    @Override
    public String link() { return "logit";}

    @Override
    public double[] init(double w, double o, double y) {
        return new double[]{w * (y - o), w};
    }

    @Override
    public double gradient(double y, double f) {
        return y - (1 / (1 + exp(-f)));
    }

    @Override
    public double gradient(double y, double f, int l) {
        return gradient(y, f);
    }

    @Override
    public double[] gamma(double w, double y, double z, double f) {
        double ff = y - z;
        return new double[]{w * z, w * ff * (1 - ff)};
    }
}
