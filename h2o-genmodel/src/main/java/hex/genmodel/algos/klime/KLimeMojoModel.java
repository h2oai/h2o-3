package hex.genmodel.algos.klime;

import hex.genmodel.MojoModel;
import hex.genmodel.algos.glm.GlmMojoModel;

public class KLimeMojoModel extends MojoModel {

  MojoModel _clusteringModel;
  MojoModel _globalRegressionModel;
  MojoModel[] _clusterRegressionModels;

  KLimeMojoModel(String[] columns, String[][] domains) {
    super(columns, domains);
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    assert preds.length == row.length + 2;
    System.arraycopy(row, 0, preds, 2, row.length);
    _clusteringModel.score0(row, preds);
    final int cluster = (int) preds[0];
    GlmMojoModel regressionModel = getRegressionModel(cluster);
    System.arraycopy(preds, 2, row, 0, row.length);
    regressionModel.score0(row, preds);
    preds[1] = cluster;
    for (int i = 2; i < preds.length; i++)
      preds[i] = Double.NaN;
    // preds = {prediction, cluster, NaN, ..., NaN)
    regressionModel.applyCoefficients(row, preds, 2);
    // preds = {prediction, cluster, reason code 1, ..., reason code N}
    return preds;
  }

  GlmMojoModel getRegressionModel(int cluster) {
    return (GlmMojoModel) (_clusterRegressionModels[cluster] != null ?
            _clusterRegressionModels[cluster] : _globalRegressionModel);
  }

  @Override
  public int getPredsSize() {
    return nfeatures() + 2;
  }

}
