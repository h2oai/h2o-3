package ai.h2o.cascade.asts;

import ai.h2o.cascade.Cascade;
import ai.h2o.cascade.core.Function;
import ai.h2o.cascade.core.Scope;
import ai.h2o.cascade.core.Val;
import water.util.SB;

import java.util.ArrayList;

/**
 * Function application AST node. It consists of the {@code head} -- an AST
 * node for the function itself, and {@code args} -- AST nodes for the list
 * of arguments that should be passed to the function.
 */
public class AstApply extends AstNode<AstApply> {
  private AstNode head;
  private AstNode[] args;


  public AstApply(AstNode head, ArrayList<AstNode> args) {
    this.head = head;
    this.args = args.toArray(new AstNode[args.size()]);
  }

  @Override
  public Val exec(Scope scope) {
    Val vhead = head.exec(scope);
    if (!vhead.isFun()) {
      throw new Cascade.TypeError(head.start, head.length, "Function expected");
    }

    Function f = vhead.getFun();
    f.scope = scope;

    Val[] vals = new Val[args.length];
    for (int i = 0; i < vals.length; i++) {
      vals[i] = args[i].exec(scope);
    }

    Val ret;
    try {
      ret = f.apply0(vals);
    } catch (Function.Error e) {
      int i = (e instanceof Function.TypeError)? ((Function.TypeError)e).index :
              (e instanceof Function.ValueError)? ((Function.ValueError)e).index : -1;
      AstNode src = i >= 0? args[i] : this;
      throw e.toCascadeError(src.start, src.length);
    }
    return ret;
  }

  @Override
  public String str() {
    SB sb = new SB().p('(').p(head.str());
    for (AstNode arg : args) {
      sb.p(' ').p(arg.str());
    }
    return sb.p(')').toString();
  }
}
