package water.rapids.ast.prims.reducers;

import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.vals.ValNum;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 * Bulk OR operation on boolean column.  Returns 0 or 1.
 */
public class AstAnyNa extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public int nargs() {
    return 1 + 1;
  } // (any.na x)

  @Override
  public String str() {
    return "any.na";
  }

  @Override
  public ValNum apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    for (Vec vec : fr.vecs()) if (vec.nzCnt() > 0) return new ValNum(1);
    return new ValNum(0);
  }
}
