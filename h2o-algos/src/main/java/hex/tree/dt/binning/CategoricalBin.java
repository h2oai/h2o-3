package hex.tree.dt.binning;

import org.apache.commons.lang.ArrayUtils;

import java.util.Arrays;

/**
 * For categorical features values are already binned to categories - each bin corresponds to one value (category)
 */
public class CategoricalBin extends AbstractBin {
    public int _category;

    public CategoricalBin(int category, int[] classesDistribution, int count) {
        _category = category;
        _classesDistribution = classesDistribution;
        _count = count;
    }

    public CategoricalBin(int category, int nclass) {
        _category = category;
        _classesDistribution = new int[nclass];
        _count = 0;
    }

    public int getCategory() {
        return _category;
    }
    
    public CategoricalBin clone() {
        return new CategoricalBin(_category, _classesDistribution, _count);
    }
    
    public double[] toDoubles() {
        // category|count|class0|class1|...
        return ArrayUtils.addAll(new double[]{_category, _count}, 
                Arrays.stream(_classesDistribution).asDoubleStream().toArray());
    }

}
