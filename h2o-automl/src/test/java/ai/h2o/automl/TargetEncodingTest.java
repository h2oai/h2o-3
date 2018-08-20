package ai.h2o.automl;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.*;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;
import water.util.TwoDimTable;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.*;
import static water.TestUtil.assertStringVecEquals;
import static water.TestUtil.cvec;

public class TargetEncodingTest extends TestUtil{


  @BeforeClass public static void setup() {
    stall_till_cloudsize(1);
  }

  private Frame fr = null;


    @Test(expected = IllegalStateException.class)
    public void targetEncoderPrepareEncodingFrameValidationDataIsNotNullTest() {

        TargetEncoder tec = new TargetEncoder();
        String[] teColumns = {"0"};

        tec.prepareEncodingMap(null, teColumns, "2", null);
    }


    @Test(expected = IllegalStateException.class)
    public void targetEncoderPrepareEncodingFrameValidationTEColumnsIsNotEmptyTest() {

        TargetEncoder tec = new TargetEncoder();
        String[] teColumns = {};

        tec.prepareEncodingMap(null, teColumns, "2", null);

    }

    @Test
    public void changeKeyFrameTest() {
      Frame res = null;
      try {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA")
                .withVecTypes(Vec.T_NUM)
                .withDataForCol(0, ard(1, 2))
                .build();
        String tree = "( append testFrame 42 'appended' )";
        Val val = Rapids.exec(tree);
        res = val.getFrame();
        res._key = fr._key;
        DKV.put(fr._key, res);

      } finally {
        res.delete();
      }
    }

    @Test
    public void allTEColumnsAreCategoricalTest() {

        TestFrameBuilder baseBuilder = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC")
                .withDataForCol(0, ar("1", "0"))
                .withDataForCol(2, ar("1", "6"));

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {0, 1};
        Map<String, Frame> encodingMap = null;

        fr = baseBuilder
                .withDataForCol(1, ar(0, 1))
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
                .build();
        try {
          tec.prepareEncodingMap(fr, teColumns, 2, null);
            fail();
        } catch (IllegalStateException ex) {
            assertEquals("Argument 'columnsToEncode' should contain only names of categorical columns", ex.getMessage());
        }

        fr = baseBuilder
                .withDataForCol(1, ar("a", "b"))
                .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT)
                .build();

        try {
          encodingMap = tec.prepareEncodingMap(fr, teColumns, 2, null);
        } catch (IllegalStateException ex) {
            fail(String.format("All columns were categorical but something else went wrong: %s", ex.getMessage()));
        }

      encodingMapCleanUp(encodingMap);
    }

    @Test
    public void prepareEncodingMapForKFoldCaseTest() {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB", "ColC", "fold_column")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "b", "b"))
              .withDataForCol(1, ard(1, 1, 4, 7))
              .withDataForCol(2, ar("2", "6", "6", "6"))
              .withDataForCol(3, ar(1, 2, 2, 3))
              .build();

      TargetEncoder tec = new TargetEncoder();
      int[] teColumns = {0};

      Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

      Frame colAEncoding = targetEncodingMap.get("ColA");

      Vec vec1 = vec(0, 2, 1);
      assertVecEquals(vec1, colAEncoding.vec(2), 1e-5);
      Vec vec2 = vec(1, 2, 1);
      assertVecEquals(vec2, colAEncoding.vec(3), 1e-5);

      vec1.remove();
      vec2.remove();
      encodingMapCleanUp(targetEncodingMap);
    }

    @Test
    public void prepareEncodingMapWithoutFoldColumnCaseTest() {
      Scope.enter();
      Map<String, Frame> targetEncodingMap = null;
      try {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
                .withDataForCol(0, ar("a", "b", "b", "b"))
                .withDataForCol(1, ard(1, 1, 4, 7))
                .withDataForCol(2, ar("2", "6", "6", "6"))
                .build();

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {0};

        targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2);

        Frame colAEncoding = targetEncodingMap.get("ColA");
        Scope.track(colAEncoding);

        assertVecEquals(vec(0, 3), colAEncoding.vec(1), 1e-5);
        assertVecEquals(vec(1, 3), colAEncoding.vec(2), 1e-5);
      } finally {
        Scope.exit();
      }

    }

  @Test
  public void cloningFrameTest() { //TODO Move it to FrameTest
    Scope.enter();
    try {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b"))
              .withDataForCol(1, ar("c", "d"))
              .build();

      Frame newFrame = fr.deepCopy(Key.make().toString());

      fr.delete();
      assertStringVecEquals(newFrame.vec("ColB"), cvec("c", "d"));

    } finally {
      Scope.exit();
    }
  }


    @Test
    public void prepareEncodingMapForKFoldCaseWithSomeOfTheTEValuesRepresentedOnlyInOneFold_Test() {
        //TODO like in te_encoding_possible_bug_demo.R test
    }

    // ------------------------ KFold holdout type -------------------------------------------------------------------//
    @Test
    public void targetEncoderKFoldHoldoutApplyingTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "fold_column")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT,  Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "b", "b", "a"))
                .withDataForCol(1, ard(1, 1, 4, 7, 4))
                .withDataForCol(2, ar("2", "6", "6", "6", "6"))
                .withDataForCol(3, ar(1, 2, 2, 3, 2))
                .build();

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {0};

        Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

        Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.KFold, 3, false, 0, 1234.0);

      Vec expected = vec(1, 0, 1, 1, 1);
      assertVecEquals(expected, resultWithEncoding.vec(4), 1e-5);

      expected.remove();
      resultWithEncoding.delete();
      encodingMapCleanUp(targetEncodingMap);
    }

    @Test
    public void getUniqueValuesOfTheFoldColumnTest() {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("fold_column")
              .withVecTypes( Vec.T_NUM)
              .withDataForCol(0, ar(1, 2, 2, 3, 2))
              .build();

      TargetEncoder tec = new TargetEncoder();

      long[] result = tec.getUniqueValuesOfTheFoldColumn(fr, 0);
      Arrays.sort(result);
      assertArrayEquals( ar(1L, 2L, 3L), result);
    }

    @Test
    public void appendColumnTest() {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_NUM)
              .withDataForCol(0, ar(1))
              .build();

      TargetEncoder tec = new TargetEncoder();

      String appendedColumnName = "appended42";
      Frame withAppended = tec.appendColumn(fr, 42, appendedColumnName);
      assertEquals(42, withAppended.vec(appendedColumnName).at(0), 1e-5);

      withAppended.delete();
    }

    @Test
    public void rbindTest() {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_NUM)
              .withDataForCol(0, ar(1))
              .build();

      TargetEncoder tec = new TargetEncoder();

      Frame result = tec.rBind(null, fr);
      assertEquals(fr._key, result._key);

      Frame fr2 = new TestFrameBuilder()
              .withName("testFrame2")
              .withColNames("ColA")
              .withVecTypes(Vec.T_NUM)
              .withDataForCol(0, ar(42))
              .build();

      Frame result2 = tec.rBind(fr, fr2);

      assertEquals(1, result2.vec("ColA").at(0), 1e-5);
      assertEquals(42, result2.vec("ColA").at(1), 1e-5);

      fr2.delete();
      result2.delete();
    }

    @Test
    public void targetEncoderKFoldHoldout_WithNonFirstColumnToEncode_ApplyingTest() {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColA2", "ColB", "ColC", "fold_column")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ar("a", "b", "b", "b", "a"))
              .withDataForCol(2, ard(1, 1, 4, 7, 4))
              .withDataForCol(3, ar("2", "6", "6", "6", "6"))
              .withDataForCol(4, ar(1, 2, 2, 3, 2))
              .build();

      TargetEncoder tec = new TargetEncoder();
      int[] teColumns = {1};

      Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 3, 4);

      Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 3, targetEncodingMap, TargetEncoder.HoldoutType.KFold, 4, false, 0, 1234.0);

      Vec expected = vec(1, 0, 1, 1, 1);
      assertVecEquals(expected, resultWithEncoding.vec(5), 1e-5);

      expected.remove();
      encodingMapCleanUp(targetEncodingMap);
      resultWithEncoding.delete();
    }

    @Test
    public void targetEncoderKFoldHoldoutApplyingWithoutFoldColumnTest() {
      //TODO fold_column = null case
    }

    @Test
    public void encodingWasCreatedWithFoldsCheckTest() {
      //TODO encoding contains fold column but user did not provide fold column name during application phase.
    }

    @Test
    public void targetEncoderKFoldHoldoutApplyingWithNoiseTest() {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB", "ColC", "fold_column")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ard(1, 1, 4, 7, 4))
              .withDataForCol(2, ar("2", "6", "6", "6", "6"))
              .withDataForCol(3, ar(1, 2, 2, 3, 2))
              .build();

      TargetEncoder tec = new TargetEncoder();
      int[] teColumns = {0};

      Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

      //If we do not pass noise_level as parameter then it will be calculated according to the type of target column. For categorical target column it defaults to 1e-2
      Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.KFold, 3, false);

      Vec expected = vec(1, 0, 1, 1, 1);
      assertVecEquals(expected, resultWithEncoding.vec(4), 1e-2); // TODO is it ok that encoding contains negative values?

      expected.remove();
      encodingMapCleanUp(targetEncodingMap);
      resultWithEncoding.delete();
    }

    @Test
    public void targetEncoderKFoldHoldoutApplyingWithCustomNoiseTest() {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB", "ColC", "fold_column")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ard(1, 1, 4, 7, 4))
              .withDataForCol(2, ar("2", "6", "6", "6", "6"))
              .withDataForCol(3, ar(1, 2, 2, 3, 2))
              .build();

      TargetEncoder tec = new TargetEncoder();
      int[] teColumns = {0};

      Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

      Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.KFold, 3, false, 0.02, 1234.0);

      TwoDimTable resultTable = resultWithEncoding.toTwoDimTable();
      System.out.println("Result table" + resultTable.toString());
      Vec expected = vec(1, 0, 1, 1, 1);
      assertVecEquals(expected, resultWithEncoding.vec(4), 2e-2); // TODO we do not check here actually that we have noise more then default 0.01. We need to check that sometimes we get 0.01 < delta < 0.02

      expected.remove();
      encodingMapCleanUp(targetEncodingMap);
      resultWithEncoding.delete();
    }

    @Test
    public void targetEncoderKFoldHoldoutApplyingWithCustomNoiseForNumericColumnTest() {
        //TODO
    }

    @Test
    public void calculateSingleNumberResultTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA")
                .withVecTypes(Vec.T_NUM)
                .withDataForCol(0, ard(1, 2, 3))
                .build();
        String tree = "(sum (cols testFrame [0.0] ))";
        Val val = Rapids.exec(tree);
        assertEquals(val.getNum(), 6.0, 1e-5);
    }

    @Test
    public void calculateGlobalMeanTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("numerator", "denominator")
                .withVecTypes(Vec.T_NUM, Vec.T_NUM)
                .withDataForCol(0, ard(1, 2, 3))
                .withDataForCol(1, ard(3, 4, 5))
                .build();
        TargetEncoder tec = new TargetEncoder();
        double result = tec.calculateGlobalMean(fr);

        assertEquals(result, 0.5, 1e-5);
    }

    // ----------------------------- blended average -----------------------------------------------------------------//
    @Test
    public void calculateAndAppendBlendedTEEncodingTest() {

      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "a"))
              .withDataForCol(1, ar("yes", "no", "yes"))
              .build();
      TargetEncoder tec = new TargetEncoder();
      int[] teColumns = {0};
      Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 1);

      Frame merged = tec.mergeByTEColumn(fr, targetEncodingMap.get("ColA"), 0, 0);

      Frame resultWithEncoding = tec.calculateAndAppendBlendedTEEncoding(merged, targetEncodingMap.get("ColA"), "ColB", "targetEncoded");

      // k <- 20
      // f <- 10
      // global_mean <- sum(x_map$numerator)/sum(x_map$denominator)
      // lambda <- 1/(1 + exp((-1)* (te_frame$denominator - k)/f))
      // te_frame$target_encode <- ((1 - lambda) * global_mean) + (lambda * te_frame$numerator/te_frame$denominator)

      double globalMean = 2.0 / 3;
      double lambda1 = 1.0 / (1.0 + (Math.exp((20.0 - 2) / 10)));
      double te1 = (1.0 - lambda1) * globalMean + (lambda1 * 2 / 2);

      double lambda2 = 1.0 / (1 + Math.exp((20.0 - 1) / 10));
      double te2 = (1.0 - lambda2) * globalMean + (lambda2 * 0 / 1);

      double lambda3 = 1.0 / (1.0 + (Math.exp((20.0 - 2) / 10)));
      double te3 = (1.0 - lambda3) * globalMean + (lambda3 * 2 / 2);

      assertEquals(te1, resultWithEncoding.vec(4).at(0), 1e-5);
      assertEquals(te3, resultWithEncoding.vec(4).at(1), 1e-5);
      assertEquals(te2, resultWithEncoding.vec(4).at(2), 1e-5);

      encodingMapCleanUp(targetEncodingMap);
      merged.delete();
      resultWithEncoding.delete();

    }

    // In case of LOO holdout we subtract target value of the current row from aggregated values per group.
    // This is where we can end up with 0 in denominator column.
    @Test
    public void calculateAndAppendBlendedTEEncodingDivisionByZeroTest() {

      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB", "numerator", "denominator")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "a"))
              .withDataForCol(1, ar("yes", "no", "yes"))
              .withDataForCol(2, ar(2, 0, 2))
              .withDataForCol(3, ar(2, 0, 2))  // For b row we set denominator to 0
              .build();
      TargetEncoder tec = new TargetEncoder();
      int[] teColumns = {0};
      Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 1);

      Frame result = tec.calculateAndAppendBlendedTEEncoding(fr, targetEncodingMap.get("ColA"), "ColB", "targetEncoded");

      double globalMean = 2.0 / 3;
      double lambda2 = 1.0 / (1 + Math.exp((20.0 - 0) / 10));
      double te2 = (1.0 - lambda2) * globalMean + (lambda2 * (1 - globalMean)); //because target value for row b is 0 we use (1 - globalMean) substitution.

      assertEquals(te2, result.vec(4).at(1), 1e-5);
      assertFalse(result.vec(2).isNA(1));

      encodingMapCleanUp(targetEncodingMap);
      result.delete();
    }

    @Ignore
    @Test
    public void targetEncoderKFoldHoldoutApplyingWithBlendedAvgTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "fold_column")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT,  Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "b", "b", "a", "c"))
                .withDataForCol(1, ard(1, 1, 4, 7, 4, 9))
                .withDataForCol(2, ar("2", "6", "6", "6", "6", "2"))
                .withDataForCol(3, ar(1, 2, 2, 3, 2, 2))
                .build();

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {0};

        Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

        Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.KFold, 3, true, 0.0, 1234.0);

        Vec encodedVec = resultWithEncoding.vec(4);

        // TODO I'm not sure if the values are correct but we at least can fix them and avoid regression while changing code further.
        assertEquals(0.855, encodedVec.at(0), 1e-3);
        assertEquals(0.724, encodedVec.at(1), 1e-3);
        assertEquals( 0.855, encodedVec.at(2), 1e-3);
        assertEquals( 0.856, encodedVec.at(4), 1e-3);

        encodedVec.remove();
      encodingMapCleanUp(targetEncodingMap);
      resultWithEncoding.delete();
    }

    @Test
    public void blendingTest() {
      //      //TODO more tests for blending
    }

    // ------------------------ LeaveOneOut holdout type -------------------------------------------------------------//

    @Test
    public void targetEncoderLOOHoldoutDivisionByZeroTest() {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB", "ColC")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "c", "d", "b", "a"))
              .withDataForCol(1, ard(1, 1, 4, 7, 5, 4))
              .withDataForCol(2, ar("2", "6", "6", "2", "6", "6"))
              .build();

      TargetEncoder tec = new TargetEncoder();
      int[] teColumns = {0};

      Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2);

      Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.LeaveOneOut, false, 0.0, 1234.0);

      // For level `c` and `d` we got only one row... so after leave one out subtraction we get `0` for denominator. We need to use different formula(value) for the result.
      assertEquals(0.666667, resultWithEncoding.vec("ColA_te").at(4) , 1e-5);
      assertEquals(0.33333, resultWithEncoding.vec("ColA_te").at(5) ,  1e-5);

      encodingMapCleanUp(targetEncodingMap);
      resultWithEncoding.delete();
    }

    @Test
    public void targetEncoderLOOHoldoutSubtractCurrentRowTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "numerator", "denominator", "target")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
                .withDataForCol(0, ar("a", "b", "b", "b", "a", "b"))
                .withDataForCol(1, ard(1, 1, 4, 7, 4, 2))
                .withDataForCol(2, ard(1, 1, 4, 7, 4, 6))
                .withDataForCol(3, ar("2", "6", "6", "6", "6", null))
                .build();

        TargetEncoder tec = new TargetEncoder();

        Frame res = tec.subtractTargetValueForLOO(fr, "target");

        // We check here that for  `target column = NA` we do not subtract anything and for other cases we subtract current row's target value
        Vec vecNotSubtracted = vec(1, 0, 3, 6, 3, 2);
        assertVecEquals(vecNotSubtracted, res.vec(1), 1e-5);
        Vec vecSubtracted = vec(0, 0, 3, 6, 3, 6);
        assertVecEquals(vecSubtracted, res.vec(2), 1e-5);

      vecNotSubtracted.remove();
      vecSubtracted.remove();
      res.delete();
    }

    @Test
    public void vecESPCTest() {
      Vec vecOfALengthTwo = vec(1, 0);
      long[] espcForLengthTwo = {0, 2};
      assertArrayEquals(espcForLengthTwo, Vec.ESPC.espc(vecOfALengthTwo));

      Vec vecOfALengthThree = vec(1, 0, 3);
      long[] espcForVecOfALengthThree = {0, 3};
      assertArrayEquals(espcForVecOfALengthThree, Vec.ESPC.espc(vecOfALengthThree));

      vecOfALengthTwo.remove();
      vecOfALengthThree.remove();
    }

    @Test
    public void targetEncoderLOOHoldoutApplyingTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
                .withDataForCol(0, ar("a", "b", "b", "b", "a"))
                .withDataForCol(1, ard(1, 1, 4, 7, 4))
                .withDataForCol(2, ar("2", "6", "6", "6", "6"))
                .build();

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {0};

        Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2);

        Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.LeaveOneOut,false, 0, 1234.0);

      Vec expected = vec(1, 0, 1, 1, 1);
      assertVecEquals(expected, resultWithEncoding.vec(3), 1e-5);

      expected.remove();
      encodingMapCleanUp(targetEncodingMap);
      resultWithEncoding.delete();
    }

    @Test
    public void targetEncoderLOOHoldoutApplyingWithFoldColumnTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "fold_column")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "b", "b", "a"))
                .withDataForCol(1, ard(1, 1, 4, 7, 4))
                .withDataForCol(2, ar("2", "6", "6", "6", "6"))
                .withDataForCol(3, ar(1, 2, 2, 3, 2))
                .build();

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {0};

        Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

        Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.LeaveOneOut, 3,false, 0, 1234.0);

      Vec expected = vec(1, 0, 1, 1, 1);
      assertVecEquals(expected, resultWithEncoding.vec(4), 1e-5);

      expected.remove();
      encodingMapCleanUp(targetEncodingMap);
      resultWithEncoding.delete();
    }

  @Test
  public void targetEncoderLOOApplyWithNoiseTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", "ColC", "fold_column")
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "b", "b", "a"))
            .withDataForCol(1, ard(1, 1, 4, 7, 4))
            .withDataForCol(2, ar("2", "6", "6", "6", "6"))
            .withDataForCol(3, ar(1, 2, 2, 3, 2))
            .build();

    TargetEncoder tec = new TargetEncoder();
    int[] teColumns = {0};

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

    //If we do not pass noise_level as parameter then it will be calculated according to the type of target column. For categorical target column it defaults to 1e-2
    Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.LeaveOneOut, 3, false);

    Vec expected = vec(1, 0, 1, 1, 1);
    double expectedDifferenceDueToNoise = 1e-2;
    assertVecEquals(expected, resultWithEncoding.vec(4), expectedDifferenceDueToNoise); // TODO is it ok that encoding contains negative values?

    expected.remove();
    encodingMapCleanUp(targetEncodingMap);
    resultWithEncoding.delete();
  }

    // ------------------------ None holdout type --------------------------------------------------------------------//

    @Test
    public void targetEncoderNoneHoldoutApplyingTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "fold_column")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "b", "b", "a"))
                .withDataForCol(1, ard(1, 1, 4, 7, 4))
                .withDataForCol(2, ar("2", "6", "6", "6", "6"))
                .withDataForCol(3, ar(1, 2, 2, 3, 2))
                .build();

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {0};

        Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

        Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.None, 3,false, 0, 1234.0);

        Vec vec = resultWithEncoding.vec(4);
        assertEquals(0.5, vec.at(0), 1e-5);
        assertEquals(0.5, vec.at(1), 1e-5);
        assertEquals(1, vec.at(2), 1e-5);
        assertEquals(1, vec.at(3), 1e-5);
        assertEquals(1, vec.at(4), 1e-5);

      encodingMapCleanUp(targetEncodingMap);
      resultWithEncoding.delete();
    }

  @Test
  public void holdoutTypeNoneApplyWithNoiseTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", "ColC", "fold_column")
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "b", "b", "a"))
            .withDataForCol(1, ard(1, 1, 4, 7, 4))
            .withDataForCol(2, ar("2", "6", "6", "6", "6"))
            .withDataForCol(3, ar(1, 2, 2, 3, 2))
            .build();

    TargetEncoder tec = new TargetEncoder();
    int[] teColumns = {0};

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

    printOutFrameAsTable(targetEncodingMap.get("ColA"));
    //If we do not pass noise_level as parameter then it will be calculated according to the type of target column. For categorical target column it defaults to 1e-2
    Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.None, 3, false);

    printOutFrameAsTable(resultWithEncoding);
    double expectedDifferenceDueToNoise = 1e-2;
    Vec vec = resultWithEncoding.vec(4);
    assertEquals(0.5, vec.at(0), expectedDifferenceDueToNoise);
    assertEquals(0.5, vec.at(1), expectedDifferenceDueToNoise);
    assertEquals(1, vec.at(2), expectedDifferenceDueToNoise);
    assertEquals(1, vec.at(3), expectedDifferenceDueToNoise);
    assertEquals(1, vec.at(4), expectedDifferenceDueToNoise);

    encodingMapCleanUp(targetEncodingMap);
    resultWithEncoding.delete();
  }

  @Test
  public void manualHighCardinalityKFoldTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", "fold_column")
            .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "b", "c", "c", "a", "d", "d", "d", "d", "e", "e", "a", "f", "f"))
            .withDataForCol(1, ar("2", "6", "6", "6", "6", "6", "2", "6", "6", "6", "6", "2", "2", "2", "2"))
            .withDataForCol(2, ar( 1 ,  2 ,  1 ,  2 ,  1 ,  3 ,  2 ,  2 ,  1 ,  3 ,  1 ,  2 ,  3 ,  3 ,  2))
            .build();

    TargetEncoder tec = new TargetEncoder();
    int[] teColumns = {0};

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 1, 2);

    printOutFrameAsTable(targetEncodingMap.get("ColA"))
    ;
    //If we do not pass noise_level as parameter then it will be calculated according to the type of target column. For categorical target column it defaults to 1e-2
    Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 1, targetEncodingMap, TargetEncoder.HoldoutType.KFold, 2, false, 0.0, 1234);

    printOutFrameAsTable(resultWithEncoding, true, true);
    double expectedDifferenceDueToNoise = 1e-5;
    Vec vec = resultWithEncoding.vec(3);
    assertEquals(0.5, vec.at(0), expectedDifferenceDueToNoise);
    assertEquals(0, vec.at(1), expectedDifferenceDueToNoise);
    assertEquals(0, vec.at(2), expectedDifferenceDueToNoise);
    assertEquals(1, vec.at(3), expectedDifferenceDueToNoise);
    assertEquals(1, vec.at(4), expectedDifferenceDueToNoise);
    assertEquals(1, vec.at(5), expectedDifferenceDueToNoise);
    assertEquals(1, vec.at(6), expectedDifferenceDueToNoise);
    assertEquals(0.66666, vec.at(7), expectedDifferenceDueToNoise);
    assertEquals(1, vec.at(8), expectedDifferenceDueToNoise);
    assertEquals(1, vec.at(9), expectedDifferenceDueToNoise);
    assertEquals(0.66666, vec.at(10), expectedDifferenceDueToNoise);
    assertEquals(0, vec.at(11), expectedDifferenceDueToNoise);
    assertEquals(1, vec.at(12), expectedDifferenceDueToNoise);
    assertEquals(0, vec.at(13), expectedDifferenceDueToNoise);
    assertEquals(0, vec.at(14), expectedDifferenceDueToNoise);


    encodingMapCleanUp(targetEncodingMap);
    resultWithEncoding.delete();
  }

    // ------------------------ Multiple columns for target encoding -------------------------------------------------//

    @Test
    public void KFoldHoldoutMultipleTEColumnsWithFoldColumnTest() {
        TestFrameBuilder frameBuilder = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "fold_column")
                .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "b", "b", "a"))
                .withDataForCol(1, ar("d", "e", "d", "e", "e"))
                .withDataForCol(2, ar("2", "6", "6", "6", "6"))
                .withDataForCol(3, ar(1, 2, 2, 3, 2));

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {0, 1};

        fr = frameBuilder.withName("testFrame").build();

        Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

        Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.KFold, 3,false, 0, 1234.0);
        Frame sortedBy2 = resultWithEncoding.sort(new int[]{2});
        Vec encodingForColumnA_Multiple = sortedBy2.vec(4);
        Frame sortedBy0 = resultWithEncoding.sort(new int[]{0});
        Vec encodingForColumnB_Multiple = sortedBy0.vec(5);

        //Let's check it with Single TE version of the algorithm. So we rely here on a correctness of the single-column encoding.
        //  For the first encoded column
        Frame frA = frameBuilder.withName("testFrameA").build();

        int[] indexForColumnA = Arrays.copyOfRange(teColumns, 0, 1);
        Map<String, Frame> targetEncodingMapForColumnA = tec.prepareEncodingMap(frA, indexForColumnA, 2, 3);
        Frame resultWithEncodingForColumnA = tec.applyTargetEncoding(frA, indexForColumnA, 2, targetEncodingMapForColumnA, TargetEncoder.HoldoutType.KFold, 3,false, 0, 1234.0);
        Vec encodingForColumnA_Single = resultWithEncodingForColumnA.vec(4);

        assertVecEquals(encodingForColumnA_Single, encodingForColumnA_Multiple, 1e-5);

        // For the second encoded column
        Frame frB = frameBuilder.withName("testFrameB").build();

        int[] indexForColumnB = Arrays.copyOfRange(teColumns, 1, 2);
        Map<String, Frame> targetEncodingMapForColumnB = tec.prepareEncodingMap(frB, indexForColumnB, 2, 3);
        Frame resultWithEncodingForColumnB = tec.applyTargetEncoding(frB, indexForColumnB, 2, targetEncodingMapForColumnB, TargetEncoder.HoldoutType.KFold, 3,false, 0, 1234.0);
        Frame sortedByColA = resultWithEncodingForColumnB.sort(new int[]{0});
        Vec encodingForColumnB_Single = sortedByColA.vec(4);
        assertVecEquals(encodingForColumnB_Single, encodingForColumnB_Multiple, 1e-5);


      sortedBy0.delete();
      sortedBy2.delete();
      sortedByColA.delete();
      encodingMapCleanUp(targetEncodingMap);
      encodingMapCleanUp(targetEncodingMapForColumnA);
      encodingMapCleanUp(targetEncodingMapForColumnB);
      frA.delete();
      frB.delete();
      resultWithEncoding.delete();
      resultWithEncodingForColumnA.delete();
      resultWithEncodingForColumnB.delete();
    }

    @Test
    public void LOOHoldoutMultipleTEColumnsWithFoldColumnTest() {
      TestFrameBuilder frameBuilder = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB", "ColC", "fold_column")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ar("d", "e", "d", "e", "e"))
              .withDataForCol(2, ar("2", "6", "6", "6", "6"))
              .withDataForCol(3, ar(1, 2, 2, 3, 2));

      fr = frameBuilder.withName("testFrame").build();

      TargetEncoder tec = new TargetEncoder();
      int[] teColumns = {0, 1};

      Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

      Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.LeaveOneOut, 3, false, 0, 1234.0);
      Frame sortedBy1 = resultWithEncoding.sort(new int[]{1});
      Vec encodingForColumnA_Multiple = sortedBy1.vec(4);
      Frame sortedBy0 = resultWithEncoding.sort(new int[]{0});
      Vec encodingForColumnB_Multiple = sortedBy0.vec(5);

      // Let's check it with Single TE version of the algorithm. So we rely here on a correctness of the single-column encoding.
      //  For the first encoded column
      Frame frA = frameBuilder.withName("testFrameA").build();

      int[] indexForColumnA = Arrays.copyOfRange(teColumns, 0, 1);
      Map<String, Frame> targetEncodingMapForColumn1 = tec.prepareEncodingMap(frA, indexForColumnA, 2, 3);
      Frame resultWithEncodingForColumn1 = tec.applyTargetEncoding(frA, indexForColumnA, 2, targetEncodingMapForColumn1, TargetEncoder.HoldoutType.LeaveOneOut, 3, false, 0, 1234.0);
      Frame sortedSingleColumn1ByColA = resultWithEncodingForColumn1.sort(new int[]{0});
      Vec encodingForColumnA_Single = sortedSingleColumn1ByColA.vec(4);

      assertVecEquals(encodingForColumnA_Single, encodingForColumnA_Multiple, 1e-5);

      // For the second encoded column
      Frame frB = frameBuilder.withName("testFrameB").build();

      int[] indexForColumnB = Arrays.copyOfRange(teColumns, 1, 2);
      Map<String, Frame> targetEncodingMapForColumn2 = tec.prepareEncodingMap(frB, indexForColumnB, 2, 3);
      Frame resultWithEncodingForColumn2 = tec.applyTargetEncoding(frB, indexForColumnB, 2, targetEncodingMapForColumn2, TargetEncoder.HoldoutType.LeaveOneOut, 3, false, 0, 1234.0);
      Frame sortedSingleColumn2ByColA = resultWithEncodingForColumn2.sort(new int[]{0});
      Vec encodingForColumnB_Single = sortedSingleColumn2ByColA.vec(4);

      assertVecEquals(encodingForColumnB_Single, encodingForColumnB_Multiple, 1e-5);

      sortedBy0.delete();
      sortedBy1.delete();
      sortedSingleColumn1ByColA.delete();
      sortedSingleColumn2ByColA.delete();
      encodingMapCleanUp(targetEncodingMap);
      encodingMapCleanUp(targetEncodingMapForColumn1);
      encodingMapCleanUp(targetEncodingMapForColumn2);
      frA.delete();
      frB.delete();
      resultWithEncoding.delete();
      resultWithEncodingForColumn1.delete();
      resultWithEncodingForColumn2.delete();
    }

    @Test
    public void NoneHoldoutMultipleTEColumnsWithFoldColumnTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "fold_column")
                .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "b", "b", "a"))
                .withDataForCol(1, ar("d", "e", "d", "e", "e"))
                .withDataForCol(2, ar("2", "6", "6", "6", "6"))
                .withDataForCol(3, ar(1, 2, 2, 3, 2))
                .build();

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {0, 1};

        Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

        Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.None, 3,false, 0, 1234.0);

        printOutFrameAsTable(resultWithEncoding);
        // TODO We need vec(..) for doubles to make it easier.
        // For the first encoded column
        assertEquals(0.5, resultWithEncoding.vec(4).at(0), 1e-5);
        assertEquals(1, resultWithEncoding.vec(4).at(1), 1e-5);
        assertEquals(0.5, resultWithEncoding.vec(4).at(2), 1e-5);
        assertEquals(1, resultWithEncoding.vec(4).at(3), 1e-5);
        assertEquals(1, resultWithEncoding.vec(4).at(4), 1e-5);

        // For the second encoded column
        assertEquals(0.5, resultWithEncoding.vec(5).at(0), 1e-5);
        assertEquals(0.5, resultWithEncoding.vec(5).at(1), 1e-5);
        assertEquals(1, resultWithEncoding.vec(5).at(2), 1e-5);
        assertEquals(1, resultWithEncoding.vec(5).at(3), 1e-5);
        assertEquals(1, resultWithEncoding.vec(5).at(4), 1e-5);

        encodingMapCleanUp(targetEncodingMap);
        resultWithEncoding.delete();
    }

    @Test
    public void AddNoiseLevelTest() {

      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_NUM)
              .withDataForCol(0, ard(1, 2, 3))
              .build();

      double noiseLevel = 1e-2;
      TargetEncoder tec = new TargetEncoder();

      Frame res = tec.addNoise(fr, "ColA", noiseLevel, 1234.0);
      Vec expected = vec(1, 2, 3);
      assertVecEquals(expected, fr.vec(0), 1e-2);

      res.delete();
      expected.remove();
    }

    @Test
    public void getColumnNamesByIndexesTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
                .withDataForCol(0, ar("a", "b"))
                .withDataForCol(1, ard(1, 1))
                .withDataForCol(2, ar("2", "6"))
                .build();

        TargetEncoder tec = new TargetEncoder();
        int[] columns = ari(0,2);
        String [] columnNames = tec.getColumnNamesBy(fr, columns);
        assertEquals("ColA", columnNames[0]);
        assertEquals("ColC", columnNames[1]);
    }

    @Test
    public void renameColumnTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "fold_column")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT,  Vec.T_NUM)
                .withDataForCol(0, ar("a", "b"))
                .withDataForCol(1, ard(1, 1))
                .withDataForCol(2, ar("2", "6"))
                .withDataForCol(3, ar(1, 2))
                .build();

        TargetEncoder tec = new TargetEncoder();

        // Case1: Renaming by index
        int indexOfColumnToRename = 0;
        String newName = "NewColA";
        tec.renameColumn(fr, indexOfColumnToRename, newName);

        assertEquals( fr.names()[indexOfColumnToRename], newName);

        // Case2: Renaming by name
        String newName2 = "NewColA-2";
        tec.renameColumn(fr, "NewColA", newName2);
        assertEquals( fr.names()[indexOfColumnToRename], newName2);
    }

    @Test
    public void ensureTargetColumnIsNumericOrBinaryCategoricalTest() {
      Scope.enter();
      try {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "ColD")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_STR, Vec.T_CAT)
                .withDataForCol(0, ar("a", "b", "c", "d"))
                .withDataForCol(1, ard(1, 2, 3, 4))
                .withDataForCol(2, ar("2", "6", "6", "6"))
                .withDataForCol(3, ar("2", "6", "6", null))
                .build();

        TargetEncoder tec = new TargetEncoder();

        try {
          tec.ensureTargetColumnIsNumericOrBinaryCategorical(fr, 0);
          fail();
        } catch (Exception ex) {
          assertEquals("`target` must be a binary vector", ex.getMessage());
        }

        try {
          tec.ensureTargetColumnIsNumericOrBinaryCategorical(fr, 2);
          fail();
        } catch (Exception ex) {
          assertEquals("`target` must be a numeric or binary vector", ex.getMessage());
        }

        // Check that numerical column is ok
        Frame tmp3 = tec.ensureTargetColumnIsNumericOrBinaryCategorical(fr, 1);
        Scope.track(tmp3);

        // Check that binary categorical is ok (transformation is checked in another test)
        Frame tmp4 = tec.ensureTargetColumnIsNumericOrBinaryCategorical(fr, 3);
        printOutFrameAsTable(tmp4, true, true);
        Scope.track(tmp4);
      } finally {
        Scope.exit();
      }
    }

    @Test
    public void ensureTargetEncodingAndRemovingNAsWorkingTogetherTest() {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar("2", "6", "6", null))
              .build();

      TargetEncoder tec = new TargetEncoder();

      Frame tmp1 = tec.filterOutNAsFromTargetColumn(fr, 0);
      Frame tmp2 = tec.ensureTargetColumnIsNumericOrBinaryCategorical(tmp1, 0);

      Vec expected = vec(0, 1, 1);
      assertVecEquals(expected, tmp2.vec(0), 1e-5);

      expected.remove();
      tmp1.delete();
      tmp2.delete();
    }

    @Test
    public void ensureTargetColumnIsNumericOrBinaryCategoricalOrderTest() {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_CAT ,Vec.T_NUM)
              .withDataForCol(0, ar("NO", "YES", "NO"))
              .withDataForCol(1, ar(1, 2, 3))
              .build();

      Frame fr2 = new TestFrameBuilder()
              .withName("testFrame2")
              .withColNames("ColA2", "ColB2")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("YES", "NO", "NO"))
              .withDataForCol(1, ar(1, 2, 3))
              .build();

      TargetEncoder tec = new TargetEncoder();

      try {
        assertArrayEquals(fr.vec(0).domain(), fr2.vec(0).domain());
        fail();
      } catch (AssertionError ex) {
        assertEquals("arrays first differed at element [0]; expected:<[NO]> but was:<[YES]>", ex.getMessage());
      }

      Frame encoded = tec.transformBinaryTargetColumn(fr, 0);
      Frame encoded2 = tec.transformBinaryTargetColumn(fr2, 0);

      // Checking that Label Encoding will not assign 0 label to the first category it encounters. We are sorting domain to make order consistent.
      assertEquals(0, encoded.vec(0).at(0), 1e-5);
      assertEquals(1, encoded2.vec(0).at(0), 1e-5);
      fr.delete();
      fr2.delete();
      encoded.delete();
      encoded2.delete();
    }

    @Ignore
    @Test
    public void ensureTargetColumnIsNumericOrBinaryCategoricalUnderrepresentedClassTest() {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_CAT ,Vec.T_NUM)
              .withDataForCol(0, ar("NO")) //case 2: ("yes") let say all the examples are "yes" // case 3: YES
              .withDataForCol(1, ar(111))
              .build();

      Frame fr2 = new TestFrameBuilder()
              .withName("testFrame2")
              .withColNames("ColA2", "ColB2")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("YES")) //case 2: ("no", "yes")  in validation set we will be comparing YESs with NOs // case 3: NO - we will think that all examples are of 0 class.
              .withDataForCol(1, ar(222))
              .build();

      // TODO consider all possible combinations. Some of them does not make sense but still we should check them.

      fr2.delete();
    }

    @Test
    public void transformBinaryTargetColumnTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "fold_column")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT,  Vec.T_NUM)
                .withDataForCol(0, ar("a", "b"))
                .withDataForCol(1, ard(1, 1))
                .withDataForCol(2, ar("2", "6"))
                .withDataForCol(3, ar(1, 2))
                .build();

        TargetEncoder tec = new TargetEncoder();

        Frame res = tec.transformBinaryTargetColumn(fr, 2);

        Vec transformedVector = res.vec(2);
        assertTrue(transformedVector.isNumeric());
        assertEquals(0, transformedVector.at(0), 1e-5);
        assertEquals(1, transformedVector.at(1), 1e-5);
        res.delete();
    }

    @Test
    public void targetEncoderGetOutOfFoldDataTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB")
                .withVecTypes(Vec.T_NUM, Vec.T_NUM)
                .withDataForCol(0, ard(5, 6, 7, 9))
                .withDataForCol(1, ard(1, 2, 3, 1))
                .build();

        TargetEncoder tec = new TargetEncoder();

        Frame outOfFoldData = tec.getOutOfFoldData(fr, "ColB", 1);
        TwoDimTable twoDimTable = outOfFoldData.toTwoDimTable();
        assertEquals(outOfFoldData.numRows(), 2);

        assertEquals(6L, twoDimTable.get(5, 0));
        assertEquals(7L, twoDimTable.get(6, 0));

        Frame outOfFoldData2 = tec.getOutOfFoldData(fr, "ColB", 2);
        TwoDimTable twoDimTable2 = outOfFoldData2.toTwoDimTable();

        assertEquals(5L, twoDimTable2.get(5, 0));
        assertEquals(7L, twoDimTable2.get(6, 0));
        assertEquals(9L, twoDimTable2.get(7, 0));

        outOfFoldData.delete();
        outOfFoldData2.delete();
    }

    // Can we do it simply ? with mutation?
    @Test
    public void appendingColumnsInTheLoopTest() {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_NUM)
              .withDataForCol(0, ar(1,2))
              .build();

      Frame accFrame = fr.deepCopy(Key.make().toString());;
      DKV.put(accFrame);

      printOutFrameAsTable(accFrame, true, false);

      for(int i = 0 ; i < 3; i ++) {

        String tree = String.format("( append %s %d 'col_%d' )", accFrame._key, i, i);
        Frame withAppendedFrame = Rapids.exec(tree).getFrame();
        withAppendedFrame._key = Key.make();
        DKV.put(withAppendedFrame);

        accFrame.delete();
        accFrame = withAppendedFrame.deepCopy(Key.make().toString());
        DKV.put(accFrame);
        withAppendedFrame.delete();
        printOutFrameAsTable(accFrame);
      }

      accFrame.delete();
    }

    @Test
    public void filterOutByTest() {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_STR)
              .withDataForCol(0, ar("SAN", "SFO"))
              .build();
      Frame res = filterOutBy(fr, 0, "SAN");
      res.delete();
    }

    @Test
    public void filterByTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA")
                .withVecTypes(Vec.T_STR)
                .withDataForCol(0, ar("SAN", "SFO"))
                .build();
        Frame res = filterBy(fr, 0, "SAN");
        res.delete();
    }

    public Frame filterOutBy(Frame data, int columnIndex, String value)  {
        String tree = String.format("(rows %s  (!= (cols %s [%s] ) '%s' )  )", data._key, data._key, columnIndex, value);
        Val val = Rapids.exec(tree);
        Frame res = val.getFrame();
        res._key = data._key;
        DKV.put(res._key , res);
        return res;
    }

    public Frame filterBy(Frame data, int columnIndex, String value)  {
        String tree = String.format("(rows %s  (==(cols %s [%s] ) '%s' ) )", data._key, data._key, columnIndex, value);
        Val val = Rapids.exec(tree);
        Frame res = val.getFrame();
        res._key = data._key;
        DKV.put(res);
        return res;
    }

    @After
    public void afterEach() {
        System.out.println("After each test we do H2O.STORE.clear() and Vec.ESPC.clear()");
        if( fr!= null) fr.delete();
    }

    private void encodingMapCleanUp(Map<String, Frame> encodingMap) {
      for( Map.Entry<String, Frame> map : encodingMap.entrySet()) {
        map.getValue().delete();
      }
    }

    private void printOutFrameAsTable(Frame fr) {

        TwoDimTable twoDimTable = fr.toTwoDimTable();
        System.out.println(twoDimTable.toString(2, false));
    }

  private void printOutFrameAsTable(Frame fr, boolean full, boolean rollups) {

    TwoDimTable twoDimTable = fr.toTwoDimTable(0, 10000, rollups);
    System.out.println(twoDimTable.toString(2, full));
  }

  private void printOutColumnsMeta(Frame fr) {
    for (String header : fr.toTwoDimTable().getColHeaders()) {
      String type = fr.vec(header).get_type_str();
      int cardinality = fr.vec(header).cardinality();
      System.out.println(header + " - " + type + String.format("; Cardinality = %d", cardinality));

    }
  }
}
