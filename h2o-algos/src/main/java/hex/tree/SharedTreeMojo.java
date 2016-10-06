package hex.tree;

import hex.ModelMojo;
import water.DKV;
import water.Key;
import water.Value;
import water.exceptions.H2OKeyNotFoundArgumentException;

import java.io.IOException;

/**
 * Shared Mojo definition file for DRF and GBM models.
 */
public class SharedTreeMojo<M extends SharedTreeModel<M, P, O>,
                            P extends SharedTreeModel.SharedTreeParameters,
                            O extends SharedTreeModel.SharedTreeOutput> extends ModelMojo<M, P, O> {

  public SharedTreeMojo(M model) {
    super(model);
  }

  @Override
  protected void writeExtraModelInfo() throws IOException {
    writekv("n_trees", model._output._ntrees);
  }

  @Override
  protected void writeModelData() throws IOException {
    assert model._output._treeKeys.length == model._output._ntrees;
    int nclasses = model._output.nclasses();
    int ntreesPerClass = model.binomialOpt() && nclasses == 2 ? 1 : nclasses;
    for (int i = 0; i < model._output._ntrees; i++) {
      for (int j = 0; j < ntreesPerClass; j++) {
        Key<CompressedTree> key = model._output._treeKeys[i][j];
        Value ctVal = key != null ? DKV.get(key) : null;
        if (ctVal == null)
          throw new H2OKeyNotFoundArgumentException("CompressedTree " + key + " not found");
        CompressedTree ct = ctVal.get();
        assert ct._nclass == nclasses;
        // assume ct._seed is useless and need not be persisted
        writeBinaryFile(String.format("trees/t%02d_%03d.bin", j, i), ct._bits);
      }
    }
  }
}
