package water.rapids.vals;

import water.rapids.Val;
import water.rapids.ast.AstPrimitive;

/**
 * A Rapids function
 */
public class ValFun extends Val {
  private final AstPrimitive _ast;

  public ValFun(AstPrimitive ast) {
    _ast = ast;
  }

  @Override public int type() { return FUN; }
  @Override public boolean isFun() { return true; }
  @Override public AstPrimitive getFun() { return _ast; }
  @Override public String toString() { return _ast.toString(); }

  public String[] getArgs() {
    return _ast.args();
  }
}
