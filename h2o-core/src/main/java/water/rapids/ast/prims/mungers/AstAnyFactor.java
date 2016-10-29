package water.rapids.ast.prims.mungers;

import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.ast.Ast;
import water.rapids.vals.ValNum;
import water.rapids.ast.AstFunction;

/**
 * Any columns factor/categorical?
 */
public class AstAnyFactor extends AstFunction {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public int nargs() {
    return 1 + 1;
  } // (any.factor frame)

  @Override
  public String str() {
    return "any.factor";
  }

  @Override
  public ValNum apply(Env env, Env.StackHelp stk, Ast asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    for (Vec vec : fr.vecs()) if (vec.isCategorical()) return new ValNum(1);
    return new ValNum(0);
  }
}
