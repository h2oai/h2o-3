package water.currents;

import water.H2O;

// Apply a function
class ASTExec extends AST {
  protected ASTExec( Val[] vals ) { super(vals); }
  @Override public String toString() { return "fun"; }
  // Default execution pattern for most things: evaluate all arguments and push
  // them on the stack in reverse order, with the function last.  Then pop and
  // apply the function.
  @Override Val exec( Env env ) {
    try( Env.StackHelp stk = env.stk()) {
        for( Val val : _vals )
          stk.push(val.exec(env)); // Args are evaled and pushed on stack in order
        Val fun = env.peek(-_vals.length);
        if( !fun.isFun() )
          throw new Exec.IllegalASTException("Expected a function but found "+fun.getClass());
        // Validate stack depth: called primitive function pulls _vals.length
        //return ((ValFun)fun)._ast.apply(env,_vals.length);
        throw H2O.unimpl();
      }
  }
}
