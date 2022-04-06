package hex.genmodel.algos.tree;

import hex.genmodel.algos.isoforextended.ExtendedIsolationForestMojoModel;

public final class ScoreIsolationTree0 implements ScoreIsolationTree {

  @Override
  public double scoreTree(byte[] tree, double[] row) {
    return ExtendedIsolationForestMojoModel.scoreTree0(tree, row);
  }

}
