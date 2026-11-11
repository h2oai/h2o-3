package hex.tree.dt;


import java.util.Arrays;

public class CategoricalSplittingRule extends AbstractSplittingRule {

  public CategoricalSplittingRule(int featureIndex, boolean[] mask, double criterionValue) {
    _featureIndex = featureIndex;
    _mask = mask;
    _criterionValue = criterionValue;
  }

  public CategoricalSplittingRule(boolean[] mask) {
    _mask = mask;
  }
  
  // categories for the left split - bitmask
  private final boolean[] _mask;

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
    assert category < _mask.length; // new values in the train set are not supported yet - will be treated as missing values
    return _mask[category];
  }
}
