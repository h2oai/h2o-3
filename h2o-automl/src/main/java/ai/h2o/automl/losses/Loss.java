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
        _loss+= perRow(cs[0], cs[1], row);
    }
    @Override public void reduce(LossTask t) { _loss+=t._lo; }
  }

  public double loss(Vec predicted, Vec actual) {
    return new LossTask().doAll(predicted, actual)._loss;
  }
}