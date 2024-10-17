package hex.tree.dt.binning;

import org.apache.commons.lang.ArrayUtils;
import water.util.Pair;

import java.util.Arrays;

/**
 * Single bin holding limits (min excluded), count of samples and count of class 0.
 */
public class NumericBin extends AbstractBin {
    public double _min;
    public double _max;

    public static final int MIN_INDEX = 1;
    public static final int MAX_INDEX = 2;

    public NumericBin(double min, double max, int[] classesDistribution, int count) {
        _min = min;
        _max = max;
        _classesDistribution = classesDistribution;
        _count = count;
    }

    public NumericBin(double min, double max, int nclass) {
        _min = min;
        _max = max;
        _classesDistribution = new int[nclass];
        _count = 0;
    }

    public NumericBin(Pair<Double, Double> binLimits, int nclass) {
        _min = binLimits._1();
        _max = binLimits._2();
        _classesDistribution = new int[nclass];
        _count = 0;
    }
    
    public NumericBin clone() {
        return new NumericBin(_min, _max, _classesDistribution, _count);
    }

    public double[] toDoubles() {
        // Place numeric flag -1.0 on the index 0 to mark that the feature is numeric
        // -1|min|max|count|class0|class1|...
        return ArrayUtils.addAll(new double[]{-1.0, _min, _max, _count}, 
                Arrays.stream(_classesDistribution).asDoubleStream().toArray());
    }

    public void setMin(double min) {
        _min = min;
    }

    public void setMax(double max) {
        _max = max;
    }
}
