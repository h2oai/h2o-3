package hex.genmodel.algos.isoforextended;

import hex.genmodel.algos.tree.SharedTreeMojoModel;

public final class ExtendedIsolationForestMojoModel extends SharedTreeMojoModel {

  public ExtendedIsolationForestMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  @Override
  public double[] unifyPreds(double[] row, double offset, double[] preds) {
    return new double[0];
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    return score0(row, 0.0, preds);
  }

  @Override
  public double[] score0(double[] row, double offset, double[] preds) {
    super.scoreAllTrees(row, preds);
    return unifyPreds(row, offset, preds);
  }
}
