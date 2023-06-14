package hex.tree.dt;

/**
 * Limits for one feature.
 */
public class FeatureLimits {
    public double _min; // min value is not included in the range (excluded)
    public double _max; // max value is included in the range

    private double EPSILON = 1e-3; // epsilon for approximate comparing of limits, used in tests only
    public FeatureLimits(final double min, final double max) {
        _min = min;
        _max = max;
    }

    public void setNewMax(final double newMax) {
        _max = newMax;
    }

    public void setNewMin(final double newMin) {
        _min = newMin;
    }

    public static FeatureLimits clone(final FeatureLimits toClone) {
        return new FeatureLimits(toClone._min, toClone._max);
    }

    public boolean equals(FeatureLimits other) {
        return Math.abs(_min - other._min) < EPSILON && Math.abs(_max - other._max) < EPSILON;
    }
}
