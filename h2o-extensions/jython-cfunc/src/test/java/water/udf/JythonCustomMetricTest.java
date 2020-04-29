package water.udf;

import org.apache.commons.io.IOUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import water.TestUtil;

import static water.udf.CustomMetricUtils.testNullModelRegression;
import static water.udf.JFuncUtils.loadRawTestFunc;

public class JythonCustomMetricTest extends TestUtil {
  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }

  @Test
  public void testNullModelCustomMetric() throws Exception {
    testNullModelRegression(maeCustomMetric());
  }

  private CFuncRef maeCustomMetric() throws IOException {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    try(InputStream is = cl.getResourceAsStream("py/test_mae_custom_metric.py")) {
      byte[] ba = IOUtils.toByteArray(is);
      return loadRawTestFunc("python", "test_mae.py", "test_cm.MAE", ba, "test_cm.py");
    }
  }
}
