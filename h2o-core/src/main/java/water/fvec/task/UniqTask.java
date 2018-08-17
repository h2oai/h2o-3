package water.fvec.task;

import water.MRTask;
import water.fvec.Chunk;
import water.rapids.ast.prims.mungers.AstGroup;
import water.util.IcedHashMap;

public class UniqTask extends MRTask<UniqTask> {
  public IcedHashMap<AstGroup.G, String> _uniq;

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