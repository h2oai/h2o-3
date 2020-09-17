package ai.h2o.targetencoding;

import com.pholser.junit.quickcheck.Mode;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.InRange;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
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

import java.util.Random;

import static ai.h2o.targetencoding.TargetEncoderBroadcastJoin.encodingsToArray;
import static ai.h2o.targetencoding.TargetEncoderHelper.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JUnitQuickcheck.class)
public class TargetEncoderBroadcastJoinTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Property(trials = 2, mode = Mode.EXHAUSTIVE) 
  public void joinPerformsWithoutLoosingOriginalOrderTest(boolean isZeroBasedFoldValues) {
    try {
      Scope.enter();
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
              .withColNames("ColA", "fold", TargetEncoderHelper.NUMERATOR_COL, TargetEncoderHelper.DENOMINATOR_COL)
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c"))
              .withDataForCol(1, isZeroBasedFoldValues ? new long[]{1,0,0}: new long[]{2,1,1})
              .withDataForCol(2, ar(22, 33, 42))
              .withDataForCol(3, ar(44, 66, 84))
              .withChunkLayout(1,1,1)
              .build();

      Frame joined = TargetEncoderBroadcastJoin.join(fr, new int[]{0}, 1, rightFr, new int[]{0}, 1, 2);
      Scope.track(joined);

      assertStringVecEquals(cvec("a", "c", "b"), joined.vec("ColA"));
      assertEquals(22, joined.vec(TargetEncoderHelper.NUMERATOR_COL).at(0), 1e-5);
      assertEquals(44, joined.vec(TargetEncoderHelper.DENOMINATOR_COL).at(0), 1e-5);
      assertEquals(42, joined.vec(TargetEncoderHelper.NUMERATOR_COL).at(1), 1e-5);
      assertEquals(84, joined.vec(TargetEncoderHelper.DENOMINATOR_COL).at(1), 1e-5);
      assertTrue(joined.vec(TargetEncoderHelper.NUMERATOR_COL).isNA(2));
      assertTrue(joined.vec(TargetEncoderHelper.DENOMINATOR_COL).isNA(2));
    } finally {
      Scope.exit();
    }
  }
  
  @Property(trials = 5)
  public void joinWorksWithoutLoosingOriginalOrderTest(@InRange(minInt = 2, maxInt = 10000)int sizeOfLeftFrame,
                                                       @InRange(minInt = 1, maxInt = 1000) int numberOfFolds) {
    try {
      Scope.enter();
      String responseColumnName = "response";
      String[] randomArrOfStrings = randomArrOfStrings(sizeOfLeftFrame);
      String catColumnName = "ColA";
      String foldColumnName = "fold";
      long seed = 1234;

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

      Frame colAEncodings = buildEncodingsFrame(leftFr, 0, 1, 2, 2);
      Scope.track(colAEncodings);
      
      Frame joined = TargetEncoderBroadcastJoin.join(
              leftFr, new int[]{0}, leftFr.find(foldColumnName), 
              colAEncodings, new int[]{0}, colAEncodings.find(foldColumnName), 
              numberOfFolds
      );
      Scope.track(joined);
      
      // Checking that order was preserved
      assertStringVecEquals(leftFr.vec(catColumnName), joined.vec(catColumnName));
      
      // Randomly selecting one row to check merged num and den values against
      int randomIdx = new Random(seed).nextInt(sizeOfLeftFrame);
      double randomColA = joined.vec(catColumnName).at(randomIdx);
      double randomFold  = joined.vec(foldColumnName).at(randomIdx);
      Frame filteredByColA = filterByValue(joined, 0, randomColA);
      Frame filteredByFoldAndColA = filterByValue(filteredByColA, filteredByColA.find(foldColumnName), randomFold);
      Frame encodingsFilteredByColA = filterByValue(colAEncodings, 0, randomColA);
      Frame encodingsFilteredByFoldAndColA = filterByValue(encodingsFilteredByColA, encodingsFilteredByColA.find(foldColumnName), randomFold);
      Scope.track(filteredByColA, filteredByFoldAndColA, encodingsFilteredByColA, encodingsFilteredByFoldAndColA);

      assertEquals(filteredByFoldAndColA.vec(TargetEncoderHelper.NUMERATOR_COL).at(0), 
              encodingsFilteredByFoldAndColA.vec(TargetEncoderHelper.NUMERATOR_COL).at(0), 
              1e-5);
      assertEquals(filteredByFoldAndColA.vec(TargetEncoderHelper.DENOMINATOR_COL).at(0),
              encodingsFilteredByFoldAndColA.vec(TargetEncoderHelper.DENOMINATOR_COL).at(0), 
              1e-5);
    } finally {
      Scope.exit();
    }
  }

  // Shows that with Merge.merge method we will loose original order due to grouping otherwise this(swapping left and right frames) would be a possible workaround 
  @Test(expected = AssertionError.class)
  public void mergeWillUseRightFramesOrderAndGroupByValues() {
    try {
      Scope.enter();
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
      Frame res = Merge.merge(holdoutEncodingMap, fr, new int[]{0}, new int[]{0}, false, levelMaps);
      Scope.track(res);
      
      //We expect this assertion to fail
      assertStringVecEquals(cvec("a", "b", "c", "e", "a"), res.vec("ColB"));
    } finally {
      Scope.exit();
    }
  }


  @Test(expected = AssertionError.class)
  public void foldValuesThatAreBiggerThanIntegerWillCauseExceptionTest() {
    long biggerThanIntMax = Integer.MAX_VALUE + 1;
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "fold", TargetEncoderHelper.NUMERATOR_COL, TargetEncoderHelper.DENOMINATOR_COL)
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c"))
              .withDataForCol(1, ar(biggerThanIntMax, 33, 42))
              .withDataForCol(2, ar(44, 66, 84))
              .withDataForCol(3, ar(88, 132, 168))
              .withChunkLayout(2, 1)
              .build();

      int cardinality = fr.vec("ColA").cardinality();

      encodingsToArray(fr, 0, 1, 2, 3, cardinality, (int) biggerThanIntMax);
    } finally {
      Scope.exit();
    }
  }

  @Property(trials = 100)
  public void foldValuesThatAreInRangeWouldNotCauseExceptionTest(@InRange(minInt = 1, maxInt = 1000)int randomInt) {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "fold", TargetEncoderHelper.NUMERATOR_COL, TargetEncoderHelper.DENOMINATOR_COL)
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c"))
              .withDataForCol(1, ar(0, 1, 2))
              .withDataForCol(2, ar(randomInt, 66, 84))
              .withDataForCol(3, ar(88, 132, randomInt))
              .withChunkLayout(2, 1)
              .build();

      int cardinality = fr.vec("ColA").cardinality();

      encodingsToArray(fr, 0, 1, 2, 3, cardinality, Math.max(randomInt, 42));
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void joinWithoutFoldColumnTest() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar("a", "c", "b"))
              .build();

      Frame rightFr = new TestFrameBuilder()
              .withName("testFrame2")
              .withColNames("ColA", NUMERATOR_COL, DENOMINATOR_COL)
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c"))
              .withDataForCol(1, ar(22, 33, 42))
              .withDataForCol(2, ar(44, 66, 84))
              .withChunkLayout(1,1,1)
              .build();

      Frame joined = TargetEncoderBroadcastJoin.join(fr, new int[]{0}, -1, rightFr, new int[]{0}, -1, 0);
      Scope.track(joined);

      assertStringVecEquals(cvec("a", "c", "b"), joined.vec("ColA"));
      assertVecEquals(vec(22, 42, 33), joined.vec(NUMERATOR_COL), 1e-5);
      assertVecEquals(vec(44, 84, 66), joined.vec(DENOMINATOR_COL), 1e-5);
    } finally {
      Scope.exit();
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

}
