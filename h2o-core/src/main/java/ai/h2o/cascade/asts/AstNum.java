package ai.h2o.cascade.asts;

import water.rapids.vals.ValNum;


/**
 * A number literal.
 */
public class AstNum extends Ast<AstNum> {
  private final ValNum value;

  public AstNum(double d) {
    value = new ValNum(d);
  }

  @Override
  public String str() {
    return value.toString();
  }

}
