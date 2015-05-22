package water.currents;

import water.H2O;
import water.currents.Env.*;

// Apply a function
class ASTExec extends AST {
  protected ASTExec( Val[] vals ) { super(vals); }
  // Default execution pattern for most things: evaluate all arguments and push
  // them on the stack in reverse order, with the function last.  Then pop and
  // apply the function.
  @Override Val exec( Env env ) {
    try( Env.StackHelp stk = env.stk()) {
        for( int i=_vals.length-1; i>=0; i-- ) {
          Val val = _vals[i];
          stk.push(val.isID() ? lookup(val) : val);
        }
        Val fun = stk.pop();
        if( !fun.isFun() )
          throw new Exec.IllegalASTException("Expected a function but found "+fun.getClass());
        throw H2O.unimpl();
      }
  }
}
