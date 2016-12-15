package water.operations;

import org.apache.commons.math3.special.Gamma;

public class Unary {

    public static double DiGamma(double d) {
        return Double.isNaN(d) ? Double.NaN : Gamma.digamma(d);
    }

    public static double gamma(double d) {
        return Gamma.gamma(d);
    }

    public static double Not(double d) {
        return Double.isNaN(d) ? Double.NaN : d == 0 ? 1 : 0;
    }

    public static double round(double x, double digits) {
        // e.g.: floor(2.676*100 + 0.5) / 100 => 2.68
        if (Double.isNaN(x)) return x;
        double sgn = x < 0 ? -1 : 1;
        x = Math.abs(x);
        if ((int) digits != digits) digits = Math.round(digits);
        double power_of_10 = (int) Math.pow(10, (int) digits);
        return sgn * (digits == 0
                // go to the even digit
                ? (x % 1 > 0.5 || (x % 1 == 0.5 && !(Math.floor(x) % 2 == 0)))
                ? Math.ceil(x)
                : Math.floor(x)
                : Math.floor(x * power_of_10 + 0.5) / power_of_10);
    }

    public static double signif(double x, double digits) {
        if (Double.isNaN(x)) return x;
        if (digits < 1) digits = 1; //mimic R's base::signif
        if ((int) digits != digits) digits = Math.round(digits);
        java.math.BigDecimal bd = new java.math.BigDecimal(x);
        bd = bd.round(new java.math.MathContext((int) digits, java.math.RoundingMode.HALF_EVEN));
        return bd.doubleValue();
    }

    public static double trigamma(double d) {
        return Double.isNaN(d) ? Double.NaN : Gamma.trigamma(d);
    }

    public static double trunc(double d) {
        return d >= 0 ? Math.floor(d) : Math.ceil(d);
    }
}
