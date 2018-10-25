package water.util;

import hex.CreateFrame;
import hex.Model;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.FrameTestUtil;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.util.ArrayList;
import java.util.Random;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;


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

  // This test is used to test some utilities that I have written to make sure they function as planned.
  @Test
  public void testIDColumnOperationEncoder() {
    Scope.enter();
    Random _rand = new Random();
    int numRows = 1000;
    int rowsToTest = _rand.nextInt(numRows);
    try {
      FrameTestUtil.Create1IDColumn tempO = new FrameTestUtil.Create1IDColumn(numRows);
      Frame f = tempO.doAll(tempO.returnFrame()).returnFrame();
      Scope.track(f);

      ArrayList<Integer> badRows = new FrameTestUtil.CountAllRowsPresented(0, f).doAll(f).findMissingRows();
      assertEquals("All rows should be present but not!", badRows.size(), 0);
      long countAppear = new FrameTestUtil.CountIntValueRows(rowsToTest, 0,
              0, f).doAll(f).getNumberAppear();
      assertEquals("All values should appear only once.", countAppear, 1);

      // delete a row to make sure it is not found again.
      f.remove(rowsToTest);  // row containing value rowsToTest is no longer there.
      countAppear = new FrameTestUtil.CountIntValueRows(2000, 0, 0,
              f).doAll(f).getNumberAppear();
      assertEquals("Value of interest should not been found....", countAppear, 0);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void getColumnIndexByName() {
    Scope.enter();
    try {

      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b"))
              .withDataForCol(1, ard(1, 1))
              .build();
      Scope.track(fr);

      assertEquals(0, fr.find("ColA"));
      assertEquals(1, fr.find("ColB"));

    } finally {
      Scope.exit();
    }
  }

  // I want to test and make sure the CalculateWeightMeanSTD will calculate the correct weighted mean and STD
  // for a column of a dataset using another column as the weight column.  Note that the weighted
  @Test
  public void testCalculateWeightMeanSTD() {
    Scope.enter();
    try {
      Frame trainData = parse_test_file("smalldata/prostate/prostate.csv");
      Scope.track(trainData);
      Vec orig = trainData.remove(trainData.numCols() - 1);
      Vec[] weights = new Vec[2];
      weights[0] = orig.makeCon(2.0); // constant weight, should give same answer as normal

      weights[1] = orig.makeCon(1.0); // increasing weights
      for (int rindex = 0; rindex < orig.length(); rindex++) {
        weights[1].set(rindex, rindex + 1);
      }
      Scope.track(orig);
      Scope.track(weights[0]);
      Scope.track(weights[1]);

      Frame test = new Frame(new String[]{"weight0", "weight1"},weights);
      test._key = Key.make();
      Scope.track(test);
      FrameUtils.CalculateWeightMeanSTD calMeansSTDW1 = new FrameUtils.CalculateWeightMeanSTD();
      calMeansSTDW1.doAll(trainData.vec(0), test.vec(0)); // calculate statistic with constant weight
      // compare with results with no weights, should be the same
      assert Math.abs(trainData.vec(0).mean()-calMeansSTDW1.getWeightedMean())<1e-10:"Error, weighted mean "+
              calMeansSTDW1.getWeightedMean()+ " and expected mean "+trainData.vec(0).mean()+" should equal but not.";
      assert Math.abs(trainData.vec(0).sigma()-calMeansSTDW1.getWeightedSigma())<1e-10:"Error, weighted sigma "+
              calMeansSTDW1.getWeightedSigma()+ " and expected sigma "+trainData.vec(0).sigma()+" should equal but not.";

      FrameUtils.CalculateWeightMeanSTD calMeansSTDW2 = new FrameUtils.CalculateWeightMeanSTD();
      calMeansSTDW2.doAll(trainData.vec(0), test.vec(1)); // calculate statistic with increasing weight
      double[] meanSigma = calWeightedMeanSigma(trainData, test,0, 1);
      assert Math.abs(meanSigma[0]-calMeansSTDW2.getWeightedMean())<1e-10:"Error, weighted mean "+
              calMeansSTDW1.getWeightedMean()+ " and expected mean "+meanSigma[0]+" should equal but not.";
      assert Math.abs(meanSigma[1]-calMeansSTDW2.getWeightedSigma())<1e-10:"Error, weighted sigma "+
              calMeansSTDW1.getWeightedSigma()+ " and expected sigma "+meanSigma[1]+" should equal but not.";

    } finally {
      Scope.exit();
    }
  }

  /*
  calculate weighted mean and sigma from theory.
   */
  public double[] calWeightedMeanSigma(Frame dataFrame,Frame weightF, int targetIndex, int WeightIndex) {
    double[] meanSigma = new double[2];
    int zeroWeightCount = 0;
    double weightSum = 0.0;
    double weightEleSum = 0.0;
    double weightedEleSqSum = 0.0;

    for (int rindex=0; rindex < dataFrame.numRows(); rindex++) {
      double tempWeight = weightF.vec(WeightIndex).at(rindex);
      if (Math.abs(tempWeight) > 0) {
        double tempVal = dataFrame.vec(targetIndex).at(rindex);
        double weightedtempVal = tempVal*tempWeight;

        weightSum += tempWeight;
        weightEleSum += weightedtempVal;
        weightedEleSqSum += weightedtempVal*tempVal;
      } else {
        zeroWeightCount++;
      }
    }
    meanSigma[0] = weightEleSum/weightSum;
    double scale = (dataFrame.numRows()-zeroWeightCount)/(dataFrame.numRows()-zeroWeightCount-1.0);
    meanSigma[1] = Math.sqrt(scale*(weightedEleSqSum/weightSum-meanSigma[0]*meanSigma[0]));

    return meanSigma;
  }

}
