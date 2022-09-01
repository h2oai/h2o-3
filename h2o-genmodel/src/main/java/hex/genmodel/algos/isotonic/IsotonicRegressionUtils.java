package hex.genmodel.algos.isotonic;

import java.util.Arrays;

public class IsotonicRegressionUtils {

    public static double score(double x, double minX, double maxX, 
                               double[] thresholdsX, double[] thresholdsY) {
        if (Double.isNaN(x) || x < minX || x > maxX) {
            return Double.NaN;
        }
        final int pos = Arrays.binarySearch(thresholdsX, x);
        final double y;
        if (pos >= 0) {
            y = thresholdsY[pos];
        } else {
            final int lo = -pos - 2;
            final int hi = lo + 1;
            assert lo >= 0;
            assert hi < thresholdsX.length;
            assert x > thresholdsX[lo];
            assert x < thresholdsX[hi];
            y = interpolate(x, thresholdsX[lo], thresholdsX[hi],
                    thresholdsY[lo], thresholdsY[hi]);
        }
        return y;
    }

    public static double clip(double x, double min, double max) {
        final double clipped;
        if (Double.isNaN(x))
            clipped = Double.NaN;
        else if (x < min)
            clipped = min;
        else
            clipped = Math.min(x, max);
        return clipped;
    }

    static double interpolate(double x, double xLo, double xHi, double yLo, double yHi) {
        final double slope = (yHi - yLo) / (xHi - xLo);
        return yLo + slope * (x - xLo);
    }

}
