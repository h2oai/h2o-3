package hex.genmodel.algos.kmeans;

import hex.genmodel.ModelMojoReader;

import java.io.IOException;

public class KMeansMojoReader extends ModelMojoReader<KMeansMojoModel> {

  @Override
  public String getModelName() {
    return "K-means";
  }

  @Override
  protected void readModelData() throws IOException {
    _model._standardize = readkv("standardize");
    if (_model._standardize) {
      _model._means = readkv("standardize_means");
      _model._mults = readkv("standardize_mults");
      _model._modes = readkv("standardize_modes");
    }
    final int centerNum = readkv("center_num");
    _model._centers = new double[centerNum][];
    for (int i = 0; i < centerNum; i++)
      _model._centers[i] = readkv("center_" + i);
  }

  @Override
  protected KMeansMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
    return new KMeansMojoModel(columns, domains, responseColumn);
  }

  @Override public String mojoVersion() { return "1.00"; }

}
