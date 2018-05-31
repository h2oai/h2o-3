package hex.genmodel.algos.xgboost;

import biz.k11i.xgboost.Predictor;
import hex.genmodel.ModelMojoReader;
import ml.dmlc.xgboost4j.java.BoosterHelper;
import ml.dmlc.xgboost4j.java.XGBoostError;

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
    try {
      byte[] boosterBytes = readblob("boosterBytes");
      if (useJavaScoring())
        return makeJavaModel(columns, domains, responseColumn, boosterBytes);
      else
        return makeNativeModel(columns, domains, responseColumn, boosterBytes);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to load the Booster", e);
    }
  }

  public static boolean useJavaScoring() {
    return Boolean.parseBoolean("sys.ai.h2o.xgboost.scoring.java.enable");
  }

  private  XGBoostMojoModel makeNativeModel(String[] columns, String[][] domains, String responseColumn,
                                            byte[] boosterBytes) throws IOException {
    XGBoostNativeMojoModel model = new XGBoostNativeMojoModel(columns, domains, responseColumn);
    InputStream is = new ByteArrayInputStream(boosterBytes);
    try {
      model._booster = BoosterHelper.loadModel(is);
    } catch (XGBoostError xgBoostError) {
      xgBoostError.printStackTrace();
    }
    return model;
  }

  private  XGBoostMojoModel makeJavaModel(String[] columns, String[][] domains, String responseColumn,
                                          byte[] boosterBytes) throws IOException {
    XGBoostJavaMojoModel model = new XGBoostJavaMojoModel(columns, domains, responseColumn);
    try (InputStream is = new ByteArrayInputStream(boosterBytes)) {
      model._predictor = new Predictor(is);
    }
    return model;
  }

}
