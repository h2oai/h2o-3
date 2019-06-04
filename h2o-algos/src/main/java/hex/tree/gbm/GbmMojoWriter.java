package hex.tree.gbm;

import hex.genmodel.utils.DistributionFamily;
import hex.tree.SharedTreeMojoWriter;
import water.H2O;

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
    return "1.30";
  }

  @Override
  protected void writeModelData() throws IOException {
    if (model._parms._distribution == DistributionFamily.custom) {
      H2O.unimpl("MOJO is not currently supported for custom distribution models.");
    }
    super.writeModelData();
    writekv("distribution", model._parms._distribution);
    writekv("init_f", model._output._init_f);
    writekv("offset_column", "null");  // Not known yet
  }
}
