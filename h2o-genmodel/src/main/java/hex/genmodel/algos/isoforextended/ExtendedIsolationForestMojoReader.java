package hex.genmodel.algos.isoforextended;

import hex.genmodel.algos.tree.SharedTreeMojoReader;

import java.io.IOException;

public class ExtendedIsolationForestMojoReader extends SharedTreeMojoReader<ExtendedIsolationForestMojoModel> {

  @Override
  public String getModelName() {
    return "Extended Isolation Forest";
  }

  @Override
  protected void readModelData() throws IOException {
    super.readModelData();
  }

  @Override
  protected ExtendedIsolationForestMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
    return new ExtendedIsolationForestMojoModel(columns, domains, responseColumn);
  }

  @Override public String mojoVersion() {
    return "1.30";
  }

}
