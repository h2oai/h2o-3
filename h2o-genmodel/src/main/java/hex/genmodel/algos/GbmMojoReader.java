package hex.genmodel.algos;

import hex.genmodel.utils.DistributionFamily;

import java.io.IOException;

/**
 */
public class GbmMojoReader extends TreeMojoReader<GbmModel> {

  @Override
  protected void readModelData() throws IOException {
    super.readModelData();
    _model._family = DistributionFamily.valueOf((String)readkv("distribution"));
    _model._init_f = readkv("init_f");
  }

  @Override
  protected GbmModel makeModel(String[] columns, String[][] domains) {
    return new GbmModel(columns, domains);
  }
}
