package ml.dmlc.xgboost4j.java;

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

  /**
   * Invalidates XGBoost objects (Booster, DMatrix) and frees up their memory
   * @param xgbObjects list of XGBoost objects
   * @throws IllegalStateException when object invalidation fails, only the first exception will be reported (as the
   * exception cause), we assume the other ones will have a same reason
   */
  public static void dispose(Object... xgbObjects) throws IllegalStateException {
    Exception firstException = null;
    for (Object xgbObject : xgbObjects) {
      if (xgbObject == null)
        continue;
      if (xgbObject instanceof Booster) {
        try {
          ((Booster) xgbObject).dispose();
        } catch (Exception e) {
          if (firstException == null)
            firstException = e;
        }
      } else if (xgbObject instanceof DMatrix) {
        try {
          ((DMatrix) xgbObject).dispose();
        } catch (Exception e) {
          if (firstException == null)
            firstException = e;
        }
      } else
        assert false : "Unsupported XGBoost object type: " + xgbObject.getClass();
    }
    if (firstException != null) {
      throw new IllegalStateException("We were unable to free-up xgboost memory. " +
              "This could indicate a memory leak and it can lead to H2O instability.", firstException);
    }
  }

}