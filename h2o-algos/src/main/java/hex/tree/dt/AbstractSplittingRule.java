package hex.tree.dt;

import water.Iced;


public abstract class AbstractSplittingRule extends Iced<AbstractSplittingRule> {

  protected double _criterionValue;

  public double getCriterionValue() {
    return _criterionValue;
  }
  
  // true for left, false for right
  public abstract boolean routeSample(double[] sample);
  
  public abstract String toString();

}
