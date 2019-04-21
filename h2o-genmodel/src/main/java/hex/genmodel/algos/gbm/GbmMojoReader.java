package hex.genmodel.algos.gbm;

import hex.genmodel.algos.tree.SharedTreeMojoReader;
import hex.genmodel.utils.DistributionFamily;

import java.io.IOException;

/**
 */
public class GbmMojoReader extends SharedTreeMojoReader<GbmMojoModel> {

  @Override
  public String getModelName() {
    return "Gradient Boosting Machine";
  }

  @Override
  protected void readModelData() throws IOException {
    super.readModelData();
    _model._family = DistributionFamily.valueOf((String)readkv("distribution"));
    _model._init_f = readkv("init_f");
  }

  @Override
  protected GbmMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
    return new GbmMojoModel(columns, domains, responseColumn);
  }

  @Override public String mojoVersion() {
    return "1.30";
  }
}
