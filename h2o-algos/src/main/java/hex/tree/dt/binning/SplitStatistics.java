package hex.tree.dt.binning;

import hex.tree.dt.AbstractSplittingRule;
import org.apache.commons.math3.util.Precision;

/**
 * Potential split including splitting rule and statistics on count of samples and distribution of target variable.
 * Holds how many samples (and 0-samples) are in a right and left splits
 */
public class SplitStatistics {
    public AbstractSplittingRule _splittingRule;

    public int _leftCount;
    public int _leftCount0;

    public int _rightCount;
    public int _rightCount0;

    public SplitStatistics() {
        _leftCount = 0;
        _leftCount0 = 0;
        _rightCount = 0;
        _rightCount0 = 0;
    }

    public void accumulateLeftStatistics(int leftCount, int leftCount0) {
        _leftCount += leftCount;
        _leftCount0 += leftCount0;
    }

    public void accumulateRightStatistics(int rightCount, int rightCount0) {
        _rightCount += rightCount;
        _rightCount0 += rightCount0;
    }

    public void copyLeftValues(SplitStatistics toCopy) {
        _leftCount = toCopy._leftCount;
        _leftCount0 = toCopy._leftCount0;
    }

    public void copyRightValues(SplitStatistics toCopy) {
        _rightCount = toCopy._rightCount;
        _rightCount0 = toCopy._rightCount0;
    }

    public SplitStatistics setCriterionValue(double criterionOfSplit) {
        _splittingRule.setCriterionValue(criterionOfSplit);
        return this;
    }

    public SplitStatistics setFeatureIndex(int featureIndex) {
        _splittingRule.setFeatureIndex(featureIndex);
        return this;
    }

    public static double entropyBinarySplit(final double oneClassFrequency) {
        return -1 * ((oneClassFrequency < Precision.EPSILON ? 0 : (oneClassFrequency * Math.log(oneClassFrequency)))
                + ((1 - oneClassFrequency) < Precision.EPSILON ? 0 : ((1 - oneClassFrequency) * Math.log(1 - oneClassFrequency))));
    }
    
    public Double binaryEntropy() {
        double a1 = (entropyBinarySplit(_leftCount0 * 1.0 / _leftCount)
                * _leftCount / (_leftCount + _rightCount));
        double a2 = (entropyBinarySplit(_rightCount0 * 1.0 / _rightCount)
                * _rightCount / (_leftCount + _rightCount));
        return a1 + a2;
    }
}
