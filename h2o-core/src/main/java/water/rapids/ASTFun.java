package water.rapids;

import water.util.SB;

import java.util.ArrayList;

/** Define a function
 *  Syntax: { ids... . expr }
 *  IDs are bound within expr
 */
class ASTFun extends AST {
  final String[] _ids;          // Identifier names
  final AST _body;              // The function body

  // If this function is being evaluated, record the arguments and parent
  // lexical scope
  final Val[] _args;            // Evaluated arguments to a function
  final ASTFun _parent;         // Parent lexical scope

  protected ASTFun( Exec e ) { 
    e.xpeek('{');
    ArrayList<String> ids = new ArrayList<>();
    ids.add("");                // 1-based ID list

    while( e.skipWS()!= '.' ) {
      String id = e.token();
      if( !Character.isJavaIdentifierStart(id.charAt(0)) ) throw new Exec.IllegalASTException("variable must be a valid Java identifier: "+id);
      for( char c : id.toCharArray() )
        if( !Character.isJavaIdentifierPart(c) ) throw new Exec.IllegalASTException("variable must be a valid Java identifier: "+id);
      ids.add(id);
    }
    e.xpeek('.');
    _ids = ids.toArray(new String[ids.size()]);
    _body = e.parse();
    _args = null;               // This is a template of an uncalled function
    _parent = null;
    e.skipWS();
    e.xpeek('}');
  }

  @Override public String str() { 
    SB sb = new SB().p('{');
    penv(sb);
    for( String id : _ids )
      sb.p(id).p(' ');
    sb.p(". ").p(_body.toString()).p('}');
    return sb.toString();
  }

  // Print environment
  private void penv( SB sb ) {
    if( _parent != null ) _parent.penv(sb);
    if( _args != null )
      for( int i=1; i<_ids.length; i++ )
        sb.p(_ids[i]).p('=').p(_args[i].toString()).p(' ');
  }

  // Function execution.  Just throw self on stack like a constant.  However,
  // capture the existing global scope.
  @Override
  public Val exec(Env env) { return new ValFun(new ASTFun(this,null,env._scope)); }

  // Expected argument count, plus self
  @Override int nargs() { return _ids.length; }

  // A function applied to arguments
  ASTFun( ASTFun fun, Val[] args, ASTFun parent ) {
    _ids = fun._ids;
    _body = fun._body;
    _parent = parent;
    _args = args;
  }

  // Do a ID lookup, returning the matching argument if found
  Val lookup( String id ) {
    for( int i=1; i<_ids.length; i++ )
      if( id.equals(_ids[i]) )
        return _args[i];        // Hit, return found argument
    return _parent == null ? null : _parent.lookup(id);
  }

  // Apply this function: evaluate all arguments, push a lexical scope mapping
  // the IDs to the ARGs, then evaluate the body.  After execution pop the
  // lexical scope and return the results.
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    // Evaluation all arguments
    Val[] args = new Val[asts.length];
    for( int i=1; i<asts.length; i++ )
      args[i] = stk.track(asts[i].exec(env));
    ASTFun old = env._scope;
    env._scope = new ASTFun(this,args,_parent); // Push a new lexical scope, extended from the old
    
    Val res = stk.untrack(_body.exec(env));

    env._scope = old;           // Pop the lexical scope off (by restoring the old unextended scope)
    return res;
  }
}
