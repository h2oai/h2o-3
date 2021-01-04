package water.util;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import water.DKV;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

/**
 * Test VecUtils interface.
 */
public class VecUtilsTest extends TestUtil {
  @BeforeClass
  static public void setup() {  stall_till_cloudsize(1); }

  @Test
  public void testStringVec2Categorical() {
    Frame f = parseTestFile("smalldata/junit/iris.csv");
    try {
      Assert.assertTrue(f.vec(4).isCategorical());
      int categoricalCnt = f.vec(4).cardinality();
      f.replace(4, f.vec(4).toStringVec()).remove();
      DKV.put(f);
      Assert.assertTrue(f.vec(4).isString());
      f.replace(4, f.vec(4).toCategoricalVec()).remove();
      DKV.put(f);
      Assert.assertTrue(f.vec(4).isCategorical());
      Assert.assertEquals(categoricalCnt, f.vec(4).cardinality());
    } finally {
      f.delete();
    }
  }

  @Test
  public void collectIntegerDomain() {

    String[] data = new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "11"};

    Frame frame = null;
    try {
      frame = new TestFrameBuilder().withColNames("C1")
              .withName("testFrame")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, data)
              .build();
      Assert.assertNotNull(frame);

      final long[] levels = new VecUtils.CollectIntegerDomain().doAll(frame.vec(0)).domain();
      Assert.assertEquals(levels.length, data.length);
    } finally {
      if (frame != null) frame.remove();
    }
  }
}
