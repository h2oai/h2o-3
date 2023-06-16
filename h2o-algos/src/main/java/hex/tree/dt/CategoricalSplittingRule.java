package hex.tree.dt;


import java.util.Arrays;

public class CategoricalSplittingRule extends AbstractSplittingRule {

  public CategoricalSplittingRule(int featureIndex, boolean[] mask, double criterionValue) {
    this._featureIndex = featureIndex;
    // categories for the left split - bitmask
    this._mask = mask;
    this._criterionValue = criterionValue;
  }

  private final int _featureIndex;
  private final boolean[] _mask;

  public int getField() {
    return _featureIndex;
  }

  public boolean[] getMask() {
    return _mask;
  }


  @Override
  public String toString() {
    return "x" + _featureIndex + " in [" + Arrays.toString(_mask) + "]";
  }

  // true for left, false for right
  public boolean routeSample(double[] sample) {
    int category = (int) sample[_featureIndex];
    return _mask[category];
  }
}
