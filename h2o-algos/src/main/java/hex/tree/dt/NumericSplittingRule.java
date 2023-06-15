package hex.tree.dt;

import org.apache.commons.math3.util.Precision;

public class NumericSplittingRule extends AbstractSplittingRule {

  public NumericSplittingRule(int featureIndex, double threshold, double criterionValue) {
    this._featureIndex = featureIndex;
    this._threshold = threshold;
    this._criterionValue = criterionValue;
  }

  private final int _featureIndex;
  private final double _threshold;

  public int getField() {
    return _featureIndex;
  }

  public double getThreshold() {
    return _threshold;
  }


  @Override
  public String toString() {
    return "(x" + _featureIndex + " <= " + _threshold + ")";
  }

  // true for left, false for right
  public boolean routeSample(double[] sample) {
    return sample[_featureIndex] < _threshold
            || Precision.equals(sample[_featureIndex], _threshold, Precision.EPSILON);
  }
}
