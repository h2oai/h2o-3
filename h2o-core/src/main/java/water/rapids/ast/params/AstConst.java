package water.rapids.ast.params;

import water.H2O;
import water.rapids.ast.AstParameter;

/**
 * Class for constants
 */
public class AstConst extends AstParameter {

  final public static AstConst FALSE = new AstConst("False", 0);
  final public static AstConst TRUE = new AstConst("True", 1);
  final public static AstConst NAN = new AstConst("NaN", Double.NaN);
  final public static AstConst PI = new AstConst("Pi", Math.PI);
  final public static AstConst E = new AstConst("E", Math.E);


  private final String name;

  public AstConst() {
    name = null;
  }

  public AstConst(String name, double d) {
    super(d);
    this.name = name;
  }

  @Override
  public String str() {
    return name;
  }

  @Override
  public void setNum(double d) {
    throw H2O.fail("Attempt to modify constant " + name);
  }
}
