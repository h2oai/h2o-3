package water.rapids.ast.prims.advmath;

import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import java.util.*;
import water.etl.prims.advmath.AdvMath;


public class AstStratifiedSplit extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "test_frac", "seed"};
  }

  @Override
  public int nargs() {
    return 1 + 3;
  } // (h2o.random_stratified_split y test_frac seed)

  @Override
  public String str() {
    return "h2o.random_stratified_split";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {

    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Vec stratVec = fr.anyVec();
    final double testFrac = asts[2].exec(env).getNum();
    long seed = (long) asts[3].exec(env).getNum();
    seed = seed == -1 ? new Random().nextLong() : seed;
    ValFrame res = new ValFrame(AdvMath.StratifiedSplit(fr,stratVec,testFrac,seed));
    return res;
  }
}
