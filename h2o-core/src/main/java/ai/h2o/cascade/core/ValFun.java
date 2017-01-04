package ai.h2o.cascade.core;

/**
 * Function wrapper.
 */
public class ValFun extends Val {
  private Function f;


  public ValFun(Function function) {
    f = function;
  }

  @Override
  public Type type() {
    return Type.FUN;
  }

  @Override
  public boolean isFun() {
    return true;
  }

  @Override
  public Function getFun() {
    return f;
  }

}
