package water.rapids;


import water.Iced;
import water.Key;
import water.MRTask;

import java.util.HashSet;

/**
 * Exec is an interpreter of abstract syntax trees.
 *
 * Trees have a Lisp-like structure with the following "reserved" special characters:
 *
 *     '('   signals the parser to parse a function name, the next token is an identifier or a (single char) flag
 *     '#'   signals the parser to parse a double: attached_token
 *     '"'   signals the parser to parse a String (double quote): attached_token
 *     "'"   signals the parser to parse a String (single quote): attached_token
 *     '$'   signals a variable lookup: attached_token
 *     '!'   signals a variable set: attached_token
 *     '['   signals a column slice by index - R handles all named to int conversions (as well as 1-based to 0-based)
 *     'def' signals the parser to a parse a function: (def name args body).
 *     '='   signals the parser to assign the RHS to the LHS.
 *     'g'   signals &gt;
 *     'G'   signals &gt;=
 *     'l'   signals &lt;
 *     'L'   signals &lt;=
 *     'n'   signals ==
 *     'N'   signals !=
 *     '_'   signals negation (!)
 *     '{'   signals the parser to begin parsing a ';'-separated array of flagged inputs (#, $, ", ') (ASTSeries is the resulting AST)
 *
 * In the above, attached_token signals that the special char has extra chars that must be parsed separately. These are
 * variable names (in the case of $ and !), doubles (in the case of #), or Strings (in the case of ' and ").
 *
 * Everything else is a function call (prefix/infix/func) and has a leading char of '('.
 */
public class Exec extends Iced {

  //parser
  final byte[] _ast;
  final String _str;
  int _x;

  //global env
  final Env _env;

  public Exec(String ast, Env env) {
    _str = ast;
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
      if (ex.skipWS().hasNext()) throwErr("Note that only a single statement can be processed at a time. Junk at the end of the statement: ",ex);

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

  public static void new_func(final String str) throws IllegalArgumentException {
    cluster_init();

    new MRTask() {
      @Override public void setupLocal() {
        HashSet<Key> locked = new HashSet<>();
        Env env = new Env(locked);

        // Some global constants
        env.put("TRUE",  Env.NUM, "1"); env.put("T", Env.NUM, "1");
        env.put("FALSE", Env.NUM, "0"); env.put("F", Env.NUM, "0");
        env.put("NA",  Env.NUM, Double.toString(Double.NaN));
        env.put("Inf", Env.NUM, Double.toString(Double.POSITIVE_INFINITY));
        Exec ex = new Exec(str, env);
        ex.parse_fun();
      }
    }.doAllNodes();
  }

  protected AST parse() {
    skipWS();
    // Parse a token --> look for a function or a special char.
    if (!hasNext()) throw new IllegalArgumentException("End of input unexpected. Badly formed AST.");
    String tok = parseID();
    if (!hasNext()) throw new IllegalArgumentException("End of input unexpected. Badly formed AST.");
    //lookup of the token
    AST ast = lookup(tok);
    return ast.parse_impl(this);
  }

  protected void parse_fun() {
    // parse a token -> should be "def"
    String tok = parseID();
    if (!tok.equals("def")) throw new IllegalArgumentException("Expected function definition but got "+tok);
    ASTFuncDef ast = new ASTFuncDef();
    ast.parse_func(this);
  }

  private AST lookup(String tok) {
    AST sym = ASTOp.SYMBOLS.get(tok);
    if (sym != null) return sym;
    sym = ASTOp.UDF_OPS.get(tok);
    if (sym != null) return sym;
    throw new IllegalArgumentException("*Unimplemented* failed lookup on token: `"+tok+"`. Contact support@0xdata.com for more information.");
  }

  String parseID() {
    StringBuilder sb = new StringBuilder();
    if (peek() == '(') {_x++; skipWS(); return parseID(); } // eat the '(' and any ws.
    if ( isSpecial(peek())) { return sb.append((char)_ast[_x++]).toString(); } // if attached_token, then use parse_impl
    while(_x < _ast.length && _ast[_x] != ' ' && _ast[_x] != ')' && _ast[_x] != ';') {  // while not WS...
      sb.append((char)_ast[_x++]);
    }
    _x++; // skip a WS
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

  boolean hasNext() { return _x < _ast.length; }
  boolean hasNextStmnt() {
    if (hasNext()) {
      if (_x+1 >= _ast.length) return false;
      if (_x+2 >= _ast.length) return false;
      if (_ast[_x] == ';' && _ast[_x+1] == ';' && _ast[_x+2] == ';') return false; // end of all statements == ;;;
      return true;
    }
    return false;
  }

  double nextDbl() { return ((ASTNum) this.skipWS().parse()).dbl(); }
  String nextStr() { return ((ASTString) this.skipWS().parse())._s; }

  Exec xpeek(char c) {
    assert _ast[_x] == c : "Expected '"+c+"'. Got: '"+(char)_ast[_x]+"'. unparsed: "+ unparsed() + " ; _x = "+_x;
    _x++; return this;
  }

  char ppeek() { return (char)_ast[_x-1];}  // past peek
  char peek() { return (char)_ast[_x]; }    // ppek ahead
  char peekPlus() { return (char)_ast[_x++]; } // peek and move ahead

  Exec skipWS() {
    while (true) {
      if (_x >= _ast.length) break;
      if (peek() == ' ' || peek() == ')' || peek() == ';') {
        _x++;
        continue;
      }
      break;
    }
    return this;
  }

  Exec skipEOS() {
    while (true) {
      if (_x >= _ast.length) break;
      if (peek() == ';' || peek() == ' ' || peek() == ')') {
        _x++;
        continue;
      }
      break;
    }
    return this;
  }

  Exec rewind() {
    while (true) {
      if (_x <= 0) { _x = 0; break; }
      if (!(ppeek() == ' ' || ppeek() == ')' || ppeek() == ';')) {
        _x--;
        continue;
      }
      break;
    }
    return this;
  }

  boolean isSpecial(char c) { return c == '\"' || c == '\'' || c == '#' || c == '!' || c == '$' || c =='{'; }

  String unparsed() { return new String(_ast,_x,_ast.length-_x); }

  static AST throwErr( String msg, Exec E) {
    int idx = E._ast.length-1;
    int lo = E._x, hi=idx;

    String str = E._str;
    if( idx < lo ) { lo = idx; hi=lo; }
    String s = msg+ '\n'+str+'\n';
    int i;
    for( i=0; i<lo; i++ ) s+= ' ';
    s+='^'; i++;
    for( ; i<hi; i++ ) s+= '-';
    if( i<=hi ) s+= '^';
    s += '\n';
    throw new IllegalArgumentException(s);
  }

  // To avoid a class-circularity hang, we need to force other members of the
  // cluster to load the Exec & AST classes BEFORE trying to execute code
  // remotely, because e.g. ddply runs functions on all nodes.
  private static boolean _inited;       // One-shot init
  static void cluster_init() {
    if( _inited ) return;
    new MRTask() {
      @Override public void setupLocal() {
        new ASTPlus(); // Touch a common class to force loading
      }
    }.doAllNodes();
    _inited = true;
  }
}