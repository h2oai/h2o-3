package hex.tree.sdt;

import org.apache.commons.math3.util.Precision;

public class NumericSplittingRule extends AbstractSplittingRule {

  public NumericSplittingRule(int featureIndex, double threshold) {
    this._featureIndex = featureIndex;
    this._threshold = threshold;
  }

  private final int _featureIndex;
  private final double _threshold;

  public int getField() {
    return _featureIndex;
  }

  public double getThreshold() {
    return _threshold;
  }


//  @Override
//  public String toString() {
//    return _featureIndex + " " + _threshold + " " + Arrays.toString(_categories);
//  }

  // true for left, false for right
  public boolean routeSample(double[] sample) {
    return sample[_featureIndex] < _threshold
            || Precision.equals(sample[_featureIndex], _threshold, Precision.EPSILON);
  }
}
