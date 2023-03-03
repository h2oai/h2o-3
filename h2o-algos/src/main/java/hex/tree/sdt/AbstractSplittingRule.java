package hex.tree.sdt;

import water.Iced;


public abstract class AbstractSplittingRule extends Iced<AbstractSplittingRule> {

  // true for left, false for right
  public abstract boolean routeSample(double[] sample);

}
