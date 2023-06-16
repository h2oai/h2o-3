package hex.tree.dt;


import static hex.tree.dt.DT.EPSILON;

/**
 * Limits for one feature.
 */
public class NumericFeatureLimits extends AbstractFeatureLimits {
    public double _min;
    public double _max;
    
    // min of the first bin is moved with precision EPSILON, one additional decimal point of the difference is allowed 
    private double EQUALS_PRECISION = EPSILON * 10; 
    
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
        return new double[]{_min, _max};
    }

    @Override
    public boolean equals(AbstractFeatureLimits other) {
        return Math.abs(_min - ((NumericFeatureLimits) other)._min) < EQUALS_PRECISION
                && Math.abs(_max - ((NumericFeatureLimits) other)._max) < EQUALS_PRECISION;
    }
}
