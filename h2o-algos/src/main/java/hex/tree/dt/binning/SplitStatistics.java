package hex.tree.dt.binning;

import hex.tree.dt.AbstractSplittingRule;

import java.util.Arrays;

/**
 * Potential split including splitting rule and statistics on count of samples and distribution of target variable.
 * Holds how many samples (and 0-samples) are in a right and left splits
 */
public class SplitStatistics {
    public AbstractSplittingRule _splittingRule;

    public int _leftCount;
    public int[] _leftClassDistribution;

    public int _rightCount;
    public int[] _rightClassDistribution;

    public SplitStatistics(int numClasses) {
        _leftCount = 0;
        _leftClassDistribution = new int[numClasses];
        _rightCount = 0;
        _rightClassDistribution = new int[numClasses];
    }

    public SplitStatistics() {
        // call constructor with default value
        this(1);
    }

    public void accumulateLeftStatistics(int leftCount, int[] leftClassDistribution) {
        _leftCount += leftCount;
        for (int i = 0; i < _leftClassDistribution.length; i++) {
            _leftClassDistribution[i] += leftClassDistribution[i];
        }
    }

    public void accumulateRightStatistics(int rightCount, int[] rightClassDistribution) {
        _rightCount += rightCount;
        for (int i = 0; i < _rightClassDistribution.length; i++) {
            _rightClassDistribution[i] += rightClassDistribution[i];
        }
    }

    public void copyLeftValues(SplitStatistics toCopy) {
        _leftCount = toCopy._leftCount;
        _leftClassDistribution = Arrays.copyOf(toCopy._leftClassDistribution, toCopy._leftClassDistribution.length);
    }

    public void copyRightValues(SplitStatistics toCopy) {
        _rightCount = toCopy._rightCount;
        _rightClassDistribution = Arrays.copyOf(toCopy._rightClassDistribution, toCopy._rightClassDistribution.length);
    }

    public SplitStatistics setCriterionValue(double criterionOfSplit) {
        _splittingRule.setCriterionValue(criterionOfSplit);
        return this;
    }

    public SplitStatistics setFeatureIndex(int featureIndex) {
        _splittingRule.setFeatureIndex(featureIndex);
        return this;
    }


    public static double entropyMulticlass(final int[] classCountsDistribution, final int totalCount) {
        return -1 * Arrays.stream(classCountsDistribution)
                .mapToDouble(count -> (count == 0) ? 0 : (count * 1.0 / totalCount) * Math.log((count * 1.0 / totalCount)))
                .sum();
    }

    public Double splitEntropy() {
        int totalCount = _leftCount + _rightCount;
        double res =  entropyMulticlass(_leftClassDistribution, _leftCount) * _leftCount / totalCount
                + entropyMulticlass(_rightClassDistribution, _rightCount) * _rightCount / totalCount;
        return res;
    }
}
