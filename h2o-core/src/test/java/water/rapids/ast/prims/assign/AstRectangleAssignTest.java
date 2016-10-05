package water.rapids.ast.prims.assign;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.vals.ValFrame;

import static org.junit.Assert.*;

public class AstRectangleAssignTest extends TestUtil {

  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }

  @Test
  public void testAssignFrameSlice() throws Exception {
    final Frame data = parse_test_file(Key.make("data"), "smalldata/airlines/allyears2k_headers.zip");
    Frame output = null;
    try {
      String rapids = "(tmp= tst (:= data (rows (cols data [8.0, 11.0] ) [10000.0:10000.0] ) [8.0, 11.0] [0.0:10000.0] ) )";
      Val val = Rapids.exec(rapids);
      if (val instanceof ValFrame) {
        output = val.getFrame();
        // categorical column
        String[] expectedCats = catVec2array(data.vec(8));
        System.arraycopy(expectedCats, 10000, expectedCats, 0, 10000);
        String[] actualCats = catVec2array(output.vec(8));
        assertArrayEquals(expectedCats, actualCats);
        // numerical column
        double[] expected = vec2array(data.vec(11));
        System.arraycopy(expected, 10000, expected, 0, 10000);
        double[] actual = vec2array(output.vec(11));
        assertArrayEquals(expected, actual, 0.0001d);
      }
    } finally {
      data.delete();
      if (output != null) {
        output.delete();
      }
    }
  }

  @Test
  public void testAssignFrameSlice_domainsDiffer() throws Exception {
    final Frame data = parse_test_file(Key.make("data"), "smalldata/airlines/allyears2k_headers.zip");
    Frame output = null;
    try {
      String rapids = "(tmp= tst (:= data (rows (cols data [8.0] ) [10000.0:10000.0] ) [16.0] [0.0:10000.0] ) )";
      Val val = Rapids.exec(rapids);
      if (val instanceof ValFrame) output = val.getFrame();
      fail("No exception was thrown, IllegalArgumentException expected");
    } catch (IllegalArgumentException e) {
      assertEquals("Cannot assign to a categorical column with a different domain; source column UniqueCarrier, target column Origin", e.getMessage());
    } finally {
      data.delete();
      if (output != null) {
        output.delete();
      }
    }
  }

  private static double[] vec2array(Vec v) {
    Vec.Reader ovr = v.new Reader();
    assert ovr.length() < Integer.MAX_VALUE;
    final int len = (int) ovr.length();
    double[] array = new double[len];
    for (int i = 0; i < len; i++) array[i] = ovr.at(i);
    return array;
  }

  private static String[] catVec2array(Vec v) {
    double[] raw = vec2array(v);
    String[] cats = new String[raw.length];
    for (int i = 0; i < cats.length; i++) cats[i] = v.factor((long) raw[i]);
    return cats;
  }

}