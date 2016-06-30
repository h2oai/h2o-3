package water.rapids.ast.prims.reducers;

import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.vals.ValNums;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 */
public class AstMean extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "na_rm"};
  }

  @Override
  public String str() {
    return "mean";
  }

  @Override
  public int nargs() {
    return 1 + 2;
  } // (mean X na.rm)

  @Override
  public ValNums apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    boolean narm = asts[2].exec(env).getNum() == 1;
    double[] ds = new double[fr.numCols()];
    Vec[] vecs = fr.vecs();
    for (int i = 0; i < fr.numCols(); i++)
      ds[i] = (!vecs[i].isNumeric() || vecs[i].length() == 0 || (!narm && vecs[i].naCnt() > 0)) ? Double.NaN : vecs[i].mean();
    return new ValNums(ds);
  }
}
