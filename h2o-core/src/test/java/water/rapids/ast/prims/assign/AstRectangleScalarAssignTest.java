package water.rapids.ast.prims.assign;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.vals.ValFrame;

import java.util.Arrays;
import java.util.Random;

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
    final Frame data = makeTestFrame();
    Frame output = null;
    try {
      String[] expectedCats = catVec2array(data.vec(2));
      for (int i = 0; i < _nRows; i++)
        expectedCats[i + _offset] = "c2";
      String rapids = "(tmp= tst (:= data \"c2\" [2] [" + _offset + ":" + _nRows + "]))";
      Val val = Rapids.exec(rapids);
      output = val.getFrame();
      String[] actualCats = catVec2array(output.vec(2));
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
    final Frame data = makeTestFrame();
    Frame output = null;
    try {
      String rapids = "(tmp= tst (:= data \"Invalid\" [2] [" + _offset + "" + _nRows + "]))";
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
    final Frame data = makeTestFrame();
    Frame output = null;
    try {
      String[] expectedStrs = strVec2array(data.vec(1));
      for (int i = 0; i < _nRows; i++)
        expectedStrs[i + _offset] = "New Value";
      String rapids = "(tmp= tst (:= data \"New Value\" [1] [" + _offset + ":" + _nRows + "]))";
      Val val = Rapids.exec(rapids);
      output = val.getFrame();
      String[] actualStrs = strVec2array(output.vec(1));
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
    final Frame data = makeTestFrame();
    Frame output = null;
    try {
      double[] expected = vec2array(data.vec(0));
      for (int i = 0; i < _nRows; i++)
        expected[i + _offset] = 42.0;
      String rapids = "(tmp= tst (:= data 42.0 [0] [" + _offset + ":" + _nRows + "]))";
      Val val = Rapids.exec(rapids);
      output = val.getFrame();
      double[] actual = vec2array(output.vec(0));
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
    final Frame data = makeTestFrame();
    Frame output = null;
    try {
      double[] expectedNums = vec2array(data.vec(0));
      String[] expectedCats = catVec2array(data.vec(2));
      for (int i = 0; i < _nRows; i++) {
        expectedNums[i + _offset] = Double.NaN;
        expectedCats[i + _offset] = null;
      }
      String rapids = "(tmp= tst (:= data NA [0,2] [" + _offset + ":" + _nRows + "]))";
      Val val = Rapids.exec(rapids);
      output = val.getFrame();
      double[] actualNums = vec2array(output.vec(0));
      assertArrayEquals(expectedNums, actualNums, 0.0001);
      String[] actualCats = catVec2array(output.vec(2));
      assertArrayEquals(expectedCats, actualCats);
    } finally {
      data.delete();
      if (output != null) {
        output.delete();
      }
    }
  }

  private Frame makeTestFrame() {
    Random rnd = new Random();
    final int len = 43978;
    double numData[] = new double[len];
    String[] strData = new String[len];
    String[] catData = new String[len];
    for (int i = 0; i < len; i++) {
      numData[i] = rnd.nextDouble();
      strData[i] = "s" + Character.toString((char) ('A' + rnd.nextInt('Z' - 'A')));
      catData[i] = "c" + Character.toString((char) ('0' + rnd.nextInt('9' - '0')));
    }
    return new TestFrameBuilder()
       .withName("data")
       .withColNames("Num", "Str", "Cat")
       .withVecTypes(Vec.T_NUM, Vec.T_STR, Vec.T_CAT)
       .withDataForCol(0, numData)
       .withDataForCol(1, strData)
       .withDataForCol(2, catData)
       .withChunkLayout(10000, 10000, 20000, 3978)
       .build();
  }

}
