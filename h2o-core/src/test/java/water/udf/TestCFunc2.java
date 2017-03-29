package water.udf;

/*
 * Note: At this point, we need to be careful.
 * The class is loaded by modified DkvClassLoader, so we need to make sure
 * that CFunc2 is available via parent classloader.
 * However, CFunc2 cannot be private (package), else
 * "java.lang.IllegalAccessError: class water.udf.TestFunc cannot access its superinterface water.udf.CFunc2"
 * exception is thrown during load of TestFunc from DKV via DkvClassLoader.
 *
 * We have to make sure that CFunc2 is public!
 *
 * Furthermore, to allow test to load the class via a different classloader and be accessible
 * in the test, it is necessary to make it public.
 */

import org.junit.Ignore;

@Ignore("Support for tests, but no actual tests here")
/**
 * Func: x,y => sum(x) + sum(y)
 */
public class TestCFunc2 implements CFunc2 {

  @Override
  public double apply(CBlock.CRow row1, CBlock.CRow row2) {
    double sum = 0.0;
    for (int i = 0; i < row1.len(); i++) {
      sum += row1.readDouble(i);
    }
    for (int i = 0; i < row2.len(); i++) {
      sum += row2.readDouble(i);
    }
    return sum;
  }
}
