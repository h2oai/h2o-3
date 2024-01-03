package hex.tree.dt;


import static hex.tree.dt.DT.EPSILON;

/**
 * Limits for one feature.
 */
public class NumericFeatureLimits extends AbstractFeatureLimits {
    public double _min;
    public double _max;

    // indices for the serialized array
    public static final int NUMERICAL_FLAG = 0;
    public static final int LIMIT_MIN = 1;
    public static final int LIMIT_MAX = 2;
    
    // min of the first bin is moved with precision EPSILON, one additional decimal point of the difference is allowed 
    private static final double EQUALS_PRECISION = EPSILON * 10; 
    
    public NumericFeatureLimits(final double min, final double max) {
        _min = min;
        _max = max;
    }

    public void setNewMax(final double newMax) {
        _max = newMax;
    }

    public void setNewMin(final double newMin) {
        _min = newMin;
    }

    public NumericFeatureLimits clone() {
        return new NumericFeatureLimits(_min, _max);
    }

    @Override
    public double[] toDoubles() {
        // -1.0 at index 0 identifies numeric feature limits
        return new double[]{-1.0, _min, _max};
    }

    @Override
    public boolean equals(AbstractFeatureLimits other) {
        return Math.abs(_min - ((NumericFeatureLimits) other)._min) < EQUALS_PRECISION
                && Math.abs(_max - ((NumericFeatureLimits) other)._max) < EQUALS_PRECISION;
    }
}
