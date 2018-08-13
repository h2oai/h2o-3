package ai.h2o.automl;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.DKV;
import water.H2O;
import water.Key;
import water.TestUtil;
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
    public void targetEncoderFilterOutNAsTest() {

        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC")
                .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_STR)
                .withDataForCol(0, ard(1, 1))
                .withDataForCol(1, ard(1, 1))
                .withDataForCol(2, ar(null, "6"))
                .build();
        TargetEncoder tec = new TargetEncoder();
        Frame result = tec.filterOutNAsFromTargetColumn(fr, 2);
        assertEquals(1L, result.numRows());

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
            tec.prepareEncodingMap(fr, teColumns, 2, null);
        } catch (IllegalStateException ex) {
            fail(String.format("All columns were categorical but something else went wrong: %s", ex.getMessage()));
        }
    }

    @Test
    public void prepareEncodingMapForKFoldCaseTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "fold_column")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT,  Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "b", "b"))
                .withDataForCol(1, ard(1, 1, 4, 7))
                .withDataForCol(2, ar("2", "6", "6", "6"))
                .withDataForCol(3, ar(1, 2, 2, 3))
                .build();

        TargetEncoder tec = new TargetEncoder();
        int[] teColumns = {0};

        Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

        Frame colAEncoding = targetEncodingMap.get("ColA");
        TwoDimTable twoDimTable = colAEncoding.toTwoDimTable();
        System.out.println(twoDimTable.toString());

        assertVecEquals(vec(0, 2, 1), colAEncoding.vec(2), 1e-5);
        assertVecEquals(vec(1, 2, 1), colAEncoding.vec(3), 1e-5);

    }

    @Test
    public void prepareEncodingMapWithoutFoldColumnCaseTest() {
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

        Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2);

        Frame colAEncoding = targetEncodingMap.get("ColA");
        TwoDimTable twoDimTable = colAEncoding.toTwoDimTable();
        System.out.println(twoDimTable.toString());

        assertVecEquals(vec(0, 3), colAEncoding.vec(1), 1e-5);
        assertVecEquals(vec(1, 3), colAEncoding.vec(2), 1e-5);

    }

  @Test
  public void cloningFrameTest() { //TODO Move it to FrameTest
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b"))
            .withDataForCol(1, ar("c", "d"))
            .build();

    Frame newFrame = new Frame(fr);
    newFrame._key = Key.make("testFrame_new");

    fr.remove("ColB");

    assertStringVecEquals(newFrame.vec("ColB"), cvec("c", "d"));
    assertEquals("testFrame_new", newFrame._key.toString());
    assertEquals(newFrame.numCols(), 2);
    assertEquals(fr.numCols(), 1);

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

        TwoDimTable resultTable = resultWithEncoding.toTwoDimTable();
        System.out.println("Result table" + resultTable.toString());
        Vec vec = resultWithEncoding.vec(4);
        assertVecEquals(vec(1,0,1,1,1), vec, 1e-5);
    }

    @Test
    public void targetEncoderKFoldHoldout_WithNonZeroColumnToEncode_ApplyingTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColA2", "ColB", "ColC", "fold_column")
                .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_NUM, Vec.T_CAT,  Vec.T_NUM)
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

        TwoDimTable resultTable = resultWithEncoding.toTwoDimTable();
        System.out.println("Result table" + resultTable.toString());
        Vec vec = resultWithEncoding.vec(5);
        assertVecEquals(vec(1,0,1,1,1), vec, 1e-5);
    }

    @Test
    public void targetEncoderKFoldHoldoutApplyingWithoutFoldColumnTest() {
      //TODO fold_column = null case
    }

    @Test
    public void targetEncoderKFoldHoldoutApplyingWithNoiseTest() {
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

        //If we do not pass noise_level as parameter then it will be calculated according to the type of target column. For categorical target column it defaults to 1e-2
        Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.KFold, 3, false);

        TwoDimTable resultTable = resultWithEncoding.toTwoDimTable();
        System.out.println("Result table" + resultTable.toString());
        assertVecEquals(vec(1,0,1,1,1), resultWithEncoding.vec(4), 1e-2); // TODO if it's ok that encoding contains negative values?
    }

    @Test
    public void targetEncoderKFoldHoldoutApplyingWithCustomNoiseTest() {
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

        Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.KFold, 3, false, 0.02, 1234.0);

        TwoDimTable resultTable = resultWithEncoding.toTwoDimTable();
        System.out.println("Result table" + resultTable.toString());
        assertVecEquals(vec(1,0,1,1,1), resultWithEncoding.vec(4), 2e-2); // TODO we do not check here actually that we have noise more then default 0.01. We need to check that sometimes we get 0.01 < delta < 0.02
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
    @Ignore //TODO we need fix for divizion by zero
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

        Frame result = tec.calculateAndAppendBlendedTEEncoding(fr, targetEncodingMap.get("ColA"),"targetEncoded" );

        TwoDimTable twoDimTable = result.toTwoDimTable();
        System.out.println(twoDimTable.toString());

        // k <- 20
        // f <- 10
        // global_mean <- sum(x_map$numerator)/sum(x_map$denominator)
        // lambda <- 1/(1 + exp((-1)* (te_frame$denominator - k)/f))
        // te_frame$target_encode <- ((1 - lambda) * global_mean) + (lambda * te_frame$numerator/te_frame$denominator)

        double globalMean = (1.0 + 2.0 + 3.0) / (3 + 4 + 5);
        double lambda1 = 1.0 /( 1 + Math.exp( (20.0 - 3) / 10));
        double te1 = (1 - lambda1) * globalMean + ( lambda1 * 1 / 3);

        double lambda2 = 1.0 /( 1 + Math.exp( (20.0 - 4) / 10));
        double te2 = (1.0 - lambda2) * globalMean + ( lambda2 * 2 / 4);

        double lambda3 = 1.0 /( 1 + Math.exp( (20.0 - 5) / 10));
        double te3 = (1.0 - lambda3) * globalMean + ( lambda3 * 3 / 5);

        assertEquals(te1, result.vec(2).at(0), 1e-5);
        assertEquals(te2, result.vec(2).at(1), 1e-5);
        assertEquals(te3, result.vec(2).at(2), 1e-5);

    }

    @Ignore
    @Test
    public void targetEncoderKFoldHoldoutApplyingWithBlendedAvgTest() {
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

        Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.KFold, 3, true, 0.0, 1234.0);

        printOutFrameAsTable(resultWithEncoding);

        Vec encodedVec = resultWithEncoding.vec(4);

        // TODO I'm not sure if the values are correct but we at least can fix them and avoid regression while changing code further.
        assertEquals(0.855, encodedVec.at(0), 1e-3);
        assertEquals(0.724, encodedVec.at(1), 1e-3);
        assertEquals( 0.855, encodedVec.at(2), 1e-3);
        assertEquals( 0.856, encodedVec.at(4), 1e-3);
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

      printOutFrameAsTable(resultWithEncoding);

      // For level `c` and `d` we got only one row... so after leave one out subtraction we get `0` for denominator. We need to use different formula(value) for the result.
      assertEquals(0.666667, resultWithEncoding.vec("ColA_te").at(4) , 1e-5);
      assertEquals(0.33333, resultWithEncoding.vec("ColA_te").at(5) ,  1e-5);
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
        printOutFrameAsTable(fr);

        Frame res = tec.subtractTargetValueForLOO(fr, "target");
        printOutFrameAsTable(res);

        // We check here that for  `target column = NA` we do not subtract anything and for other cases we subtract current row's target value
        Vec vecNotSubtracted = vec(1, 0, 3, 6, 3, 2);
        assertVecEquals(vecNotSubtracted, res.vec(1), 1e-5);
        Vec vecSubtracted = vec(0, 0, 3, 6, 3, 6);
        assertVecEquals(vecSubtracted, res.vec(2), 1e-5);
    }

    @Test
    public void vecESPCTest() {
        Vec vecOfALengthTwo = vec(1, 0);
        long [] espcForLengthTwo = {0, 2};
        assertArrayEquals(espcForLengthTwo, Vec.ESPC.espc(vecOfALengthTwo));
        Vec vecOfALengthThree = vec(1, 0, 3);
        long [] espcForVecOfALengthThree = {0, 3};
        assertArrayEquals(espcForVecOfALengthThree, Vec.ESPC.espc(vecOfALengthThree));
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

        TwoDimTable resultTable = resultWithEncoding.toTwoDimTable();
        System.out.println("Result table" + resultTable.toString());
        assertVecEquals(vec(1,0,1,1,1), resultWithEncoding.vec(3), 1e-5);
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

        assertVecEquals(vec(1,0,1,1,1), resultWithEncoding.vec(4), 1e-5);
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

        TwoDimTable resultTable = resultWithEncoding.toTwoDimTable();
        System.out.println("Result table" + resultTable.toString());
        Vec vec = resultWithEncoding.vec(4);
        assertEquals(0.5, vec.at(0), 1e-5);
        assertEquals(0.5, vec.at(1), 1e-5);
        assertEquals(1, vec.at(2), 1e-5);
        assertEquals(1, vec.at(3), 1e-5);
        assertEquals(1, vec.at(4), 1e-5);
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
        Vec encodingForColumnA_Multiple = resultWithEncoding.sort(new int[]{2}).vec(4);
        Vec encodingForColumnB_Multiple = resultWithEncoding.sort(new int[]{0}).vec(5);

        //Let's check it with Single TE version of the algorithm. So we rely here on a correctness of the single-column encoding.
        //  For the first encoded column
        Frame frA = frameBuilder.withName("testFrameA").build();

        int[] indexForColumnA = Arrays.copyOfRange(teColumns, 0, 1);
        Map<String, Frame> targetEncodingMapForColumn1 = tec.prepareEncodingMap(frA, indexForColumnA, 2, 3);
        Frame resultWithEncodingForColumn1 = tec.applyTargetEncoding(frA, indexForColumnA, 2, targetEncodingMapForColumn1, TargetEncoder.HoldoutType.KFold, 3,false, 0, 1234.0);
        Vec encodingForColumnA_Single = resultWithEncodingForColumn1.vec(4);

        assertVecEquals(encodingForColumnA_Single, encodingForColumnA_Multiple, 1e-5);

        // For the second encoded column
        Frame frB = frameBuilder.withName("testFrameB").build();

        int[] indexForColumnB = Arrays.copyOfRange(teColumns, 1, 2);
        Map<String, Frame> targetEncodingMapForColumn2 = tec.prepareEncodingMap(frB, indexForColumnB, 2, 3);
        Frame resultWithEncodingForColumnB = tec.applyTargetEncoding(frB, indexForColumnB, 2, targetEncodingMapForColumn2, TargetEncoder.HoldoutType.KFold, 3,false, 0, 1234.0);
        Vec encodingForColumnB_Single = resultWithEncodingForColumnB.vec(4);

        assertVecEquals(encodingForColumnB_Single, encodingForColumnB_Multiple, 1e-5);
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

        Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.HoldoutType.LeaveOneOut, 3,false, 0, 1234.0);
        Vec encodingForColumnA_Multiple = resultWithEncoding.sort(new int[]{1}).vec(4);
        Vec encodingForColumnB_Multiple = resultWithEncoding.sort(new int[]{0}).vec(5);

        // Let's check it with Single TE version of the algorithm. So we rely here on a correctness of the single-column encoding.
        //  For the first encoded column
        Frame frA = frameBuilder.withName("testFrameA").build();

        int[] indexForColumnA = Arrays.copyOfRange(teColumns, 0, 1);
        Map<String, Frame> targetEncodingMapForColumn1 = tec.prepareEncodingMap(frA, indexForColumnA, 2, 3);
        Frame resultWithEncodingForColumn1 = tec.applyTargetEncoding(frA, indexForColumnA, 2, targetEncodingMapForColumn1, TargetEncoder.HoldoutType.LeaveOneOut, 3,false, 0, 1234.0);
        Vec encodingForColumnA_Single = resultWithEncodingForColumn1.vec(4);

        assertVecEquals(encodingForColumnA_Single, encodingForColumnA_Multiple, 1e-5);

        // For the second encoded column
        Frame frB = frameBuilder.withName("testFrameB").build();

        int[] indexForColumnB = Arrays.copyOfRange(teColumns, 1, 2);
        Map<String, Frame> targetEncodingMapForColumn2 = tec.prepareEncodingMap(frB, indexForColumnB, 2, 3);
        Frame resultWithEncodingForColumnB = tec.applyTargetEncoding(frB, indexForColumnB, 2, targetEncodingMapForColumn2, TargetEncoder.HoldoutType.LeaveOneOut, 3,false, 0, 1234.0);
        Vec encodingForColumnB_Single = resultWithEncodingForColumnB.vec(4);

        assertVecEquals(encodingForColumnB_Single, encodingForColumnB_Multiple, 1e-5);
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
        fr = tec.addNoise(fr, "ColA", noiseLevel, 1234.0);
        assertVecEquals(vec(1, 2, 3), fr.vec(0), 1e-2);
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
        String indexOfColumnToRename = "0";
        String newName = "NewColA";
        Frame renamedFrame = tec.renameColumn(fr, Integer.parseInt(indexOfColumnToRename), newName);

        TwoDimTable twoDimTable = renamedFrame.toTwoDimTable();
        assertEquals( twoDimTable.getColHeaders()[Integer.parseInt(indexOfColumnToRename)], newName);

        // Case2: Renaming by name
        String newName2 = "NewColA-2";
        renamedFrame = tec.renameColumn(fr, "NewColA", newName2);
        TwoDimTable table = renamedFrame.toTwoDimTable();
        assertEquals( table.getColHeaders()[Integer.parseInt(indexOfColumnToRename)], newName2);
    }

    @Test
    public void ensureTargetColumnIsNumericOrBinaryCategoricalTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC", "ColD")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_STR, Vec.T_CAT)
                .withDataForCol(0, ar("a", "b", "c"))
                .withDataForCol(1, ard(1, 2, 3))
                .withDataForCol(2, ar("2", "6", "6"))
                .withDataForCol(3, ar("2", "6", "6"))
                .build();

        TargetEncoder tec = new TargetEncoder();

        try {
            tec.ensureTargetColumnIsNumericOrBinaryCategorical(fr, 0);
            fail();
        } catch (Exception ex) {
            assertEquals( "`target` must be a binary vector", ex.getMessage());
        }

        try {
            tec.ensureTargetColumnIsNumericOrBinaryCategorical(fr, 2);
            fail();
        } catch (Exception ex) {
            assertEquals( "`target` must be a numeric or binary vector", ex.getMessage());
        }

        // Check that numerical column is ok
        tec.ensureTargetColumnIsNumericOrBinaryCategorical(fr, 1);

        // Check that binary categorical is ok (transformation is checked in another test)
        tec.ensureTargetColumnIsNumericOrBinaryCategorical(fr, 3);
    }

    @Test
    public void ensureTargetColumnIsNumericOrBinaryCategoricalOrderTest() {
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA")
                .withVecTypes(Vec.T_CAT)
                .withDataForCol(0, ar("NO", "YES", "NO"))
                .build();

        Frame fr2 = new TestFrameBuilder()
                .withName("testFrame2")
                .withColNames("ColA")
                .withVecTypes(Vec.T_CAT)
                .withDataForCol(0, ar("YES", "NO", "NO"))
                .build();

        TargetEncoder tec = new TargetEncoder();

        Frame encoded = tec.appendBinaryTargetColumn(fr, 0);
        Frame encoded2 = tec.appendBinaryTargetColumn(fr2, 0);


        //So, domains could be different. They seem to be sorted in a natural order.
        try {
            assertArrayEquals(fr.vec(0).domain(), fr2.vec(0).domain());
            fail();
        } catch (AssertionError ex) {
        }

        Vec frVec = fr.vec(0);
        frVec.setDomain(fr.vec(0).domain());
        Vec fr2Vec = fr2.vec(0);
        fr2Vec.setDomain(fr2.vec(0).domain());

        //So, setDomain does not do ordering.
        try {
            assertArrayEquals(frVec.domain(), fr2Vec.domain());
            fail();
        } catch (AssertionError ex) {
        }


        // Checking that Label Encoding will assign 0 label to the first category it encounters.
        assertEquals(0, encoded.vec(0).at(0), 1e-5);
        assertEquals(0, encoded2.vec(0).at(0), 1e-5);
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

        TwoDimTable twoDimTable = fr.toTwoDimTable();
        System.out.println(twoDimTable.toString());

        Frame res = tec.transformBinaryTargetColumn(fr, 2);

        TwoDimTable twoDimTable2 = res.toTwoDimTable();
        System.out.println(twoDimTable2.toString());

        Vec transformedVector = res.vec(2);
        assertTrue(transformedVector.isNumeric());
        assertEquals(0, transformedVector.at(0), 1e-5);
        assertEquals(1, transformedVector.at(1), 1e-5);
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

        Frame outOfFoldData = tec.getOutOfFoldData(fr, 1, 1);
        TwoDimTable twoDimTable = outOfFoldData.toTwoDimTable();
        assertEquals(outOfFoldData.numRows(), 2);

        assertEquals(6L, twoDimTable.get(5, 0));
        assertEquals(7L, twoDimTable.get(6, 0));

        Frame outOfFoldData2 = tec.getOutOfFoldData(fr, 1, 2);
        TwoDimTable twoDimTable2 = outOfFoldData2.toTwoDimTable();

        assertEquals(5L, twoDimTable2.get(5, 0));
        assertEquals(7L, twoDimTable2.get(6, 0));
        assertEquals(9L, twoDimTable2.get(7, 0));

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
        printOutFrameAsTable(res);

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
        printOutFrameAsTable(res);

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
        DKV.put(res._key , res);

        return res;
    }

    @After
    public void afterEach() {
        System.out.println("After each test we do H2O.STORE.clear() and Vec.ESPC.clear()");
        Vec.ESPC.clear();
        H2O.STORE.clear();
    }

    private void printOutFrameAsTable(Frame fr) {

        TwoDimTable twoDimTable = fr.toTwoDimTable();
        System.out.println(twoDimTable.toString(2, false));
    }

  private void printOutFrameAsTable(Frame fr, boolean full, boolean rollups) {

    TwoDimTable twoDimTable = fr.toTwoDimTable(0, 10000, rollups);
    System.out.println(twoDimTable.toString(2, full));
  }
}
