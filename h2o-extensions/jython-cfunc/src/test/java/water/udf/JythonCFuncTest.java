package water.udf;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.InputStream;

import water.DKV;
import water.TestUtil;

import static water.udf.JFuncTest.mockedRow;
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

  private void testPyFunc2Invocation(CFuncRef testFuncDef, String[] resourcesToSkip) throws Exception {
    try {
      ClassLoader skippingCl = getSkippingClassloader(JythonCFuncTest.class.getClassLoader(),
                                                      new String[0], resourcesToSkip);
      ClassLoader cl = new DkvClassLoader(testFuncDef, skippingCl);
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
