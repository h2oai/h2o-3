package ai.h2o.cascade;

import ai.h2o.cascade.asts.AstNode;
import ai.h2o.cascade.asts.AstApply;
import ai.h2o.cascade.vals.Val;
import ai.h2o.cascade.vals.ValNull;
import ai.h2o.cascade.core.Function;
import ai.h2o.cascade.core.Scope;
import ai.h2o.cascade.core.WorkFrame;
import water.util.StringUtils;



/**
 * <hr/>
 * <h1>Cascade</h1>
 *
 * <p> Cascade is the next generation of the Rapids language. It is an
 * interpreted language with Lisp-like syntax. A Cascade program is first read
 * by the {@link CascadeParser} and converted into an Abstract Syntax Tree.
 * The root node of this AST is then executed, and its result is returned to
 * the user. Each {@link AstNode} will first evaluate its children nodes, and
 * thus the entire AST is recursively executed.
 *
 * <p> Within a single session execution is single-threaded. However operations
 * on large data frames are still parallelized across the entire cluster, thus
 * most time-consuming work is performed efficiently.
 *
 * <p> All {@code AstNode}s are executed within the context of some
 * {@link Scope}. A <i>scope</i> is essentially a namespace for variables
 * lookup. The scopes can be nested, with variables in the inner scope
 * shadowing variables from the outer scopes. Also, when a scope is exited,
 * then all variables that were defined within that scope will be automatically
 * garbage-collected. Each scope carries a reference to its parent scope, so
 * that if a variable cannot be found within the current scope, it will be
 * searched for in all parent scopes. Thus, scopes also form a tree, with root
 * being the "global scope". This global scope is kept in an instance of a
 * {@link CascadeSession} and persists across multiple REST API calls.
 *
 * <p> Executing an {@code AstNode} in Cascade produces a {@link Val}. Thus,
 * {@code Val} is Cascade's equivalent of {@code Object} in Java / Python.
 * Some {@code Val}s (notably {@link WorkFrame}s) may depend on external
 * resources (such as DKV) and thus require explicit deallocation when such
 * values are garbage-collected:
 * <ul>
 *   <li> If a {@code Val} is returned from a function, then it is the caller's
 *   responsibility to dispose of that value. Usually this happens within
 *   {@link AstApply}, and you don't need to do anything extra.
 *   <li> If a {@code Val} is stored in a variable, then it should be finalized
 *   when the variable is destroyed (either by going out of scope, or
 *   explicitly deleted by the user).
 *   <li> If in a function you create {@code Val}s but do not return them, you
 *   have to finalize them yourself. (This also applies to {@code Val}s that
 *   you mutate but do not return).
 * </ul>
 *
 * <p> For efficiency purposes, some {@code Val}s are considered mutable, while
 * others are read-only. All {@code Val}s are created in the mutable state, and
 * they become read-only when written to a variable. Mutating methods on
 * {@code Val}s are all written in such a way as to return modified copies of
 * themselves if they're in the read-only state; or otherwise change in-place
 * and return {@code this}. Certain functions may need to circumvent the
 * read-only protection and modify the {@code Val} anyways -- this should be
 * done only if the semantics of the function demands it, and when it is
 * clearly documented.
 *
 *
 */
@SuppressWarnings("unused")
public abstract class Cascade {

  /**
   * Parse a Cascade expression string into an AST object.
   */
  public static AstNode parse(String expr) {
    return new CascadeParser(expr).parse();
  }

  /**
   * Evaluate Cascade expression within the context of the provided session.
   *
   * Compute and return a value in this session.  Any returned frame shares
   * Vecs with the session (is not deep copied), and so must be deleted by the
   * caller (with a Rapids "rm" call) or will disappear on session exit, or is
   * a normal global frame.
   * @param cascade the Cascade expression to parse
   * @param session session within which the expression will be evaluated
   */
  public static Val eval(String cascade, CascadeSession session) {
    if (StringUtils.isNullOrEmpty(cascade)) return new ValNull();
    AstNode ast = parse(cascade);
    return ast.exec(session.globalScope());
  }


  //--------------------------------------------------------------------------------------------------------------------
  // Exceptions
  //--------------------------------------------------------------------------------------------------------------------

  /**
   * Base class for all Cascade exceptions. It carries an error message,
   * and coordinates (within the Cascade expression being executed) where the
   * error has occurred. It <b>does not</b> carry the Cascade expression
   * itself, because that would require storing it within each AST node, which
   * is too expensive. Instead, we assume that the caller remembers what
   * expression he/she tries to execute, and will be able to interpret error
   * location correctly.
   */
  public abstract static class Error extends RuntimeException {
    public int location;
    public int length;

    public Error(int start, int len, String message) {
      super(message);
      location = start;
      length = len;
    }

    public Error(int start, int len, Throwable cause) {
      super(cause);
      location = start;
      length = len;
    }
  }


  /**
   * This exception is thrown whenever a Cascade expression cannot be parsed
   * correctly. Should be thrown from {@link CascadeParser} only.
   */
  public static class SyntaxError extends Error {
    public SyntaxError(String s, int start) {
      super(start, 1, s);
    }
    public SyntaxError(String s, int start, int len) {
      super(start, len, s);
    }
  }


  /**
   * Error indicating general type mismatch between the expected and the
   * provided argument(s).
   *
   * @see Function.TypeError
   */
  public static class TypeError extends Error {
    public TypeError(int start, int len, String message) {
      super(start, len, message);
    }
    public TypeError(int start, int len, Throwable cause) {
      super(start, len, cause);
    }
  }


  /**
   * Error indicating that the provided value is somehow invalid, even though
   * its type is correct.
   *
   * @see Function.ValueError
   */
  public static class ValueError extends Error {
    public ValueError(int start, int len, String message) {
      super(start, len, message);
    }
    public ValueError(int start, int len, Throwable cause) {
      super(start, len, cause);
    }
  }


  /**
   * All other kinds of errors, that do not fit either the {@link TypeError}
   * or the {@link ValueError} definitions.
   *
   * @see Function.RuntimeError
   */
  public static class RuntimeError extends Error {
    public RuntimeError(int start, int len, String message) {
      super(start, len, message);
    }
    public RuntimeError(int start, int len, Throwable cause) {
      super(start, len, cause);
    }
  }


  /**
   * Error indicating that an identifier cannot be resolved.
   */
  public static class NameError extends Error {
    public NameError(int start, int len, String message) {
      super(start, len, message);
    }
    public NameError(int start, int len, Throwable cause) {
      super(start, len, cause);
    }
  }

}
