package hex.tree.xgboost.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class PredictConfigurationTest {
  
  @Test
  public void testUseJavaPredict() {
    assertTrue(PredictConfiguration.useJavaScoring());
    System.setProperty(PredictConfiguration.PREDICT_NATIVE_PROP, "true");
    assertFalse(PredictConfiguration.useJavaScoring());

    System.setProperty(PredictConfiguration.PREDICT_NATIVE_PROP, "false");
    assertTrue(PredictConfiguration.useJavaScoring());

    System.setProperty(PredictConfiguration.PREDICT_JAVA_PROP, "false");
    assertTrue(PredictConfiguration.useJavaScoring()); // still respecting native prop

    System.clearProperty(PredictConfiguration.PREDICT_NATIVE_PROP);
    assertFalse(PredictConfiguration.useJavaScoring());

    System.setProperty(PredictConfiguration.PREDICT_JAVA_PROP, "true");
    assertTrue(PredictConfiguration.useJavaScoring());
  }
}
