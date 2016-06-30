package water.rapids.ast;

import water.H2O;
import water.rapids.*;
import water.rapids.ast.params.AstNum;
import water.rapids.vals.ValNum;
import water.rapids.vals.ValStr;


public abstract class AstParameter extends AstRoot {
  public final Val _v;

  public AstParameter() {
    _v = null;
  }

  public AstParameter(String str) {
    _v = new ValStr(str);
  }

  public AstParameter(Rapids e) {
    _v = new ValNum(Double.valueOf(e.token()));
  }

  public AstParameter(double d) {
    _v = new ValNum(d);
  }

  public AstParameter(Rapids e, char c) {
    _v = new ValStr(e.match(c));
  }

  @Override
  public String str() {
    return _v.toString();
  }

  @Override
  public Val exec(Env env) {
    return _v;
  }

  @Override
  public int nargs() {
    return 1;
  }

  @Override
  public String example() {
    return null;
  }

  @Override
  public String description() {
    return null;
  }

  public String toJavaString() {
    return str();
  }

  public static AstNum makeNum(double d) {
    return new AstNum(d);
  }

  public void setNum(double d) {
    throw H2O.unimpl();
  }
}
