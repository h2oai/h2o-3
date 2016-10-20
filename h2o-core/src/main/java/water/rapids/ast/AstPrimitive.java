package water.rapids.ast;

import water.H2O;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFun;
import water.util.StringUtils;


/**
 * A primitive operation.  Execution just returns the function.  *Application* (not execution) applies the function
 * to the arguments.
 *
 * TODO: rename to AstFunction
 */
public abstract class AstPrimitive extends AstRoot {
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
   * <p>The default implementation of this method executes all Asts within
   * the provided environment, and then calls {@link #apply(Val[])} passing it
   * the arguments as the list of {@link Val}s. A derived class will then only
   * need to override the second `apply()` function which is much simpler.</p>
   *
   * <p>However for certain functions (such as short-circuit boolean operators)
   * executing all arguments is not desirable -- these functions would have to
   * override this more general method.</p>
   *
   * @param env Current execution environment. Variables are looked up here.
   * @param stk TODO need clarification
   * @param asts List of AstRoot expressions that are arguments to the
   *             function. First element in this list is the function itself.
   * @return value resulting from calling the function with the provided list
   *         of arguments.
   */
  public Val apply(Env env, Env.StackHelp stk, AstRoot[] asts) {
    Val[] args = new Val[asts.length];
    args[0] = null;
    for (int i = 1; i < asts.length; i++) {
      args[i] = stk.track(asts[i].exec(env));
    }
    return apply(args);
  }

  /**
   * Most Ast* functions will want to override this method. The semantics is
   * "call this function with the provided list of arguments".
   */
  @SuppressWarnings("UnusedParameters")
  protected Val apply(Val[] args) {
    throw H2O.unimpl();
  }



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
