package water.rapids.ast.prims.advmath;

import water.DKV;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.fvec.task.UniqTask;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.ast.prims.mungers.AstGroup;
import water.rapids.vals.ValFrame;
import water.util.VecUtils;

public class AstUnique extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public int nargs() {
    return 2 + 1;
  }  // (unique col)

  @Override
  public String str() {
    return "unique";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    final Frame fr = stk.track(asts[1].exec(env)).getFrame();
    final boolean includeNAs = asts[2].exec(env).getBool();
    return new ValFrame(uniqueValuesBy(fr,0, includeNAs));
  }

  /** return a frame with unique values from the specified column */
  public static Frame uniqueValuesBy(final Frame fr, final int columnIndex, final boolean includeNAs) {
    Vec vec0 = fr.vec(columnIndex);
    Vec v;
    if (vec0.isCategorical()) {
      // Vector domain might contain levels not actually present in the vector - collection of actual values is required.
      final String[] actualVecDomain = VecUtils.collectDomainFast(vec0);
      final boolean contributeNAs = vec0.naCnt() > 0 && includeNAs;
      final long uniqueVecLength = contributeNAs ? actualVecDomain.length + 1 : actualVecDomain.length;
      v = Vec.makeSeq(0, uniqueVecLength, true);
      if(contributeNAs) {
        v.setNA(uniqueVecLength - 1);
      }
      v.setDomain(actualVecDomain);
      DKV.put(v);
    } else {
      UniqTask t = new UniqTask().doAll(vec0);
      int nUniq = t._uniq.size();
      final AstGroup.G[] uniq = t._uniq.toArray(new AstGroup.G[nUniq]);
      v = Vec.makeZero(nUniq, vec0.get_type());
      new MRTask() {
        @Override
        public void map(Chunk c) {
          int start = (int) c.start();
          for (int i = 0; i < c._len; ++i) c.set(i, uniq[i + start]._gs[0]);
        }
      }.doAll(v);
    }
    return new Frame(v);
  }
}
