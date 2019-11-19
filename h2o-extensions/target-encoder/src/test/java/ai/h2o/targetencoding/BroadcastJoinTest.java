package ai.h2o.targetencoding;

import com.pholser.junit.quickcheck.Mode;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.InRange;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.After;
import org.junit.Assume;
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
import water.util.IcedHashMapGeneric;

import java.util.Random;

import static ai.h2o.targetencoding.TargetEncoder.DENOMINATOR_COL_NAME;
import static ai.h2o.targetencoding.TargetEncoder.NUMERATOR_COL_NAME;
import static ai.h2o.targetencoding.TargetEncoderFrameHelper.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JUnitQuickcheck.class)
public class BroadcastJoinTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  private Frame fr = null;

  
  @Property(trials = 2, mode = Mode.EXHAUSTIVE) 
  public void joinPerformsWithoutLoosingOriginalOrderTest(boolean isZeroBasedFoldValues) {

    Scope.enter();
    try {

      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "fold")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "c", "b"))
              .withDataForCol(1, isZeroBasedFoldValues ? new long[]{1,0,1}: new long[]{2,1,2})
              .withChunkLayout(1,1,1)
              .build();

      Frame rightFr = new TestFrameBuilder()
              .withName("testFrame2")
              .withColNames("ColA", "fold", TargetEncoder.NUMERATOR_COL_NAME, TargetEncoder.DENOMINATOR_COL_NAME)
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c"))
              .withDataForCol(1, isZeroBasedFoldValues ? new long[]{1,0,0}: new long[]{2,1,1})
              .withDataForCol(2, ar(22, 33, 42))
              .withDataForCol(3, ar(44, 66, 84))
              .withChunkLayout(1,1,1)
              .build();

      Vec emptyNumerator = fr.anyVec().makeCon(0);
      fr.add(TargetEncoder.NUMERATOR_COL_NAME, emptyNumerator);
      Vec emptyDenominator = fr.anyVec().makeCon(0);
      fr.add(TargetEncoder.DENOMINATOR_COL_NAME, emptyDenominator);
      Scope.track(emptyNumerator);
      Scope.track(emptyDenominator);
      
      Frame joined = BroadcastJoinForTargetEncoder.join(fr, new int[]{0}, 1, rightFr, new int[]{0}, 1, 2);

      assertStringVecEquals(cvec("a", "c", "b"), joined.vec("ColA"));
      assertEquals(22, joined.vec(TargetEncoder.NUMERATOR_COL_NAME).at(0), 1e-5);
      assertEquals(44, joined.vec(TargetEncoder.DENOMINATOR_COL_NAME).at(0), 1e-5);
      assertEquals(42, joined.vec(TargetEncoder.NUMERATOR_COL_NAME).at(1), 1e-5);
      assertEquals(84, joined.vec(TargetEncoder.DENOMINATOR_COL_NAME).at(1), 1e-5);
      assertTrue(joined.vec(TargetEncoder.NUMERATOR_COL_NAME).isNA(2));
      assertTrue(joined.vec(TargetEncoder.DENOMINATOR_COL_NAME).isNA(2));
    } finally {
      Scope.exit();
    }
  }
  
  @Property(trials = 5)
  public void joinWorksWithoutLoosingOriginalOrderTest(@InRange(minInt = 2, maxInt = 10000)int sizeOfLeftFrame,
                                                       @InRange(minInt = 1, maxInt = 1000) int numberOfFolds) {
    Scope.enter();
    long seed = 1234;
    IcedHashMapGeneric<String, Frame> encodingMap = null;
    try {

      String responseColumnName = "response";
      String[] randomArrOfStrings = randomArrOfStrings(sizeOfLeftFrame);
      String catColumnName = "ColA";
      String foldColumnName = "fold";

      Frame leftFr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames(catColumnName, responseColumnName)
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, randomArrOfStrings)
              .withRandomBinaryDataForCol(1, sizeOfLeftFrame, seed)
              .withChunkLayout(sizeOfLeftFrame / 2, sizeOfLeftFrame - (sizeOfLeftFrame / 2))
              .build();

      Assume.assumeTrue(leftFr.vec(responseColumnName).cardinality() == 2);

      addKFoldColumn(leftFr, foldColumnName, numberOfFolds, 1234L);
      Assume.assumeTrue(leftFr.vec(foldColumnName).clone().toCategoricalVec().cardinality() == numberOfFolds);

      TargetEncoder tec = new TargetEncoder(new String[]{catColumnName});

      encodingMap = tec.prepareEncodingMap(leftFr, responseColumnName, foldColumnName);
      Frame encodingMapForColA = encodingMap.get(catColumnName);

      Vec emptyNumerator = leftFr.anyVec().makeCon(0);
      leftFr.add(TargetEncoder.NUMERATOR_COL_NAME, emptyNumerator);
      Vec emptyDenominator = leftFr.anyVec().makeCon(0);
      leftFr.add(TargetEncoder.DENOMINATOR_COL_NAME, emptyDenominator);
      Scope.track(emptyNumerator);
      Scope.track(emptyDenominator);

      Frame joined = BroadcastJoinForTargetEncoder.join(leftFr, new int[]{0}, leftFr.find(foldColumnName), encodingMapForColA, new int[]{0}, encodingMapForColA.find(foldColumnName), numberOfFolds);
      Scope.track(joined);
      
      // Checking that order was preserved
      assertStringVecEquals(leftFr.vec(catColumnName), joined.vec(catColumnName));
      
      // Randomly selecting one row to check merged num and den values against
      int randomIdx = new Random(seed).nextInt(sizeOfLeftFrame);
      double randomColAValueFromLeftFr = leftFr.vec(catColumnName).at(randomIdx);
      double randomFoldValueFromLeftFr  = leftFr.vec(foldColumnName).at(randomIdx);
      Frame filteredByColA = filterByValue(leftFr, 0, randomColAValueFromLeftFr);
      Frame filteredByFoldAndColAColumns = filterByValue(filteredByColA, filteredByColA.find(foldColumnName), randomFoldValueFromLeftFr);
      
      Frame filteredByColAFromEM = filterByValue(encodingMapForColA, 0, randomColAValueFromLeftFr);
      Frame filteredByFoldAndColAColumnsFromEM = filterByValue(filteredByColAFromEM, filteredByColAFromEM.find(foldColumnName), randomFoldValueFromLeftFr);

      Scope.track(filteredByColA, filteredByFoldAndColAColumns, filteredByColAFromEM, filteredByFoldAndColAColumnsFromEM);

      assertEquals(filteredByFoldAndColAColumns.vec(TargetEncoder.NUMERATOR_COL_NAME).at(0), filteredByFoldAndColAColumnsFromEM.vec(TargetEncoder.NUMERATOR_COL_NAME).at(0), 1e-5);
      assertEquals(filteredByFoldAndColAColumns.vec(TargetEncoder.DENOMINATOR_COL_NAME).at(0), filteredByFoldAndColAColumnsFromEM.vec(TargetEncoder.DENOMINATOR_COL_NAME).at(0), 1e-5);
    } finally {
      if(encodingMap != null ) encodingMapCleanUp(encodingMap);
      Scope.exit();
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

    int[][] encodingDataArray = new BroadcastJoinForTargetEncoder.FrameWithEncodingDataToArray(0, 1, 2, 3, cardinality, (int)biggerThanIntMax)
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
            .withDataForCol(1, ar(0, 1, 2))
            .withDataForCol(2, ar(randomInt, 66, 84))
            .withDataForCol(3, ar(88, 132, randomInt))
            .withChunkLayout(2,1)
            .build();

    int cardinality = fr.vec("ColA").cardinality();

    new BroadcastJoinForTargetEncoder.FrameWithEncodingDataToArray(0, 1, 2, 3, cardinality, Math.max(randomInt, 42))
            .doAll(fr)
            .getEncodingDataArray();
  }

  @Test
  public void joinWithoutFoldColumnTest() {

    Frame rightFr = null;
    Vec emptyNumerator = null;
    Vec emptyDenominator = null;
    try {

      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar("a", "c", "b"))
              .build();

      rightFr = new TestFrameBuilder()
              .withName("testFrame2")
              .withColNames("ColA", NUMERATOR_COL_NAME, DENOMINATOR_COL_NAME)
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c"))
              .withDataForCol(1, ar(22, 33, 42))
              .withDataForCol(2, ar(44, 66, 84))
              .withChunkLayout(1,1,1)
              .build();

      emptyNumerator = Vec.makeZero(fr.numRows());
      fr.add(NUMERATOR_COL_NAME, emptyNumerator);
      emptyDenominator = Vec.makeZero(fr.numRows());
      fr.add(DENOMINATOR_COL_NAME, emptyDenominator);

      Frame joined = BroadcastJoinForTargetEncoder.join(fr, new int[]{0}, -1, rightFr, new int[]{0}, -1, 0);

      Scope.enter();
      assertStringVecEquals(cvec("a", "c", "b"), joined.vec("ColA"));
      assertVecEquals(vec(22, 42, 33), joined.vec(NUMERATOR_COL_NAME), 1e-5);
      assertVecEquals(vec(44, 84, 66), joined.vec(DENOMINATOR_COL_NAME), 1e-5);
      Scope.exit();
    } finally {
      if(rightFr != null) rightFr.delete();
    }
  }
  
  private String[] randomArrOfStrings(int size) {
    String[] arr = new String[size];
    Random rg = new Random();
    int cardinality = size / 2;
    for (int a = 0; a < size; a++) {
      arr[a] = Integer.toString(rg.nextInt(Math.max(1, cardinality)));
    }
    return arr;
  }

  @After
  public void afterEach() {
    if (fr != null) fr.delete();
  }

}
