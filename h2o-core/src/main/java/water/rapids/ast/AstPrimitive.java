package water.rapids.ast;

import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFun;
import water.util.StringUtils;

import java.util.Arrays;

/**
 * A primitive operation.  Execution just returns the function.  *Application* (not execution) applies the function
 * to the arguments.
 */
public abstract class AstPrimitive extends AstRoot {
  @Override
  public String example() {
    int nargs = nargs();
    return nargs == 1? "(" + str() + ")" :
           nargs >= 2? "(" + str() + " " + StringUtils.join(" ", args()) + ")" :
           nargs == -1? "(" + str() + " ...)" :  // these cases should really be overridden...
                   null;  // shouldn't be possible, but who knows?
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
