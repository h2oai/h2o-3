package water.udf;

import org.junit.Ignore;

@Ignore("Support for tests, but no actual tests here")
public interface CFunc1 extends CFunc {
  double apply(CBlock.CRow row);
}
