package hex.genmodel.algos;

import java.io.IOException;

/**
 */
public class DrfMojoReader extends TreeMojoReader<DrfModel> {

  @Override
  protected void readModelData() throws IOException {
    super.readModelData();
    _model._binomial_double_trees = readkv("binomial_double_trees");
    _model._effective_n_classes = _model._nclasses == 2 && !_model._binomial_double_trees ? 1 : _model._nclasses;
  }

  @Override
  protected DrfModel makeModel(String[] columns, String[][] domains) {
    return new DrfModel(columns, domains);
  }
}
