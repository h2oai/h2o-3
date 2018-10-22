package hex.tree;

import hex.ModelMojoWriter;
import hex.glm.GLMModel;
import water.DKV;
import water.Key;
import water.Value;
import water.exceptions.H2OKeyNotFoundArgumentException;

import java.io.IOException;

/**
 * Shared Mojo definition file for DRF and GBM models.
 */
public abstract class SharedTreeMojoWriter<
      M extends SharedTreeModel<M, P, O>,
      P extends SharedTreeModel.SharedTreeParameters,
      O extends SharedTreeModel.SharedTreeOutput
    > extends ModelMojoWriter<M, P, O> {

  public SharedTreeMojoWriter() {}

  public SharedTreeMojoWriter(M model) {
    super(model);
  }

  @Override
  protected void writeModelData() throws IOException {
    assert model._output._treeKeys.length == model._output._ntrees;
    int nclasses = model._output.nclasses();
    int ntreesPerClass = model.binomialOpt() && nclasses == 2 ? 1 : nclasses;
    writekv("n_trees", model._output._ntrees);
    writekv("n_trees_per_class", ntreesPerClass);
    if (model._output._calib_model != null) {
      GLMModel calibModel = model._output._calib_model;
      double[] beta = calibModel.beta();
      assert beta.length == nclasses; // n-1 coefficients + 1 intercept
      writekv("calib_method", "platt");
      writekv("calib_glm_beta", beta);
    }
    for (int i = 0; i < model._output._ntrees; i++) {
      for (int j = 0; j < ntreesPerClass; j++) {
        Key<CompressedTree> key = model._output._treeKeys[i][j];
        Value ctVal = key != null ? DKV.get(key) : null;
        if (ctVal == null)
          continue; //throw new H2OKeyNotFoundArgumentException("CompressedTree " + key + " not found");
        CompressedTree ct = ctVal.get();
        // assume ct._seed is useless and need not be persisted
        writeblob(String.format("trees/t%02d_%03d.bin", j, i), ct._bits);

        if (model._output._treeKeysAux!=null) {
          key = model._output._treeKeysAux[i][j];
          ctVal = key != null ? DKV.get(key) : null;
          if (ctVal != null) {
            ct = ctVal.get();
            writeblob(String.format("trees/t%02d_%03d_aux.bin", j, i), ct._bits);
          }
        }
      }
    }
  }
}
