package water.rapids.ast.prims.mungers;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;

import java.util.Arrays;

/**
 */
public class AstSetLevel extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "level"};
  }

  @Override
  public int nargs() {
    return 1 + 2;
  } // (setLevel x level)

  @Override
  public String str() {
    return "setLevel";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if (fr.numCols() != 1) throw new IllegalArgumentException("`setLevel` works on a single column at a time.");
    String[] doms = fr.anyVec().domain().clone();
    if (doms == null)
      throw new IllegalArgumentException("Cannot set the level on a non-factor column!");

    String lvl = asts[2].exec(env).getStr();

    final int idx = Arrays.asList(doms).indexOf(lvl);
    if (idx == -1) throw new IllegalArgumentException("Did not find level `" + lvl + "` in the column.");


    // COW semantics
    Frame fr2 = new MRTask() {
      @Override
      public void map(Chunk c, NewChunk nc) {
        for (int i = 0; i < c._len; ++i)
          nc.addNum(idx);
      }
    }.doAll(new byte[]{Vec.T_NUM}, fr.anyVec()).outputFrame(null, fr.names(), fr.domains());
    return new ValFrame(fr2);
  }
}
