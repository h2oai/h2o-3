package hex.tree.gbm;

import hex.tree.SharedTreeMojo;

import java.io.IOException;

/**
 * MOJO support for GBM model.
 */
public class GbmModelMojo extends SharedTreeMojo<GBMModel, GBMModel.GBMParameters, GBMModel.GBMOutput> {

  public GbmModelMojo(GBMModel model) {
    super(model);
  }

  @Override
  protected void writeExtraModelInfo() throws IOException {
    super.writeExtraModelInfo();
    writeln("distribution = " + model._parms._distribution);
    writeln("init_f = " + model._output._init_f);
    writeln("offset_column = " + null);  // Not known yet
  }
}
