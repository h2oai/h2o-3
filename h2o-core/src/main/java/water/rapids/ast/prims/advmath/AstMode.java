package water.rapids.ast.prims.advmath;

import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.vals.ValNum;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.util.ArrayUtils;
import water.util.MRUtils;

/**
 * Find the mode: the most popular element.
 */
public class AstMode extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public String str() {
    return "mode";
  }

  @Override
  public int nargs() {
    return 1 + 1;
  } // (mode ary)

  @Override
  public ValNum apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if (fr.numCols() != 1 || !fr.anyVec().isCategorical())
      throw new IllegalArgumentException("mode only works on a single categorical column");
    return new ValNum(mode(fr.anyVec()));
  }

  public static int mode(Vec v) {
    if (v.isNumeric()) {
      MRUtils.Dist t = new MRUtils.Dist().doAll(v);
      int mode = ArrayUtils.maxIndex(t.dist());
      return (int) t.keys()[mode];
    }
    double[] dist = new MRUtils.ClassDist(v).doAll(v).dist();
    return ArrayUtils.maxIndex(dist);
  }
}
