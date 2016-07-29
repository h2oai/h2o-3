package water.util;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import water.DKV;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.VecAry;

/**
 * Test VecUtils interface.
 */
public class VecUtilsTest extends TestUtil {
  @BeforeClass
  static public void setup() {  stall_till_cloudsize(1); }

  @Test
  public void testStringVec2Categorical() {
    Frame f = parse_test_file("smalldata/junit/iris.csv");
    VecAry vecs = f.vecs();
    try {
      Assert.assertTrue(vecs.isCategorical(4));
      int categoricalCnt = vecs.cardinality(4);

      f.vecs().setVec(4, f.vecs(4).toStringVec()).remove();
      DKV.put(f);
      Assert.assertTrue(f.vecs().isString(4));
      f.vecs().setVec(4, f.vecs(4).toCategoricalVec()).remove();
      DKV.put(f);
      Assert.assertTrue(f.vecs().isCategorical(4));
      Assert.assertEquals(categoricalCnt, f.vecs().cardinality(4));
    } finally {
      f.delete();
    }
  }
}
