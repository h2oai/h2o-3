package hex.genmodel.algos.klime;

import hex.genmodel.MojoModel;
import hex.genmodel.MultiModelMojoReader;

import java.io.IOException;

public class KLimeMojoReader extends MultiModelMojoReader<KLimeMojoModel> {

  @Override
  protected void readParentModelData() throws IOException {
    int clusterNum = readkv("cluster_num", 0);
    _model._clusteringModel = getModel((String) readkv("clustering_model"));
    _model._globalRegressionModel = getModel((String) readkv("global_regression_model"));
    _model._clusterRegressionModels = new MojoModel[clusterNum];
    for (int i = 0; i < clusterNum; i++) {
      String modelKey = readkv("cluster_regression_model_" + i);
      if (modelKey != null)
        _model._clusterRegressionModels[i] = getModel(modelKey);
    }
  }

  @Override
  protected KLimeMojoModel makeModel(String[] columns, String[][] domains) {
    return new KLimeMojoModel(columns, domains);
  }

}
