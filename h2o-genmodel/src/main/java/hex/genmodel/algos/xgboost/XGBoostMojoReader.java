package hex.genmodel.algos.xgboost;

import hex.genmodel.ModelMojoReader;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.XGBoostError;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 */
public class XGBoostMojoReader extends ModelMojoReader<XGBoostMojoModel> {

  @Override
  protected void readModelData() throws IOException {
    byte[] boosterBytes = readblob("boosterBytes");
    InputStream is = new ByteArrayInputStream(boosterBytes);
    try {
      _model._booster = Booster.loadModel(is);
    } catch (XGBoostError xgBoostError) {
      xgBoostError.printStackTrace();
    }
    _model._nums = readkv("nums");
    _model._cats = readkv("cats");
    _model._catOffsets = readkv("cat_offsets");
    _model._useAllFactorLevels = readkv("use_all_factor_levels");
    _model._sparse = readkv("sparse");
  }

  @Override
  protected XGBoostMojoModel makeModel(String[] columns, String[][] domains) {
    return new XGBoostMojoModel(columns, domains);
  }
}
