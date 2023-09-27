package hex.tree.dt.binning;

import water.util.Pair;

/**
 * Single bin holding limits (min excluded), count of samples and count of class 0.
 */
public class NumericBin extends AbstractBin {
    public double _min;
    public double _max;

    public static final int MIN_INDEX = 3;
    public static final int MAX_INDEX = 4;

    public NumericBin(double min, double max, int count, int count0) {
        _min = min;
        _max = max;

        _count = count;
        _count0 = count0;
    }

    public NumericBin(double min, double max) {
        _min = min;
        _max = max;
        _count = 0;
        _count0 = 0;
    }

    public NumericBin(Pair<Double, Double> binLimits) {
        _min = binLimits._1();
        _max = binLimits._2();
        _count = 0;
        _count0 = 0;
    }
    
    public NumericBin clone() {
        return new NumericBin(_min, _max, _count, _count0);
    }

    public double[] toDoubles() {
        // Place numeric flag -1.0 on the index 0 to mark that the feature is numeric
        return new double[]{-1.0, _count, _count0, _min, _max};
    }

    public void setMin(double min) {
        _min = min;
    }

    public void setMax(double max) {
        _max = max;
    }
}
