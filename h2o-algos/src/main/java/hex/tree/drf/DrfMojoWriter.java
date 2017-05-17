package hex.tree.drf;

import hex.tree.SharedTreeMojoWriter;

import java.io.IOException;

/**
 * Mojo definition for DRF model.
 */
public class DrfMojoWriter extends SharedTreeMojoWriter<DRFModel, DRFModel.DRFParameters, DRFModel.DRFOutput> {

  @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
  public DrfMojoWriter() {}

  public DrfMojoWriter(DRFModel model) { super(model); }

  @Override public String mojoVersion() {
    return "1.20";
  }

  @Override
  protected void writeModelData() throws IOException {
    super.writeModelData();
    writekv("binomial_double_trees", model._parms._binomial_double_trees);
  }
}
