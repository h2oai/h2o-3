//package water.cascade;
//
//
//import water.H2O;
//import water.Iced;
////import water.cascade.Program.*;
//
///**
// * An interpreter.
// *
// * Note the (no longer tacit) assumption that when local is null, ScratchWorkution is assumed to be in the global environment.
// * And when local is not null, ScratchWorkution is assumed to be in the local environment *only* -- the global environment is
// * assumed to be read-only (can only peek and peekAt) in this case.
// */
//
//public class ScratchWork extends Iced {
//  //parser
//  final byte[] _ast;
//  int _x;
//
////  final ArrayList<Env> _display;  // A list of scopes, idx0 is the global scope.
////  final AST2IR _ast2ir;           // The set of instructions for each Program.
//
//  //  public ScratchWork make(JsonObject ast) { return new ScratchWork(ast); }
//  public ScratchWork(String ast) {
//    _ast = ast == null ? null : ast.getBytes();
////    _display = new ArrayList<>();
////    _ast2ir = new AST2IR(ast); _ast2ir.make();
////    _display.add(new Env(_ast2ir.getLocked()));
//  }
//
//  protected AST parse() {
//    //take a '('
//    String tok = xpeek('(').parseID();
//    //lookup of the token
//    AST ast = AST.SYMBOLS.get(tok); // hash table declared here!
//    assert ast != null : "Failed lookup on token: "+tok;
//    return ast.parse_impl(this);
//  }
//
//  String parseID() {
//    StringBuilder sb = new StringBuilder();
//    while(_ast[_x] != ' ' && _ast[_x] != ')') { // isWhiteSpace...
//      sb.append((char)_ast[_x++]);
//    }
//    _x++;
//    return sb.toString();
//  }
//
//  ScratchWork xpeek(char c) {
//    assert _ast[_x] == c : "Expected '"+c+"'. Got: '"+(char)_ast[_x]+"'"; _x++; return this;
//  }
//
//  String rest() {
//    return new String(_ast,_x, _ast.length-_x);
//  }
//
////  public Object runMain() {
////    Env global = _display.get(0);
////    Program main = _ast2ir.program()[0];
////    for (Statement s : main ) {
////      processInstruction(s, global, null);
////    }
////    Frame fr = (Frame) global.pop();
////    return fr;
////  }
//
//  private void runCall(String call_name, Env global) { throw H2O.unimpl(); }
//
//  // Apply: ScratchWorkute all arguments (including the function argument) yielding
//  // the function itself, plus all normal arguments on the stack.  Then ScratchWorkute
//  // the function, which is responsible for popping all arguments and pushing
//  // the result.
//  private void runOp(String op, Env global, Env local) { ASTOp.get(op).apply(global, local); }
//
////  private void processInstruction(Statement s, Env global, Env local) {
////    switch(s.kind()) {
////      case Program.OP: runOp(s.op(), global, local); break;
////      case Program.CALL: runCall(s.name(), global); break;
////      case Program.PUSH: {
////        if (local != null) {
////          // TODO: Here is where to use symbol tables to lookup s.value() (look in local.
////          local.push(s.value());
////        } else global.push(s.value()); break;
////      }
////    }
////  }
//}