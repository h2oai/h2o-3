package hex.genmodel.algos.tree;

import com.google.gson.JsonObject;
import hex.genmodel.ModelMojoReader;
import hex.genmodel.attributes.*;

import java.io.IOException;

public abstract class SharedTreeMojoReader<M extends SharedTreeMojoModel> extends ModelMojoReader<M> {


  @Override
  protected void readModelData(final boolean readModelMetadata) throws IOException {
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
    if (_model._mojo_version < 1.40) {
        _model._genmodel_encoding = "AUTO";
    } else {
        _model._genmodel_encoding = readkv("_genmodel_encoding").toString();
        _model._orig_projection_array = readkv("_orig_projection_array", new double[0]);
        Integer n = readkv("_n_orig_names");
        if (n != null) {
            _model._orig_names = readStringArray("_orig_names", n);
        }
        n = readkv("_n_orig_domain_values");
        if (n != null) {
            _model._orig_domain_values = new String[n][];
            for (int i = 0; i < n; i++) {
                int m = readkv("_m_orig_domain_values_" + i);
                if (m > 0) {
                    _model._orig_domain_values[i] = readStringArray("_orig_domain_values_" + i, m);
                } 
            }
        }
    }

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

  @Override
  protected SharedTreeModelAttributes readModelSpecificAttributes() {
    final JsonObject modelJson = ModelJsonReader.parseModelJson(_reader);
    if(modelJson != null) {
      return new SharedTreeModelAttributes(modelJson, _model);
    } else {
      return null;
    }
  }
}
