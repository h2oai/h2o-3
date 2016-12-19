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

}
