package ai.h2o.cascade;

import ai.h2o.cascade.asts.AstNode;
import ai.h2o.cascade.core.*;
import water.fvec.Frame;
import water.fvec.Vec;
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
 * Some {@code Val}s (notably {@link GhostFrame}s) may depend on external
 * resources (such as DKV) and thus require explicit deallocation in order to
 * prevent memory leaks -- see the KRC section for details.
 *
 * <p> All {@link Frame}s used within Cascade should be owned by the Cascade.
 * In particular, any code that uses an external {@code Frame} (from DKV) must
 * clone that frame if it wants to keep it around. Additionally, raw
 * {@code Frame}s should not be passed around, but instead wrapped in the
 * {@link CorporealFrame} class.
 *
 *
 * <h2>Ghost Frames</h2>
 * <p> The central piece of the Cascade runtime is the {@link GhostFrame}. This
 * class is used as a replacement for the traditional {@link Frame}. The most
 * simple ghost frame is the {@link CorporealFrame}, which is just a wrapper
 * around the underlying {@code Frame}. More advanced ghost frames have
 * frame-like interface, and contain instructions on how to evaluate
 * themselves. This process of evaluation runs an {@code MRTask} to compute
 * the new "materialized" {@code Frame}, and returns it in the form of a
 * {@code CorporealFrame} result.
 *
 * <p> Thus, a {@code GhostFrame} can be viewed as a sequence of transforms
 * applied to one or more "parent" frames, but not carried out yet. New
 * {@code GhostFrame}s can be built on top of existing ones, which is
 * equivalent to chaining the transformations. When such GhostFrame is
 * materialized (i.e. distilled into a physical {@code Frame}), then all
 * transformations are applied within a single {@code MRTask} building the
 * result but avoiding creation of the intermediate vecs.
 *
 * <p> Only materialized {@code GhostFrame}s can be bound to local variables:
 * this avoids computing same values multiple times if the variable is used
 * more than once within an expression, as well as makes it possible to
 * return the computed data to the user, and makes it easier to track the
 * dependencies between the frames. In particular, since the result from a
 * top-level cascade command is stored in local variable {@code _}, it is
 * not possible to return to the user an unmaterialized {@code GhostFrame}.
 *
 *
 * <h2>Keyed Ref Counts (KRC)</h2>
 * <p> This is a registry of all DKV objects (except {@code Vec}s) owned by a
 * Cascade session. All these objects will be removed from the DKV once the
 * session ends.
 * <ul>
 *   <li>When a {@code CorporealFrame} is created, it increases the ref count
 *       for its wrapped {@code Frame}.</li>
 *   <li>When a {@code CorporealFrame} is disposed (lifespan of each such frame
 *       is carefully managed), the ref count for the underlying {@code Frame}
 *       is decreased.</li>
 *   <li>If ref count for some {@code Frame} reaches zero, that frame is
 *       removed from the DKV. Its vecs are also removed, see the RVC section
 *       for details.</li>
 * </ul>
 *
 * <h2>Corporeal Frame Registry (CFR)</h2>
 * <p> This is a registry of all {@link CorporealFrame}s created since the
 * beginning of the current session-level command. The purpose of this registry
 * is to help reliably keep track of all the {@code CorporealFrame}s in the
 * runtime. However, it does not keep track of the {@code CorporealFrame}s
 * bound to local variables.
 * <ul>
 *   <li>The registry exists at the session level, and is cleaned up at the end
 *       of each top level Cascade command.</li>
 *   <li>When a {@code CorporealFrame} is created, it must be registered in
 *       the CFR.</li>
 *   <li>If a {@code CorporealFrame} is bound to a variable, it is removed from
 *       this registry. This includes the case when the return value from the
 *       top level Cascade command is bound to variable {@code _}.</li>
 *   <li>The CFR is purged at the end of execution of a top level Cascade
 *       command. All {@code CorporealFrame}s within the registry are
 *       disposed (see the KRC section).</li>
 * </ul>
 *
 * <h2>Registry of Vec Copies (RVC)</h2>
 * <p> This mechanism allows us to share {@link Vec}s across multiple
 * {@code Frame}s without the need to copy them. It is similar to ref counts
 * in the KRC registry, except that here we keep track of the number of
 * <i>copies</i> rather than the number of references. Number of copies is
 * equal to the number of references minus one.
 * <ul>
 *   <li>If a {@code Frame} is created in such a way that it reuses
 *       {@code Vec}s from some other {@code Frame}, then those vecs should be
 *       put into this registry (meaning the copies count for such vecs will
 *       be increased).</li>
 *   <li>When a {@code Frame} is removed (see KRC section), we iterate through
 *       its vecs and check whether they exist in this registry. If a vec is
 *       not on the registry, it is removed from the DKV. If a vec is on the
 *       registry, then its copy count is decreased by 1.</li>
 *   <li>If copy count of some vec reaches 0, that vec is removed from this
 *       registry, but <b>not</b> from the DKV.</li>
 *   <li>When any function wants to modify some {@code Vec} in-place, it should
 *       check with the RVC whether that vec is on the registry. If no, then
 *       the function can proceed. If yes, then the function should clone that
 *       vec, and decrease its copy-count by 1.</li>
 * </ul>
 *
 */
@SuppressWarnings("unused")
public abstract class Cascade {

  /**
   * Parse a Cascade expression string into an Abstract Syntax Tree object.
   *
   * @param cascade the Cascade expression to parse. See {@link CascadeParser}
   *                for a summary of the Cascade language syntax.
   */
  public static AstNode parse(String cascade) {
    CascadeParser cp = new CascadeParser(cascade);
    return cp.parse();
  }

  /**
   * Evaluate Cascade expression within the context of the provided session.
   *
   * Compute and return a value in this session. The value is also stored in
   * the local variable {@code _} (underscore).
   *
   * @param cascade the Cascade expression to parse. See {@link CascadeParser}
   *                for a summary of the Cascade language syntax.
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
