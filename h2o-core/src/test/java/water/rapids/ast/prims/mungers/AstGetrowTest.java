package water.rapids.ast.prims.mungers;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
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
}
