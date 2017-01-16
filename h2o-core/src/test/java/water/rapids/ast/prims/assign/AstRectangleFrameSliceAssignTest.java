package water.rapids.ast.prims.assign;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.*;
import water.fvec.*;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.vals.ValFrame;

import java.util.Arrays;

import static org.junit.Assert.*;
import static water.rapids.ast.prims.assign.AstRecAssignTestUtils.*;

@RunWith(Parameterized.class)
public class AstRectangleFrameSliceAssignTest extends TestUtil {

  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }

  @Parameterized.Parameters(name= "{index}: nRows = {0}")
  public static Iterable<? extends Object> data() {
    return Arrays.asList(999, 10000);
  }

  @Parameterized.Parameter()
  public int _nRows;

  @Test
  public void testAssignFrameSlice() throws Exception {
    final Frame data = parse_test_file(Key.make("data"), "smalldata/airlines/allyears2k_headers.zip");
    Frame output = null;
    try {
      String rapids = "(tmp= tst (:= data (rows (cols data [8.0, 11.0] ) [10000.0:" + _nRows + ".0] ) [8.0, 11.0] [0.0:" + _nRows + ".0] ) )";
      Val val = Rapids.exec(rapids);
      if (val instanceof ValFrame) {
        output = val.getFrame();
        // categorical column
        String[] expectedCats = catVec2array(data.vec(8));
        System.arraycopy(expectedCats, 10000, expectedCats, 0, _nRows);
        String[] actualCats = catVec2array(output.vec(8));
        assertArrayEquals(expectedCats, actualCats);
        // numerical column
        double[] expected = vec2array(data.vec(11));
        System.arraycopy(expected, 10000, expected, 0, _nRows);
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
      String rapids = "(tmp= tst (:= data (rows (cols data [8.0] ) [10000.0:" + _nRows + ".0] ) [16.0] [0.0:" + _nRows + ".0] ) )";
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

  @Test
  public void testAssignFrameSlice_strings() throws Exception {
    Vec strVec = seqStrVec(10000, 5, 7, 8, 10000);
    final Frame data = new Frame(Key.<Frame>make("data"), null, new Vec[] {strVec});
    DKV.put(data._key, data);
    Frame output = null;
    try {
      String rapids = "(tmp= tst (:= data (rows (cols data [0.0] ) [10000.0:" + _nRows + ".0] ) [0.0] [0.0:" + _nRows + ".0] ) )";
      Val val = Rapids.exec(rapids);
      if (val instanceof ValFrame) {
        output = val.getFrame();
        String[] expected = strVec2array(strVec);
        System.arraycopy(expected, 10000, expected, 0, _nRows);
        String[] actual = strVec2array(output.vec(0));
        assertArrayEquals(expected, actual);
      }
    } finally {
      strVec.remove();
      data.delete();
      if (output != null) {
        output.delete();
      }
    }
  }

}