package hex.genmodel.algos.tree;

import hex.genmodel.ModelMojoReader;

import java.io.IOException;

/**
 */
public abstract class SharedTreeMojoReader<M extends SharedTreeMojoModel> extends ModelMojoReader<M> {

  @Override
  protected void readModelData() throws IOException {
    // In mojos v=1.0 this info wasn't saved.
    Integer tpc = readkv("n_trees_per_class");
    if (tpc == null) {
      Boolean bdt = readkv("binomial_double_trees");  // This flag exists only for DRF models
      tpc = _model.nclasses() == 2 && (bdt == null || !bdt)? 1 : _model.nclasses();
    }

    _model._ntree_groups = readkv("n_trees");
    _model._ntrees_per_group = tpc;
    _model._compressed_trees = new byte[_model._ntree_groups * tpc][];
    _model._mojo_version = ((Number) readkv("mojo_version")).doubleValue();


    if (_model._mojo_version > 1.0) { // In mojos v=1.0 this info wasn't saved
      _model._compressed_trees_aux = new byte[_model._ntree_groups * tpc][];
    }

    for (int j = 0; j < _model._ntree_groups; j++)
      for (int i = 0; i < tpc; i++) {
        String blobName = String.format("trees/t%02d_%03d.bin", i, j);
        if (!exists(blobName)) continue;
        _model._compressed_trees[_model.treeIndex(j, i)] = readblob(blobName);
        if (_model._compressed_trees_aux!=null) {
          _model._compressed_trees_aux[_model.treeIndex(j, i)] = readblob(String.format("trees/t%02d_%03d_aux.bin", i, j));
        }
      }

    // Calibration
    String calibMethod = readkv("calib_method");
    if (calibMethod != null) {
      if (! "platt".equals(calibMethod))
        throw new IllegalStateException("Unknown calibration method: " + calibMethod);
      _model._calib_glm_beta = readkv("calib_glm_beta", new double[0]);
    }

    _model.postInit();
  }
}
