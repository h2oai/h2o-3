package water.currents;

import java.util.HashMap;

abstract class AST {
  // Subclasses define their execution
  abstract Val exec( Env env );
  abstract public String toString();

  // Built-in primitives, done after other namespace lookups happen
  static final HashMap<String,AST> PRIMS = new HashMap<>();
  static void init(AST ast ) { PRIMS.put(ast.toString(),ast); }
}

class ASTNum extends AST {
  final ValNum _d;
  ASTNum( Exec e ) { _d = new ValNum(Double.valueOf(e.token())); }
  @Override public String toString() { return _d.toString(); }
  @Override Val exec( Env env ) { return _d; }
}

class ASTStr extends AST {
  final ValStr _str;
  ASTStr(Exec e, char c) { _str = new ValStr(e.match(c)); }
  @Override public String toString() { return _str.toString(); }
  @Override Val exec(Env env) { return _str; }
}

class ASTId extends AST {
  final String _id;
  ASTId(Exec e) { _id = e.token(); }
  @Override public String toString() { return _id.toString(); }
  @Override Val exec(Env env) { return env.lookup(_id); }
}
