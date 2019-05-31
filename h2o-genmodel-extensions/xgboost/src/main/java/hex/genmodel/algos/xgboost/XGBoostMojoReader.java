package hex.genmodel.algos.xgboost;

import hex.genmodel.ModelMojoReader;

import java.io.IOException;
import java.util.Arrays;

/**
 */
public class XGBoostMojoReader extends ModelMojoReader<XGBoostMojoModel> {
  
  public static final String SCORE_JAVA_PROP = "sys.ai.h2o.xgboost.scoring.java.enable";

  @Override
  public String getModelName() {
    return "XGBoost";
  }

  @Override
  protected void readModelData() throws IOException {
    _model._nums = readkv("nums");
    _model._cats = readkv("cats");
    _model._catOffsets = readkv("cat_offsets");
    _model._useAllFactorLevels = readkv("use_all_factor_levels");
    _model._sparse = readkv("sparse");
    if (exists("feature_map")) {
      _model._featureMap = new String(readblob("feature_map"), "UTF-8");
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
    Boolean conf = getJavaScoringConfig();
    if (useJavaScoring(conf)) {
      return new XGBoostJavaMojoModel(boosterBytes, columns, domains, responseColumn);
    } else {
      return new XGBoostNativeMojoModel(boosterBytes, columns, domains, responseColumn);
    }
  }

  private boolean useJavaScoring(Boolean conf) {
    if (conf != null) {
      // user set the property - respect the decision
      return conf;
    }
    String booster = readkv("booster");
    return "gbtree".equals(booster); // use java scoring for `gbtree` (well tested); `native` for dart & gblinear
  }

  public static Boolean getJavaScoringConfig() {
    String javaScoringEnabled = System.getProperty(SCORE_JAVA_PROP);
    if (javaScoringEnabled == null) {
      return null;
    }
    return Boolean.valueOf(javaScoringEnabled);
  }

  @Override public String mojoVersion() {
    return "1.00";
  }

}
