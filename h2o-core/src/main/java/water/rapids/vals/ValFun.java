package water.rapids.vals;

import water.rapids.ast.AstFunction;

/**
 * A Rapids function
 */
public class ValFun extends Val {
  private final AstFunction _ast;

  public ValFun(AstFunction ast) {
    _ast = ast;
  }

  @Override public int type() { return FUN; }
  @Override public boolean isFun() { return true; }
  @Override public AstFunction getFun() { return _ast; }
  @Override public String toString() { return _ast.toString(); }

  public String[] getArgs() {
    return _ast.args();
  }
}
