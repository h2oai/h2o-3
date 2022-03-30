package hex.genmodel.algos.isoforextended;

import hex.genmodel.ModelMojoReader;
import hex.genmodel.algos.tree.ScoreTree0;
import hex.genmodel.algos.tree.ScoreTree1;
import hex.genmodel.algos.tree.ScoreTree2;

import java.io.IOException;

public class ExtendedIsolationForestMojoReader extends ModelMojoReader<ExtendedIsolationForestMojoModel> {

  @Override
  public String getModelName() {
    return "Extended Isolation Forest";
  }

  @Override
  protected void readModelData() throws IOException {
    _model._ntrees = readkv("ntrees", 0);
    _model._sample_size = readkv("sample_size", 0);
    _model._compressedTrees = new byte[_model._ntrees][];
    for (int treeId = 0; treeId < _model._ntrees; treeId++) {
      String blobName = String.format("trees/t%02d.bin", treeId);
      _model._compressedTrees[treeId] = readblob(blobName);
    }
    _model.postInit();
  }

  @Override
  protected ExtendedIsolationForestMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
    return new ExtendedIsolationForestMojoModel(columns, domains, responseColumn);
  }

  @Override public String mojoVersion() {
    return "1.00";
  }

}
