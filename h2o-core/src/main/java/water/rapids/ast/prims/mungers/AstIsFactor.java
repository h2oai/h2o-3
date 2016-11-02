package water.rapids.ast.prims.mungers;

import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValNums;
import water.rapids.ast.AstPrimitive;

/**
 * Is a factor/categorical?
 */
public class AstIsFactor extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public int nargs() {
    return 1 + 1;
  } // (is.factor col)

  @Override
  public String str() {
    return "is.factor";
  }

  @Override
  public ValNums apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if (fr.numCols() == 1) return new ValNums(new double[]{fr.anyVec().isCategorical() ? 1 : 0});
    double ds[] = new double[fr.numCols()];
    for (int i = 0; i < fr.numCols(); i++)
      ds[i] = fr.vec(i).isCategorical() ? 1 : 0;
    return new ValNums(ds);
  }
}
