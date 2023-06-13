package hex.tree.dt.binning;

import water.util.Pair;

/**
 * Single bin holding limits (min excluded), count of samples and count of class 0.
 */
public class Bin {
    public double _min;
    public double _max;
    public int _count0;
    public int _count;

    public Bin(double min, double max, int count0, int count) {
        _min = min;
        _max = max;
        _count0 = count0;
        _count = count;
    }

    public Bin(double min, double max) {
        _min = min;
        _max = max;
    }

    public Bin(Pair<Double, Double> binLimits) {
        _min = binLimits._1();
        _max = binLimits._2();
    }
    
    public static Bin clone(Bin toCLone) {
        return new Bin(toCLone._min, toCLone._max, toCLone._count0, toCLone._count);
    }
}
