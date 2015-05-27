package water.currents;

import java.util.ArrayList;
import water.util.SB;

/** Apply A Function.  Basic function execution. */
class ASTExec extends AST {
  final AST[] _asts;
  protected ASTExec( Exec e ) { 
    e.xpeek('(');
    AST ast = e.parse();
    // An eager "must fail at runtime" test.  Not all ASTId's will yield a
    // function, so still need a runtime test.
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

  // Function application.  Execute the first AST and verify that it is a
  // function.  Then call that function's apply method.
  @Override Val exec( Env env ) {
    Val fun = _asts[0].exec(env);
    if( !fun.isFun() )
      throw new IllegalArgumentException("Expected a function but found "+fun.getClass());
    return ((ValFun)fun)._ast.apply(env,_asts);
  }
}
