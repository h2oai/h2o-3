package ai.h2o.cascade;

import ai.h2o.cascade.asts.*;
import water.util.CollectionUtils;
import water.util.StringUtils;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;


/**
 * This class handles parsing of a Cascade expression into a Cascade AST.
 * Main language structures are the following:
 *
 * <dl>
 *   <dt><p>{@code (fun val1 ... valN)}</dt>
 *   <dd>Function {@code fun} applied to the provided list of values. Any
 *       expression surrounded in parentheses is considered a function
 *       application, with first token being interpreted as the function
 *       itself, and subsequent tokens as the function's arguments.<br/>
 *       It is also possible to provide names for all (or some) of  the
 *       values: {@code (fun val1 ... argN=valN)}. If such construct is used
 *       then the unnamed arguments should precede any named ones.</dd>
 *
 *   <dt><p>{@code -42.7e+03}</dt>
 *   <dd>Number literal. In addition to standard floating-point literals we
 *       recognize strings {@code "nan"} and {@code "NaN"} as representing
 *       a Not-a-Number value. We do not recognize literals corresponding to
 *       infinite double values.</dd>
 *
 *   <dt><p>{@code [7 4.2 nan -1.78E+3]}</dt>
 *   <dd>List of numbers. The elements of the list may be separated either with
 *       spaces (canonical) or with commas (for convenience).</dd>
 *
 *   <dt><p>{@code "Hello, \"world\"!"}</dt>
 *   <dd>String literal, may be enclosed either in single or in double quotes.
 *       Standard C-style escapes are supported: {@code "\n", "\t", "\r", "\f",
 *       "\b", "\'", "\"", "\\", "\xAA", "\u005Cu1234", "\U0010FFFF"}. Only
 *       {@code "\"} character and quotes have to be escaped, escaping other
 *       characters is optional. For example, you can use a string literal with
 *       newline characters, or ASCII control characters, or Unicode. However
 *       such usage may be outlawed in the future.</dd>
 *
 *   <dt><p>{@code ['one' "two" "thre\u005cu0207"]}</dt>
 *   <dd>List of strings.</dd>
 *
 *   <dt><p>{@code <1:2:-1 3:3 5 7>}</dt>
 *   <dd>Slice list: special notation for writing ranges of numbers compactly.
 *       Individual items within this list are either single numbers, or
 *       pairs {@code base:count}, or triples {@code base:count:stride}. When
 *       either count or stride are not given, they are assumed to be 1.<br/>
 *       Each triple corresponds to the sequence of numbers {@code [base,
 *       base + stride, ..., base + (count-1)*stride]}.<br/>
 *       Within this list only integers (long) are allowed. Additionally,
 *       {@code count}s must be positive, while {@code base}s and
 *       {@code stride}s may be positive, negative, or zero.</dd>
 *
 *   <dt><p>{@code `var1 var2 ... varN *argvars`}</dt>
 *   <dd>List of unevaluated identifiers.</dd>
 * </dl>
 *
 */
public class CascadeParser {
  private String expr;  // Statement to parse and then execute
  private int pos;      // Parse pointer, points to the index of the next character to be consumed


  public CascadeParser(String expresssion) {
    expr = expresssion;
    pos = 0;
  }

  /**
   * Parse a Cascade expression string into an AST object.
   */
  public Ast parse() throws CascadeSyntaxError {
    Ast res = parseNext();
    if (nextChar() != ' ')
      throw syntaxError("illegal Cascade expression");
    return res;
  }


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
    if (ch == '`') {
      return parseIdList();
    }
    if (ch == '<') {
      return parseSliceList();
    }
    if (isQuote(ch)) {
      return new AstStr(parseString());
    }
    if (isDigit(ch) || ch == '-') {
      return new AstNum(parseDouble());
    }
    if (isAlpha(ch)) {
      return new AstId(parseId());
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
    while (nextChar() != ']') {
      strs.add(parseString());
      if (nextChar() == ',') consumeChar(',');
    }
    return new AstStrList(strs);
  }

  /**
   * Parse a plain list of numbers.
   */
  private Ast parseNumList() {
    ArrayList<Double> nums = new ArrayList<>(10);
    while (nextChar() != ']') {
      nums.add(parseDouble());
      if (nextChar() == ',') consumeChar(',');
    }
    return new AstNumList(nums);
  }


  /**
   * Parse a list of numbers with slices/ranges. For example:
   * <pre>{@code
   *   [0, -3, 2:7:5, 3:2, -5:11:-2]
   * }</pre>
   * The format of each "range" token is {@code start:count[:stride]}, and it
   * denotes the sequence (where {@code stride=1} if not given)
   * <pre>{@code
   *   [start, start + stride, ..., start + (count-1)*stride]
   * }</pre>
   * Real numbers cannot be used in this list format. Within each range token
   * {@code count} must be positive, whereas {@code stride} can be either
   * positive or negative.
   *
   * <p> Primary use for this number list is to support indexing into columns/
   * rows of a frame.
   */
  private AstSliceList parseSliceList() {
    consumeChar('<');
    ArrayList<Long> bases = new ArrayList<>(5);
    ArrayList<Long> counts = new ArrayList<>(5);
    ArrayList<Long> strides = new ArrayList<>(5);

    while (nextChar() != '>') {
      long base = parseLong();
      long count = 1;
      long stride = 1;
      if (nextChar() == ':') {
        consumeChar(':');
        count = parseLong();
        if (count <= 0)
          throw syntaxError("Count must be a positive integer, got " + count);
      }
      if (nextChar() == ':') {
        consumeChar(':');
        stride = parseLong();
      }
      // If count is 1 then stride is irrelevant, so we force it to be 1 as well.
      if (count == 1) stride = 1;
      bases.add(base);
      counts.add(count);
      strides.add(stride);
      // Optional comma separating list elements
      if (nextChar() == ',') consumeChar(',');
    }
    consumeChar('>');
    return new AstSliceList(bases, counts, strides);
  }


  /**
   * Parse list of identifiers that will be kept unevaluated. This list takes
   * the form
   * {@code  `var1 var2 ... varN`}
   */
  private AstIdList parseIdList() {
    consumeChar('`');
    ArrayList<String> ids = new ArrayList<>(10);
    String argsId = null;
    while (nextChar() != '`') {
      if (nextChar() == '*') {
        consumeChar('*');
        argsId = parseId();
      } else {
        if (argsId != null)
          throw syntaxError("regular variable cannot follow a vararg variable");
        ids.add(parseId());
      }
      if (nextChar() == ',') consumeChar(',');
    }
    consumeChar('`');
    return new AstIdList(ids, argsId);
  }


  /**
   * Parse an id from the input stream. An id has common interpretation:
   * an alpha character followed by any number of alphanumeric characters.
   */
  private String parseId() {
    int start = pos;
    while (isAlphaNum(peek())) pos++;
    assert pos > start;
    return expr.substring(start, pos);
  }


  /**
   * Parse a number from the token stream.
   */
  private double parseDouble() {
    int start = pos;
    while (validNumberCharacters.contains(peek())) pos++;
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
   * Parse a (long) integer from the token stream.
   */
  private long parseLong() {
    nextChar();
    int start = pos;
    if (peek() == '-') pos++;
    while (isDigit(peek())) pos++;
    if (start == pos) throw syntaxError("Missing a number");
    String s = expr.substring(start, pos);
    try {
      return Long.parseLong(s);
    } catch (NumberFormatException e) {
      throw syntaxError(e.toString());
    }
  }


  /**
   * Parse a string from the token stream.
   */
  private String parseString() {
    char quote = peek();
    int start = ++pos;
    boolean has_escapes = false;
    while (pos < expr.length()) {
      char c = peek();
      if (c == '\\') {
        has_escapes = true;
        pos++;
        char cc = peek();
        if (simpleEscapeSequences.containsKey(cc)) {
          pos += 1;
        } else if (cc == 'x') {
          pos += 3;   // e.g: \x5A
        } else if (cc == 'u') {
          pos += 5;   // e.g: \u1234
        } else if (cc == 'U') {
          pos += 9;   // e.g: \U0010FFFF
        } else
          throw syntaxError("Invalid escape sequence \\" + cc);
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
                  throw syntaxError(e.toString());
                }
                if (hex > 0x10FFFF)
                  throw syntaxError("Illegal unicode codepoint " + hex);
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
    throw syntaxError("Unterminated string at " + start);
  }


  //--------------------------------------------------------------------------------------------------------------------
  // (Private) helpers
  //--------------------------------------------------------------------------------------------------------------------

  /**
   * Return a character at the current parse position without advancing it.
   * If current position is at the end of the input, return a space.
   */
  private char peek() {
    return pos < expr.length()? expr.charAt(pos) : ' ';
  }

  /**
   * Advance parse pointer to the first non-whitespace character, and return
   * that character. If all remaining input characters are whitespace, then
   * advance parse position to the end of the input and return a space.
   */
  private char nextChar() {
    char c = ' ';
    while (pos < expr.length() && isWhitespace(c = peek())) pos++;
    return c;
  }

  /**
   * Consume the next character from the parse stream, throwing an exception
   * if it is not {@code c}.
   */
  private void consumeChar(char c) {
    if (peek() != c)
      throw syntaxError("Expected '" + c + "'. Got: '" + peek());
    pos++;
  }

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


  /**
   * This exception is thrown whenever a Cascade expression cannot be parsed
   * correctly.
   */
  public class CascadeSyntaxError extends RuntimeException {
    public CascadeSyntaxError(String s) {
      super(s);
    }

    public String expr() {
      return expr;
    }

    public int errorPos() {
      return pos;
    }
  }

  /**
   * Usage: {@code throw syntaxError("error message")}.
   */
  private CascadeSyntaxError syntaxError(String message) {
    return this.new CascadeSyntaxError(message);
  }

}
