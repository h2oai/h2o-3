package water.rapids.ast.prims.mungers;

import water.Futures;
import water.Key;
import water.fvec.AppendableVec;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 */
public class AstLevels extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public int nargs() {
    return 1 + 1;
  } // (levels x)

  @Override
  public String str() {
    return "levels";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame f = stk.track(asts[1].exec(env)).getFrame();
    Futures fs = new Futures();
    Key[] keys = Vec.VectorGroup.VG_LEN1.addVecs(f.numCols());
    Vec[] vecs = new Vec[keys.length];

    // compute the longest vec... that's the one with the most domain levels
    int max = 0;
    for (int i = 0; i < f.numCols(); ++i)
      if (f.vec(i).isCategorical())
        if (max < f.vec(i).domain().length) max = f.vec(i).domain().length;

    final int rowLayout = Vec.ESPC.rowLayout(keys[0], new long[]{0, max});
    for (int i = 0; i < f.numCols(); ++i) {
      AppendableVec v = new AppendableVec(keys[i], Vec.T_NUM);
      NewChunk nc = new NewChunk(v, 0);
      String[] dom = f.vec(i).domain();
      int numToPad = dom == null ? max : max - dom.length;
      if (dom != null)
        for (int j = 0; j < dom.length; ++j) nc.addNum(j);
      for (int j = 0; j < numToPad; ++j) nc.addNA();
      nc.close(0, fs);
      vecs[i] = v.close(rowLayout, fs);
      vecs[i].setDomain(dom);
    }
    fs.blockForPending();
    Frame fr2 = new Frame(vecs);
    return new ValFrame(fr2);
  }
}
