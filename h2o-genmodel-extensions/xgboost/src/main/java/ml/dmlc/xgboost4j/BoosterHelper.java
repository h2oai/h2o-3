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