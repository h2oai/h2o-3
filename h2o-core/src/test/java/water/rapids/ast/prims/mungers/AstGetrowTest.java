package water.rapids.ast.prims.mungers;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.vals.ValRow;
import water.util.ArrayUtils;

import static org.junit.Assert.*;

/**
 */
public class AstGetrowTest extends TestUtil {

  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }

  /** Test that in normal case the result has the correct type and value. */
  @Test public void TestGetrow() {
    Frame f = null;
    try {
      f = ArrayUtils.frame(ar("A", "B", "C", "D", "E"), ard(1.0, -3, 12, 1000000, Double.NaN));
      Val v = Rapids.exec("(getrow " + f._key + ")");
      assertTrue(v instanceof ValRow);
      double[] row = v.getRow();
      assertEquals(row.length, 5);
      assertArrayEquals(ard(1.0, -3, 12, 1000000, Double.NaN), row, 1e-8);
    } finally {
      if (f != null) f.delete();
    }
  }

  /** Test that an exception is thrown when number of rows in the frame is > 1. */
  @Test public void TestGetrow2() {
    Frame f = null;
    try {
      f = ArrayUtils.frame(ard(-3, 4), ard(0, 1));
      Val v2 = null;
      try {
        v2 = Rapids.exec("(getrow " + f._key + ")");
      } catch (IllegalArgumentException ignored) {}
      assertNull("getrow is allowed only for single-row frames", v2);
    } finally {
      if (f != null) f.delete();
    }
  }

  /** Test columns of various types */
  @Test public void TestGetrow3() {
    Frame f = null;
    Vec[] vv = null;
    try {
      f = ArrayUtils.frame(ar("D1", "D2"), ard(0, 1));
      vv = f.vec(0).makeCons(5, 0, ar(ar("N", "Y"), ar("a", "b", "c"), null, null, null),
              ar(Vec.T_CAT, Vec.T_CAT, Vec.T_TIME, Vec.T_STR, Vec.T_UUID));
      f.add(ar("C1", "C2", "T1", "S1", "U1"), vv);
      Val v = Rapids.exec("(getrow " + f._key + ")");
      assertTrue(v instanceof ValRow);
      double[] row = v.getRow();
      assertEquals(7, row.length);
      assertArrayEquals(ard(0, 1, Double.NaN, Double.NaN, 0, Double.NaN, Double.NaN), row, 1e-8);
    } finally {
      if (f != null) f.delete();
      if (vv != null)
        for (Vec v : vv)
          v.remove();
    }
  }
}
