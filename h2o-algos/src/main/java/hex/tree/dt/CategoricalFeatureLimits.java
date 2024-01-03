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

    public CategoricalFeatureLimits(final double[] doubleMask) {
        _mask = new boolean[doubleMask.length];
        for (int i = 0; i < doubleMask.length; i++) {
            if (doubleMask[i] == 1.0) {
                _mask[i] = true;
            }
        }
    }
    
    public CategoricalFeatureLimits(final int cardinality) {
        _mask = new boolean[cardinality];
        // fill with true as it is used for the initial features limits where all categories are present
        Arrays.fill(_mask, true);
    }

    public void setNewMask(final boolean[] mask) {
        _mask = Arrays.copyOf(mask, mask.length);
    }

    public void setNewMaskExcluded(final boolean[] maskToExclude) {
        _mask = Arrays.copyOf(_mask, _mask.length);
        // length of the mask is number of categories in the initial dataset, has to be the same through the whole build
        assert _mask.length == maskToExclude.length;
        for (int i = 0; i < maskToExclude.length; i++) {
            // if the category is defined in the given mask, it should be excluded from the actual mask
            if(maskToExclude[i]) {
                _mask[i] = false;
            }
        }
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
