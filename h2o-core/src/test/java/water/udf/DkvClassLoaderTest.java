package water.udf;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import water.DKV;
import water.Key;
import water.TestUtil;
import water.Value;
import water.fvec.Frame;
import water.rapids.Rapids;

import static water.udf.JFuncUtils.loadTestJar;

/**
 * Test DkvClassLoader.
 */
public class DkvClassLoaderTest extends TestUtil {
  
  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }

  @Test
  public void testClassLoadFromKey() throws Exception {
    Key k = loadTestJar("testKeyName.jar", "water/rapids/ast/prims/mungers/astjfunc_test.jar");
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
