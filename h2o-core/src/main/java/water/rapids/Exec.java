package water.rapids;

import water.fvec.Frame;
import water.fvec.Vec;
import water.nbhm.*;

/**
 * Exec is an interpreter of abstract syntax trees.
 *
 * This file contains the AST parser and parser helper functions.  
 * AST Execution starts in the ASTExec file, but spreads throughout Rapids.
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

  public static Val exec( String rapids ) {
    // Execute a single rapids call in a short-lived session
    Session ses = new Session();
    try {
      AST ast = new Exec(rapids).parse();
      Val val = ses.exec(ast,null);
      // Any returned Frame has it's REFCNT raised by +1, and the end(val) call
      // will account for that, copying Vecs as needed so that the returned
      // Frame is independent of the Session (which is disappearing).
      return ses.end(val);
    } catch( Throwable ex ) {
      throw ses.endQuietly(ex);
    }
  }

  // Compute and return a value in this session.  Any returned frame shares
  // Vecs with the session (is not deep copied), and so must be deleted by the
  // caller (with a Rapids "rm" call) or will disappear on session exit, or is
  // a normal global frame.
  static public Val exec( String rapids, Session ses ) {
    AST ast = new Exec(rapids).parse();
    Val val = ses.exec(ast, null);
    // Any returned Frame has it's REFCNT raised by +1, but is exiting the
    // session.  If it's a global, we simply need to lower the internal refcnts
    // (which won't delete on zero cnts because of the global).  If it's a
    // named temp, the ref cnts are accounted for by being in the temp table.
    if( val.isFrame() ) {
      Frame fr = val.getFrame();
      assert fr._key != null; // No nameless Frame returns, as these are hard to cleanup
      if( ses.FRAMES.containsKey(fr) ) {
        throw water.H2O.unimpl();
      } else {
        ses.addRefCnt(fr, -1);
      }
    }
    return val;
  }

  // Parse an expression
  //   '('   a nested function application expression ')
  //   '{'   a nested function definition  expression '}'
  //   '#'   a double: attached_token
  //   '['   a numeric list expression, till ']'
  //   '"'   a String (double quote): attached_token
  //   "'"   a String (single quote): attached_token
  //   digits: a double
  //   letters or other specials: an ID
  @SuppressWarnings({"fallthrough"})
  public AST parse( ) {
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
    case '-':  return (peek(1)>='0' && peek(1) <='9') ? new ASTNum(this) : new ASTId(this);
    default:  return new ASTId(this);
    }    
  }

  char peek() { return _x < _str.length() ? _str.charAt(_x) : ' '; } // peek ahead
  char peek(int off) { return _x+off < _str.length() ? _str.charAt(_x+off) : ' '; } // peek ahead
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
}
