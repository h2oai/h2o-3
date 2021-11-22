package hex.genmodel.algos.kmeans;

import hex.genmodel.IClusteringModel;
import hex.genmodel.MojoModel;

public class KMeansMojoModel extends MojoModel implements IClusteringModel {

  public boolean _standardize;
  public double[][] _centers;

  public double[] _means;
  public double[] _mults;
  public int[] _modes;

  KMeansMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    if (_standardize)
      Kmeans_preprocessData(row, _means, _mults, _modes);
    preds[0] = KMeans_closest(_centers, row, _domains);
    return preds;
  }

  @Override
  public int distances(double[] row, double[] distances) {
    if (_standardize)
      Kmeans_preprocessData(row, _means, _mults, _modes);
    return KMeans_distances(_centers, row, _domains, distances);
  }

  @Override
  public int getNumClusters() {
    return _centers.length;
  }

}
