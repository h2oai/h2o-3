package ai.h2o.cascade.asts;


import ai.h2o.cascade.CascadeScope;
import ai.h2o.cascade.vals.ValNum;

/**
 * A number literal.
 */
public class AstNum extends Ast<AstNum> {
  private double value;

  public AstNum(double d) {
    value = d;
  }

  @Override
  public ValNum exec(CascadeScope scope) {
    return new ValNum(value);
  }

  @Override
  public String str() {
    return Double.toString(value);
  }

}
