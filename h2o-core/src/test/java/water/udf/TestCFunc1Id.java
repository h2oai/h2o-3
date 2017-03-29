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
 * Futhermore, to allow test to load the class via a different classloader and be accessible
 * in the test, it is necessary to make it public.
 */

import org.junit.Ignore;

@Ignore("Support for tests, but no actual tests here")
public class TestCFunc1Id implements CFunc1 {

  @Override
  public double apply(CBlock.CRow row) {
    return row.readDouble(0);
  }
}
