package water.currents;

import water.MRTask;
import water.fvec.Frame;
import water.fvec.Vec;

/**
 * Exec is an interpreter of abstract syntax trees.
 *
 * Trees have a Lisp-like structure with the following "reserved" special
 * characters:
 *
 *     '('   a nested function application expression ')
 *     '{'   a nested function definition  expression '}'
 *     '#'   a double: attached_token
 *     '['   a numeric or string list expression, till ']'
 *     '%'   an ID: attached_token
 *     '"'   a String (double quote): attached_token
 *     "'"   a String (single quote): attached_token
 *     digits: a double
 *     letters or other specials: an ID
 *
 * In the above, attached_token signals that the special char has extra chars
 * that must be parsed separately.  These are variable names (in the case of
 * %), doubles (in the case of #), Strings (in the case of ' and "), or number
 * lists (in the case of '[' till ']')
 * 
 * Variables are lexically scoped inside 'let' expressions or at the top-level
 * looked-up in the DKV directly (and must refer to a known type that is valid
 * on the execution stack)
 */
public class Exec {
  final String _str;            // Statement to parse and execute
  int _x;                       // Parse pointer

  public Exec(String str) { _str = str; }

  public static Val exec( String str ) throws IllegalArgumentException {
    cluster_init();
    // Parse
    AST ast = new Exec(str).parse();
    // Execute
    Env env = new Env();
    Val val = ast.exec(env);
    // Results.  Deep copy returned Vecs.  Always return a key-less Frame
    if( val.isFrame() ) {
      Frame fr = val.getFrame();
      if( fr._key != null ) val=new ValFrame(fr = new Frame(null,fr.names(),fr.vecs()));
      Vec vecs[] = fr.vecs();
      for( int i=0; i<vecs.length; i++ )
        if( env.isPreExistingGlobal(vecs[i]) )
          fr.replace(i,vecs[i].makeCopy());
    }

    return val;
  }


  // Parse an expression
  //   '('   a nested function application expression ')
  //   '{'   a nested function definition  expression '}'
  //   '#'   a double: attached_token
  //   '['   a numeric list expression, till ']'
  //   '%'   an ID: attached_token
  //   '"'   a String (double quote): attached_token
  //   "'"   a String (single quote): attached_token
  //   digits: a double
  //   letters or other specials: an ID
  @SuppressWarnings({"fallthrough"}) 
  AST parse( ) {
    switch( skipWS() ) {
    case '(':  return new ASTExec(this); // function application
    case '{':  return new ASTFun(this);  // function definition
    case '#':  _x++;                     // Skip before double, FALL THRU
    case '0':  case '1':  case '2':  case '3':  case '4':
    case '5':  case '6':  case '7':  case '8':  case '9':
      return new ASTNum(this);
    case '\"': return new ASTStr(this,'\"');
    case '\'': return new ASTStr(this,'\'');
    case '[':  return isQuote(xpeek('[').skipWS()) ? new ASTStrList(this) : new ASTNumList(this);
    case ' ':  throw new IllegalASTException("Expected an expression but ran out of text");
    case '%':  _x++;             // Skip before ID, FALL THRU
    default:  return new ASTId(this);
    }    
  }

  char peek() { return _x < _str.length() ? _str.charAt(_x) : ' '; } // peek ahead
  // Peek, and throw if not found an expected character
  Exec xpeek(char c) {
    if( peek() != c )
      throw new IllegalASTException("Expected '"+c+"'. Got: '"+peek()+"'.  unparsed: "+ unparsed() + " ; _x = "+_x);
    _x++;
    return this;                // Flow coding
  }
  // Skip white space, return the 1st non-whitespace char or ' ' if out of text
  char skipWS() {
    char c=' ';
    while( _x < _str.length() && isWS(c=peek()) ) _x++;
    return c;
  }

  // Parse till whitespace or close-paren
  String token() {
    int start = _x;
    char c;
    while( !isWS(c=peek()) && c!=')' && c!='}') _x++;
    if( start == _x ) throw new IllegalArgumentException("Missing token");
    return _str.substring(start,_x);
  }

  // Parse while number-like, and return the number
  double number() {
    int start = _x;
    char c;
    while( !isWS(c=peek()) && c!=')' && c!=']' && c!=',' && c!=':' ) _x++;
    return Double.valueOf(_str.substring(start,_x));
  }

  // Parse till matching
  String match(char c) {
    int start = ++_x;
    while( peek() != c ) _x++;
    _x++;                       // Skip the match
    return _str.substring(start,_x-1);
  }

  // Return unparsed text, useful in error messages and debugging
  String unparsed() { return _str.substring(_x,_str.length()); }

  static boolean isWS(char c) { return c==' '; }
  static boolean isQuote(char c) { return c=='\'' || c=='\"'; }


  AST throwErr( String msg ) {
    int idx = _str.length()-1;
    int lo = _x, hi=idx;

    String str = _str;
    if( idx < lo ) { lo = idx; hi=lo; }
    String s = msg+ '\n'+str+'\n';
    int i;
    for( i=0; i<lo; i++ ) s+= ' ';
    s+='^'; i++;
    for( ; i<hi; i++ ) s+= '-';
    if( i<=hi ) s+= '^';
    s += '\n';
    throw new IllegalASTException(s);
  }
  static class IllegalASTException extends IllegalArgumentException { IllegalASTException(String s) {super(s);} }

  // To avoid a class-circularity hang, we need to force other members of the
  // cluster to load the Exec & AST classes BEFORE trying to execute code
  // remotely, because e.g. ddply runs functions on all nodes.
  private static volatile boolean _inited; // One-shot init
  static void cluster_init() {
    if( _inited ) return;
    // Touch a common class to force loading
    new MRTask() { @Override public void setupLocal() { new ASTPlus(); } }.doAllNodes();
    _inited = true;
  }
}
