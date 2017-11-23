package water.udf;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import water.DKV;
import water.Key;
import water.TestUtil;

import static water.udf.JFuncUtils.loadTestJar;

/**
 * Test DkvClassLoader.
 */
public class DkvClassLoaderTest extends TestUtil {
  
  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }

  @Test
  public void testClassLoadFromKey() throws Exception {
    Key k = loadTestJar("testKeyName.jar", "water/udf/cfunc_test.jar");
    try {
      ClassLoader cl = new DkvClassLoader(k, Thread.currentThread().getContextClassLoader());
      Class clz = cl.loadClass("ai.h2o.TestRunnable");
      Assert.assertNotNull(clz);
      Runnable runnable = (Runnable) clz.newInstance();
      runnable.run();
    } finally {
      DKV.remove(k);
    }
  }
}
