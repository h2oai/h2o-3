package water.udf;

import org.apache.commons.io.IOUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import org.junit.runner.RunWith;
import water.TestUtil;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static water.udf.CustomMetricTest.testNullModelRegression;
import static water.udf.JFuncUtils.loadRawTestFunc;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class JythonCustomMetricTest {

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
