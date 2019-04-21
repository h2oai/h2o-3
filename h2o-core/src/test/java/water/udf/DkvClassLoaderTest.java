package water.udf;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import water.DKV;
import water.Key;
import water.TestUtil;

import static water.udf.DkvClassLoader.readJarEntry;
import static water.udf.JFuncUtils.loadTestJar;

/**
 * Test DkvClassLoader.
 */
public class DkvClassLoaderTest extends TestUtil {
  
  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }

  @Test
  public void testClassLoadFromKey() throws Exception {
    String testJar = "water/udf/cfunc_test.jar";
    Key k = loadTestJar("testKeyName.jar", testJar);
    ClassLoader cl = new DkvClassLoader(k, Thread.currentThread().getContextClassLoader());

    int classCnt = 0;
    int resourceCnt = 0;
    try(JarInputStream jis = new JarInputStream(Thread.currentThread().getContextClassLoader().getResourceAsStream(testJar))) {
      JarEntry entry = null;
      while ((entry = jis.getNextJarEntry()) != null) {
        if (entry.isDirectory()) continue;
        String entryName = entry.getName();
        if (entryName.endsWith(".class")) {
          String klazzName = entryName.replace('/', '.').substring(0, entryName.length() - ".class".length());
          assertLoadableClass(cl, klazzName);
          classCnt++;
        }
        byte[] exptectedContent = readJarEntry(jis, entry);
        assertLoadableResource(cl, entryName, exptectedContent);
        resourceCnt++;
      }
      // Just make sure, that we tested at least one class file and one resource
      Assert.assertTrue("The file " + testJar +" needs to contain at least one classfile",
                        classCnt > 0);
      Assert.assertTrue("The file " + testJar +" needs to contain at least one resource",
                        resourceCnt > 0);
    } finally {
      DKV.remove(k);
    }
  }

  void assertLoadableClass(ClassLoader cl, String klazzName) throws Exception {
    Class clz = cl.loadClass(klazzName);
    Assert.assertNotNull("Classloader should not return null for klazz " + klazzName, clz);
    if (Runnable.class.isAssignableFrom(clz)) {
      Runnable instance = (Runnable) clz.newInstance();
      instance.run();
    }
  }

  void assertLoadableResource(ClassLoader cl, String path, byte[] expectedContent) throws IOException {
    URL resourceUrl = cl.getResource(path);
    Assert.assertNotNull("Classloader should not return null for resource " + path, resourceUrl);
    Assert.assertEquals("Created URL should have 'dkv' protocol", "dkv", resourceUrl.getProtocol());
    Assert.assertTrue(resourceUrl.toString().endsWith(path));
    // Verify content
    byte[] content = IOUtils.toByteArray(resourceUrl);
    Assert.assertArrayEquals("Content of resource provided by classloader has to match content in the jar file",
                             expectedContent, content);
  }
}
