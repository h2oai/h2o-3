package hex.genmodel.algos.glrm;

import hex.genmodel.ModelMojoReader;

import java.io.IOException;

/**
 */
public class GlrmMojoReader extends ModelMojoReader<GlrmModel> {

  @Override
  protected void readModelData() throws IOException {
    _model._regx = readkv("regularization_x");
    _model._gammax = readkv("gamma_x");
  }

  @Override
  protected GlrmModel makeModel(String[] columns, String[][] domains) {
    return new GlrmModel(columns, domains);
  }

}
