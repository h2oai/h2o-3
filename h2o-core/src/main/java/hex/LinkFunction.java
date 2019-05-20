package hex;

import hex.LogExpUtil;

import java.io.Serializable;

abstract class LinkFunction implements Serializable {

    /**
     * Return x as e^x string - helper function
     * @param x
     * @return converted x to e^x string
     */
    public static String expString(String x) {
        return "Math.min(1e19, Math.exp(" + x + "))";
    }

    /**
     * Canonical link
     *
     * @param f value in original space, to be transformed to link space
     * @return link(f)
     */
    public abstract double link(double f);

    /**
     * Canonical link inverse
     *
     * @param f value in link space, to be transformed back to original space
     * @return linkInv(f)
     */
    public abstract  double linkInv(double f);

    /**
     * String version of link inverse (for POJO scoring code generation)
     *
     * @param f value to be transformed by link inverse
     * @return String that turns into compilable expression of linkInv(f)
     */
    public abstract String linkInvString(String f);
}

class IdentityFunction extends LinkFunction {

    @Override
    public double link(double f) {
        return f;
    }

    @Override
    public double linkInv(double f) {
        return f;
    }

    @Override
    public String linkInvString(String f) {
        return f;
    }
}

class LogFunction extends LinkFunction {

    @Override
    public double link(double f) {
        return LogExpUtil.log(f);
    }

    @Override
    public double linkInv(double f) {
        return LogExpUtil.exp(f);
    }

    @Override
    public String linkInvString(String f) {
        return expString(f);
    }
}

class LogitFunction extends LinkFunction {

    @Override
    public double link(double f) {
        return LogExpUtil.log(f / (1 - f));
    }

    @Override
    public double linkInv(double f) {
        return 1 / (1 + LogExpUtil.exp(-f));
    }

    @Override
    public String linkInvString(String f) {
        return "1./(1. + " + expString("-("+f+")") + ")";
    }
}
