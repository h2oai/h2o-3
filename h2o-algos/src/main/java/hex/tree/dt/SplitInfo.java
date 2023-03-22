package hex.tree.dt;

/**
 * Split info, holds feature index and threshold.
 */
public class SplitInfo {
    public int _splitFeatureIndex;
    public double _threshold;

    public SplitInfo(int splitFeatureIndex, double threshold) {
        _splitFeatureIndex = splitFeatureIndex;
        _threshold = threshold;
    }
}
