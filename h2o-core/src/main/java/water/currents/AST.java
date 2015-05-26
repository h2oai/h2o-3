package water.currents;

import java.util.HashMap;

abstract class AST {
  // Subclasses define their execution.
  // Constants like Numbers & Strings just return a ValXXX.
  // Constant functions also just return a ValFun.

  // ASTExec is Function application, and evaluates the 1st arg and calls apply
  // to evaluate the remaining arguments.  Usually "apply" is just "exec all
  // args" then apply a primitive function op to the args, but for logical
  // AND/OR and IF statements, one or more arguments may never be evaluated.
  abstract Val exec( Env env );
  Val apply( Env env, AST asts[] ) { throw water.H2O.fail(); }
  abstract String str();
  @Override public String toString() { return str(); }

  // Built-in primitives, done after other namespace lookups happen
  static final HashMap<String,AST> PRIMS = new HashMap<>();
  static void init(AST ast) { PRIMS.put(ast.str(),ast); }
  static {
    // Math ops
    init(new ASTAnd ());
    init(new ASTDiv ());
    init(new ASTMul ());
    init(new ASTOr  ());
    init(new ASTPlus());
    init(new ASTSub ());

    // Relational
    init(new ASTGE());
    init(new ASTGT());
    init(new ASTLE());
    init(new ASTLT());
    init(new ASTEQ());
    init(new ASTNE());

    // Logical - includes short-circuit evaluation
    init(new ASTLAnd());
    init(new ASTLOr());
  }
}

class ASTNum extends AST {
  final ValNum _d;
  ASTNum( Exec e ) { _d = new ValNum(Double.valueOf(e.token())); }
  @Override public String str() { return _d.toString(); }
  @Override Val exec( Env env ) { return _d; }
}

class ASTStr extends AST {
  final ValStr _str;
  ASTStr(Exec e, char c) { _str = new ValStr(e.match(c)); }
  @Override public String str() { return _str.toString(); }
  @Override Val exec(Env env) { return _str; }
}

class ASTId extends AST {
  final String _id;
  ASTId(Exec e) { _id = e.token(); }
  @Override public String str() { return _id; }
  @Override Val exec(Env env) { return env.lookup(_id); }
}
