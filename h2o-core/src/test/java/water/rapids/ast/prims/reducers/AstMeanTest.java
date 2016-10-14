package water.rapids.ast.prims.reducers;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.vals.ValFrame;

/**
 * Test the AstMean.java class
 */
public class AstMeanTest extends TestUtil {
  @BeforeClass public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test public void testMeanColumns() {
    // Use only axis = 0 here
    Vec vi1 = TestUtil.ivec(-1, -2, 0, 2, 1);
    Vec vd1 = TestUtil.dvec(1.5, 2.5, 3.5, 4.5, 8.0);
    Vec vd2 = TestUtil.dvec(0.2, 0.4, 0.6, 0.8, 1.0);
    Vec vd3 = TestUtil.dvec(1, 2, Double.NaN, 3, Double.NaN);
    Vec vs1 = TestUtil.svec("a", "b", "c", "d", "e");
    Vec vt1 = TestUtil.tvec(10000000, 10000020, 10000030, 10000040, 10000060);

    Key<Frame> key1 = Key.make();
    Key<Frame> key2 = Key.make();
    Frame fr1 = null, fr2 = null, res1 = null, res2 = null, res3 = null;
    try {
      // Test simple mean, with na_rm = False
      String[] names1 = {"I", "D", "DD", "DN", "T", "S"};
      byte[] types1 = {Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_TIME, Vec.T_NUM};
      Vec[] vecs1 = {vi1, vd1, vd2, vd3, vt1, vs1};
      fr1 = new Frame(key1, names1, vecs1);
      DKV.put(key1, fr1);
      Val val1 = Rapids.exec("(mean " + key1 + " 0 0)");
      Assert.assertTrue(val1 instanceof ValFrame);
      res1 = val1.getFrame();
      Assert.assertArrayEquals(names1, res1.names());
      Assert.assertArrayEquals(types1, res1.types());
      assertRowFrameEquals(new double[]{0.0, 4.0, 0.6, Double.NaN, 10000030.0, Double.NaN}, res1);

      // Test mean when NaNs are ignored (na_rm = True)
      Val val2 = Rapids.exec("(mean " + key1 + " 1 0)");
      Assert.assertTrue(val2 instanceof ValFrame);
      res2 = val2.getFrame();
      Assert.assertArrayEquals(names1, res2.names());
      Assert.assertArrayEquals(types1, res2.types());
      assertRowFrameEquals(new double[]{0.0, 4.0, 0.6, 2.0, 10000030.0, Double.NaN}, res2);

      // Apply mean to an empty frame
      fr2 = new Frame(key2);
      DKV.put(key2, fr2);
      Val val3 = Rapids.exec("(mean " + key2 + " 0 0)");
      Assert.assertTrue(val3 instanceof ValFrame);
      res3 = val3.getFrame();
      Assert.assertEquals(res3.numCols(), 0);
      Assert.assertEquals(res3.numRows(), 0);

    } finally {
      if (fr1 != null) fr1.delete();
      if (fr2 != null) fr2.delete();
      if (res1 != null) res1.delete();
      if (res2 != null) res2.delete();
      if (res3 != null) res3.delete();
      vi1.remove();
      vd1.remove();
      vd2.remove();
      vd3.remove();
      vs1.remove();
      vt1.remove();
    }
  }

  @Test public void testMeanRows() {
    // Use only axis = 1
    Vec vi1 = TestUtil.ivec(-1, -2, 0, 2, 1);
    Vec vd1 = TestUtil.dvec(1.5, 2.5, 3.5, 4.5, 8.0);
    Vec vd2 = TestUtil.dvec(0.2, 0.4, 0.6, 0.8, 1.0);
    Vec vd3 = TestUtil.dvec(1, 2, Double.NaN, 3, Double.NaN);
    Vec vs1 = TestUtil.svec("a", "b", "c", "d", "e");
    Vec vt1 = TestUtil.tvec(10000000, 10000020, 10000030, 10000040, 10000060);

    Frame fr1 = null, fr2 = null;
    Frame rs1 = null, rs2 = null, rs3 = null;
    try {
      // Test with na_rm = False and no vs1
      Key<Frame> key1 = Key.make();
      fr1 = new Frame(key1, new String[]{"i1", "d1", "d2", "d3"}, new Vec[]{vi1, vd1, vd2, vd3});
      DKV.put(key1, fr1);
      Val val1 = Rapids.exec("(mean " + key1 + " 0 1)");
      Assert.assertTrue(val1 instanceof ValFrame);
      rs1 = val1.getFrame();
      assertColFrameEquals(new double[]{1.7/4, 2.9/4, Double.NaN, 10.3/4, Double.NaN}, rs1);
      Assert.assertEquals("mean", rs1.name(0));

      // Test with na_rm = False and vs1 present
      Key<Frame> key2 = Key.make();
      fr2 = new Frame(key2, new String[]{"i1", "d1", "d2", "d3", "s1"}, new Vec[]{vi1, vd1, vd2, vd3, vs1});
      DKV.put(key2, fr2);
      Val val2 = Rapids.exec("(mean " + key2 + " 0 1)");
      Assert.assertTrue(val2 instanceof ValFrame);
      rs2 = val2.getFrame();
      assertColFrameEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN}, rs2);

      // Test na_rm = true
      Val val3 = Rapids.exec("(mean " + key2 + " 1 1)");
      Assert.assertTrue(val3 instanceof ValFrame);
      rs3 = val3.getFrame();
      assertColFrameEquals(new double[]{1.7/4, 2.9/4, 4.1/3, 10.3/4, 10.0/3}, rs3);

    } finally {
      if (fr1 != null) fr1.delete();
      if (fr2 != null) fr2.delete();
      if (rs1 != null) rs1.delete();
      if (rs2 != null) rs2.delete();
      if (rs3 != null) rs3.delete();
      vi1.remove();
      vd1.remove();
      vd2.remove();
      vd3.remove();
      vs1.remove();
      vt1.remove();
    }
  }

  public static void assertRowFrameEquals(double[] expected, Frame actual) {
    Assert.assertEquals(1, actual.numRows());
    Assert.assertEquals(expected.length, actual.numCols());
    for (int i = 0; i < expected.length; i++) {
      Assert.assertEquals("Wrong average in column " + actual.name(i), expected[i], actual.vec(i).at(0), 1e-8);
    }
  }

  public static void assertColFrameEquals(double[] expected, Frame actual) {
    Assert.assertEquals(1, actual.numCols());
    Assert.assertEquals(expected.length, actual.numRows());
    for (int i = 0; i < expected.length; i++) {
      Assert.assertEquals("Wrong average in row " + i, expected[i], actual.vec(0).at(i), 1e-8);
    }
  }


}
