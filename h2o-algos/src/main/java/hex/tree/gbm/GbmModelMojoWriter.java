package hex.tree.gbm;

import hex.tree.SharedTreeMojoWriter;

import java.io.IOException;

/**
 * MOJO support for GBM model.
 */
public class GbmModelMojoWriter extends SharedTreeMojoWriter<GBMModel, GBMModel.GBMParameters, GBMModel.GBMOutput> {

  public GbmModelMojoWriter(GBMModel model) {
    super(model);
  }

  @Override
  protected void writeExtraModelInfo() throws IOException {
    super.writeExtraModelInfo();
    writekv("distribution", model._parms._distribution);
    writekv("init_f", model._output._init_f);
    writekv("offset_column", null);  // Not known yet
  }
}
