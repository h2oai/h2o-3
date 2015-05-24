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
        Val fun = _vals[0].exec(env);
        if( !fun.isFun() )
          throw new Exec.IllegalASTException("Expected a function but found "+fun.getClass());
        int oldsp = env.sp();
        for( int i=1; i<_vals.length; i++ )
          stk.push(_vals[i].exec(env)); // Args are evaled and pushed on stack in order
        Val res = ((ValFun)fun)._ast.exec(env);
        assert oldsp==env.sp();
        return res;
      }
  }
}
