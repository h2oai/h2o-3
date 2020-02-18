package water.util;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import water.DKV;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.util.Arrays;

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
  public void testMakeGaussian() {
    Vec v = VecUtils.makeGaussianVec(5, 3,0xDECAF);
    Assert.assertArrayEquals(new int[]{0, 1, 0, 0, 0}, FrameUtils.asInts(v));
  }

  @Test
  public void testMakeGaussian2() {
    Vec v = VecUtils.makeGaussianVec(5,0xDECAF);
    Assert.assertArrayEquals(new int[]{0, 1, 0, -1, 0}, FrameUtils.asInts(v));
  }  

  @Test
  public void testUniformDistrFromFrame() {
    Frame train = parse_test_file("smalldata/anomaly/single_blob.csv");
    Vec dist = VecUtils.uniformDistrFromFrame(train, 0xDECAF);
    Assert.assertNotNull(dist);
    Assert.assertEquals( -2.32361, dist.min(), 10e-5);
    Assert.assertEquals( 0.22445, dist.max(), 10e-5);
  }

  @Test
  public void testUniformDistrFromFrame2() {
    Frame train = parse_test_file("smalldata/anomaly/single_blob_2.csv");
    Vec dist = VecUtils.uniformDistrFromFrame(train, 0xDECAF);
    Assert.assertNotNull(dist);
    Assert.assertEquals( -3.04208, dist.min(), 10e-5);
    Assert.assertEquals( 3.26150, dist.max(), 10e-5);
  }  
  
  @Test
  public void testUniformDistrFromFrameMR() {
    Frame train = parse_test_file("smalldata/anomaly/single_blob.csv");
    Vec dist = VecUtils.uniformDistrFromFrameMR(train, 0xDECAF);
    Assert.assertNotNull(dist);
    Assert.assertEquals( -2.32361, dist.min(), 10e-5);
    Assert.assertEquals( 0.22445, dist.max(), 10e-5);
  }

  @Test
  public void testUniformDistrFromFrameMR2() {
    Frame train = parse_test_file("smalldata/anomaly/single_blob_2.csv");
    Vec dist = VecUtils.uniformDistrFromFrameMR(train, 0xDECAF);
    Assert.assertNotNull(dist);
    Assert.assertEquals( -3.04208, dist.min(), 10e-5);
    Assert.assertEquals( 3.26150, dist.max(), 10e-5);
  }  
}
