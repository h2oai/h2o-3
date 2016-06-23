package water.rapids;

import water.H2O;

public abstract class ASTParameter extends AST {
  public final Val _v;
  ASTParameter(String str) { _v=new ValStr(str); }
  ASTParameter(Rapids e) { _v = new ValNum(Double.valueOf(e.token())); }
  ASTParameter(double d) { _v = new ValNum(d); }
  ASTParameter(Rapids e, char c) { _v = new ValStr(e.match(c)); }

  public ASTParameter() { _v=null; }

  @Override public String str() { return _v.toString(); }
  @Override public Val exec(Env env) { return _v; }
  @Override int nargs() { return 1; }
  public String toJavaString() { return str(); }

  public static ASTNum makeNum(double d) { return new ASTNum(d); }
  public void setNum(double d) { throw H2O.unimpl(); }
}
