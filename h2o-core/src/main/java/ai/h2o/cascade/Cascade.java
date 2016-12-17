package ai.h2o.cascade;

import ai.h2o.cascade.asts.Ast;
import ai.h2o.cascade.vals.Val;


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
    Ast ast = parse(cascade);
    /*
    Val val = session.exec(ast, null);
    // Any returned Frame has it's REFCNT raised by +1, but is exiting the
    // session.  If it's a global, we simply need to lower the internal refcnts
    // (which won't delete on zero cnts because of the global).  If it's a
    // named temp, the ref cnts are accounted for by being in the temp table.
    if (val.type() == Val.Type.FRAME) {
      Frame frame = val.getFrame();
      assert frame._key != null : "Returned frame has no key";
      // session.addRefCnt(frame, -1);
    }
    return val;*/
    return null;
  }

}
