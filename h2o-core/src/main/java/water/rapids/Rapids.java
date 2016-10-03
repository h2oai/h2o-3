package water.rapids;

import water.fvec.Frame;
import water.rapids.ast.AstExec;
import water.rapids.ast.AstFunction;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.*;

/**
 * <p> Rapids is an interpreter of abstract syntax trees.
 *
 * <p> This file contains the AstRoot parser and parser helper functions.
 * AstRoot Execution starts in the AstExec file, but spreads throughout Rapids.
 *
 * <p> Trees have a Lisp-like structure with the following "reserved" special
 * characters:
 * <dl>
 *   <dt> '('   <dd> a nested function application expression till ')'
 *   <dt> '{'   <dd> a nested function definition  expression till '}'
 *   <dt> '#'   <dd> a double: attached_token
 *   <dt> '['   <dd> a numeric or string list expression, till ']'
 *   <dt> '%'   <dd> an ID: attached_token
 *   <dt> '"'   <dd> a String (double quote): attached_token
 *   <dt> "'"   <dd> a String (single quote): attached_token
 *   <dt> digits: <dd> a double
 *   <dt> letters or other specials: <dd> an ID
 * </dl>
 *
 * <p> In the above, attached_token signals that the special char has extra chars
 * that must be parsed separately.  These are variable names (in the case of
 * %), doubles (in the case of #), Strings (in the case of ' and "), or number
 * lists (in the case of '[' till ']').
 *
 * <p> Variables are lexically scoped inside 'let' expressions or at the top-level
 * looked-up in the DKV directly (and must refer to a known type that is valid
 * on the execution stack).
 */
public class Rapids {
  private final String _str;  // Statement to parse and execute
  private int _x;             // Parse pointer

  /**
   * Parse a Rapids expression string into an Abstract Syntax Tree object.
   * @param rapids expression to parse
   */
  public static AstRoot parse(String rapids) {
    return new Rapids(rapids).parse();
  }

  /**
   * Execute a single rapids call in a short-lived session
   * @param rapids expression to parse
   */
  public static Val exec(String rapids) {
    Session session = new Session();
    try {
      AstRoot ast = Rapids.parse(rapids);
      Val val = session.exec(ast, null);
      // Any returned Frame has it's REFCNT raised by +1, and the end(val) call
      // will account for that, copying Vecs as needed so that the returned
      // Frame is independent of the Session (which is disappearing).
      return session.end(val);
    } catch (Throwable ex) {
      throw session.endQuietly(ex);
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
        assert frame._key != null; // No nameless Frame returns, as these are hard to cleanup
        session.addRefCnt(frame, -1);
      }
      return val;
    }
  }

  /**
   * Parse an expression
   * '('   a nested function application expression ')
   * '{'   a nested function definition  expression '}'
   * '#'   a double: attached_token
   * '['   a numeric list expression, till ']'
   * '"'   a String (double quote): attached_token
   * "'"   a String (single quote): attached_token
   * digits: a double
   * letters or other specials: an ID
   */
  public AstRoot parse() {
    switch (skipWS()) {
      case '(':  return new AstExec(this); // function application
      case '{':  return new AstFunction(this);  // function definition
      case '#':  _x++;                     // Skip before double, FALL THRU
      case '0': case '1': case '2': case '3': case '4': case '5': case '6': case '7': case '8': case '9':
        return new AstNum(this);
      case '\"': return new AstStr(this,'\"');
      case '\'': return new AstStr(this,'\'');
      case '[':  return isQuote(xpeek('[').skipWS()) ? new AstStrList(this) : new AstNumList(this);
      case ' ':  throw new IllegalASTException("Expected an expression but ran out of text");
      case '-':  return (peek(1)>='0' && peek(1) <='9') ? new AstNum(this) : new AstId(this);
      default:  return new AstId(this);
    }
  }


  /**
   * Primary constructor
   * @param rapidsStr String containing a Rapids expression.
   */
  protected Rapids(String rapidsStr) {
    _str = rapidsStr;
    _x = 0;
  }

  // peek ahead
  private char peek() { return peek(0); }
  private char peek(int offset) {
    return _x + offset < _str.length() ? _str.charAt(_x + offset) : ' ';
  }

  // Peek, and throw if not found an expected character
  public Rapids xpeek(char c) {
    if (peek() != c)
      throw new IllegalASTException("Expected '" + c + "'. Got: '" + peek() + "'.  unparsed: " + unparsed() + " ; _x = " + _x);
    _x++;
    return this;                // Flow coding
  }

  // Skip white space, return the 1st non-whitespace char or ' ' if out of text
  public char skipWS() {
    char c = ' ';
    while (_x < _str.length() && isWS(c = peek())) _x++;
    return c;
  }

  // Parse till whitespace or close-paren
  public String token() {
    int start = _x;
    char c;
    while (!isWS(c = peek()) && c != ')' && c != '}') _x++;
    if (start == _x) throw new IllegalArgumentException("Missing token");
    return _str.substring(start, _x);
  }

  // Parse while number-like, and return the number
  public double number() {
    int start = _x;
    char c;
    while (!isWS(c = peek()) && c != ')' && c != ']' && c != ',' && c != ':') _x++;
    return Double.valueOf(_str.substring(start, _x));
  }

  // Parse till matching
  public String match(char c) {
    int start = ++_x;
    while (peek() != c) _x++;
    _x++;                       // Skip the match
    return _str.substring(start, _x - 1);
  }

  // Return unparsed text, useful in error messages and debugging
  String unparsed() {
    return _str.substring(_x, _str.length());
  }

  static boolean isWS(char c) {
    return c == ' ' || c == '\t' || c == '\n';
  }

  public static boolean isQuote(char c) {
    return c == '\'' || c == '\"';
  }


  public AstRoot throwErr(String msg) {
    int idx = _str.length() - 1;
    int lo = _x, hi = idx;

    if (idx < lo) {
      lo = idx;
      hi = lo;
    }
    String s = msg + '\n' + _str + '\n';
    int i;
    for (i = 0; i < lo; i++) s += ' ';
    s += '^';
    i++;
    for (; i < hi; i++) s += '-';
    if (i <= hi) s += '^';
    s += '\n';
    throw new IllegalASTException(s);
  }

  public static class IllegalASTException extends IllegalArgumentException {
    public IllegalASTException(String s) {
      super(s);
    }
  }
}
