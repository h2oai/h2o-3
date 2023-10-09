package hex.tree.dt;

import water.Iced;


public abstract class AbstractSplittingRule extends Iced<AbstractSplittingRule> {

  protected int _featureIndex = -1; // valid default value before the actual one is defined
  protected double _criterionValue = -1; // valid default value before the actual one is defined

  protected AbstractSplittingRule() {
  }

  public double getCriterionValue() {
    return _criterionValue;
  }

  public int getFeatureIndex() {
    return _featureIndex;
  }
  
  // true for left, false for right
  public abstract boolean routeSample(double[] sample);
  
  public abstract String toString();

  public void setCriterionValue(double criterionOfSplit) {
      _criterionValue = criterionOfSplit;
    }

  public void setFeatureIndex(int featureIndex) {
    _featureIndex = featureIndex;
  }
}
