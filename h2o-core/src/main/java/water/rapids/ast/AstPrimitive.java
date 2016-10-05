package water.rapids.ast;

import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFun;

/**
 * A primitive operation.  Execution just returns the function.  *Application* (not execution) applies the function
 * to the arguments.
 */
public abstract class AstPrimitive extends AstRoot {
  @Override
  public String example() {
    return null;
  }

  @Override
  public String description() {
    return null;
  }

  @Override
  public ValFun exec(Env env) {
    return new ValFun(this);
  }

  public abstract String[] args();
}
