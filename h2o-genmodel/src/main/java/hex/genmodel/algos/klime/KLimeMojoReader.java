package hex.genmodel.algos.klime;

import hex.genmodel.MojoModel;
import hex.genmodel.MultiModelMojoReader;

import java.io.IOException;

public class KLimeMojoReader extends MultiModelMojoReader<KLimeMojoModel> {

  @Override
  protected String getModelMojoReaderClassName() { return null; }

  @Override
  protected void readParentModelData() throws IOException {
    int clusterNum = readkv("cluster_num", 0);
    _model._clusteringModel = getModel((String) readkv("clustering_model"));
    _model._globalRegressionModel = getModel((String) readkv("global_regression_model"));
    _model._clusterRegressionModels = new MojoModel[clusterNum];
    _model._rowSubsetMap = new int[_model._clusteringModel._names.length];
    for (int i = 0; i < clusterNum; i++) {
      String modelKey = readkv("cluster_regression_model_" + i);
      if (modelKey != null)
        _model._clusterRegressionModels[i] = getModel(modelKey);
    }
    //Subset row to columns used for kmeans (can be less than number of columns passed to k-LIME)
    //Placed here as it only needs to be done once
    for(int i = 0; i < _model._globalRegressionModel._names.length; i++){
      for(int j = 0; j < _model._clusteringModel._names.length; j++) {
        if (_model._globalRegressionModel._names[i].equals(_model._clusteringModel._names[j])) {
          _model._rowSubsetMap[j] = i;
        }
      }
    }
  }

  @Override
  public String getModelName() {
    return "k-LIME";
  }

  @Override
  protected KLimeMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
    return new KLimeMojoModel(columns, domains, responseColumn);
  }

  @Override
  public String mojoVersion() {
    return "1.00";
  }

}
