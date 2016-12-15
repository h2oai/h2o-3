package ai.h2o.cascade;

import ai.h2o.cascade.asts.*;
import water.util.CollectionUtils;
import water.util.StringUtils;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;


/**
 * <p> Cascade is the next generation of the Rapids language.
 *
 * <p> Main language structures are the following:
 * <dl>
 *   <dt><p>{@code (fun val1 ... valN)}</dt>
 *   <dd> function {@code fun} applied to the provided list of values;</dd>
 *
 *   <dt><p>{@code 42.7}</dt>
 *   <dd> a number;</dd>
 *
 *   <dt><p>{@code "Hello, \"world\"!"}</dt>
 *   <dd> a string (with standard escapes);</dd>
 *
 *   <dt><p>{@code [7, 4.2, nan, -1.78E+3]}</dt>
 *   <dd> a list of numbers;</dd>
 *
 *   <dt><p>{@code [1:2:-1, 3:3, 5, 7]}</dt>
 *   <dd> a slice: shorthand for writing ranges of numbers compactly;</dd>
 *
 *   <dt><p>{@code ['one', "two", "thre\u005cu0207"]}</dt>
 *   <dd> a list of strings;</dd>
 *
 *   <dt><p>{@code `var1 var2 ... varN`}</dt>
 *   <dd> a list of unevaluated identifiers;</dd>
 *
 *   <dt><p>{@code var = value}</dt>
 *   <dd> assign {@code value} to a variable {@code var};</dd>
 *
 *   <dt><p>{@code (def `arg1 ... argN` body)}</dt>
 *   <dd> define a function taking arguments {@code arg1, ..., argN} and
 *        executing {@code body} with those values locally bound;</dd>
 *
 *   <dt><p>{@code (if condition var1 var2)}</dt>
 *   <dd> evaluate {@code condition} and then execute {@code var1} if the
 *        condition is true, or (optionally) {@code var2} otherwise;</dd>
 *
 *   <dt><p>{@code (for `i` [list] body)}</dt>
 *   <dd> run the loop, with variable {@code i} taking values sequentially
 *        from the list;</dd>
 * </dl>
 *
 */
public class Cascade {
  private String expr;  // Statement to parse and then execute
  private int pos;      // Parse pointer, points to the index of the next character to be consumed

  /**
   * Parse a Cascade expression string into an AST object.
   */
  public static Ast parse(String expr) {
    Cascade r = new Cascade(expr);
    Ast res = r.parseNext();
    if (r.nextChar() != ' ')
      throw new IllegalASTException("Syntax error: illegal Cascade expression `" + expr + "`");
    return res;
  }

  /**
   * Execute a single rapids call in a short-lived session
   * @param cascade expression to parse
   */
  /*
  public static Val exec(String cascade) {
    Session session = new Session();
    try {
      Ast ast = Cascade.parse(rapids);
      Val val = session.exec(ast, null);
      // Any returned Frame has it's REFCNT raised by +1, and the end(val) call
      // will account for that, copying Vecs as needed so that the returned
      // Frame is independent of the Session (which is disappearing).
      return session.end(val);
    } catch (Throwable ex) {
      throw session.endQuietly(ex);
    }
  }
*/
  /**
   * Compute and return a value in this session.  Any returned frame shares
   * Vecs with the session (is not deep copied), and so must be deleted by the
   * caller (with a Rapids "rm" call) or will disappear on session exit, or is
   * a normal global frame.
   * @param cascade expression to parse
   */
  /*
  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  public static Val exec(String cascade, Session session) {
    Ast ast = Cascade.parse(rapids);
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
        // session.addRefCnt(frame, -1);
      }
      return val;
    }
  }
*/

  //--------------------------------------------------------------------------------------------------------------------
  // Private
  //--------------------------------------------------------------------------------------------------------------------

  // Set of characters that may appear in a number. Note that "NaN" or "nan" is also a number.
  private static Set<Character> validNumberCharacters = StringUtils.toCharacterSet("0123456789.-+eEnNaA");

  // List of all "simple" backslash-escape sequences (i.e. those that are only 2-characters long, e.g. '\n')
  private static Map<Character, Character> simpleEscapeSequences =
      CollectionUtils.createMap(StringUtils.toCharacterArray("ntrfb'\"\\"),
                                StringUtils.toCharacterArray("\n\t\r\f\b'\"\\"));


  /**
   * The constructor is private: rapids expression can be parsed into an AST tree, or executed, but the "naked" Rapids
   * object has no external purpose.
   * @param expresssion string with a Cascade expression.
   */
  private Cascade(String expresssion) {
    expr = expresssion;
    pos = 0;
  }

  /**
   * Parse and return the next AST from the rapids expression string.
   */
  private Ast parseNext() {
    char ch = nextChar();
    if (ch == '(') {
      return parseFunctionApplication();
    }
    if (ch == '[') {
      return parseList();
    }
    if (isQuote(ch)) {
      return new AstStr(parseString());
    }
    if (isDigit(ch) || ch == '-' || ch == '.') {
      return new AstNum(parseNumber());
    }
    if (isAlpha(ch)) {
      return parseId();
    }
    throw syntaxError("invalid syntax");
  }

  /**
   * Parse "function application" expression, i.e. construct of the form
   * {@code (func arg1 ... argN)}.
   */
  private AstExec parseFunctionApplication() {
    consumeChar('(');
    Ast head = parseNext();

    ArrayList<Ast> args = new ArrayList<>();
    ArrayList<String> names = null;
    while (nextChar() != ')') {
      Ast ast = parseNext();
      if (ast instanceof AstId && nextChar() == '=') {
        consumeChar('=');
        if (names == null) {
          names = new ArrayList<>(args.size() + 1);
          for (int i = 0; i < args.size(); ++i)
            names.add(null);
        }
        names.add(ast.str());
        args.add(parseNext());
      } else {
        if (names != null)
          throw syntaxError("positional argument after a named argument");
        args.add(ast);
      }
    }
    consumeChar(')');
    return new AstExec(head, args, names);
  }


  /**
   * Parse and return a list of tokens: either a list of strings, or a list
   * of numbers. We do not support lists of mixed types, or lists containing
   * variables / other expressions. If necessary, such lists can always be
   * created using the {@code list} function.
   */
  private Ast parseList() {
    consumeChar('[');
    char nextChar = nextChar();
    Ast res = isQuote(nextChar)? parseStringList() : parseNumList();
    consumeChar(']');
    return res;
  }

  /**
   * Parse a list of strings. Strings can be either in single- or in double
   * quotes. Additionally we allow elements to be either space- or comma-
   * separated.
   */
  private AstStrList parseStringList() {
    ArrayList<String> strs = new ArrayList<>(10);
    while (isQuote(nextChar())) {
      strs.add(parseString());
      if (nextChar() == ',') consumeChar(',');
    }
    return new AstStrList(strs);
  }

  /**
   * Parse a "num list". This could be either a plain list of numbers, or a range, or a list of ranges. For example
   * [2 3 4 5 6 7] can also be written as [2:6] or [2:2 4:4:1]. The format of each "range" is `start:count[:stride]`,
   * and it denotes the sequence {start, start + stride, ..., start + (count-1)*stride}. Here start and stride may
   * be real numbers, however count must be a non-negative integer. Negative strides are also not allowed.
   */
  private AstNumList parseNumList() {
    ArrayList<Double> bases = new ArrayList<>();
    ArrayList<Double> strides = new ArrayList<>();
    ArrayList<Long> counts = new ArrayList<>();

    while (nextChar() != ']') {
      double base = parseNumber();
      double count = 1;
      double stride = 1;
      if (nextChar() == ':') {
        consumeChar(':');
        nextChar();
        count = parseNumber();
        if (count < 1 || ((long) count) != count)
          throw new IllegalASTException("Count must be a positive integer, got " + count);
      }
      if (nextChar() == ':') {
        consumeChar(':');
        nextChar();
        stride = parseNumber();
        if (stride < 0 || Double.isNaN(stride))
          throw new IllegalASTException("Stride must be positive, got " + stride);
      }
      if (count == 1 && stride != 1)
        throw new IllegalASTException("If count is 1, then stride must be one (and ignored)");
      bases.add(base);
      counts.add((long) count);
      strides.add(stride);
      // Optional comma separating span
      if (nextChar() == ',') consumeChar(',');
    }

    return new AstNumList(bases, strides, counts);
  }

  /**
   * Parse an id from the input stream. An id has common interpretation:
   * an alpha character followed by any number of alphanumeric characters.
   */
  private AstId parseId() {
    int start = pos;
    while (isAlphaNum(peek(0))) pos++;
    assert pos > start;
    return new AstId(expr.substring(start, pos));
  }

  /**
   * Parse a number from the token stream.
   */
  private double parseNumber() {
    int start = pos;
    while (validNumberCharacters.contains(peek(0))) pos++;
    if (start == pos) throw syntaxError("Expected a number");
    String s = expr.substring(start, pos);
    if (s.toLowerCase().equals("nan")) return Double.NaN;
    try {
      return Double.valueOf(s);
    } catch (NumberFormatException e) {
      throw syntaxError(e.toString());
    }
  }

  /**
   * Parse a string from the token stream.
   */
  private String parseString() {
    char quote = peek(0);
    int start = ++pos;
    boolean has_escapes = false;
    while (pos < expr.length()) {
      char c = peek(0);
      if (c == '\\') {
        has_escapes = true;
        char cc = peek(1);
        if (simpleEscapeSequences.containsKey(cc)) {
          pos += 2;
        } else if (cc == 'x') {
          pos += 4;   // e.g: \x5A
        } else if (cc == 'u') {
          pos += 6;   // e.g: \u1234
        } else if (cc == 'U') {
          pos += 10;  // e.g: \U0010FFFF
        } else
          throw new IllegalASTException("Invalid escape sequence \\" + cc);
      } else if (c == quote) {
        pos++;
        if (has_escapes) {
          StringBuilder sb = new StringBuilder();
          for (int i = start; i < pos - 1; i++) {
            char ch = expr.charAt(i);
            if (ch == '\\') {
              char cc = expr.charAt(++i);
              if (simpleEscapeSequences.containsKey(cc)) {
                sb.append(simpleEscapeSequences.get(cc));
              } else {
                int n = (cc == 'x')? 2 : (cc == 'u')? 4 : (cc == 'U')? 8 : -1;
                int hex;
                try {
                  hex = StringUtils.unhex(expr.substring(i + 1, i + 1 + n));
                } catch (NumberFormatException e) {
                  throw new IllegalASTException(e.toString());
                }
                if (hex > 0x10FFFF)
                  throw new IllegalASTException("Illegal unicode codepoint " + hex);
                sb.append(Character.toChars(hex));
                i += n;
              }
            } else {
              sb.append(ch);
            }
          }
          return sb.toString();
        } else {
          return expr.substring(start, pos - 1);
        }
      } else {
        pos++;
      }
    }
    throw new IllegalASTException("Unterminated string at " + start);
  }

  /**
   * Return the character at the current ( + {@code offset) parse position
   * without advancing it. If there are no more characters, return ' '.
   */
  private char peek(int offset) {
    int p = pos + offset;
    return p < expr.length()? expr.charAt(p) : ' ';
  }

  /**
   * Advance parse pointer to the first non-whitespace character, and return
   * that character. If such non-whitespace character cannot be found, then
   * return ' '.
   */
  private char nextChar() {
    char c = ' ';
    while (pos < expr.length() && isWhitespace(c = peek(0))) pos++;
    return c;
  }

  /**
   * Consume the next character from the parse stream, throwing an exception
   * if it is not {@code c}.
   */
  private void consumeChar(char c) {
    if (peek(0) != c)
      throw new IllegalASTException("Expected '" + c + "'. Got: '" + peek(0));
    pos++;
  }


  //--------------------------------------------------------------------------------------------------------------------
  // (Private) helpers
  //--------------------------------------------------------------------------------------------------------------------

  /** Return true if {@code c} is a whitespace character. */
  private static boolean isWhitespace(char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r';
  }

  /** Return true if {@code c} is a quote character. */
  private static boolean isQuote(char c) {
    return c == '\'' || c == '\"';
  }

  /** Return true if character {@code c} is a letter (or _). */
  private static boolean isAlpha(char c) {
    return c >= 'a' && c <= 'z' || c == '_' || c >= 'A' && c <= 'Z';
  }

  /** Return true if character {@code c} is a digit. */
  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  /** Return true if {@code c} is an alphanumeric character. */
  private static boolean isAlphaNum(char c) {
    return isAlpha(c) || isDigit(c);
  }


  //  public AstRoot throwErr(String msg) {
  //    int idx = expr.length() - 1;
  //    int lo = pos, hi = idx;
  //
  //    if (idx < lo) {
  //      lo = idx;
  //      hi = lo;
  //    }
  //    String s = msg + '\n' + expr + '\n';
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

  public class CascadeSyntaxError extends RuntimeException {
    public CascadeSyntaxError(String s) {
      super(s);
    }
  }

  private CascadeSyntaxError syntaxError(String message) {
    return this.new CascadeSyntaxError(message);
  }

}
