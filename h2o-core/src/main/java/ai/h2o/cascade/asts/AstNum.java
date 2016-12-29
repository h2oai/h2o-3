package ai.h2o.cascade.asts;


import ai.h2o.cascade.core.Scope;
import ai.h2o.cascade.vals.ValNum;

/**
 * A number literal.
 */
public class AstNum extends AstNode<AstNum> {
  private double value;

  public AstNum(double d) {
    value = d;
  }

  @Override
  public ValNum exec(Scope scope) {
    return new ValNum(value);
  }

  @Override
  public String str() {
    return Double.toString(value);
  }

}
