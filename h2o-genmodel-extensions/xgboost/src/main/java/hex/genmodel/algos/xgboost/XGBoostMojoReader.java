package hex.genmodel.algos.xgboost;

import biz.k11i.xgboost.Predictor;
import hex.genmodel.ModelMojoReader;
import ml.dmlc.xgboost4j.java.BoosterHelper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 */
public class XGBoostMojoReader extends ModelMojoReader<XGBoostMojoModel> {

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
  }

  @Override
  protected XGBoostMojoModel makeModel(String[] columns, String[][] domains, String responseColumn) {
    byte[] boosterBytes;
    try {
      boosterBytes = readblob("boosterBytes");
    } catch (IOException e) {
      throw new IllegalStateException("MOJO is corrupted: cannot read the serialized Booster", e);
    }
    if (useJavaScoring()) {
      return makeJavaModel(columns, domains, responseColumn, boosterBytes);
    } else {
      return makeNativeModel(columns, domains, responseColumn, boosterBytes);
    }
  }

  public static boolean useJavaScoring() {
    return Boolean.getBoolean("sys.ai.h2o.xgboost.scoring.java.enable");
  }

  private  XGBoostMojoModel makeNativeModel(String[] columns, String[][] domains, String responseColumn,
                                            byte[] boosterBytes) {
    XGBoostNativeMojoModel model = new XGBoostNativeMojoModel(columns, domains, responseColumn);
    InputStream is = new ByteArrayInputStream(boosterBytes);
    try {
      model._booster = BoosterHelper.loadModel(is);
    } catch (Exception xgBoostError) {
      throw new IllegalStateException("Unable to load XGBooster", xgBoostError);
    }
    return model;
  }

  private  XGBoostMojoModel makeJavaModel(String[] columns, String[][] domains, String responseColumn,
                                          byte[] boosterBytes) {
    XGBoostJavaMojoModel model = new XGBoostJavaMojoModel(columns, domains, responseColumn);
    try (InputStream is = new ByteArrayInputStream(boosterBytes)) {
      model._predictor = new Predictor(is);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return model;
  }

}
