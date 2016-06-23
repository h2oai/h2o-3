package water.rapids;

import water.H2O;

public abstract class ASTParameter extends AST {
  public final Val _v;

  public ASTParameter() { _v = null; }
  protected ASTParameter(String str) { _v=new ValStr(str); }
  protected ASTParameter(Rapids e) { _v = new ValNum(Double.valueOf(e.token())); }
  protected ASTParameter(double d) { _v = new ValNum(d); }

  ASTParameter(Rapids e, char c) { _v = new ValStr(e.match(c)); }

  @Override public String str() { return _v.toString(); }
  @Override public Val exec(Env env) { return _v; }
  @Override int nargs() { return 1; }
  @Override public String example() { return null; }
  @Override public String description() { return null; }
  public String toJavaString() { return str(); }

  public static ASTNum makeNum(double d) { return new ASTNum(d); }
  public void setNum(double d) { throw H2O.unimpl(); }
}
