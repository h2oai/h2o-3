package ai.h2o.cascade.asts;


import ai.h2o.cascade.vals.ValStr;

/**
 * A String literal.
 */
public class AstStr extends Ast<AstStr> {
  private String value;

  public AstStr(String str) {
    value = str;
  }

  @Override
  public ValStr exec() {
    return new ValStr(value);
  }

  @Override
  public String str() {
    return value;
  }

}
