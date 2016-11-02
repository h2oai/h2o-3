package water.rapids.ast.prims.advmath;

import water.DKV;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.prims.mungers.AstGroup;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.util.IcedHashMap;

public class AstUnique extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public int nargs() {
    return 1 + 1;
  }  // (unique col)

  @Override
  public String str() {
    return "unique";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Vec v;
    if (fr.numCols() != 1)
      throw new IllegalArgumentException("Unique applies to a single column only.");
    if (fr.anyVec().isCategorical()) {
      v = Vec.makeSeq(0, (long) fr.anyVec().domain().length, true);
      v.setDomain(fr.anyVec().domain());
      DKV.put(v);
    } else {
      UniqTask t = new UniqTask().doAll(fr);
      int nUniq = t._uniq.size();
      final AstGroup.G[] uniq = t._uniq.keySet().toArray(new AstGroup.G[nUniq]);
      v = Vec.makeZero(nUniq);
      new MRTask() {
        @Override
        public void map(Chunk c) {
          int start = (int) c.start();
          for (int i = 0; i < c._len; ++i) c.set(i, uniq[i + start]._gs[0]);
        }
      }.doAll(v);
    }
    return new ValFrame(new Frame(v));
  }

  private static class UniqTask extends MRTask<UniqTask> {
    IcedHashMap<AstGroup.G, String> _uniq;

    @Override
    public void map(Chunk[] c) {
      _uniq = new IcedHashMap<>();
      AstGroup.G g = new AstGroup.G(1, null);
      for (int i = 0; i < c[0]._len; ++i) {
        g.fill(i, c, new int[]{0});
        String s_old = _uniq.putIfAbsent(g, "");
        if (s_old == null) g = new AstGroup.G(1, null);
      }
    }

    @Override
    public void reduce(UniqTask t) {
      if (_uniq != t._uniq) {
        IcedHashMap<AstGroup.G, String> l = _uniq;
        IcedHashMap<AstGroup.G, String> r = t._uniq;
        if (l.size() < r.size()) {
          l = r;
          r = _uniq;
        }  // larger on the left
        for (AstGroup.G rg : r.keySet()) l.putIfAbsent(rg, "");  // loop over smaller set
        _uniq = l;
        t._uniq = null;
      }
    }
  }
}
