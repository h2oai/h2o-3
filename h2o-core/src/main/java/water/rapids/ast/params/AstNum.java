package water.rapids.ast.params;

import water.rapids.Rapids;
import water.rapids.vals.ValNum;
import water.rapids.ast.AstParameter;

/**
 * A number literal.  Execution simply returns its value.
 */
public class AstNum extends AstParameter {
  public AstNum() {
  }

  public AstNum(Rapids e) {
    super(e);
  }

  public AstNum(double d) {
    super(d);
  }

  @Override
  public int[] columns(String[] names) {
    return new int[]{(int) _v.getNum()};
  }

  public void setNum(double d) {
    ((ValNum) _v).setNum(d);
  }

  public double getNum() {
    return _v.getNum();
  }

}
