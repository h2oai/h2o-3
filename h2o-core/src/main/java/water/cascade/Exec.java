package water.cascade;


import water.Iced;
import water.Key;
import water.MRTask;

import java.util.HashSet;

/**
 * Exec is an interpreter of abstract syntax trees.
 *
 * Trees have a Lisp-like structure with the following "reserved" special characters:
 *
 *     '('  signals the parser to begin a function application, next token is an identifier or a (single char) flag
 *     '#'  signals the parser to parse a double: attached_token
 *     '"'  signals the parser to parse a String (double quote): attached_token
 *     "'"  signals the parser to parse a String (single quote): attached_token
 *     '$'  signals a variable lookup: attached_token
 *     '!'  signals a variable set: attached_token
 *     '['  signals a column slice by index -> R handles all named to int conversions (as well as 1-based to 0-based)
 *     'f'  signals the parser to a parse a function: (f ( args ) ( body ).
 *     '='  signals the parser to assign the RHS to the LHS.
 *     'g'  signals >
 *     'G'  signals >=
 *     'l'  signals >
 *     'L'  signals >=
 *     'n'  signals ==
 *     'N'  signals !=
 *     '_'  signals negation (!)
 *     '{'  signals the parser to begin parsing a ';'-separated array of things (ASTSeries is the resulting AST)
 *
 * In the above, attached_token signals that the special char has extra chars that must be parsed separately. These are
 * variable names (in the case of $ and !), doubles (in the case of #), or Strings (in the case of ' and ").
 *
 * Everything else is a function call (prefix/infix/func) and has a leading char of '('.
 */
public class Exec extends Iced {

  //parser
  final byte[] _ast;
  int _x;

  //global env
  final Env _env;

  public Exec(String ast, Env env) {
    _ast = ast == null ? null : ast.getBytes();
    _env = env;
  }

  public static Env exec( String str ) throws IllegalArgumentException {
    cluster_init();
    // Preload the global environment from existing Frames
    HashSet<Key> locked = new HashSet<>();
    Env env = new Env(locked);

    // Some global constants
    env.put("TRUE",  Env.NUM, "1"); env.put("T", Env.NUM, "1");
    env.put("FALSE", Env.NUM, "0"); env.put("F", Env.NUM, "0");
    env.put("NA",  Env.NUM, Double.toString(Double.NaN));
    env.put("Inf", Env.NUM, Double.toString(Double.POSITIVE_INFINITY));

    try {
      Exec ex = new Exec(str, env);

      // Parse
      AST ast = ex.parse();

      // Execute
      env = ast.treeWalk(env);

      // Write back to DKV (if needed) and return
      env.postWrite();

    } catch( RuntimeException t ) {
      env.remove_and_unlock();
      throw t;
    }
    return env;
  }

  protected AST parse() {
    // Parse a token --> look for a function or a special char.
    String tok = parseID();
    //lookup of the token
    AST ast = ASTOp.SYMBOLS.get(tok);
    if (ast == null) throw new IllegalArgumentException("*Unimplemented* failed lookup on token: `"+tok+"`. Contact support@0xdata.com for more information.");
    return ast.parse_impl(this);
  }

  String parseID() {
    StringBuilder sb = new StringBuilder();
    if (peek() == '(') {_x++; return parseID(); } // eat the '('
    if ( isSpecial(peek())) { return sb.append((char)_ast[_x++]).toString(); } // if attached_token, then use parse_impl
    while(_ast[_x] != ' ' && _ast[_x] != ')') {  // while not WS...
      sb.append((char)_ast[_x++]);
    }
    _x++;
    return sb.toString();
  }

  String parseString(char eq) {
    StringBuilder sb = new StringBuilder();
    while(_ast[_x] != eq) {
      sb.append((char)_ast[_x++]);
    }
    _x++;
    return sb.toString();
  }

  Exec xpeek(char c) {
    assert _ast[_x] == c : "Expected '"+c+"'. Got: '"+(char)_ast[_x]+"'. unparsed: "+ unparsed() + " ; _x = "+_x;
    _x++; return this;
  }

  char peek() { return (char)_ast[_x]; }
  char peekPlus() { return (char)_ast[_x++]; }

  Exec skipWS() {
    while (true) {
      if (_x >= _ast.length) break;
      if (peek() == ' ' || peek() == ')') {
        _x++;
        continue;
      }
      break;
    }
    return this;
  }

  private boolean isSpecial(char c) { return c == '\"' || c == '\'' || c == '#' || c == '!' || c == '$' || c =='{'; }

  String unparsed() { return new String(_ast,_x,_ast.length-_x); }

  // To avoid a class-circularity hang, we need to force other members of the
  // cluster to load the Exec & AST classes BEFORE trying to execute code
  // remotely, because e.g. ddply runs functions on all nodes.
  private static boolean _inited;       // One-shot init
  static void cluster_init() {
    if( _inited ) return;
    new ASTPlus();
    new MRTask() {
      @Override public void setupLocal() {
        new ASTPlus(); // Touch a common class to force loading
        tryComplete();
      }
    }.doAllNodes();
    _inited = true;
  }
}