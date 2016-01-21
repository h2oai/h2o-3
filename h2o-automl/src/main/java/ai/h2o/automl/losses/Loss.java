package ai.h2o.automl.losses;

import water.H2O;
import water.Iced;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

public abstract class Loss extends Iced {
  abstract double perRow(Chunk predicted, Chunk actual, int row);
  private class RegressionLossTask extends MRTask<RegressionLossTask> {
    private double _loss;
    @Override public void map(Chunk[] cs) {
      for( int row=0; row<cs[0]._len; ++row)
        _loss += perRow(cs[0 /*predicted*/], cs[1/*actual*/], row) * (cs.length>2 ? cs[2 /*weight*/].atd(row) : 1);  // weight * perRowLoss
    }
    @Override public void reduce(RegressionLossTask t) { _loss+=t._loss; }
  }

  private class ClassifierLossTask extends MRTask<ClassifierLossTask> {
    private double _loss;
    @Override public void map(Chunk[] cs) {
      throw H2O.unimpl();  // TODO
    }
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

  // regression loss task
  public double loss(Vec predicted, Vec actual, Vec weights) {
    RegressionLossTask lt = new RegressionLossTask();
    if( weights!=null ) lt.doAll(predicted, actual, weights);
    else                lt.doAll(predicted, actual);
    return lt._loss;
  }

  // classification loss task
  public double loss(Frame predicted, Vec actual, Vec weights) {
    ClassifierLossTask lt = new ClassifierLossTask();
    Vec[] vecs = new Vec[predicted.numCols() + (weights==null?1:2) /*actual + weights*/];
    System.arraycopy(predicted.vecs(), 0, vecs, 0, predicted.numCols());
    vecs[vecs.length-2] = actual;
    if( weights!=null ) vecs[vecs.length-1] = weights;
    return lt.doAll(vecs)._loss;
  }
}