package hex;

import water.Iced;

import hex.genmodel.utils.LinkFunctionType;
import org.apache.commons.math3.distribution.NormalDistribution;

/**
 * Link function class to calculate link, link inverse and string link inverse functions.
 * 
 */
public abstract class LinkFunction extends Iced<LinkFunction> {
    
    public LinkFunctionType linkFunctionType;

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
     * Be careful if you are changing code here - you have to change it in DeeplearningMojoModel and GbmMojoModel too
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

    public String linkInvStringFloat(String f) {
        return linkInvString(f);
    }
}

class IdentityFunction extends LinkFunction {
    
    public IdentityFunction(){
        linkFunctionType = LinkFunctionType.identity;
    }

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

class InverseFunction extends LinkFunction {

    public InverseFunction(){
        linkFunctionType = LinkFunctionType.inverse;
    }

    @Override
    public double link(double f) {
        double xx = f < 0 ? Math.min(-1e-5, f) : Math.max(-1e-5, f);
        return 1.0/xx;
    }

    @Override
    public double linkInv(double f) {
        return link(f);
    }

    @Override
    public String linkInvString(String f) {
        if(Integer.parseInt(f) < 0){
            return "1.0/Math.min(-1e-5, "+f+")";
        }
        return "1.0/Math.max(1e-5, "+f+")";
    }
}

class LogFunction extends LinkFunction {

    public LogFunction(){
        linkFunctionType = LinkFunctionType.log;
    }

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

    public LogitFunction(){
        linkFunctionType = LinkFunctionType.logit;
    }

    @Override
    public double link(double f) { return LogExpUtil.log(f / (1 - f)); }

    @Override
    public double linkInv(double f) {
        return 1 / (1 + LogExpUtil.exp(-f));
    }

    @Override
    public String linkInvString(String f) {
        return "1./(1. + " + expString("-(" + f + ")") + ")";
    }

    @Override
    public String linkInvStringFloat(String f) {
        return "1f/(1f + " + "(float)" + expString("-("+f+")") + ")";
    }
}

class OlogitFunction extends LinkFunction {

    public OlogitFunction(){
        linkFunctionType = LinkFunctionType.ologit;
    }

    @Override
    public double link(double f) { return LogExpUtil.log(f / (1 - f)); }

    @Override
    public double linkInv(double f) {
        return 1 / (1 + LogExpUtil.exp(-f));
    }

    @Override
    public String linkInvString(String f) {
        return "1./(1. + " + expString("-("+f+")") + ")";
    }
}

class OloglogFunction extends LinkFunction {

    public OloglogFunction(){
        linkFunctionType = LinkFunctionType.ologlog;
    }

    @Override
    public double link(double f) { return LogExpUtil.log(-1 * LogExpUtil.log(1-f) ); }

    @Override
    public double linkInv(double f) { return 1 - LogExpUtil.exp(-1 * LogExpUtil.exp(f)); }

    @Override
    public String linkInvString(String f) { return expString("1. * "+expString("(-1. * "+expString("("+f+")")+")")); }
}

class OprobitFunction extends LinkFunction {

    org.apache.commons.math3.distribution.NormalDistribution normalDistribution;

    public OprobitFunction(){
        linkFunctionType = LinkFunctionType.oprobit;
        normalDistribution = new NormalDistribution(0, 1);
    }

    @Override
    public double link(double f) { return normalDistribution.inverseCumulativeProbability(f); }

    @Override
    public double linkInv(double f) { return normalDistribution.cumulativeProbability(f); }

    @Override
    public String linkInvString(String f) { 
        return "new org.apache.commons.math3.distribution.NormalDistribution(0, 1).cumulativeProbability("+f+");"; 
    }
}

