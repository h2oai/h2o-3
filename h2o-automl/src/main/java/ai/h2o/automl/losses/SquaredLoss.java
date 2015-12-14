package ai.h2o.automl.losses;

import water.fvec.Chunk;

public class SquaredLoss extends Loss {
  @Override double perRow(Chunk predicted, Chunk actual, int row) {
    double d = predicted.atd(row) - actual.atd(row);
    return d*d;
  }
}
