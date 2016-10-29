package water.rapids.ast.prims.advmath;

import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.ast.Ast;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstFunction;

import java.util.Random;

public class AstRunif extends AstFunction {
  @Override
  public String[] args() {
    return new String[]{"ary", "seed"};
  }

  @Override
  public int nargs() {
    return 1 + 2;
  } // (h2o.runif frame seed)

  @Override
  public String str() {
    return "h2o.runif";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, Ast asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    long seed = (long) asts[2].exec(env).getNum();
    if (seed == -1) seed = new Random().nextLong();
    return new ValFrame(new Frame(new String[]{"rnd"}, new Vec[]{fr.anyVec().makeRand(seed)}));
  }
}
