package hex.tree.dt;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Limits for one feature.
 */
public class CategoricalFeatureLimits extends AbstractFeatureLimits {
    public boolean[] _mask;
    public CategoricalFeatureLimits(final boolean[] mask) {
        _mask = Arrays.copyOf(mask, mask.length);
    }

    public void setNewSetOfCategories(final boolean[] mask) {
        _mask = Arrays.copyOf(mask, mask.length);
    }

    public CategoricalFeatureLimits clone() {
        return new CategoricalFeatureLimits(Arrays.copyOf(_mask, _mask.length));
    }

    @Override
    public double[] toDoubles() {
        return IntStream.range(0, _mask.length).mapToDouble(idx -> _mask[idx] ? 1.0 : 0.0).toArray();
    }

    @Override
    public boolean equals(AbstractFeatureLimits other) {
        return Arrays.equals(_mask, ((CategoricalFeatureLimits) other)._mask);
    }
}
