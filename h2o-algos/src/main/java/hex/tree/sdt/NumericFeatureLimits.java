package hex.tree.sdt;


/**
 * Limits for one feature.
 */
public class NumericFeatureLimits extends AbstractFeatureLimits {
    public double _min;
    public double _max;
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

}
