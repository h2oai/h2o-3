package hex.tree.drf;

import hex.tree.SharedTreeMojo;

import java.io.IOException;

/**
 * Mojo definition for DRF model.
 */
public class DrfModelMojo extends SharedTreeMojo<DRFModel, DRFModel.DRFParameters, DRFModel.DRFOutput> {

  public DrfModelMojo(DRFModel model) { super(model); }

  @Override
  protected void writeExtraModelInfo() throws IOException {
    super.writeExtraModelInfo();
    writekv("binomial_double_trees", model._parms._binomial_double_trees);
  }
}
