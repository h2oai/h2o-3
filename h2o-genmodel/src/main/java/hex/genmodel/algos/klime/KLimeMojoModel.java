package hex.genmodel.algos.klime;

import hex.ModelCategory;
import hex.genmodel.MojoModel;
import hex.genmodel.algos.glm.GlmMojoModel;

import java.util.EnumSet;

public class KLimeMojoModel extends MojoModel {

  MojoModel _clusteringModel;
  MojoModel _globalRegressionModel;
  MojoModel[] _clusterRegressionModels;
  int[] _rowSubsetMap;
  
  @Override public EnumSet<ModelCategory> getModelCategories() {
    return EnumSet.of(ModelCategory.Regression, ModelCategory.KLime);
  }
  
  KLimeMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    assert preds.length == row.length + 2;
    //K-Means scoring
    double[] predsSubset = new double[_clusteringModel.nfeatures()+2];
    double[] rowSubset = new double[_clusteringModel.nfeatures()];
    for(int j = 0; j < _clusteringModel._names.length; j++) {
      rowSubset[j] = row[_rowSubsetMap[j]];
    }
    _clusteringModel.score0(rowSubset, predsSubset);
    final int cluster = (int) predsSubset[0];
    //GLM scoring
    GlmMojoModel regressionModel = getRegressionModel(cluster);
    regressionModel.score0(row, preds);
    preds[1] = cluster;
    for (int i = 2; i < preds.length; i++)
      preds[i] = Double.NaN;
    // preds = {prediction, cluster, NaN, ..., NaN)
    regressionModel.applyCoefficients(row, preds, 2);
    // preds = {prediction, cluster, reason code 1, ..., reason code N}
    return preds;
  }

  public GlmMojoModel getRegressionModel(int cluster) {
    return (GlmMojoModel) (_clusterRegressionModels[cluster] != null ?
            _clusterRegressionModels[cluster] : _globalRegressionModel);
  }

  @Override
  public int getPredsSize() {
    return nfeatures() + 2;
  }
}
