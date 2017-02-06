package hex.tree.gbm;

import hex.tree.SharedTreeMojoWriter;

import java.io.IOException;

/**
 * MOJO support for GBM model.
 */
public class GbmMojoWriter extends SharedTreeMojoWriter<GBMModel, GBMModel.GBMParameters, GBMModel.GBMOutput> {

  @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
  public GbmMojoWriter() {}

  public GbmMojoWriter(GBMModel model) {
    super(model);
  }

  @Override public String mojoVersion() {
    return "1.20";
  }

  @Override
  protected void writeModelData() throws IOException {
    super.writeModelData();
    writekv("distribution", model._parms._distribution);
    writekv("init_f", model._output._init_f);
    writekv("offset_column", "null");  // Not known yet
  }
}
