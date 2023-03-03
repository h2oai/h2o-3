package hex.tree.sdt;


public class CategoricalSplittingRule extends AbstractSplittingRule {

  public CategoricalSplittingRule(int featureIndex, boolean[] mask) {
    this._featureIndex = featureIndex;
    // categories for the left split - bitmask
    this._mask = mask;
  }

  private final int _featureIndex;
  private final boolean[] _mask;

  public int getField() {
    return _featureIndex;
  }

  public boolean[] getMask() {
    return _mask;
  }


//  @Override
//  public String toString() {
//    return _featureIndex + " " + _threshold + " " + Arrays.toString(_categories);
//  }

  // true for left, false for right
  public boolean routeSample(double[] sample) {
    int category = (int) sample[_featureIndex];
    return _mask[category];
  }
}
