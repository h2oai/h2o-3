package water.rapids.ast.params;

import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstParameter;
import water.rapids.vals.ValNull;
import water.rapids.vals.ValNum;

/**
 * Class for constants
 */
public class AstConst extends AstParameter {
  private Val value;
  private String name;

  public static final AstConst NULL = new AstConst("null", new ValNull());
  public static final AstConst FALSE = new AstConst("False", new ValNum(0));
  public static final AstConst TRUE = new AstConst("True", new ValNum(1));
  public static final AstConst NAN = new AstConst("NaN", new ValNum(Double.NaN));
  public static final AstConst PI = new AstConst("Pi", new ValNum(Math.PI));
  public static final AstConst E = new AstConst("E", new ValNum(Math.E));


  public AstConst() {}  // For Serializable

  public AstConst(String name, Val value) {
    this.name = name;
    this.value = value;
  }

  @Override
  public String str() {
    return name;
  }

  @Override
  public Val exec(Env env) {
    return value;
  }

}
