package hex.tree.isoforfaircut;

import hex.ModelMojoWriter;
import hex.tree.isoforfaircut.isolationtree.CompressedIsolationTree;
import water.DKV;

import java.io.IOException;

public class FairCutForestMojoWriter extends ModelMojoWriter<FairCutForestModel, FairCutForestModel.FairCutForestParameters, FairCutForestModel.FairCutForestOutput> {
  @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
  public FairCutForestMojoWriter() {}

  public FairCutForestMojoWriter(FairCutForestModel model) {
    super(model);
  }

  @Override public String mojoVersion() {
    return "1.00";
  }

  @Override
  protected void writeModelData() throws IOException {
    writekv("ntrees", model._output._ntrees);
    writekv("sample_size", model._output._sample_size);
    for (int i = 0; i < model._output._ntrees; i++) {
      CompressedIsolationTree compressedIsolationTree = DKV.getGet(model._output._iTreeKeys[i]);
      writeblob(String.format("trees/t%02d.bin", i), compressedIsolationTree.toBytes());
    }
  }
}
