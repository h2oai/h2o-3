package ai.h2o.cascade.asts;

import ai.h2o.cascade.Cascade;
import ai.h2o.cascade.CascadeScope;
import ai.h2o.cascade.core.Function;
import ai.h2o.cascade.stdlib.StdlibFunction;
import ai.h2o.cascade.vals.Val;
import water.util.SB;

import java.util.ArrayList;

/**
 * Function application AST node. It consists of the {@code head} -- an AST
 * node for the function itself, and {@code args} -- AST nodes for the list
 * of arguments that should be passed to the function.
 */
public class AstApply extends Ast<AstApply> {
  private Ast head;
  private Ast[] args;


  public AstApply(Ast head, ArrayList<Ast> args) {
    this.head = head;
    this.args = args.toArray(new Ast[args.size()]);
  }

  @Override
  public Val exec(CascadeScope scope) {
    Val vhead = head.exec(scope);
    if (!vhead.maybeFunc())
      throw new Cascade.TypeError(head.start, head.length,
                                  "Expected a function, but got a " + vhead.type().toString());
    Function f = vhead.getFunc();
    f.scope = scope;

    Val[] vals = new Val[args.length];
    for (int i = 0; i < vals.length; i++) {
      vals[i] = args[i].exec(scope);
    }

    try {
      return f.apply0(vals);
    } catch (StdlibFunction.TypeError e) {
      if (e.index >= 0) {
        Ast ast = args[e.index];
        throw new Cascade.TypeError(ast.start, ast.length, e.getMessage());
      } else {
        throw new Cascade.TypeError(this.start, this.length, e.getMessage());
      }
    } catch (StdlibFunction.ValueError e) {
      Ast ast = args[e.index];
      throw new Cascade.ValueError(ast.start, ast.length, e.getMessage());
    } catch (StdlibFunction.RuntimeError e) {
      throw new Cascade.RuntimeError(this.start, this.length, e.getMessage());
    }
  }

  @Override
  public String str() {
    SB sb = new SB().p('(').p(head.str());
    for (Ast arg : args) {
      sb.p(' ').p(arg.str());
    }
    return sb.p(')').toString();
  }
}
