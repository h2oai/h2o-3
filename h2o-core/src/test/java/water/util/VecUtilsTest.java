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
    Frame f = parse_test_file("smalldata/junit/iris.csv");
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

  @Test
  public void collectIntegerDomainKnownSize() {
    int length = 10000000;
    String[] data = new String[length];
    for (int i = 0; i < length; i++){
      data[i] = "0";
    }
    data[999] = "1";
    
    Frame frame = null;
    try {
      frame = new TestFrameBuilder().withColNames("C1")
              .withName("testFrame")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, data)
              .build();
      Assert.assertNotNull(frame);

      long start = System.currentTimeMillis();
      final long[] levels = new VecUtils.CollectIntegerDomain().doAll(frame.vec(0)).domain();
      long elapsedTimeMillis = System.currentTimeMillis()-start;

      start = System.currentTimeMillis();
      final long[] levelsKnownSize = new VecUtils.CollectIntegerDomainKnownSize(2).doAll(frame.vec(0)).domain();
      long elapsedTimeMillisKnownSize = System.currentTimeMillis()-start;
      
      Assert.assertArrayEquals(levels, new long[]{0, 1});
      Assert.assertArrayEquals(levels, levelsKnownSize);
      Assert.assertTrue(elapsedTimeMillis > elapsedTimeMillisKnownSize);
      
    } finally {
      if (frame != null) frame.remove();
    }
  }

  @Test
  public void collectIntegerDomainKnownSizeInteger() {
    int length = 10000000;
    long[] data = new long[length];
    for (int i = 0; i < length; i++){
      data[i] = 0;
    }
    data[999] = 1;

    Frame frame = null;
    try {
      frame = new TestFrameBuilder().withColNames("C1")
              .withName("testFrame")
              .withVecTypes(Vec.T_NUM)
              .withDataForCol(0, data)
              .build();
      Assert.assertNotNull(frame);

      long start = System.currentTimeMillis();
      final long[] levels = new VecUtils.CollectIntegerDomain().doAll(frame.vec(0)).domain();
      long elapsedTimeMillis = System.currentTimeMillis()-start;

      start = System.currentTimeMillis();
      final long[] levelsKnownSize = new VecUtils.CollectIntegerDomainKnownSize(2).doAll(frame.vec(0)).domain();
      long elapsedTimeMillisKnownSize = System.currentTimeMillis()-start;

      Assert.assertArrayEquals(levels, new long[]{0, 1});
      Assert.assertArrayEquals(levels, levelsKnownSize);
      Assert.assertTrue(elapsedTimeMillis > elapsedTimeMillisKnownSize);

    } finally {
      if (frame != null) frame.remove();
    }
  }
}
