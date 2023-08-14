package hex.tree.dt.binning;


/**
 * Single bin holding limits (min excluded), count of samples and count of class 0.
 */
public abstract class AbstractBin {
    public int _count0;
    public int _count;
    
    public int getCount0() {
        return _count0;
    }

    public abstract AbstractBin clone();
    public abstract double[] toDoubles();
}
