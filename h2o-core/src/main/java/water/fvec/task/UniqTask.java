package water.fvec.task;

import water.MRTask;
import water.fvec.Chunk;
import water.rapids.ast.prims.mungers.AstGroup;
import water.util.IcedHashSet;

public class UniqTask extends MRTask<UniqTask> {
  public IcedHashSet<AstGroup.G> _uniq;

  @Override
  public void map(Chunk[] c) {
    _uniq = new IcedHashSet<>();
    AstGroup.G g = new AstGroup.G(1, null);
    for (int i = 0; i < c[0]._len; ++i) {
      g.fill(i, c, new int[]{0});
      AstGroup.G old = _uniq.addIfAbsent(g);
      if (old == null)
        g = new AstGroup.G(1, null);
    }
  }

  @Override
  public void reduce(UniqTask t) {
    if (_uniq != t._uniq) {
      IcedHashSet<AstGroup.G> l = _uniq;
      IcedHashSet<AstGroup.G> r = t._uniq;
      if (l.size() < r.size()) {
        l = r;
        r = _uniq;
      }  // larger on the left
      for (AstGroup.G rg : r) 
        l.addIfAbsent(rg);  // loop over smaller set
      _uniq = l;
      t._uniq = null;
    }
  }
}
