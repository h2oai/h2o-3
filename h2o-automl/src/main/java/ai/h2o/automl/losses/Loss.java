package ai.h2o.automl.losses;

import water.Iced;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Vec;

public abstract class Loss extends Iced {
  abstract double perRow(Chunk predicted, Chunk actual, int row);
  public class LossTask extends MRTask<LossTask> {
    private double _loss;
    @Override public void map(Chunk[] cs) {
      for( int row=0; row<cs[0]._len; ++row)
        _loss += perRow(cs[0 /*predicted*/], cs[1/*actual*/], row)*(cs.length>2 ? cs[2 /*weight*/].atd(row) : 1);
    }
    @Override public void reduce(LossTask t) { _loss+=t._lo; }
  }

  // weighted thing is that you could "drop out" a loser prediction
  // For example (rule/heuristic-based, but would like to be data-driven?):
  //   RF, GLM, and DL predictions are all within 0-1.5 SD, but the
  //   GBM is 3.4 SD, you'd just kill it or drop it to 10% confidence
  //   or something.
  //
  // Coarse worked well on consecutive problems... The alternative is
  // something even worse: rule based. Something to not say 1% probability
  // just because of 1 missing value.
  public double loss(Vec predicted, Vec actual, Vec weights) {
    LossTask lt = new LossTask();
    if( weights!=null ) lt.doAll(predicted, actual, weights);
    else                lt.doAll(predicted, actual);
    return lt._loss;
  }
}