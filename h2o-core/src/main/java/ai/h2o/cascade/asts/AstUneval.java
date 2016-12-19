package ai.h2o.cascade.asts;

import ai.h2o.cascade.vals.Val;
import ai.h2o.cascade.vals.ValAst;

/**
 * Unevaluated expression.
 */
public class AstUneval extends Ast<AstUneval> {
  private Ast value;


  public AstUneval(Ast v) {
    value = v;
  }

  @Override
  public Val exec() {
    return new ValAst(value);
  }

  @Override
  public String str() {
    return "?" + value.str();
  }
}
