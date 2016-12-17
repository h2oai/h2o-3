package ai.h2o.cascade.asts;


import ai.h2o.cascade.vals.ValStr;

/**
 * A String literal.
 */
public class AstStr extends Ast<AstStr> {
  private final ValStr value;

  public AstStr(String str) {
    value = new ValStr(str);
  }

  @Override
  public ValStr exec() {
    return value;
  }

  @Override
  public String str() {
    return value.toString();
  }

}
