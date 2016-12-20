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
    Function f = head.exec(scope).getFunc();

    Val[] vals = new Val[args.length];
    for (int i = 0; i < vals.length; i++) {
      try {
        vals[i] = args[i].exec(scope);
      } catch (StdlibFunction.TypeError e) {
        throw new Cascade.RuntimeError(args[i].startPos, args[i].length, e.getMessage());
      }
    }
    return f.apply0(vals);
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
