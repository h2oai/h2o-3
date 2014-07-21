package water.cascade;


import com.google.gson.JsonObject;
import water.H2O;
import water.Iced;
import java.util.ArrayList;
import water.cascade.Program.*;
import water.fvec.Frame;

/**
 * An interpreter.
 *
 * Note the (no longer tacit) assumption that when local is null, execution is assumed to be in the global environment.
 * And when local is not null, execution is assumed to be in the local environment *only* -- the global environment is
 * assumed to be read-only (can only peek and peekAt) in this case.
 */

public class Exec extends Iced {

  final ArrayList<Env> _display;  // A list of scopes, idx0 is the global scope.
  final AST2IR _ast2ir;           // The set of instructions for each Program.

//  public Exec make(JsonObject ast) { return new Exec(ast); }
  public Exec(JsonObject ast) {
    _display = new ArrayList<>();
    _ast2ir = new AST2IR(ast); _ast2ir.make();
    _display.add(new Env(_ast2ir.getLocked()));
  }

  public Object runMain() {
    Env global = _display.get(0);
    Program main = _ast2ir.program()[0];
    for (Statement s : main ) {
      processInstruction(s, global, null);
    }
    Frame fr = (Frame) global.pop();
    return fr;
  }

  private void runCall(String call_name, Env global) { throw H2O.unimpl(); }

  // Apply: execute all arguments (including the function argument) yielding
  // the function itself, plus all normal arguments on the stack.  Then execute
  // the function, which is responsible for popping all arguments and pushing
  // the result.
  private void runOp(String op, Env global, Env local) { ASTOp.get(op).apply(global, local); }

  private void processInstruction(Statement s, Env global, Env local) {
    switch(s.kind()) {
      case Program.OP: runOp(s.op(), global, local); break;
      case Program.CALL: runCall(s.name(), global); break;
      case Program.PUSH: {
        if (local != null) {
          // TODO: Here is where to use symbol tables to lookup s.value() (look in local.
          local.push(s.value());
        } else global.push(s.value()); break;
      }
    }
  }
}