package water.rapids.ast;

import water.rapids.Env;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.vals.ValFun;
import water.util.SB;

import java.util.ArrayList;

/**
 * Define a function
 * Syntax: { ids... . expr }
 * IDs are bound within expr
 *
 * TODO: rename to AstUserDefinedFunction
 */
public class AstFunction extends AstPrimitive {
  final String[] _ids;          // Identifier names
  final AstRoot _body;              // The function body
  // If this function is being evaluated, record the arguments and parent lexical scope
  final Val[] _args;            // Evaluated arguments to a function
  final AstFunction _parent;         // Parent lexical scope

  public AstFunction() {
    _ids = null;
    _body = null;
    _args = null;
    _parent = null;
  }

  public AstFunction(ArrayList<String> ids, AstRoot body) {
    _ids = ids.toArray(new String[ids.size()]);
    _body = body;
    _args = null;  // This is a template of an uncalled function
    _parent = null;
  }

  // A function applied to arguments
  public AstFunction(AstFunction fun, Val[] args, AstFunction parent) {
    _ids = fun._ids;
    _body = fun._body;
    _parent = parent;
    _args = args;
  }


  @Override
  public String str() {
    SB sb = new SB().p('{');
    penv(sb);
    for (String id : _ids)
      sb.p(id).p(' ');
    sb.p(". ").p(_body.toString()).p('}');
    return sb.toString();
  }

  @Override
  public String example() {
    return "{ ...args . expr }";
  }

  @Override
  public String description() {
    return "Function definition: a list of tokens in curly braces. All initial tokens (which must be valid " +
        "identifiers) become function arguments, then a single dot '.' must follow, and finally an expression which " +
        "is the body of the function. Functions with variable number of arguments are not supported. Example: " +
        "squaring function `{x . (^ x 2)}`";
  }

  // Print environment
  private void penv(SB sb) {
    if (_parent != null) _parent.penv(sb);
    if (_args != null)
      for (int i = 1; i < _ids.length; i++)
        sb.p(_ids[i]).p('=').p(_args[i].toString()).p(' ');
  }

  // Function execution.  Just throw self on stack like a constant.  However,
  // capture the existing global scope.
  @Override
  public ValFun exec(Env env) {
    return new ValFun(new AstFunction(this, null, env._scope));
  }

  // Expected argument count, plus self
  @Override
  public int nargs() {
    return _ids.length;
  }

  @Override
  public String[] args() { return _ids; }

  // Do a ID lookup, returning the matching argument if found
  public Val lookup(String id) {
    for (int i = 1; i < _ids.length; i++)
      if (id.equals(_ids[i]))
        return _args[i];        // Hit, return found argument
    return _parent == null ? null : _parent.lookup(id);
  }

  // Apply this function: evaluate all arguments, push a lexical scope mapping
  // the IDs to the ARGs, then evaluate the body.  After execution pop the
  // lexical scope and return the results.
  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    // Evaluation all arguments
    Val[] args = new Val[asts.length];
    for (int i = 1; i < asts.length; i++)
      args[i] = stk.track(asts[i].exec(env));
    AstFunction old = env._scope;
    env._scope = new AstFunction(this, args, _parent); // Push a new lexical scope, extended from the old

    Val res = stk.untrack(_body.exec(env));

    env._scope = old;           // Pop the lexical scope off (by restoring the old unextended scope)
    return res;
  }
}
