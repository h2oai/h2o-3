package hex.tree.dt.binning;


/**
 * Single bin holding limits (min excluded), count of samples and count of class 0.
 */
public abstract class AbstractBin {
    public int[] _classesDistribution;
    public int _count;
    
    public int getClassCount(int i) {
        return _classesDistribution[i];
    }

    public abstract AbstractBin clone();
    public abstract double[] toDoubles();
}
