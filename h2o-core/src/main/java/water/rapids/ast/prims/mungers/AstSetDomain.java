package water.rapids.ast.prims.mungers;

import water.DKV;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.params.AstStrList;
import water.util.VecUtils;

import java.util.Arrays;

/**
 */
public class AstSetDomain extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "newDomains"};
  }

  @Override
  public int nargs() {
    return 1 + 2;
  } // (setDomain x [list of strings])

  @Override
  public String str() {
    return "setDomain";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame f = stk.track(asts[1].exec(env)).getFrame();
    String[] _domains = ((AstStrList) asts[2])._strs;
    if (f.numCols() != 1)
      throw new IllegalArgumentException("Must be a single column. Got: " + f.numCols() + " columns.");
    Vec v = f.anyVec();
    if (!v.isCategorical())
      throw new IllegalArgumentException("Vector must be a factor column. Got: " + v.get_type_str());
    if (_domains != null && _domains.length != v.domain().length) {
      // in this case we want to recollect the domain and check that number of levels matches _domains
      VecUtils.CollectDomainFast t = new VecUtils.CollectDomainFast((int) v.max());
      t.doAll(v);
      final long[] dom = t.domain();
      if (dom.length != _domains.length)
        throw new IllegalArgumentException("Number of replacement factors must equal current number of levels. Current number of levels: " + dom.length + " != " + _domains.length);
      new MRTask() {
        @Override
        public void map(Chunk c) {
          for (int i = 0; i < c._len; ++i) {
            if (!c.isNA(i)) {
              long num = Arrays.binarySearch(dom, c.at8(i));
              if (num < 0) throw new IllegalArgumentException("Could not find the categorical value!");
              c.set(i, num);
            }
          }
        }
      }.doAll(v);
    }
    v.setDomain(_domains);
    DKV.put(v);
    return new ValFrame(f);
  }
}
