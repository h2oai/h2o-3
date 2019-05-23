package hex.tree.xgboost.util;

import static water.H2O.OptArgs.SYSTEM_PROP_PREFIX;

public class PredictConfiguration {

  public static final String PREDICT_JAVA_PROP = SYSTEM_PROP_PREFIX + "xgboost.predict.java.enable";
  public static final String PREDICT_NATIVE_PROP = SYSTEM_PROP_PREFIX + "xgboost.predict.native.enable";

  public static boolean useJavaScoring() {
    String predictNativePropValue = System.getProperty(PREDICT_NATIVE_PROP);
    String predictJavaPropValue = System.getProperty(PREDICT_JAVA_PROP);
    if (predictNativePropValue != null) {
      return !Boolean.valueOf(predictNativePropValue);
    } if (predictJavaPropValue != null) {
      return Boolean.valueOf(predictJavaPropValue);
    } else {
      return true;
    }
  }

}
