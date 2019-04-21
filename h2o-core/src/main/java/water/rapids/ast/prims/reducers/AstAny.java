package water.rapids.ast.prims.reducers;

import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValNum;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

import static hex.genmodel.utils.ArrayUtils.isBoolColumn;

/**
 * Bulk OR operation on boolean column; NAs count as true.  Returns 0 or 1.
 */
public class AstAny extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public int nargs() {
    return 1 + 1;
  } // (any x)

  @Override
  public String str() {
    return "any";
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
      if (trueCount > 0)
        return new ValNum(1);   // Some nonzeros in there somewhere
    }
    return new ValNum(0);
  }
}
