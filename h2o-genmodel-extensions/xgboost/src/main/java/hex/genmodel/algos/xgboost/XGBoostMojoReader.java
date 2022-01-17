package hex.genmodel.algos.xgboost;

import com.google.gson.JsonObject;
import hex.genmodel.ModelMojoReader;
import hex.genmodel.attributes.ModelJsonReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class XGBoostMojoReader extends ModelMojoReader<XGBoostMojoModel> {
  
  @Override
  public String getModelName() {
    return "XGBoost";
  }

  @Override
  protected String getModelMojoReaderClassName() { return "hex.tree.xgboost.XGBoostMojoWriter"; }

  @Override
  protected void readModelData(final boolean readModelMetadata) throws IOException {
    _model._boosterType = readkv("booster");
    _model._ntrees = readkv("ntrees", 0);
    _model._nums = readkv("nums");
    _model._cats = readkv("cats");
    _model._catOffsets = readkv("cat_offsets");
    _model._useAllFactorLevels = readkv("use_all_factor_levels");
    _model._sparse = readkv("sparse");
    if (exists("feature_map")) {
      _model._featureMap = new String(readblob("feature_map"), StandardCharsets.UTF_8);
    }
    // Calibration
    String calibMethod = readkv("calib_method");
    if (calibMethod != null) {
      if (!"platt".equals(calibMethod))
        throw new IllegalStateException("Unknown calibration method: " + calibMethod);
      _model._calib_glm_beta = readkv("calib_glm_beta", new double[0]);
    }
    _model._hasOffset = readkv("has_offset", false);
    _model.postReadInit();
  }

  @Override
  protected XGBoostMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
    byte[] boosterBytes;
    byte[] auxNodeWeights = null;
    try {
      boosterBytes = readblob("boosterBytes");
      if (exists("auxNodeWeights"))
        auxNodeWeights = readblob("auxNodeWeights");
    } catch (IOException e) {
      throw new IllegalStateException("MOJO is corrupted: cannot read the serialized Booster", e);
    }
    return new XGBoostJavaMojoModel(boosterBytes, auxNodeWeights, columns, domains, responseColumn, false);
  }

  @Override public String mojoVersion() {
    return "1.10";
  }

  @Override
  protected XGBoostModelAttributes readModelSpecificAttributes() {
    final JsonObject modelJson = ModelJsonReader.parseModelJson(_reader);
    if(modelJson != null) {
      return new XGBoostModelAttributes(modelJson, _model);
    } else {
      return null;
    }
  }

}
