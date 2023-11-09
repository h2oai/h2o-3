package hex.tree.dt.binning;

/**
 * For categorical features values are already binned to categories - each bin corresponds to one value (category)
 */
public class CategoricalBin extends AbstractBin {
    public int _category;

    public CategoricalBin(int category, int count, int count0) {
        _category = category;
        _count = count;
        _count0 = count0;
    }

    public CategoricalBin(int category) {
        _category = category;
        _count = 0;
        _count0 = 0;
    }

    public int getCategory() {
        return _category;
    }
    
    public CategoricalBin clone() {
        return new CategoricalBin(_category, _count, _count0);
    }
    
    public double[] toDoubles() {
        return new double[]{_category, _count, _count0};
    }

}
