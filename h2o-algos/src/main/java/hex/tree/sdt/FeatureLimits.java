package hex.tree.sdt;

/**
 * Limits for one feature.
 */
public class FeatureLimits {
    public double _min;
    public double _max;
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

}
