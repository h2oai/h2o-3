package water.rapids.ast.prims.mungers;

import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.util.VecUtils;

/**
 * Convert to StringVec
 */
public class AstAsCharacter extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public int nargs() {
    return 1 + 1;
  } // (as.character col)

  @Override
  public String str() {
    return "as.character";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame ary = stk.track(asts[1].exec(env)).getFrame();
    Vec[] nvecs = new Vec[ary.numCols()];
    Vec vv;
    for (int c = 0; c < nvecs.length; ++c) {
      vv = ary.vec(c);
      try {
        nvecs[c] = vv.toStringVec();
      } catch (Exception e) {
        VecUtils.deleteVecs(nvecs, c);
        throw e;
      }
    }
    return new ValFrame(new Frame(ary._names, nvecs));
  }
}
