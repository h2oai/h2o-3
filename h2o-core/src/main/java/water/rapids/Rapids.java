package water.rapids;

import com.google.common.primitives.Chars;
import water.fvec.Frame;
import water.rapids.ast.AstExec;
import water.rapids.ast.AstFunction;
import water.rapids.ast.AstParameter;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.*;

import java.util.ArrayList;
import java.util.HashSet;

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
  private final String _str;  // Statement to parse and execute
  private int _x;             // Parse pointer, points to the index of the next character to be consumed

  /**
   * Parse a Rapids expression string into an Abstract Syntax Tree object.
   * @param rapids expression to parse
   */
  public static AstRoot parse(String rapids) {
    Rapids r = new Rapids(rapids);
    AstRoot res = r.parseNext();
    if (r.skipWS() != ' ')
      throw new IllegalASTException("Syntax error");
    return res;
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


  //--------------------------------------------------------------------------------------------------------------------
  // Private
  //--------------------------------------------------------------------------------------------------------------------

  // Set of characters that cannot appear inside a token
  private static HashSet<Character> invalidTokenCharacters =
          new HashSet<>(Chars.asList("({[]}) \t\r\n\\\"\'".toCharArray()));

  private static HashSet<Character> validNumberCharacters =
          new HashSet<>(Chars.asList("0123456789.-+eEnNaA".toCharArray()));

  /**
   * The constructor is private: rapids expression can be parsed into an AST tree, or executed, but the "naked" Rapids
   * object has no external purpose.
   * @param rapidsStr String containing a Rapids expression.
   */
  private Rapids(String rapidsStr) {
    _str = rapidsStr;
    _x = 0;
  }

  /**
   * Parse and return the next expression from the rapids string.
   * '('   a nested function application expression ')
   * '{'   a nested function definition  expression '}'
   * '['   a numeric list expression, till ']'
   * '"'   a String (double quote): attached_token
   * "'"   a String (single quote): attached_token
   * digits: a double
   * letters or other specials: an ID
   */
  private AstRoot parseNext() {
    switch (skipWS()) {
      case '(':  return parseFunctionApplication();
      case '{':  return parseFunctionDefinition();
      case '[':  return parseList();
      case '0': case '1': case '2': case '3': case '4': case '5': case '6': case '7': case '8': case '9':
        return new AstNum(number());
      case '-':  return (peek(1)>='0' && peek(1) <='9') ? new AstNum(number()) : new AstId(token());
      case '\"': case '\'':
        return new AstStr(string());
      case ' ':  throw new IllegalASTException("Expected an expression but ran out of text");
      default:  return new AstId(token());
    }
  }

  private AstExec parseFunctionApplication() {
    eatChar('(');
    ArrayList<AstRoot> asts = new ArrayList<>();
    while (skipWS() != ')')
      asts.add(parseNext());
    eatChar(')');
    AstExec res = new AstExec(asts);
    if (peek(0) == '-') {
      eatChar('-');
      eatChar('>');
      AstId tmpid = new AstId(token());
      res = new AstExec(new AstRoot[]{new AstId("tmp="), tmpid, res});
    }
    return res;
  }

  private AstFunction parseFunctionDefinition() {
    eatChar('{');

    // Parse the list of ids
    ArrayList<String> ids = new ArrayList<>();
    ids.add("");  // 1-based ID list
    while (skipWS() != '.') {
      String id = token();
      if (!Character.isJavaIdentifierStart(id.charAt(0)))
        throw new IllegalASTException("variable must be a valid Java identifier: " + id);
      for (char c : id.toCharArray())
        if (!Character.isJavaIdentifierPart(c))
          throw new IllegalASTException("variable must be a valid Java identifier: " + id);
      ids.add(id);
    }

    // Single dot separates the list of ids from the body of the function
    eatChar('.');

    // Parse the body
    AstRoot body = parseNext();
    if (skipWS() != '}')
      throw new IllegalASTException("Expected the end of the function, but found '" + peek(0) + "'");
    eatChar('}');

    return new AstFunction(ids, body);
  }

  private AstParameter parseList() {
    eatChar('[');
    char nextChar = skipWS();
    AstParameter res = isQuote(nextChar)? parseStringList() : parseNumList();
    eatChar(']');
    return res;
  }

  private AstStrList parseStringList() {
    ArrayList<String> strs = new ArrayList<>(10);
    while (isQuote(skipWS())) {
      strs.add(string());
    }
    return new AstStrList(strs);
  }

  private AstNumList parseNumList() {
    ArrayList<Double> bases = new ArrayList<>();
    ArrayList<Double> strides = new ArrayList<>();
    ArrayList<Long> counts = new ArrayList<>();

    while (skipWS() != ']') {
      double base = number();
      double cnt = 1;
      double stride = 1;
      if (skipWS() == ':') {
        eatChar(':');
        skipWS();
        cnt = number();
        if (cnt < 1 || ((long) cnt) != cnt)
          throw new IllegalASTException("Count must be an integer larger than zero, got " + cnt);
      }
      if (skipWS() == ':') {
        eatChar(':');
        skipWS();
        stride = number();
        if (stride < 0)
          throw new IllegalASTException("Stride must be positive, got " + stride);
      }
      if (cnt == 1 && stride != 1)
        throw new IllegalASTException("If count is 1, then stride must be one (and ignored)");
      bases.add(base);
      counts.add((long) cnt);
      strides.add(stride);
      // Optional comma separating span
      if (skipWS() == ',') eatChar(',');
    }

    return new AstNumList(bases, strides, counts);
  }

  /**
   * Return the character at the current parse position (or `offset` chars in the future), without advancing it.
   * If there are no more characters to peek, return ' '.
   */
  private char peek(int offset) {
    return _x + offset < _str.length() ? _str.charAt(_x + offset) : ' ';
  }

  /**
   * Consume the next character from the parse stream, throwing an exception if it is not `c`.
   */
  private void eatChar(char c) {
    if (peek(0) != c)
      throw new IllegalASTException("Expected '" + c + "'. Got: '" + peek(0));
    _x++;
  }

  /**
   * Advance parse pointer to the first non-whitespace character, and return that character.
   * If such non-whitespace character cannot be found, then return ' '.
   */
  private char skipWS() {
    char c = ' ';
    while (_x < _str.length() && isWS(c = peek(0))) _x++;
    return c;
  }

  /**
   * Parse a "token" from the input stream. A token is terminated by the next whitespace, or any of the
   * following characters: )}],:
   *
   * NOTE: our notion of "token" is very permissive. We may want to restrict it in the future...
   */
  private String token() {
    int start = _x;
    while (!invalidTokenCharacters.contains(peek(0))) _x++;
    if (start == _x) throw new IllegalASTException("Missing token");
    return _str.substring(start, _x);
  }

  /**
   * Parse a number from the token stream.
   */
  public double number() {
    int start = _x;
    while (validNumberCharacters.contains(peek(0))) _x++;
    if (start == _x) throw new IllegalASTException("Missing a number");
    String s = _str.substring(start, _x);
    if (s.toLowerCase().equals("nan")) return Double.NaN;
    return Double.valueOf(s);
  }

  /**
   * Parse a string from the token stream.
   */
  public String string() {
    char quote = peek(0);
    int start = ++_x;
    boolean has_escapes = false;
    while (_x < _str.length()) {
      char c = peek(0);
      if (c == '\\') {
        has_escapes = true;
        _x += 2;
      } else if (c == quote) {
        _x++;
        if (has_escapes) {
          StringBuilder sb = new StringBuilder();
          for (int i = start; i < _x - 1; i++) {
            char ch = _str.charAt(i);
            sb.append(ch == '\\'? _str.charAt(++i) : ch);
          }
          return sb.toString();
        } else {
          return _str.substring(start, _x - 1);
        }
      } else {
        _x++;
      }
    }
    throw new IllegalASTException("Unterminated string at " + start);
  }

  private static boolean isWS(char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r';
  }

  private static boolean isQuote(char c) {
    return c == '\'' || c == '\"';
  }


  // Return unparsed text, useful in error messages and debugging
  // private String unparsed() {
  //   return _str.substring(_x, _str.length());
  // }

  //  public AstRoot throwErr(String msg) {
  //    int idx = _str.length() - 1;
  //    int lo = _x, hi = idx;
  //
  //    if (idx < lo) {
  //      lo = idx;
  //      hi = lo;
  //    }
  //    String s = msg + '\n' + _str + '\n';
  //    int i;
  //    for (i = 0; i < lo; i++) s += ' ';
  //    s += '^';
  //    i++;
  //    for (; i < hi; i++) s += '-';
  //    if (i <= hi) s += '^';
  //    s += '\n';
  //    throw new IllegalASTException(s);
  //  }

  public static class IllegalASTException extends IllegalArgumentException {
    public IllegalASTException(String s) {
      super(s);
    }
  }
}
