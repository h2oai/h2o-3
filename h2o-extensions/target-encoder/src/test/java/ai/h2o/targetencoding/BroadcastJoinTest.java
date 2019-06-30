package ai.h2o.targetencoding;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.InRange;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.CategoricalWrappedVec;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Merge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JUnitQuickcheck.class)
public class BroadcastJoinTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  private Frame fr = null;

  @Test
  public void joinPerformsWithoutLoosingOriginalOrderTest() {

    Frame rightFr = null;
    Vec emptyNumerator = null;
    Vec emptyDenominator = null;
    try {

      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "fold")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "c", "b"))
              .withDataForCol(1, ar(2, 1, 2))
              .build();

      rightFr = new TestFrameBuilder()
              .withName("testFrame2")
              .withColNames("ColA", "fold", TargetEncoder.NUMERATOR_COL_NAME, TargetEncoder.DENOMINATOR_COL_NAME)
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c"))
              .withDataForCol(1, ar(2, 1, 1))
              .withDataForCol(2, ar(22, 33, 42))
              .withDataForCol(3, ar(44, 66, 84))
              .build();

      emptyNumerator = fr.anyVec().makeCon(0);
      fr.add(TargetEncoder.NUMERATOR_COL_NAME, emptyNumerator);
      emptyDenominator = fr.anyVec().makeCon(0);
      fr.add(TargetEncoder.DENOMINATOR_COL_NAME, emptyDenominator);
      
      Frame joined = BroadcastJoinForTargetEncoder.join(fr, new int[]{0}, 1, rightFr, new int[]{0}, 1, 2);

      Scope.enter();
      assertStringVecEquals(cvec("a", "c", "b"), joined.vec("ColA"));
      assertEquals(22, joined.vec(TargetEncoder.NUMERATOR_COL_NAME).at(0), 1e-5);
      assertEquals(44, joined.vec(TargetEncoder.DENOMINATOR_COL_NAME).at(0), 1e-5);
      assertEquals(42, joined.vec(TargetEncoder.NUMERATOR_COL_NAME).at(1), 1e-5);
      assertEquals(84, joined.vec(TargetEncoder.DENOMINATOR_COL_NAME).at(1), 1e-5);
      assertTrue(joined.vec(TargetEncoder.NUMERATOR_COL_NAME).isNA(2));
      assertTrue(joined.vec(TargetEncoder.DENOMINATOR_COL_NAME).isNA(2));
      Scope.exit();
    } finally {
      if(rightFr != null) rightFr.delete();
    }
  }

  // Shows that with Merge.merge method we will loose original order due to grouping otherwise this(swapping left and right frames) would be a possible workaround 
  @Test(expected = AssertionError.class)
  public void mergeWillUseRightFramesOrderAndGroupByValues() {
    Scope.enter();
    Frame res = null;
    try {
      Frame fr = new TestFrameBuilder()
              .withName("leftFrame")
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c", "e", "a"))
              .withDataForCol(1, ard(-1, 2, 3, 4, 7))
              .build();

      Frame holdoutEncodingMap = new TestFrameBuilder()
              .withName("holdoutEncodingMap")
              .withColNames("ColB", "ColC")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("c", "a", "e", "b"))
              .withDataForCol(1, ard(2, 3, 4, 5))
              .build();

      //Note: we end up with the order from the `right` frame
      int[][] levelMaps = {CategoricalWrappedVec.computeMap(holdoutEncodingMap.vec(0).domain(), fr.vec(0).domain())};
      res = Merge.merge(holdoutEncodingMap, fr, new int[]{0}, new int[]{0}, false, levelMaps);
      printOutFrameAsTable(res, false, res.numRows());
      
      //We expect this assertion to fail
      assertStringVecEquals(cvec("a", "b", "c", "e", "a"), res.vec("ColB"));
    } finally {
      res.delete();
      Scope.exit();
    }
  }


  @Test(expected = AssertionError.class)
  public void foldValuesThatAreBiggerThanIntegerWillCauseExceptionTest() {
    long biggerThanIntMax = Integer.MAX_VALUE + 1;
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "fold", TargetEncoder.NUMERATOR_COL_NAME, TargetEncoder.DENOMINATOR_COL_NAME)
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "c"))
            .withDataForCol(1, ar(biggerThanIntMax, 33, 42))
            .withDataForCol(2, ar(44, 66, 84))
            .withDataForCol(3, ar(88, 132, 168))
            .withChunkLayout(2,1)
            .build();

    int cardinality = fr.vec("ColA").cardinality();
    int[][] levelMaps = {CategoricalWrappedVec.computeMap(fr.vec(0).domain(), fr.vec(0).domain())};
    
    int[][] encodingDataArray = new BroadcastJoinForTargetEncoder.FrameWithEncodingDataToArray(0, 1, 2, 3, cardinality, (int)biggerThanIntMax, levelMaps)
            .doAll(fr)
            .getEncodingDataArray();
  }

  @Property(trials = 100)
  public void foldValuesThatAreInRangeWouldNotCauseExceptionTest(@InRange(minInt = 1, maxInt = 1000)int randomInt) {
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "fold", TargetEncoder.NUMERATOR_COL_NAME, TargetEncoder.DENOMINATOR_COL_NAME)
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "c"))
            .withDataForCol(1, ar(randomInt, 33, 42))
            .withDataForCol(2, ar(44, 66, 84))
            .withDataForCol(3, ar(88, 132, 168))
            .withChunkLayout(2,1)
            .build();

    int cardinality = fr.vec("ColA").cardinality();
    int[][] levelMaps = {CategoricalWrappedVec.computeMap(fr.vec(0).domain(), fr.vec(0).domain())};

    new BroadcastJoinForTargetEncoder.FrameWithEncodingDataToArray(0, 1, 2, 3, cardinality, Math.max(randomInt, 42), levelMaps)
            .doAll(fr)
            .getEncodingDataArray();
  }
    
  @After
  public void afterEach() {
    if (fr != null) fr.delete();
  }

}
