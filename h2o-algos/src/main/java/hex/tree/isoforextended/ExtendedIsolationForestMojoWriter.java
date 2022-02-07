package hex.tree.isoforextended;

import hex.ModelMojoWriter;
import hex.pca.PCAModel;
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
    writekv("ntrees", model._parms._ntrees); // for reference
  }
}
