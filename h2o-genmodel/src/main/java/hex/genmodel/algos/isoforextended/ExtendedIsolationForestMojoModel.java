package hex.genmodel.algos.isoforextended;

import hex.genmodel.MojoModel;

public final class ExtendedIsolationForestMojoModel extends MojoModel {

  int _ntrees;

  public ExtendedIsolationForestMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    return score0(row, 0.0, preds);
  }

  @Override
  public double[] score0(double[] row, double offset, double[] preds) {
    return row;
  }

  @Override
  public int getPredsSize() {
    return 2;
  }

  @Override
  public String[] getOutputNames() {
      return new String[]{"predict", "mean_length"};
  }

}
