package water.rapids.ast;

import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFun;
import water.util.StringUtils;


/**
 * A primitive operation.  Execution just returns the function.  *Application* (not execution) applies the function
 * to the arguments.
 */
public abstract class AstPrimitive<T extends AstPrimitive<T>> extends AstRoot<T> {
  private transient ValFun _v;

  /**
   * Number of function's arguments + 1. Thus, a binary operator like '+'
   * should be declared with 3 nargs: ["+", lhs, rhs].
   * For variable-argument functions this method should return -1.
   */
  public abstract int nargs();

  /**
   * List of argument names. The length of the returned array should be equal
   * to `nargs() - 1` (unless `nargs()` returns -1, in which case this function
   * may return {"..."} or something similar).
   */
  public abstract String[] args();

  /**
   * <p>Primary method to invoke this function, passing all the parameters
   * as the `asts` list.</p>
   *
   * @param env Current execution environment. Variables are looked up here.
   * @param stk TODO need clarification
   * @param asts List of AstRoot expressions that are arguments to the
   *             function. First element in this list is the function itself.
   * @return value resulting from calling the function with the provided list
   *         of arguments.
   */
  public abstract Val apply(Env env, Env.StackHelp stk, AstRoot[] asts);


  @Override
  public ValFun exec(Env env) {
    if (_v == null) _v = new ValFun(this);
    return _v;
  }

  @Override
  public String example() {
    int nargs = nargs();
    return nargs == 1? "(" + str() + ")" :
           nargs >= 2? "(" + str() + " " + StringUtils.join(" ", args()) + ")" :
           nargs == -1? "(" + str() + " ...)" :
                   null;  // shouldn't be possible, but who knows?
  }

  @Override
  public String description() {
    return "";
  }

}
