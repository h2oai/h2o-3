package hex.tree.sdt.binning;

import water.util.Pair;

/**
 * Single bin holding limits (min excluded), count of samples and count of class 0.
 */
public class NumericBin extends AbstractBin {
    public double _min;
    public double _max;

    public NumericBin(double min, double max, int count0, int count) {
        _min = min;
        _max = max;
        _count0 = count0;
        _count = count;
    }

    public NumericBin(double min, double max) {
        _min = min;
        _max = max;
    }

    public NumericBin(Pair<Double, Double> binLimits) {
        _min = binLimits._1();
        _max = binLimits._2();
    }
    
    public NumericBin clone() {
        return new NumericBin(_min, _max, _count0, _count);
    }

//    @Override
//    public AbstractBin clone() {
//        return null;
//    }
}
