package water.cascade;


import water.H2O;
import water.Iced;
//import water.cascade.Program.*;

/**
 * An interpreter.
 *
 * Parse an AST and then execute the AST by walking it.
 *
 * Parsing:
 *
 *   Trees are defined in the following way:
 *
 *   '(' begins a tree node
 *   ')' ends   a tree node
 *   '#'   signals the parser to parse a double
 *   '_s_' signals the parser to parse a string
 *   '$'   signals a named column selection
 *   '['   signals a column slice by index  -> R shalt replace column names with their indexes!
 *   '_a_' signals the parser to assign the RHS to the LHS.
 *   '_f_' signals the parser to a parse a UDF.
 */

public class Exec extends Iced {

  //parser
  final byte[] _ast;
  int _x;

  //  public Exec make(JsonObject ast) { return new Exec(ast); }
  public Exec(String ast) {
    _ast = ast == null ? null : ast.getBytes();
//    _display = new ArrayList<>();
//    _ast2ir = new AST2IR(ast); _ast2ir.make();
//    _display.add(new Env(_ast2ir.getLocked()));
  }

  protected AST parse() {
    // take a '('
    String tok = xpeek('(').parseID();
    //lookup of the token
    AST ast = AST.SYMBOLS.get(tok); // hash table declared here!
    assert ast != null : "Failed lookup on token: "+tok;
    return ast.parse_impl(this);
  }

  String parseID() {
    StringBuilder sb = new StringBuilder();
    while(_ast[_x] != ' ' && _ast[_x] != ')') { // isWhiteSpace...
      sb.append((char)_ast[_x++]);
    }
    _x++;
    return sb.toString();
  }

  Exec xpeek(char c) {
    assert _ast[_x] == c : "Expected '"+c+"'. Got: '"+(char)_ast[_x]+"'";
    _x++; return this;
  }

  String unparsed() {
    return new String(_ast,_x,_ast.length-_x);
  }
}