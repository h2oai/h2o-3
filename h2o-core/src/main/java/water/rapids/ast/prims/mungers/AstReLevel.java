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
public class AstReLevel extends AstPrimitive {
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
    return "relevel";
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
    if (idx == 0) return new ValFrame(new Frame(fr.names(), new Vec[]{fr.anyVec().makeCopy()}));
    String[] srcDom = fr.anyVec().domain();
    final String[] dom = new String[srcDom.length];
    dom[0] = srcDom[idx];
    int j = 1;
    for (int i = 0; i < srcDom.length; ++i)
      if (i != idx) dom[j++] = srcDom[i];
    return new ValFrame(new MRTask() {
      @Override
      public void map(Chunk c, NewChunk nc) {
        int[] vals = new int[c._len];
        c.getIntegers(vals, 0, c._len, -1);
        for (int i = 0; i < vals.length; ++i)
          if (vals[i] == -1) nc.addNA();
          else if (vals[i] == idx)
            nc.addNum(0);
          else
            nc.addNum(vals[i] + (vals[i] < idx ? 1 : 0));
      }
    }.doAll(1, Vec.T_CAT, fr).outputFrame(fr.names(), new String[][]{dom}));
  }
}
