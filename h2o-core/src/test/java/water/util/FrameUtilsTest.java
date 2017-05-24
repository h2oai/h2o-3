package water.util;

import hex.CreateFrame;
import hex.Model;
import hex.ToEigenVec;
import org.junit.Assert;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.BeforeClass;
import org.junit.Test;
import org.hamcrest.CoreMatchers;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.util.HashMap;


/**
 * Test FrameUtils interface.
 */
public class FrameUtilsTest extends TestUtil {
  @BeforeClass
  static public void setup() {  stall_till_cloudsize(1); }

  @Test
  public void testCategoricalColumnsBinaryEncoding() {
    int numNoncatColumns = 10;
    int[] catSizes       = {2, 3, 4, 5, 7, 8, 9, 15, 16, 30, 31, 127, 255, 256};
    int[] expBinarySizes = {2, 2, 3, 3, 3, 4, 4,  4,  5,  5,  5,   7,   8,   9};
    String[] catNames = {"duo", "Trinity", "Quart", "Star rating", "Dwarves", "Octopus legs", "Planets",
      "Game of Fifteen", "Halfbyte", "Days30", "Days31", "Periodic Table", "AlmostByte", "Byte"};
    Assert.assertEquals(catSizes.length, expBinarySizes.length);
    Assert.assertEquals(catSizes.length, catNames.length);
    int totalExpectedColumns = numNoncatColumns;
    for (int s : expBinarySizes) totalExpectedColumns += s;

    Key<Frame> frameKey = Key.make();
    CreateFrame cf = new CreateFrame(frameKey);
    cf.rows = 100;
    cf.cols = numNoncatColumns;
    cf.categorical_fraction = 0.0;
    cf.integer_fraction = 0.3;
    cf.binary_fraction = 0.1;
    cf.time_fraction = 0.2;
    cf.string_fraction = 0.1;
    Frame mainFrame = cf.execImpl().get();
    assert mainFrame != null : "Unable to create a frame";
    Frame[] auxFrames = new Frame[catSizes.length];
    Frame transformedFrame = null;
    try {
      for (int i = 0; i < catSizes.length; ++i) {
        CreateFrame ccf = new CreateFrame();
        ccf.rows = 100;
        ccf.cols = 1;
        ccf.categorical_fraction = 1;
        ccf.integer_fraction = 0;
        ccf.binary_fraction = 0;
        ccf.time_fraction = 0;
        ccf.string_fraction = 0;
        ccf.factors = catSizes[i];
        auxFrames[i] = ccf.execImpl().get();
        auxFrames[i]._names[0] = catNames[i];
        mainFrame.add(auxFrames[i]);
      }
      FrameUtils.CategoricalBinaryEncoder cbed = new FrameUtils.CategoricalBinaryEncoder(mainFrame, null);
      transformedFrame = cbed.exec().get();
      assert transformedFrame != null : "Unable to transform a frame";

      Assert.assertEquals("Wrong number of columns after converting to binary encoding",
          totalExpectedColumns, transformedFrame.numCols());
      for (int i = 0; i < numNoncatColumns; ++i) {
        Assert.assertEquals(mainFrame.name(i), transformedFrame.name(i));
        Assert.assertEquals(mainFrame.types()[i], transformedFrame.types()[i]);
      }
      for (int i = 0, colOffset = numNoncatColumns; i < catSizes.length; colOffset += expBinarySizes[i++]) {
        for (int j = 0; j < expBinarySizes[i]; ++j) {
          int jj = colOffset + j;
          Assert.assertTrue("A categorical column should be transformed into several binary ones (col "+jj+")",
              transformedFrame.vec(jj).isBinary());
          Assert.assertThat("Transformed categorical column should carry the name of the original column",
              transformedFrame.name(jj), CoreMatchers.startsWith(mainFrame.name(numNoncatColumns+i) + ":"));
        }
      }
    } catch (Throwable e) {
      e.printStackTrace();
      throw e;
    } finally {
      mainFrame.delete();
      if (transformedFrame != null) transformedFrame.delete();
      for (Frame f : auxFrames)
        if (f != null)
          f.delete();
    }
  }

  @Test
  public void testOneHotExplicitEncoder() {
    Scope.enter();
    try {
      Frame f = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("NumCol", "CatCol1", "CatCol2")
              .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ard(Double.NaN, 1, 2, 3, 4, 5.6, 7))
              .withDataForCol(1, ar("A", "B", "C", "E", "F", "I", "J"))
              .withDataForCol(2, ar("A", "B", "A", "C", null, "B", "A"))
              .withChunkLayout(2, 2, 2, 1)
              .build();
      Frame result = FrameUtils.categoricalEncoder(f, new String[]{"CatCol1"},
              Model.Parameters.CategoricalEncodingScheme.OneHotExplicit, null, -1);
      Scope.track(result);
      assertArrayEquals(
              new String[]{"NumCol", "CatCol2.A", "CatCol2.B", "CatCol2.C", "CatCol2.missing(NA)", "CatCol1"},
              result.names());
      // check that original columns are the same
      assertVecEquals(f.vec("NumCol"), result.vec("NumCol"), 1e-6);
      assertCatVecEquals(f.vec("CatCol1"), result.vec("CatCol1"));
      // validate 1-hot encoding
      Vec catVec = f.vec("CatCol2");
      for (long i = 0; i < catVec.length(); i++) {
        String hotCol = "CatCol2." + (catVec.isNA(i) ? "missing(NA)" : catVec.domain()[(int) catVec.at8(i)]);
        for (String col : result.names())
          if (col.startsWith("CatCol2.")) {
            long expectedVal = hotCol.equals(col) ? 1 : 0;
            assertEquals("Value of column " + col + " in row = " + i + " matches", expectedVal, result.vec(col).at8(i));
          }
      }
    } finally {
      Scope.exit();
    }
  }

}
