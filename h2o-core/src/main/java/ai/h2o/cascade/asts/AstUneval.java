package ai.h2o.cascade.asts;

import ai.h2o.cascade.core.Scope;
import ai.h2o.cascade.vals.Val;
import ai.h2o.cascade.vals.ValAst;

/**
 * Unevaluated expression.
 */
public class AstUneval extends AstNode<AstUneval> {
  private AstNode value;


  public AstUneval(AstNode v) {
    value = v;
  }

  @Override
  public Val exec(Scope scope) {
    return new ValAst(value);
  }

  @Override
  public String str() {
    return "?" + value.str();
  }
}
