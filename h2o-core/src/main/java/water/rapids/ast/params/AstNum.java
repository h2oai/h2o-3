package water.rapids.ast.params;

import water.rapids.Env;
import water.rapids.vals.ValNum;
import water.rapids.ast.AstParameter;

/**
 * A number literal.  Execution simply returns its value.
 */
public class AstNum extends AstParameter {
  private final ValNum _v;

  public AstNum() {
    this(0);
  }

  public AstNum(double d) {
    _v = new ValNum(d);
  }

  @Override
  public String str() {
    return _v.toString();
  }

  @Override
  public int[] columns(String[] names) {
    return new int[]{(int) _v.getNum()};
  }

  public void setNum(double d) {
    _v.setNum(d);
  }

  public double getNum() {
    return _v.getNum();
  }

  @Override
  public ValNum exec(Env env) {
    return _v;
  }

}
