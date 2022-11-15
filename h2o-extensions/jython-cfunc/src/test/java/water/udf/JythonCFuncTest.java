package water.udf;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.InputStream;

import water.DKV;
import water.TestUtil;

import static water.udf.JFuncUtils.mockedRow;
import static water.udf.JFuncUtils.getSkippingClassloader;
import static water.udf.JFuncUtils.loadRawTestFunc;
import static water.udf.JFuncUtils.loadTestFunc;
import static water.util.ArrayUtils.sum;

public class JythonCFuncTest extends TestUtil {

  @BeforeClass
  static public void setup() {
    stall_till_cloudsize(1);
  }
  
  @Test
  public void testPyFunc2InvocationFromResources() throws Exception {
    String[] functionResources = ar("py/test_cfunc2.py", "py/__init__.py");
    CFuncRef
        cFuncRef = loadTestFunc("python", "test1.py", functionResources, "py.test_cfunc2.TestCFunc2");
    testPyFunc2Invocation(cFuncRef, functionResources);
  }

  @Test
  public void testPyFunc2InvocationFromString() throws Exception {
    // Load test python code
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    try(InputStream is = cl.getResourceAsStream("py/test_cfunc2.py")) {
      byte[] ba = IOUtils.toByteArray(is);
      CFuncRef cFuncRef = loadRawTestFunc("python", "test2.py", "test.TestCFunc2", ba, "test.py");
      testPyFunc2Invocation(cFuncRef, new String[0]);
    }
  }
  
  class DummyClassLoaderWrapper extends ClassLoader {
    private final ClassLoader inner;

    public DummyClassLoaderWrapper(ClassLoader inner) {
      super(null);
      this.inner = inner;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
      return this.inner.loadClass(name);
    }
  }

  @Test
  public void testJythonCFuncLoaderDoesNotRequireRelationshipBetweenClassLoaders() throws Exception {
    // Load test python code
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    try(InputStream is = cl.getResourceAsStream("py/test_cfunc2.py")) {
      byte[] ba = IOUtils.toByteArray(is);
      CFuncRef cFuncRef = loadRawTestFunc("python", "test3.py", "test3.TestCFunc2", ba, "test3.py");

      // Testing that a classloader that loaded objects of Jython library could be hierarchically independent on a class
      // loader that reads python artifacts (DKVClassLoader). Here we wrap a default class loader to break class loader chain.
      ClassLoader parentClassLoader = new DummyClassLoaderWrapper(cl);
      testPyFunc2Invocation(cFuncRef, parentClassLoader);
    }
  }

  private void testPyFunc2Invocation(CFuncRef testFuncDef, String[] resourcesToSkip) {
    ClassLoader skippingCl = getSkippingClassloader(
            JythonCFuncTest.class.getClassLoader(),
            new String[0],
            resourcesToSkip);
    testPyFunc2Invocation(testFuncDef, skippingCl);
  }

  private void testPyFunc2Invocation(CFuncRef testFuncDef, ClassLoader parentClassLoader) {
    try {
      ClassLoader cl = new DkvClassLoader(testFuncDef, parentClassLoader);
      JythonCFuncLoader loader = new JythonCFuncLoader();
      CFunc2 testFunc = loader.load(testFuncDef.funcName, CFunc2.class, cl);

      CBlock.CRow crow1 = mockedRow(10, 1.0);
      CBlock.CRow crow2 = mockedRow(5, 1.0);

      Assert.assertEquals("Test testFunc call should return expected value",
                          sum(crow1.readDoubles()) + sum(crow2.readDoubles()),
                          testFunc.apply(crow1, crow2), 1e-10);

    } finally {
      DKV.remove(testFuncDef.getKey());
    }
  }
}
