package hex.tree.sdt;

import java.util.Arrays;

/**
 * Limits for one feature.
 */
public class CategoricalFeatureLimits extends AbstractFeatureLimits {
    public boolean[] _mask;
    public CategoricalFeatureLimits(final boolean[] mask) {
        _mask = Arrays.copyOf(mask, mask.length);
    }

    public void setNewMask(final boolean[] newMask) {
        _mask = Arrays.copyOf(newMask, newMask.length);
    }

    public CategoricalFeatureLimits clone() {
        return new CategoricalFeatureLimits(Arrays.copyOf(_mask, _mask.length));
    }

}
