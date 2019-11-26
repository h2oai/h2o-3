package hex.genmodel.algos.xgboost;

import com.google.gson.JsonObject;
import hex.genmodel.ModelMojoReader;
import hex.genmodel.attributes.ModelJsonReader;
import hex.genmodel.attributes.SharedTreeModelAttributes;

import java.io.IOException;

public class XGBoostMojoReader extends ModelMojoReader<XGBoostMojoModel> {
  
  public static final String SCORE_JAVA_PROP = "sys.ai.h2o.xgboost.scoring.java.enable";

  @Override
  public String getModelName() {
    return "XGBoost";
  }

  @Override
  protected void readModelData() throws IOException {
    _model._boosterType = readkv("booster");
    _model._ntrees = readkv("ntrees", 0);
    _model._nums = readkv("nums");
    _model._cats = readkv("cats");
    _model._catOffsets = readkv("cat_offsets");
    _model._useAllFactorLevels = readkv("use_all_factor_levels");
    _model._sparse = readkv("sparse");
    if (exists("feature_map")) {
      _model._featureMap = new String(readblob("feature_map"), "UTF-8");
    }
    // Calibration
    String calibMethod = readkv("calib_method");
    if (calibMethod != null) {
      if (!"platt".equals(calibMethod))
        throw new IllegalStateException("Unknown calibration method: " + calibMethod);
      _model._calib_glm_beta = readkv("calib_glm_beta", new double[0]);
    }

    _model.postReadInit();
  }

  @Override
  protected XGBoostMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
    byte[] boosterBytes;
    try {
      boosterBytes = readblob("boosterBytes");
    } catch (IOException e) {
      throw new IllegalStateException("MOJO is corrupted: cannot read the serialized Booster", e);
    }
    Boolean mojoPreferredJavaScoring = readkv("use_java_scoring_by_default");
    String booster = readkv("booster");
    if (useJavaScoring(mojoPreferredJavaScoring, booster)) {
      return new XGBoostJavaMojoModel(boosterBytes, columns, domains, responseColumn);
    } else {
      return new XGBoostNativeMojoModel(boosterBytes, columns, domains, responseColumn);
    }
  }

  public static boolean useJavaScoring(Boolean mojoPreferredJavaScoring, String booster) {
    String javaScoringEnabled = System.getProperty(SCORE_JAVA_PROP);
    if (javaScoringEnabled == null) {
      if (mojoPreferredJavaScoring != null) {
        return mojoPreferredJavaScoring;
      } else {
        return "gbtree".equals(booster);
      }
    } else {
      return Boolean.valueOf(javaScoringEnabled);
    }
  }

  @Override public String mojoVersion() {
    return "1.00";
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
