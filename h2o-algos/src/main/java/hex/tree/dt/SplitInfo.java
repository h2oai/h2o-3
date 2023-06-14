package hex.tree.dt;

/**
 * Split info, holds feature index and threshold.
 */
public class SplitInfo {
    public int _splitFeatureIndex;
    public double _threshold;
    public double _criterionValue;

    public SplitInfo(int splitFeatureIndex, double threshold, double criterionValue) {
        _splitFeatureIndex = splitFeatureIndex;
        _threshold = threshold;
        _criterionValue = criterionValue;
    }
}
