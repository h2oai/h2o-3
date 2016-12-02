package water.rapids.ast.prims.assign;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.vals.ValFrame;

import java.util.Arrays;

import static org.junit.Assert.*;
import static water.rapids.ast.prims.assign.AstRecAssignTestUtils.*;

@RunWith(Parameterized.class)
public class AstRectangleScalarAssignTest extends TestUtil {

  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }

  @Parameterized.Parameters(name= "{index}: {2}")
  public static Iterable<? extends Object> data() { return Arrays.asList(
          new Object[]{1, 5, "Single Row"}, new Object[]{10000, 5, "Large Case"}, new Object[]{43978, 0, "Full Dataset"}
  ); }

  @Parameterized.Parameter()
  public int _nRows;

  @Parameterized.Parameter(1)
  public int _offset;

  @Parameterized.Parameter(2)
  public String _description; // not used

  @Test
  public void testEnumAssign() throws Exception {
    final Frame data = parse_test_file(Key.make("data"), "smalldata/airlines/allyears2k_headers.zip");
    Frame output = null;
    try {
      String[] expectedCats = catVec2array(data.vec(16));
      for (int i = 0; i < _nRows; i++)
        expectedCats[i + _offset] = "MHT";
      String rapids = "(tmp= tst (:= data \"MHT\" [16] [" + _offset + ":" + _nRows + "]))";
      Val val = Rapids.exec(rapids);
      output = val.getFrame();
      String[] actualCats = catVec2array(output.vec(16));
      assertArrayEquals(expectedCats, actualCats);
    } finally {
      data.delete();
      if (output != null) {
        output.delete();
      }
    }
  }

  @Test
  public void testInvalidEnumAssign() throws Exception {
    final Frame data = parse_test_file(Key.make("data"), "smalldata/airlines/allyears2k_headers.zip");
    Frame output = null;
    try {
      String rapids = "(tmp= tst (:= data \"Invalid\" [16] [" + _offset + "" + _nRows + "]))";
      Val val = Rapids.exec(rapids);
      if (val instanceof ValFrame) output = val.getFrame();
      fail("Invalid categorical value shouldn't be assigned");
    } catch (Exception e) {
      assertEquals("Cannot assign value Invalid into a vector of type Enum.", e.getMessage());
    } finally {
      data.delete();
      if (output != null) {
        output.delete();
      }
    }
  }

  @Test
  public void testStringAssign() throws Exception {
    final Frame data = parse_test_file(Key.make("data"), "smalldata/airlines/allyears2k_headers.zip");
    Vec v = data.vec(8);
    data.replace(8, v.toStringVec());
    v.remove();
    DKV.put(data);
    Frame output = null;
    try {
      String[] expectedStrs = strVec2array(data.vec(8));
      for (int i = 0; i < _nRows; i++)
        expectedStrs[i + _offset] = "New Value";
      String rapids = "(tmp= tst (:= data \"New Value\" [8] [" + _offset + ":" + _nRows + "]))";
      Val val = Rapids.exec(rapids);
      output = val.getFrame();
      String[] actualStrs = strVec2array(output.vec(8));
      assertArrayEquals(expectedStrs, actualStrs);
    } finally {
      data.delete();
      if (output != null) {
        output.delete();
      }
    }
  }

  @Test
  public void testNumberAssign() throws Exception {
    final Frame data = parse_test_file(Key.make("data"), "smalldata/airlines/allyears2k_headers.zip");
    Frame output = null;
    try {
      double[] expected = vec2array(data.vec(2));
      for (int i = 0; i < _nRows; i++)
        expected[i + _offset] = 42.0;
      String rapids = "(tmp= tst (:= data 42.0 [2] [" + _offset + ":" + _nRows + "]))";
      Val val = Rapids.exec(rapids);
      output = val.getFrame();
      double[] actual = vec2array(output.vec(2));
      assertArrayEquals(expected, actual, 0.0001);
    } finally {
      data.delete();
      if (output != null) {
        output.delete();
      }
    }
  }

  @Test
  public void testNAAssign() throws Exception {
    final Frame data = parse_test_file(Key.make("data"), "smalldata/airlines/allyears2k_headers.zip");
    Frame output = null;
    try {
      double[] expectedNums = vec2array(data.vec(2));
      String[] expectedCats = catVec2array(data.vec(16));
      for (int i = 0; i < _nRows; i++) {
        expectedNums[i + _offset] = Double.NaN;
        expectedCats[i + _offset] = null;
      }
      String rapids = "(tmp= tst (:= data NA [2,16] [" + _offset + ":" + _nRows + "]))";
      Val val = Rapids.exec(rapids);
      output = val.getFrame();
      double[] actualNums = vec2array(output.vec(2));
      assertArrayEquals(expectedNums, actualNums, 0.0001);
      String[] actualCats = catVec2array(output.vec(16));
      assertArrayEquals(expectedCats, actualCats);
    } finally {
      data.delete();
      if (output != null) {
        output.delete();
      }
    }
  }

}
