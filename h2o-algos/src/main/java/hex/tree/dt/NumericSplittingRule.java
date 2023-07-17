package hex.tree.dt;

import org.apache.commons.math3.util.Precision;

public class NumericSplittingRule extends AbstractSplittingRule {

  public NumericSplittingRule(int featureIndex, double threshold, double criterionValue) {
    this._featureIndex = featureIndex;
    this._threshold = threshold;
    this._criterionValue = criterionValue;
  }

  public NumericSplittingRule(double threshold) {
    this._featureIndex = -1; // tmp value, non initialized yet
    this._threshold = threshold;
    this._criterionValue = -1; // tmp value, non initialized yet
  }

  private final int _featureIndex;
  private final double _threshold;

  public int getFeatureIndex() {
    return _featureIndex;
  }

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
