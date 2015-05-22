package water.currents;

import water.H2O;
import water.MRTask;
import water.currents.Env.*;

/**
 * Exec is an interpreter of abstract syntax trees.
 *
 * Trees have a Lisp-like structure with the following "reserved" special
 * characters:
 *
 *     '('   signals the parser to find a nested expression
 *     '#'   signals the parser to parse a double: attached_token
 *     '%'   signals the parser to parse an ID: attached_token
 *     '"'   signals the parser to parse a String (double quote): attached_token
 *     "'"   signals the parser to parse a String (single quote): attached_token
 *
 * In the above, attached_token signals that the special char has extra chars
 * that must be parsed separately.  These are variable names (in the case of
 * %), doubles (in the case of #), or Strings (in the case of ' and ").
 * 
 * Variables are lexically scoped inside 'let' expressions or at the top-level
 * looked-up in the DKV directly (and must refer to a known type that is valid
 * on the execution stack: Vecs)
 */
public class Exec {
  final String _str;            // Statement to parse and execute
  int _x;                       // Parse pointer

  public Exec(String str) { _str = str; }

  public static Val exec( String str ) throws IllegalArgumentException {
    cluster_init();
    // Parse
    AST ast = AST.parse(new Exec(str));
    // Execute
    Val val = ast.exec(new Env());
    // Results
    return val;
  }


  char peek() { return (char)_str.charAt(_x); } // peek ahead
  // Peek, and throw if not found an expected character
  void xpeek(char c) {
    if( peek() != c )
      throw new IllegalASTException("Expected '"+c+"'. Got: '"+peek()+"'.  unparsed: "+ unparsed() + " ; _x = "+_x);
    _x++;
  }
  // Skip white space, return the 1st non-whitespace char or ' ' if out of text
  char skipWS() {
    char c=' ';
    while( _x < _str.length() && isWS(c=peek()) ) _x++;
    return c;
  }

  // Parse a Val, one of the special tokens above, or a nested expression
  Val val() {
    char c = skipWS();
    switch( c ) {
    case '#':  return new ValNum(this);
    case '\"': return new ValStr(this,'\"');
    case '\'': return new ValStr(this,'\'');
    case '(':  return new ValFun(this);
    case ')':  xpeek('(');      // Will throw, should not be here with leading ')'
    case '%':  _x++;            // ID lookup, optional lead-in char
    default:   return new ValID (this); // ID lookup, lazily done but lexically scoped
    }
  }

  // Parse till whitespace or close-paren
  String token() {
    int start = _x;
    char c;
    while( !isWS(c=peek()) && c!=')' ) _x++;
    return _str.substring(start,_x);
  }

  // Parse till matching
  String match(char c) {
    int start = ++_x;
    while( peek() != c ) _x++;
    _x++;                       // Skip the match
    return _str.substring(start,_x-1);
  }


  // Return unparsed text, useful in error messages
  String unparsed() { return _str.substring(_x,_str.length()-_x); }

  static boolean isWS(char c) { return c==' '; }


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
  private static boolean _inited;       // One-shot init
  static void cluster_init() {
    if( _inited ) return;
    // Touch a common class to force loading
    new MRTask() { @Override public void setupLocal() { new ASTPlus(); } }.doAllNodes();
    _inited = true;
  }
}
