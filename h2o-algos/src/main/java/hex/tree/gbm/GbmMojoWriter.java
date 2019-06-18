package hex.tree.gbm;

import hex.Distribution;
import hex.DistributionFactory;
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
    super.writeModelData();
    Distribution dist = DistributionFactory.getDistribution(model._parms);
    writekv("distribution", dist.distribution);
    writekv("link_function", dist.linkFunction.linkFunctionType);
    writekv("init_f", model._output._init_f);
    writekv("offset_column", "null");  // Not known yet
  }
}
