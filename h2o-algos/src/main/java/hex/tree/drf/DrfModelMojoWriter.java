package hex.tree.drf;

import hex.tree.SharedTreeMojoWriter;

import java.io.IOException;

/**
 * Mojo definition for DRF model.
 */
public class DrfModelMojoWriter extends SharedTreeMojoWriter<DRFModel, DRFModel.DRFParameters, DRFModel.DRFOutput> {

  public DrfModelMojoWriter(DRFModel model) { super(model); }

  @Override
  protected void writeModelData() throws IOException {
    super.writeModelData();
    writekv("binomial_double_trees", model._parms._binomial_double_trees);
  }
}
