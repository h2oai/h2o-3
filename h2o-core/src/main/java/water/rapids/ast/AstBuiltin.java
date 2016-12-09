package water.rapids.ast;

import water.H2O;
import water.rapids.Env;
import water.rapids.Val;

/**
 * (Replacement for AstPrimitive).
 */
public abstract class AstBuiltin<T extends AstBuiltin<T>> extends AstPrimitive<T> {

  /**
   * <p>Primary method to invoke this function, passing all the parameters
   * as the `asts` list.</p>
   *
   * <p>The default implementation of this method executes all Asts within
   * the provided environment, and then calls {@link #exec(Val[])} passing it
   * the arguments as the list of {@link Val}s. A derived class will then only
   * need to override the second `exec()` function which is much simpler.</p>
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
    return exec(args);
  }

  /**
   * Most Ast* functions will want to override this method. The semantics is
   * "call this function with the provided list of arguments".
   */
  @SuppressWarnings("UnusedParameters")
  protected Val exec(Val[] args) {
    throw H2O.unimpl();
  }

}
