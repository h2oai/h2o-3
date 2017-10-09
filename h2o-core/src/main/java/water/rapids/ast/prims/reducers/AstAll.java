package water.rapids.ast.prims.reducers;

import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValNum;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

import static hex.genmodel.utils.ArrayUtils.isBoolColumn;

/**
 * Bulk AND operation on a scalar or numeric column; NAs count as true.  Returns 0 or 1.
 */
public class AstAll extends AstPrimitive {
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
  public ValNum apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Val val = stk.track(asts[1].exec(env));
    if (val.isNum()) return new ValNum(val.getNum() == 0 ? 0 : 1);
    for (Vec vec : val.getFrame().vecs()) {
      String[] domainV = vec.domain();
      if (domainV != null && !isBoolColumn(domainV))  // contain domain that are not true/fale levels
        return new ValNum(0);         // not a boolean column
      long trueCount = ((domainV != null) && domainV[0].equalsIgnoreCase("true"))
              ?(vec.length()-vec.nzCnt()):vec.nzCnt()+vec.naCnt();
      if (trueCount < vec.length())
        return new ValNum(0);   // Some zeros in there somewhere
    }
    return new ValNum(1);
  }
}
