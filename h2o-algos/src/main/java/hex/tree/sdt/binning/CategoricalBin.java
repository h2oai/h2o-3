package hex.tree.sdt.binning;

/**
 * For categorical features values are already binned to categories - each bin corresponds to one value (category)
 */
public class CategoricalBin extends AbstractBin {
    public int _category;

    public CategoricalBin(int category, int count0, int count) {
        _category = category;
        _count0 = count0;
        _count = count;
    }

    public CategoricalBin(int category) {
        _category = category;
    }

    
    public CategoricalBin clone() {
        return new CategoricalBin(_category, _count0, _count);
    }

}
