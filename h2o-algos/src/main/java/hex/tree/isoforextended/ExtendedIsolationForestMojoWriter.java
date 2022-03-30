package hex.tree.isoforextended;

import hex.ModelMojoWriter;
import hex.pca.PCAModel;
import hex.tree.isoforextended.isolationtree.CompressedIsolationTree;
import water.DKV;
import water.MemoryManager;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ExtendedIsolationForestMojoWriter extends ModelMojoWriter<ExtendedIsolationForestModel, ExtendedIsolationForestModel.ExtendedIsolationForestParameters, ExtendedIsolationForestModel.ExtendedIsolationForestOutput> {
  @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
  public ExtendedIsolationForestMojoWriter() {}

  public ExtendedIsolationForestMojoWriter(ExtendedIsolationForestModel model) {
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
