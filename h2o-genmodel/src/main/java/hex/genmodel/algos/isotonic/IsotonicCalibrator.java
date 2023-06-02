package hex.genmodel.algos.isotonic;

import java.io.Serializable;

public class IsotonicCalibrator implements Serializable {

    public final double _min_x;
    public final double _max_x;
    public final double[] _thresholds_x;
    public final double[] _thresholds_y;

    public IsotonicCalibrator(double minX, double maxX, double[] thresholdsX, double[] thresholdsY) {
        _min_x = minX;
        _max_x = maxX;
        _thresholds_x = thresholdsX;
        _thresholds_y = thresholdsY;
    }

    public double calibrateP1(double p1) {
        final double x = IsotonicRegressionUtils.clip(p1, _min_x, _max_x);
        return IsotonicRegressionUtils.score(x, _min_x, _max_x, _thresholds_x, _thresholds_y);
    }

}
