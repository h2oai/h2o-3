package hex.genmodel.algos.drf;

import hex.genmodel.algos.tree.SharedTreeMojoReader;

import java.io.IOException;

/**
 */
public class DrfMojoReader extends SharedTreeMojoReader<DrfMojoModel> {

  @Override
  public String getModelName() {
    return "Distributed Random Forest";
  }

  @Override
  protected void readModelData() throws IOException {
    super.readModelData();
    _model._binomial_double_trees = readkv("binomial_double_trees");
    // _model._effective_n_classes = _model._nclasses == 2 && !_model._binomial_double_trees ? 1 : _model._nclasses;
  }

  @Override
  protected DrfMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
    return new DrfMojoModel(columns, domains, responseColumn);
  }

  @Override public String mojoVersion() {
    return "1.30";
  }
}
