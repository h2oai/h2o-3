package water.rapids.ast.prims.reducers;

import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.vals.ValNums;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 */
public class AstNaCnt extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public String str() {
    return "naCnt";
  }

  @Override
  public int nargs() {
    return 1 + 1;
  }  // (naCnt fr)

  @Override
  public ValNums apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    double ds[] = new double[fr.numCols()];
    for (int i = 0; i < fr.numCols(); ++i)
      ds[i] = fr.vec(i).naCnt();
    return new ValNums(ds);
  }
}
