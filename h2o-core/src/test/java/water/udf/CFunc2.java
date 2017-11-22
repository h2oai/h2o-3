package water.udf;

import org.junit.Ignore;

@Ignore("Support for tests, but no actual tests here")
public interface CFunc2 extends CFunc {
  double apply(CBlock.CRow row1, CBlock.CRow row2);
}
