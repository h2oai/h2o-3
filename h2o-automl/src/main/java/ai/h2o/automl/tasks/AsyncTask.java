package ai.h2o.automl.tasks;

import water.H2O;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.AtomicUtils;

public abstract class AsyncTask extends H2O.H2OCountedCompleter<AsyncTask> {
  @Override public void compute2() { task(); tryComplete(); }
  abstract void task();
}

class DummyScoreConst extends AsyncTask {
  private final Vec _actual;
  private final int _nclass;
  double[] _scores;
  DummyScoreConst(Vec predicted, int nclass) { _actual = predicted; _nclass=nclass; }
  void task() {
    _scores=new ConstScoreTask(_nclass, _actual.mean()).doAll(_actual)._scores;
  }

  private static class ConstScoreTask extends MRTask<ConstScoreTask> {
    double[] _scores;
    private final int _nclass;
    private final double _val; // value to use in computing score
    ConstScoreTask(int nclass, double value) { _nclass=nclass; _val=value; }
    @Override public void setupLocal() { _scores = new double[_nclass]; }
    @Override public void map(Chunk C) {
      double[] scores = new double[_nclass];
      for(int i=0;i<C._len; ++i) {
        for(int s=0;s<_scores.length;++s ) {
          double del = (C.atd(i) - _nclass==1?_val:s);  // FIXME: this is totally bogus!
          scores[s]+=del*del;
        }
      }
      for(int s=0; s<scores.length; ++s)
        AtomicUtils.DoubleArray.add(_scores, s, scores[s]);
    }
    @Override public void reduce(ConstScoreTask cst) {
      if( _scores != cst._scores) { ArrayUtils.add(_scores, cst._scores); }
    }
  }
}
