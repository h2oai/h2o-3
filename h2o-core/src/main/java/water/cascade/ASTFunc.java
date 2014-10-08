package water.cascade;

import water.H2O;

import java.util.ArrayList;

/**
 *  The ASTFunc Object
 *
 *  An ASTFunc pulls the function ast produced by the front-end and creates a reference to this function.
 *
 *  A function has a body (which may be empty), and a body is a list of statements.
 *
 *  Statements that are possible:
 *
 *  if statements
 *  else statements
 *  for statements
 *  while statements
 *  switch statement
 *  declarative statements
 *  operative statements
 *  return statements
 *
 *  The last statement of a function will return the result of that statement.
 *
 *  Some Rules:
 *  -----------
 *
 *  Every function defines a new Environment that inherits from the current one. Put another way, the calling scope
 *  provides the context for the function to be executed in. Environments can be captured with the `capture` call.
 *
 *  No function shall modify state in any of its parent environments (which includes the DKV store). A function may only
 *  return values to a parent scope.
 */


/**
 *
 * Functions will always have all of their arguments fully specified, or filled with null.
 * Always parse `nargs` arguments.
 */
public class ASTFunc extends ASTFuncDef {
  ASTFunc() { super(); }
  AST[] _args;

  // (name args)
  @Override ASTFunc parse_impl(Exec E) {
    int nargs = _arg_names.length;
    AST[] args = new AST[nargs];
    for (int i = 0; i < nargs; ++i) args[i] = E.skipWS().parse();

    ASTFunc res = (ASTFunc)clone();
    res._args = args;
    res._asts = _asts;
    return res;
  }

  @Override String opStr() { return _name; }

  @Override ASTOp make() { return new ASTFunc(); }

  @Override void apply(Env e) {
    // this Env is the captured environment with the table already set?
  }
}


class ASTFuncDef extends ASTOp {
  protected static String _name;
  protected static String[] _arg_names;
  protected static Env.SymbolTable _table;
  ASTFuncDef() { super(null); }

  void parse_func(Exec E) {
    String name = ((ASTString)E.parse())._s;
    _name = name;

    // parse the function args: these are just arg names -> will do _local.put(name, Env.NULL, null) (local ST put)
    Env.SymbolTable table = E._env.newTable(); // grab a new SymbolTable
    String[] args = E.skipWS().peek() == '{' ? E.xpeek('{').parseString('}').split(";") : null;
    _arg_names = args;
    if (args == null) table.put(null, Env.NULL, null);
    else for (String arg : args) table.put(arg, Env.NULL, null);
    _table = table;

    // parse the function body
    ArrayList<AST> ast_ary = new ArrayList<>();
    String[] asts = E.skipWS().peek() == '{' ? E.xpeek('{').parseString('}').split(";") : null;
    if (asts != null) {
      for (String ast1 : asts) {
        AST ast = E.parseAST(ast1, E._env);
        ast_ary.add(ast);
      }
    }
    ASTFunc res = (ASTFunc) clone();
    res._asts = asts == null ? null : ast_ary.toArray(new AST[ast_ary.size()]);
    putUDF(res, name);
  }

  @Override String opStr() { return null; }

  @Override ASTOp make() { return null; }

  @Override void apply(Env e) { throw H2O.fail(); }
}