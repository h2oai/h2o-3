package water.rapids.ast;

import water.Iced;
import water.rapids.*;
import water.rapids.ast.prims.reducers.AstMean;
import water.rapids.ast.params.*;
import water.rapids.vals.*;


/**
 * Base class for all nodes in Rapids language Abstract Syntax Tree.
 */
public abstract class AstRoot<T extends AstRoot<T>> extends Iced<T> {

  /**
   * <p>"Execute" this AST expression, and return the result. For different ASTs
   * this may have different interpretation. For example, consider this Rapids
   * expression:</p>
   * <pre>    (mean frame True False)</pre>
   *
   * <p>It will be parsed into the following structure:</p>
   * <pre>
   * AstExec() instance with
   *    _asts = [AstMean() singleton instance,
   *             new AstId(frame),
   *             AstConst.TRUE,
   *             AstConst.FALSE]
   * </pre>
   *
   * <p>Execution of {@link AstExec} will execute its first argument, _asts[0],
   * verify that it produces a function ({@link ValFun}), then call
   * {@link AstPrimitive#apply(Env, Env.StackHelp, AstRoot[])} on that function
   * passing down the list of _asts arguments.</p>
   *
   * <p>The {@link AstMean} class will in turn execute all its arguments,
   * where execution of {@link AstId} fetches the referred symbol from the
   * environment, and execution of {@link AstConst} returns the value of that
   * constant.</p>
   *
   * <p>Certain other functions may choose not to evaluate all their arguments
   * (for example boolean expressions providing short-circuit evaluation).</p>
   */
  public abstract Val exec(Env env);

  /**
   * String representation of this Ast object in the Rapids language. For
   * {@link AstPrimitive}s this is the name of the function; for
   * {@link AstParameter}s this is either the name of the variable, or the
   * value of the numeric constant that the parameter represents. For more
   * complicated constructs such as {@link AstExec} or {@link AstFunction}
   * this method should return those objects as a Rapids string.
   */
  public abstract String str();


  // Note: the following 2 methods example() and description() really
  // ought to be static. Unfortunately, Java doesn't support overriding
  // static methods in subclasses, and "abstract static ..." is even a
  // syntax error.

  /**
   * <p>Return an example of how this Ast construct ought to be used. This
   * method is used to build documentation for the Rapids language. It is
   * different from {@link #str()}, in particular it must provide a valid
   * example even in a static context. For example, an {@link AstStr} may
   * return <code>"Hello, world!"</code> as an example. At the same time,
   * for different {@link AstPrimitive}s this method should generally provide
   * a typical example of how that function is to be used.</p>
   *
   * <p>Return <code>null</code> to indicate that the object should not be
   * included in the documentation.</p>
   */
  public abstract String example();

  /**
   * <p>Return the detailed description (help) for what this language construct
   * does or how it is supposed to be used. This method is used in conjunction
   * with {@link #example()} to build the documentation of the Rapids
   * language.</p>
   *
   * <p>If you need to include any formatting, then please use Markup language.
   * Although it is up to the client to support it, Markup is one of the
   * simplest and easiest alternatives.</p>
   *
   * <p>Return <code>null</code> to indicate that the object should not be
   * included in the documentation.</p>
   */
  public abstract String description();




  @Override public String toString() {
    return str();
  }

}
