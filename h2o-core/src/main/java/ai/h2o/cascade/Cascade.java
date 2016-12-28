package ai.h2o.cascade;

import ai.h2o.cascade.asts.Ast;
import ai.h2o.cascade.vals.Val;
import ai.h2o.cascade.vals.ValNull;
import water.util.StringUtils;


/**
 * Cascade is the next generation of the Rapids language.
 *
 */
@SuppressWarnings("unused")
public abstract class Cascade {

  /**
   * Parse a Cascade expression string into an AST object.
   */
  public static Ast parse(String expr) {
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
    Ast ast = parse(cascade);
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
   * @see ai.h2o.cascade.stdlib.StdlibFunction.TypeError
   */
  public static class TypeError extends Error {
    public TypeError(int start, int len, String message) {
      super(start, len, message);
    }
  }


  /**
   * Error indicating that the provided value is somehow invalid, even though
   * its type is correct.
   *
   * @see ai.h2o.cascade.stdlib.StdlibFunction.ValueError
   */
  public static class ValueError extends Error {
    public ValueError(int start, int len, String message) {
      super(start, len, message);
    }
  }


  /**
   * All other kinds of errors, that do not fit either the {@link TypeError}
   * or the {@link ValueError} definitions.
   *
   * @see ai.h2o.cascade.stdlib.StdlibFunction.RuntimeError
   */
  public static class RuntimeError extends Error {
    public RuntimeError(int start, int len, String message) {
      super(start, len, message);
    }
  }


  /**
   * Error indicating that an identifier cannot be resolved.
   */
  public static class NameError extends Error {
    public NameError(int start, int len, String message) {
      super(start, len, message);
    }
  }

}
