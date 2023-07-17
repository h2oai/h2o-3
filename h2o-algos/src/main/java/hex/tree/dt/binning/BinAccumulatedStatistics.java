package hex.tree.dt.binning;

import hex.tree.dt.AbstractSplittingRule;

/**
 * Statistics for single bin depending on all the data.
 * Holds how many samples (and 0-samples) are less or equal than the max bin value (left bins + current bin) and
 * how many samples (and 0-samples) are bigger than the max bin value (right bins excluding current bin)
 */
public class BinAccumulatedStatistics {
    public AbstractSplittingRule _splittingRule;

    public int _leftCount0;
    public int _leftCount;

    public int _rightCount0;
    public int _rightCount;

    public BinAccumulatedStatistics() {
        _leftCount0 = 0;
        _leftCount = 0;
        _rightCount0 = 0;
        _rightCount = 0;
    }

    public void accumulateLeftStatistics(int leftCount0, int leftCount) {
        _leftCount0 += leftCount0;
        _leftCount += leftCount;
    }

    public void accumulateRightStatistics(int rightCount0, int rightCount) {
        _rightCount0 += rightCount0;
        _rightCount += rightCount;
    }

    public void copyLeftValues(BinAccumulatedStatistics toCopy) {
        _leftCount0 = toCopy._leftCount0;
        _leftCount = toCopy._leftCount;
    }

    public void copyRightValues(BinAccumulatedStatistics toCopy) {
        _rightCount0 = toCopy._rightCount0;
        _rightCount = toCopy._rightCount;
    }
}
