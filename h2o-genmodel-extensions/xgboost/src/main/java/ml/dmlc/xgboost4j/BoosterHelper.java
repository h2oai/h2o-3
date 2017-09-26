package ml.dmlc.xgboost4j;

import java.io.IOException;
import java.io.InputStream;

import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.XGBoostError;

/**
 * Utility to access package private Booster methods.
 */
public class BoosterHelper {
  
  public static Booster loadModel(String modelPath) throws XGBoostError {
    return Booster.loadModel(modelPath);
  }

  public static Booster loadModel(InputStream in) throws XGBoostError, IOException {
    return Booster.loadModel(in);
  }
}