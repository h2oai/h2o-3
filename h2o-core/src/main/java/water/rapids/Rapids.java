package water.rapids;

import water.H2O;
import water.fvec.Frame;
import water.rapids.ast.AstExec;
import water.rapids.ast.AstFunction;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.AstId;
import water.rapids.ast.params.AstNum;
import water.rapids.ast.params.AstStr;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;

/**
 * <p> Rapids is an interpreter of abstract syntax trees.
 *
 * <p> AstRoot Execution starts in the AstExec file, but spreads throughout Rapids.
 *
 * <p> Trees have a Lisp-like structure with the following "reserved" special
 * characters:
 * <dl>
 *   <dt> '('   <dd> a nested function application expression till ')'
 *   <dt> '{'   <dd> a nested function definition  expression till '}'
 *   <dt> '['   <dd> a numeric or string list expression, till ']'
 *   <dt> '"'   <dd> a String (double quote)
 *   <dt> "'"   <dd> a String (single quote)
 *   <dt> digits: <dd> a number
 *   <dt> letters or other specials: <dd> an ID
 * </dl>
 *
 * <p> Variables are lexically scoped inside 'let' expressions or at the top-level
 * looked-up in the DKV directly (and must refer to a known type that is valid
 * on the execution stack).
 */
public class Rapids {

  /**
   * Parse a Rapids expression string into an Abstract Syntax Tree object.
   * @param rapids expression to parse
   */
  public static AstRoot parse(String rapids) {
    return RapidsParser.parse(rapids);
  }

  /**
   * Execute a single rapids call in a short-lived session
   * @param rapids expression to parse
   */
  public static Val exec(String rapids) {
    Session session = new Session();
    try {
      H2O.incrementActiveRapidsCounter();
      AstRoot ast = Rapids.parse(rapids);
      Val val = session.exec(ast, null);
      // Any returned Frame has it's REFCNT raised by +1, and the end(val) call
      // will account for that, copying Vecs as needed so that the returned
      // Frame is independent of the Session (which is disappearing).
      return session.end(val);
    } catch (Throwable ex) {
      throw session.endQuietly(ex);
    }
    finally {
      H2O.decrementActiveRapidsCounter();
    }
  }

  /**
   * Compute and return a value in this session.  Any returned frame shares
   * Vecs with the session (is not deep copied), and so must be deleted by the
   * caller (with a Rapids "rm" call) or will disappear on session exit, or is
   * a normal global frame.
   * @param rapids expression to parse
   */
  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  public static Val exec(String rapids, Session session) {
    try {
      H2O.incrementActiveRapidsCounter();

      AstRoot ast = Rapids.parse(rapids);
      // Synchronize the session, to stop back-to-back overlapping Rapids calls
      // on the same session, which Flow sometimes does
      synchronized (session) {
        Val val = session.exec(ast, null);
        // Any returned Frame has it's REFCNT raised by +1, but is exiting the
        // session.  If it's a global, we simply need to lower the internal refcnts
        // (which won't delete on zero cnts because of the global).  If it's a
        // named temp, the ref cnts are accounted for by being in the temp table.
        if (val.isFrame()) {
          Frame frame = val.getFrame();
          assert frame._key != null : "Returned frame has no key";
          session.addRefCnt(frame, -1);
        }
        return val;
      }
    }
    finally {
      H2O.decrementActiveRapidsCounter();
    }
  }

  public static class IllegalASTException extends IllegalArgumentException {
    public IllegalASTException(String s) {
      super(s);
    }
  }
}
