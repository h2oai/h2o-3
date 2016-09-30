package water.rapids.ast.params;

import water.rapids.Env;
import water.rapids.Rapids;
import water.rapids.ast.AstParameter;
import water.rapids.vals.ValNum;

/**
 * Class for constants
 */
public class AstConst extends AstParameter {
  private final ValNum _v;
  private final String name;

  final public static AstConst FALSE = new AstConst("False", 0);
  final public static AstConst TRUE = new AstConst("True", 1);
  final public static AstConst NAN = new AstConst("NaN", Double.NaN);
  final public static AstConst PI = new AstConst("Pi", Math.PI);
  final public static AstConst E = new AstConst("E", Math.E);


  public AstConst() {
    name = null;
    _v = null;
  }

  public AstConst(String name, double d) {
    this.name = name;
    this._v = new ValNum(d);
  }

  @Override
  public String str() {
    return name;
  }

  @Override
  public ValNum exec(Env env) {
    return _v;
  }

}
