package hex.genmodel.algos.deeplearning;

import com.google.gson.JsonObject;
import hex.genmodel.ModelMojoReader;
import hex.genmodel.attributes.DeepLearningModelAttributes;
import hex.genmodel.attributes.ModelAttributes;
import hex.genmodel.attributes.ModelJsonReader;
import hex.genmodel.utils.DistributionFamily;

import java.io.IOException;

import static hex.genmodel.GenModel.convertDouble2Float;

public class DeeplearningMojoReader extends ModelMojoReader<DeeplearningMojoModel> {

  @Override
  public String getModelName() {
    return "Deep Learning";
  }

  @Override
  protected String getModelMojoReaderClassName() { return "hex.deeplearning.DeepLearningMojoWriter"; }

  @Override
  protected void readModelData(final boolean readModelMetadata) throws IOException {
/*    if (_model.isAutoEncoder()) {
      throw new UnsupportedOperationException("AutoEncoder mojo is not ready for deployment.  Stay tuned...");
    }*/
    _model._mini_batch_size=readkv("mini_batch_size");
    _model._nums = readkv("nums");
    _model._cats = readkv("cats");
    _model._catoffsets = readkv("cat_offsets", new int[0]);
    _model._normmul = readkv("norm_mul", new double[0]);
    _model._normsub = readkv("norm_sub", new double[0]);
    _model._normrespmul = readkv("norm_resp_mul");
    _model._normrespsub = readkv("norm_resp_sub");
    _model._use_all_factor_levels = readkv("use_all_factor_levels");
    _model._activation = readkv("activation");
    _model._imputeMeans = readkv("mean_imputation");
    _model._family = DistributionFamily.valueOf((String)readkv("distribution"));
    if (_model._imputeMeans & (_model._cats > 0)) {
      _model._catNAFill = readkv("cat_modes", new int[0]);
    }
    _model._units = readkv("neural_network_sizes", new int[0]);
    _model._all_drop_out_ratios = readkv("hidden_dropout_ratios", new double[0]);

    // read in biases and weights for each layer
    int numLayers = _model._units.length-1; // exclude the output nodes.
    _model._weightsAndBias = new DeeplearningMojoModel.StoreWeightsBias[numLayers];
    for (int layerIndex = 0; layerIndex < numLayers; layerIndex++) {
      double[] tempB = readkv("bias_layer" + layerIndex, new double[0]);
      double[] tempWD = readkv("weight_layer" + layerIndex, new double[0]);
      float[] tempW;

      if (tempWD.length==0)
        tempW = new float[0];
      else
        tempW = convertDouble2Float(tempWD);
      _model._weightsAndBias[layerIndex] = new DeeplearningMojoModel.StoreWeightsBias(tempW, tempB);
    }

    if (_model._mojo_version < 1.10) {
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
    _model.init();
  }

  @Override
  protected DeeplearningMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
    return new DeeplearningMojoModel(columns, domains, responseColumn);
  }

  @Override
  public String mojoVersion() {
    return "1.10";
  }

  @Override
  protected ModelAttributes readModelSpecificAttributes() {
    final JsonObject modelJson = ModelJsonReader.parseModelJson(_reader);
    if (modelJson != null) {
      return new DeepLearningModelAttributes(_model, modelJson);
    } else {
      return null;
    }
  }
}
