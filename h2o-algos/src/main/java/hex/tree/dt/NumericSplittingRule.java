package hex.tree.dt;

import org.apache.commons.math3.util.Precision;

public class NumericSplittingRule extends AbstractSplittingRule {

  public NumericSplittingRule(int featureIndex, double threshold, double criterionValue) {
    _featureIndex = featureIndex;
    _threshold = threshold;
    _criterionValue = criterionValue;
  }

  public NumericSplittingRule(double threshold) {
    _threshold = threshold;
  }

  private final double _threshold;

  public double getThreshold() {
    return _threshold;
  }


  @Override
  public String toString() {
    return "x" + _featureIndex + " <= " + _threshold + "";
  }

  // true for left, false for right
  public boolean routeSample(double[] sample) {
    return sample[_featureIndex] < _threshold
            || Precision.equals(sample[_featureIndex], _threshold, Precision.EPSILON);
  }
}
