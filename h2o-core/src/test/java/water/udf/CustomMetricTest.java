package water.udf;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.udf.metric.MEACustomMetric;

import java.io.IOException;

import static water.udf.CustomMetricUtils.testNullModelRegression;
import static water.udf.JFuncUtils.loadTestFunc;

public class CustomMetricTest extends TestUtil {

  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }

  @Test
  public void testNullModelCustomMetric() throws Exception {
    testNullModelRegression(maeCustomMetric());
  }

  static CFuncRef maeCustomMetric() throws IOException {
    return loadTestFunc("customMetric.key", MEACustomMetric.class);
  }

}
