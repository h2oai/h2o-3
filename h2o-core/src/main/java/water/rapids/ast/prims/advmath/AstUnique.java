package water.rapids.ast.prims.advmath;

import water.DKV;
import water.H2O;
import water.fvec.Frame;
import water.fvec.Vec;
import water.fvec.task.UniqOldTask;
import water.fvec.task.UniqTask;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.util.Log;
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
    final Vec vec0 = fr.vec(columnIndex);
    final Vec v;
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
      long start = System.currentTimeMillis();
      String uniqImpl = H2O.getSysProperty("rapids.unique.impl", "IcedDouble");
      switch (uniqImpl) {
        case "IcedDouble":
          v = new UniqTask().doAll(vec0).toVec();
          break;
        case "GroupBy":
          v = new UniqOldTask().doAll(vec0).toVec();
          break;
        default:
          throw new UnsupportedOperationException("Unknown unique implementation: " + uniqImpl);
      }
      Log.info("Unique on a numerical Vec (len=" + vec0.length() + ") took " + 
              (System.currentTimeMillis() - start) + "ms and returned " + v.length() + " unique values (impl: " + uniqImpl + ").");
    }
    return new Frame(v);
  }
}
