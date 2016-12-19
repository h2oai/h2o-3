package ai.h2o.cascade.asts;

import ai.h2o.cascade.vals.Val;
import water.util.SB;

import java.util.ArrayList;

/**
 */
public class AstExec extends Ast<AstExec> {
  private Ast head;
  private Ast[] args;
  private String[] names;

  public AstExec(Ast head, ArrayList<Ast> args, ArrayList<String> names) {
    this.head = head;
    this.args = args.toArray(new Ast[args.size()]);
    if (names != null) {
      assert args.size() == names.size() : "Size mismatch between args and names";
      this.names = names.toArray(new String[names.size()]);
    }
  }

  @Override
  public Val exec() {
    // TODO
    return null;
  }

  @Override
  public String str() {
    SB sb = new SB().p('(').p(head.str());
    for (int i = 0; i < args.length; i++) {
      sb.p(' ');
      if (names != null && names[i] != null)
        sb.p(names[i]).p('=');
      sb.p(args[i].str());
    }
    return sb.p(')').toString();
  }
}
