package water.fvec.task;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.util.IcedDouble;
import water.util.IcedHashSet;

public class UniqTask extends MRTask<UniqTask> {
  private IcedHashSet<IcedDouble> _uniq;
  private boolean _na;

  @Override
  public void map(Chunk[] c) {
    _uniq = new IcedHashSet<>();
    double prev = Double.NaN;
    for (int i = 0; i < c[0]._len; ++i) {
      final double val = c[0].atd(i);
      if (Double.isNaN(val)) {
        _na = true;
        continue;
      }
      if (val == prev) // helps with sparse data and continuous runs of single values
        continue;
      prev = val;
      _uniq.addIfAbsent(new IcedDouble(val));
    }
  }

  @Override
  public void reduce(UniqTask t) {
    IcedHashSet<IcedDouble> l = _uniq;
    IcedHashSet<IcedDouble> r = t._uniq;
    if (l.size() < r.size()) {
      l = r;
      r = _uniq;
    }  // larger on the left
    for (IcedDouble rg : r)
      l.addIfAbsent(rg);  // loop over smaller set
    _uniq = l;
    _na = _na || t._na;
    t._uniq = null;
  }

  public double[] toArray() {
    final int size = _uniq.size();
    double[] res = new double[size + (_na ? 1 : 0)];
    int i = 0;
    if (_na)
      res[i++] = Double.NaN;
    for (IcedDouble d : _uniq)
      res[i++] = d._val;
    assert i == res.length;
    return res;
  }

  public Vec toVec() {
    double[] uniq = toArray();
    Vec v = Vec.makeZero(uniq.length, _fr.vec(0).get_type());
    new MRTask() {
      @Override
      public void map(Chunk c) {
        int start = (int) c.start();
        for (int i = 0; i < c._len; ++i) c.set(i, uniq[i + start]);
      }
    }.doAll(v);
    return v;
  }

}
