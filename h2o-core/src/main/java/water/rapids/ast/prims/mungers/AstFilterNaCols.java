package water.rapids.ast.prims.mungers;

import water.fvec.Frame;
import water.fvec.Vec;
import water.fvec.VecAry;
import water.rapids.Env;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValNums;
import water.rapids.ast.AstPrimitive;
import water.util.ArrayUtils;

import java.util.ArrayList;

/**
 */
public class AstFilterNaCols extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "fraction"};
  }

  /* (filterNACols frame frac) */
  @Override
  public int nargs() {
    return 1 + 2;
  }

  @Override
  public String str() {
    return "filterNACols";
  }

  @Override
  public ValNums apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    double frac = asts[2].exec(env).getNum();
    double nrow = fr.numRows() * frac;
    VecAry vecs = fr.vecs();
    ArrayUtils.IntAry idxs = new ArrayUtils.IntAry();
    for (int i = 0; i < fr.numCols(); i++)
      if (vecs.naCnt(i) < nrow)
        idxs.add(i);
    double[] include_cols = new double[idxs.size()];
    int j = 0;
    for (int i : idxs.toArray())
      include_cols[j++] = i;
    return new ValNums(include_cols);
  }
}
