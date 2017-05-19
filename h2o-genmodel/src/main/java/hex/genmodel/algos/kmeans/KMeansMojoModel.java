package hex.genmodel.algos.kmeans;

import hex.genmodel.MojoModel;

public class KMeansMojoModel extends MojoModel {

  boolean _standardize;
  double[][] _centers;

  double[] _means;
  double[] _mults;
  int[] _modes;

  KMeansMojoModel(String[] columns, String[][] domains) {
    super(columns, domains);
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    if (_standardize)
      Kmeans_preprocessData(row, _means, _mults, _modes);
    preds[0] = KMeans_closest(_centers, row, _domains);
    return preds;
  }

}
