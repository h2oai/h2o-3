package hex.tree.sdt.binning;

import water.util.Pair;

public class Bin {
    double _min;
    double _max;
    int _count0;
    int _count;

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
}
