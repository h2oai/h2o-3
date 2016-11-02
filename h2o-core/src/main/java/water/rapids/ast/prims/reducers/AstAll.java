package water.rapids.ast.prims.reducers;

import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.vals.Val;
import water.rapids.ast.Ast;
import water.rapids.vals.ValNum;
import water.rapids.ast.AstFunction;

/**
 * Bulk AND operation on a scalar or numeric column; NAs count as true.  Returns 0 or 1.
 */
public class AstAll extends AstFunction {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public String str() {
    return "all";
  }

  @Override
  public int nargs() {
    return 1 + 1;
  }

  @Override
  public ValNum apply(Env env, Env.StackHelp stk, Ast asts[]) {
    Val val = stk.track(asts[1].exec(env));
    if (val.isNum()) return new ValNum(val.getNum() == 0 ? 0 : 1);
    for (Vec vec : val.getFrame().vecs())
      if (vec.nzCnt() + vec.naCnt() < vec.length())
        return new ValNum(0);   // Some zeros in there somewhere
    return new ValNum(1);
  }
}
