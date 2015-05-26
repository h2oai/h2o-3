package water.currents;

import water.util.SB;

import java.util.ArrayList;

// Apply a function
class ASTExec extends AST {
  final AST[] _asts;
  protected ASTExec( Exec e ) { 
    e.xpeek('(');
    AST ast = e.parse();
    if( !(ast instanceof ASTExec) && !(ast instanceof ASTId) )
      e.throwErr("Expected a function but found a "+ast.getClass());
    ArrayList<AST> asts = new ArrayList<>();
    asts.add(0,ast);
    while( e.skipWS() != ')' )
      asts.add(e.parse());
    e.xpeek(')');
    _asts = asts.toArray(new AST[asts.size()]);
  }

  @Override public String str() { 
    SB sb = new SB().p('(');
    for( AST ast : _asts )
      sb.p(ast.toString()).p(' ');
    return sb.p(')').toString();
  }

  // Default execution pattern for most things: evaluate all arguments and push
  // them on the stack in reverse order, with the function last.  Then pop and
  // apply the function.
  @Override Val exec( Env env ) {
    Val fun = _asts[0].exec(env);
    if( !fun.isFun() )
      throw new IllegalArgumentException("Expected a function but found "+fun.getClass());
    return ((ValFun)fun)._ast.apply(env,_asts);
  }
}
