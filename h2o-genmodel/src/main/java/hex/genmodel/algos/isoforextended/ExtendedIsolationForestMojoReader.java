package hex.genmodel.algos.isoforextended;

import hex.genmodel.ModelMojoReader;

import java.io.IOException;

public class ExtendedIsolationForestMojoReader extends ModelMojoReader<ExtendedIsolationForestMojoModel> {

  @Override
  public String getModelName() {
    return "Extended Isolation Forest";
  }

  @Override
  protected void readModelData() throws IOException {
    _model._ntrees = readkv("ntrees", 0);
  }

  @Override
  protected ExtendedIsolationForestMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
    return new ExtendedIsolationForestMojoModel(columns, domains, responseColumn);
  }

  @Override public String mojoVersion() {
    return "1.00";
  }

}
