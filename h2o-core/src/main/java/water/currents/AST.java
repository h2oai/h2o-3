package water.currents;

import java.util.ArrayList;

abstract class AST {
  final Val[] _vals;
  protected AST( Val[] vals ) { _vals = vals; }

  // Subclasses define their execution
  abstract Val exec( Env env );

  // Parse a subtree
  static AST parse( Exec exec ) {
    exec.skipWS();
    exec.xpeek('(');
    Val val = exec.val();
    if( !val.isFun() && !val.isID() )
      exec.throwErr("Expected a function but found a "+val.getClass());
    ArrayList<Val> vals = new ArrayList<>();
    vals.add(0,val);
    while( exec.skipWS() != ')' )
      vals.add(exec.val());
    exec.xpeek(')');
    return new ASTExec(vals.toArray(new Val[vals.size()]));
  }
}

