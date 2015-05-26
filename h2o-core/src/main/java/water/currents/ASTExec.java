package water.currents;

import water.H2O;
import water.util.SB;

import java.util.ArrayList;

// Apply a function
class ASTExec extends AST {
  final AST[] _asts;
  protected ASTExec( Exec e ) { 
    e.xpeek('(');
    AST ast = e.parse();
    if( !ast instanceof ASTExec && !ast instanceof ASTId )
      e.throwErr("Expected a function but found a "+ast.getClass());
    ArrayList<AST> asts = new ArrayList<>();
    asts.add(0,ast);
    while( e.skipWS() != ')' )
      asts.add(e.ast());
    e.xpeek(')');
    _asts = asts.toArray(new AST[asts.size()]);
  }

  @Override public String toString() { 
    SB sb = new SB().p('(');
    for( Ast ast : _asts )
      sb.p(ast.toString()).p(' ');
    return sb.p(')').toString();
  }
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
