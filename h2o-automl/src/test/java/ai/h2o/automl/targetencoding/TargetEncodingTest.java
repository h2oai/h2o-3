package ai.h2o.automl.targetencoding;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.*;
import water.rapids.Rapids;
import water.rapids.Val;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.*;

public class TargetEncodingTest extends TestUtil {


  @BeforeClass public static void setup() {
    stall_till_cloudsize(1);
  }

  private Frame fr = null;


    @Test(expected = IllegalStateException.class)
    public void targetEncoderPrepareEncodingFrameValidationDataIsNotNullTest() {

      String[] teColumns = {"ColA"};
      TargetEncoder tec = new TargetEncoder(teColumns);

      tec.prepareEncodingMap(null, "ColB", null, true);
    }


    @Test(expected = IllegalStateException.class)
    public void targetEncoderPrepareEncodingFrameValidationTEColumnsIsNotEmptyTest() {

      String[] teColumns = {};
      TargetEncoder tec = new TargetEncoder(teColumns);

      tec.prepareEncodingMap(null, "2", null, true);

    }

    @Test
    public void teColumnExistsTest() {

      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b"))
              .withDataForCol(1, ar("yes", "no"))
              .build();
      String teColumnName = "ColThatNotExist";
      String[] teColumns = {teColumnName};
      TargetEncoder tec = new TargetEncoder(teColumns);

      try {
        tec.prepareEncodingMap(fr, "ColB", null, true);
        fail();
      } catch (AssertionError ex) {
        assertEquals("Column name `" +  teColumnName + "` was not found in the provided data frame", ex.getMessage());
      }
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
    public void imputationWorksForBinaryCategoricalColumnsTest() {
        long seed = 42L;
        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA")
                .withVecTypes(Vec.T_CAT)
                .withRandomBinaryDataForCol(0, 1000, seed)
                .withChunkLayout(500, 500) // that way our task could be executed with 2 threads
                .build();

        String nullStr = null;
        fr.vec(0).set(2, nullStr);

        String[] teColumns = {""};
        TargetEncoder tec = new TargetEncoder(teColumns);

//        printOutFrameAsTable(fr);
        assertTrue(fr.vec("ColA").isCategorical());
        assertEquals(2, fr.vec("ColA").cardinality());

        Frame res = tec.imputeNAsForColumn(fr, "ColA", "ColA_NA");

        Vec colA = res.vec("ColA");

        assertTrue(colA.isCategorical());
        assertEquals(3, colA.cardinality());

        //Checking here that we have replaced NA with index of the new category
        assertEquals(2, colA.at(2), 1e-5);
        assertEquals("ColA_NA", colA.domain()[2]);

        res.delete();
    }

  @Test
  public void imputationWorksForMultiCategoricalColumnsTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA")
            .withVecTypes(Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", "c", "d", null, null, null))
            .withChunkLayout(2,2,2,1)
            .build();

    String[] teColumns = {""};
    TargetEncoder tec = new TargetEncoder(teColumns);

    assertTrue(fr.vec("ColA").isCategorical());
    assertEquals(4, fr.vec("ColA").cardinality());

    Frame res = tec.imputeNAsForColumn(fr, "ColA", "ColA_NA");

    Vec colA = res.vec("ColA");

    assertTrue(colA.isCategorical());
    assertEquals(5, colA.cardinality());

    //Checking here that we have replaced NA with index of the new category
    assertEquals(4, colA.at(4), 1e-5);
    assertEquals("ColA_NA", colA.domain()[4]);

    res.delete();
  }

    @Test
    public void testThatNATargetsAreSkippedTest() {
      //TODO
    }

    @Test
    public void allTEColumnsAreCategoricalTest() {

        TestFrameBuilder baseBuilder = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB", "ColC")
                .withDataForCol(0, ar("1", "0"))
                .withDataForCol(2, ar("1", "6"));

      String[] teColumns = {"ColA", "ColB"};
      String targetColumnName = "ColC";
      TargetEncoder tec = new TargetEncoder(teColumns);
        Map<String, Frame> encodingMap = null;

        fr = baseBuilder
                .withDataForCol(1, ar(0, 1))
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
                .build();
        try {
          tec.prepareEncodingMap(fr, targetColumnName, null);
            fail();
        } catch (IllegalStateException ex) {
            assertEquals("Argument 'columnsToEncode' should contain only names of categorical columns", ex.getMessage());
        }

        fr = baseBuilder
                .withDataForCol(1, ar("a", "b"))
                .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT)
                .build();

        try {
          encodingMap = tec.prepareEncodingMap(fr, targetColumnName, null);
        } catch (IllegalStateException ex) {
            fail(String.format("All columns were categorical but something else went wrong: %s", ex.getMessage()));
        }

      encodingMapCleanUp(encodingMap);
    }

    @Test
    public void checkAllTEColumnsExistAndAreCategoricalTest() {

      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withDataForCol(0, ar("1", "0"))
              .withVecTypes(Vec.T_CAT)
              .build();

      String[] teColumns = {"ColA", "ColNonExist"};
      String targetColumnName = "ColC";
      TargetEncoder tec = new TargetEncoder(teColumns);

      try{
        tec.prepareEncodingMap(fr, targetColumnName, null);
        fail();
      } catch (AssertionError ex){
        assertEquals("Column name `ColNonExist` was not found in the provided data frame", ex.getMessage());
      }

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

        String[] teColumns = {"ColA"};
        String targetColumnName = "ColC";
        TargetEncoder tec = new TargetEncoder(teColumns);

        targetEncodingMap = tec.prepareEncodingMap(fr, targetColumnName, null);

        Frame colAEncoding = targetEncodingMap.get("ColA");
        Scope.track(colAEncoding);

        assertVecEquals(vec(0, 3), colAEncoding.vec(1), 1e-5);
        assertVecEquals(vec(1, 3), colAEncoding.vec(2), 1e-5);
      } finally {
        Scope.exit();
      }

    }

  @Test // Test that we are not introducing keys leakage when we reassign within if-statement
  public void ifStatementsWithFramesTest() {
    Scope.enter();
    try {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b"))
              .withDataForCol(1, ar("yes", "no"))
              .build();

      boolean flag = false;
      String[] teColumns = {""};
      TargetEncoder tec = new TargetEncoder(teColumns);
      Frame dataWithAllEncodings = null ;
      if(flag) {
        Frame dataWithEncodedTarget = tec.ensureTargetColumnIsBinaryCategorical(fr, "ColB");
        dataWithAllEncodings = dataWithEncodedTarget.deepCopy(Key.make().toString());
        DKV.put(dataWithAllEncodings);

        assertVecEquals(dataWithAllEncodings.vec("ColB"), vec(1, 0), 1E-5);
      }
      else {
        dataWithAllEncodings = fr;
      }

      dataWithAllEncodings.delete();
    } finally {
      Scope.exit();
    }
  }

    @Test
    public void imputeWithMeanTest() {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_STR)
              .withDataForCol(0, ar("1", "2", null))
              .build();

      String[] teColumns = {""};
      TargetEncoder tec = new TargetEncoder(teColumns);

      // We have to do this trick because we cant initialize array with `null` values.
      Vec strVec = fr.vec("ColA");
      Vec numericVec = strVec.toNumericVec();
      fr.replace(0, numericVec);

      Frame withImputed = tec.imputeWithMean(fr, 0, 1.5);
      Vec expected = dvec(1, 2, 1.5);
      Vec resultVec = withImputed.vec(0);
      assertVecEquals(expected, resultVec, 1e-5);

      expected.remove();
      strVec.remove();
      resultVec.remove();
      withImputed.delete();
      numericVec.remove();
    }

    @Test
    public void rbindTest() {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_NUM)
              .withDataForCol(0, ar(1))
              .build();

      String[] teColumns = {""};
      TargetEncoder tec = new TargetEncoder(teColumns);

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
        String[] teColumns = {""};
        TargetEncoder tec = new TargetEncoder(teColumns);
        double result = tec.calculatePriorMean(fr);

        assertEquals(result, 0.5, 1e-5);
    }

    @Test
    public void groupByTEColumnAndAggregateTest() {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("teColumn", "numerator", "denominator")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ar("a", "a", "b"))
              .withDataForCol(1, ard(1, 2, 3))
              .withDataForCol(2, ard(3, 4, 5))
              .build();
      String[] teColumns = {""};
      TargetEncoder tec = new TargetEncoder(teColumns);
      Frame result = tec.groupByTEColumnAndAggregate(fr, 0);
//      printOutFrameAsTable(result, false, result.numRows());

      Vec expectedNum = vec(3, 3);
      assertVecEquals(expectedNum, result.vec("sum_numerator"), 1e-5);
      Vec expectedDen = vec(7, 5);
      assertVecEquals(expectedDen, result.vec("sum_denominator"), 1e-5);

      result.delete();
      expectedNum.remove();
      expectedDen.remove();
    }


    @Test
    public void mapOverTheFrameWithImmutableApproachTest() {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB", "ColC")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c"))
              .withDataForCol(1, ar(1,2,3))
              .withDataForCol(2, ar(4,5,6))
              .build();

      Frame oneColumnMultipliedOnly = new CalculatedColumnTask(1).doAll(Vec.T_NUM, fr).outputFrame();

//      printOutFrameAsTable(oneColumnMultipliedOnly, false, oneColumnMultipliedOnly.numRows());
      assertEquals(1, oneColumnMultipliedOnly.numCols());

      Vec expectedVec = vec(2, 4, 6);
      Vec outcomeVec = oneColumnMultipliedOnly.vec(0);
      assertVecEquals(expectedVec, outcomeVec, 1e-5);

      expectedVec.remove();
      outcomeVec.remove();
      oneColumnMultipliedOnly.delete();
    }

    public static class CalculatedColumnTask extends MRTask<CalculatedColumnTask> {
      long columnIndex;

      public CalculatedColumnTask(long columnIndex) {
        this.columnIndex = columnIndex;
      }

      @Override
      public void map(Chunk cs[], NewChunk ncs[]) {
        for (int col = 0; col < cs.length; col++) {
          if (col == columnIndex) {
            Chunk c = cs[col];
            NewChunk nc = ncs[0];
            for (int i = 0; i < c._len; i++)
              nc.addNum(c.at8(i) * 2);
          }


        }
      }
    }

    @Test
    public void mutateOnlyParticularColumnsOfTheFrameTest() {
        fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB", "ColC")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c"))
              .withDataForCol(1, ar(1,2,3))
              .withDataForCol(2, ar(4,5,6))
              .build();

      new TestMutableTask(1).doAll(fr);

//      printOutFrameAsTable(fr, false, fr.numRows());
      assertEquals(3, fr.numCols());

      Vec expected = vec(2, 4, 6);
      assertVecEquals(expected, fr.vec(1), 1e-5);

      expected.remove();
    }


    public static class TestMutableTask extends MRTask<TestMutableTask> {
      long columnIndex;
      public TestMutableTask(long columnIndex) {
        this.columnIndex = columnIndex;
      }
      @Override
      public void map(Chunk cs[]) {
        for (int col = 0; col < cs.length; col++) {
          if(col == columnIndex) {
            for (int i = 0; i < cs[col]._len; i++) {
              long value = cs[col].at8(i);
              cs[col].set(i, value * 2);
            }
          }
        }
      }
    }

    // ----------------------------- blended average -----------------------------------------------------------------//
    @Test
    public void calculateAndAppendBlendedTEEncodingTest() {
      String tmpName = null;
      Frame reimportedFrame = null;
      Frame merged = null;
      Frame resultWithEncoding = null;
      Map<String, Frame> targetEncodingMap = null;
      try {

        fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA", "ColB")
                .withVecTypes(Vec.T_CAT, Vec.T_CAT)
                .withDataForCol(0, ar("a", "b", "a"))
                .withDataForCol(1, ar("yes", "no", "yes"))
                .withChunkLayout(1, 1, 1)
                .build();

        tmpName = UUID.randomUUID().toString();
        Job export = Frame.export(fr, tmpName, fr._key.toString(), true, 1);
        export.get();

        reimportedFrame = parse_test_file(Key.make("parsed"), tmpName, true);

        String[] teColumns = {"ColA"};
        String targetColumnName = "ColB";
        TargetEncoder tec = new TargetEncoder(teColumns);
        targetEncodingMap = tec.prepareEncodingMap(reimportedFrame, targetColumnName, null);

        merged = tec.mergeByTEColumn(reimportedFrame, targetEncodingMap.get("ColA"), 0, 0);

        resultWithEncoding = tec.calculateAndAppendBlendedTEEncoding(merged, targetEncodingMap.get("ColA"), "ColB", "targetEncoded");

//      String[] dom = resultWithEncoding.vec(1).domain();
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

//        printOutFrameAsTable(resultWithEncoding, true, resultWithEncoding.numRows());

        assertEquals(te1, resultWithEncoding.vec(4).at(0), 1e-5);
        assertEquals(te3, resultWithEncoding.vec(4).at(1), 1e-5);
        assertEquals(te2, resultWithEncoding.vec(4).at(2), 1e-5);


      } finally {
        new File(tmpName).delete();
        encodingMapCleanUp(targetEncodingMap);
        merged.delete();
        resultWithEncoding.delete();
        reimportedFrame.delete();
      }
    }

  @Test
  public void calculateAndAppendEncodingsOrderIsPreservedWhenWeUseAddMethodTest() {

    int sizeOfDataset = 1000000;
    long seed = 42L;
    double[] arr = new double[sizeOfDataset];
      Arrays.fill(arr, 0.5);
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("numerator", "denominator", "target", "encodings_to_compare_with")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withRandomDoubleDataForCol(0, sizeOfDataset, 1, 1, seed)
              .withRandomDoubleDataForCol(1, sizeOfDataset, 2, 2, seed + 1)
              .withRandomBinaryDataForCol(2, sizeOfDataset, seed + 2)
              .withDataForCol(3,  arr) // vec that is used for comparing with encodings
              .withChunkLayout(100, 200, 300, sizeOfDataset - 600)
              .build();
      Vec zeroVec = Vec.makeZero(sizeOfDataset);
      fr.add( "placeholder_for_encodings", zeroVec); // should get 4th index

      int changedIndex = new Random().nextInt(sizeOfDataset);
      fr.vec(0).set(changedIndex, 0);
      fr.vec(3).set(changedIndex, 0);


      new TargetEncoder.CalcEncodings(0, 1, 42, 4).doAll(fr);

      zeroVec.remove();
      assertEquals(0, fr.vec("placeholder_for_encodings").at(changedIndex), 1e-5);
      assertVecEquals(fr.vec(3), fr.vec("placeholder_for_encodings"), 1e-5);


      fr.delete();
  }

    @Test
    public void calculateAndAppendBlendedTEEncodingPerformanceTest() {
      long startTimeEncoding = System.currentTimeMillis();

      int numberOfRuns = 10;
      long seed = 42L;
      for(int i = 0; i < numberOfRuns; i ++) {
        int dataframeSize = 1000000;
        Frame fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("numerator", "denominator", "target")
                .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
                .withRandomDoubleDataForCol(0, dataframeSize, 0, 50, seed)
                .withRandomDoubleDataForCol(1, dataframeSize, 1, 100, seed + 1)
                .withRandomBinaryDataForCol(2, dataframeSize, seed + 2)
                .build();

        BlendingParams blendingParams = new BlendingParams(20, 10);

        Vec zeroVec = Vec.makeZero(dataframeSize);
        fr.add( "placeholder_for_encodings", zeroVec);
        int indexOfEncodingsColumn = 3;

        new TargetEncoder.CalcEncodingsWithBlending(0, 1, 42, blendingParams, indexOfEncodingsColumn).doAll(fr);
        fr.delete();
        zeroVec.remove();
      }
      long finishTimeEncoding = System.currentTimeMillis();
      System.out.println("Calculation of encodings took(ms): " + (finishTimeEncoding - startTimeEncoding));
      System.out.println("Avg calculation of encodings took(ms): " + (double)(finishTimeEncoding - startTimeEncoding) / numberOfRuns);
    }

  @Test
  public void calculateAndAppendTEEncodingPerformanceTest() {
    long startTimeEncoding = System.currentTimeMillis();

    int numberOfRuns = 10;
    long seed = 42L;
    for(int i = 0; i < numberOfRuns; i ++) {
      int dataframeSize = 1000000;

      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("numerator", "denominator", "target")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
              .withRandomDoubleDataForCol(0, dataframeSize, 0, 50, seed)
              .withRandomDoubleDataForCol(1, dataframeSize, 1, 100, seed + 1)
              .withRandomBinaryDataForCol(2, dataframeSize, seed + 2)
              .build();

      Vec zeroVec = Vec.makeZero(dataframeSize);
      fr.add( "placeholder_for_encodings", zeroVec);

      new TargetEncoder.CalcEncodings(0, 1, 42, 3).doAll( fr);
      zeroVec.remove();
      fr.delete();
    }
    long finishTimeEncoding = System.currentTimeMillis();
    System.out.println("Calculation of encodings took(ms): " + (finishTimeEncoding - startTimeEncoding));
    System.out.println("Avg calculation of encodings took(ms): " + (double)(finishTimeEncoding - startTimeEncoding) / numberOfRuns);
  }

    @Test
    public void blendingTest() {
      //      //TODO more tests for blending
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

  // --------------------------- Merging tests -----------------------------------------------------------------------//

    @Test
    public void mergingByTEAndFoldTest() {

      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "a"))
              .withDataForCol(1, ar(1,1,2))
              .build();

      Frame holdoutEncodingMap = new TestFrameBuilder()
              .withName("holdoutEncodingMap")
              .withColNames("ColA", "ColC", "foldValueForMerge")
              .withVecTypes(Vec.T_CAT, Vec.T_STR, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "a"))
              .withDataForCol(1, ar("yes", "no", "yes"))
              .withDataForCol(2, ar(1, 2, 2))
              .build();

      String[] teColumns = {""};
      TargetEncoder tec = new TargetEncoder(teColumns);

      Frame merged = tec.mergeByTEAndFoldColumns(fr, holdoutEncodingMap, 0, 1, 0);
//      printOutFrameAsTable(merged);
      Vec expecteds = svec("yes", "yes", null);
      assertStringVecEquals(expecteds, merged.vec("ColC"));

      expecteds.remove();
      merged.delete();
      holdoutEncodingMap.delete();
    }

    @Test
    public void AddNoiseLevelTest() {

      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB", "ColC")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(1, 2, 3))
              .withDataForCol(1, ard(1, 2, 3))
              .withDataForCol(2, ard(1, 2, 3))
              .build();

      double noiseLevel = 1e-2;
      String[] teColumns = {""};
      TargetEncoder tec = new TargetEncoder(teColumns);

      tec.addNoise(fr, "ColA", noiseLevel, 1234);
      tec.addNoise(fr, "ColB", noiseLevel, 5678);
      tec.addNoise(fr, "ColC", noiseLevel, 1234);
      Vec expected = vec(1, 2, 3);
      assertVecEquals(expected, fr.vec(0), 1e-2);

      try {
        assertVecEquals(fr.vec(0), fr.vec(1), 0.0);
        fail();
      } catch (AssertionError ex){ }

      //Vectors with the noises generated from the same seeds should be equal
      assertVecEquals(fr.vec(0), fr.vec(2), 0.0);

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
        String[] teColumns = {""};
        TargetEncoder tec = new TargetEncoder(teColumns);
        int[] columns = ari(0,2);
        String [] columnNames = tec.getColumnNamesBy(fr, columns);
        assertEquals("ColA", columnNames[0]);
        assertEquals("ColC", columnNames[1]);
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

        String[] teColumns = {""};
        TargetEncoder tec = new TargetEncoder(teColumns);

        try {
          tec.ensureTargetColumnIsBinaryCategorical(fr, "ColA");
          fail();
        } catch (Exception ex) {
          assertEquals("`target` must be a binary vector. We do not support multi-class target case for now", ex.getMessage());
        }

        // Check that string column will be rejected.
        try {
          tec.ensureTargetColumnIsBinaryCategorical(fr, "ColC");
          fail();
        } catch (Exception ex) {
          assertEquals("`target` must be a binary categorical vector. We do not support multi-class and continuos target case for now", ex.getMessage());
        }

        // Check that numerical column is not supported for now
        try {
          tec.ensureTargetColumnIsBinaryCategorical(fr, "ColB");
          fail();
        } catch (Exception ex) {
          assertEquals("`target` must be a binary categorical vector. We do not support multi-class and continuos target case for now", ex.getMessage());
        }

        // Check that binary categorical is ok (transformation is checked in another test)
        Frame tmp4 = tec.ensureTargetColumnIsBinaryCategorical(fr, "ColD");
        Scope.track(tmp4);

        assertTrue(tmp4.vec(3).isNA(3));
      } finally {
        Scope.exit();
      }
    }

    @Test
    public void ensureTargetEncodingAndRemovingNAsWorkingTogetherTest() {
      String targetColumnName = "ColA";
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames(targetColumnName)
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar("2", "6", "6", null))
              .build();

      String[] teColumns = {""};
      TargetEncoder tec = new TargetEncoder(teColumns);

      Frame tmp1 = tec.filterOutNAsFromTargetColumn(fr, 0);
      Frame tmp2 = tec.ensureTargetColumnIsBinaryCategorical(tmp1, targetColumnName);

      Vec expected = vec(0, 1, 1);
      assertVecEquals(expected, tmp2.vec(0), 1e-5);

      expected.remove();
      tmp1.delete();
      tmp2.delete();
    }

  @Test
  public void isBinaryTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_CAT, Vec.T_NUM)
            .withDataForCol(0, ar("NO", "YES", "NO"))
            .withDataForCol(1, ard(0, 0.5, 1))
            .build();

    assertTrue(fr.vec(0).isBinary());
    assertFalse(fr.vec(1).isBinary());
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

//      printOutFrameAsTable(accFrame, true, false);

      for(int i = 0 ; i < 3; i ++) {

        String tree = String.format("( append %s %d 'col_%d' )", accFrame._key, i, i);
        Frame withAppendedFrame = Rapids.exec(tree).getFrame();
        withAppendedFrame._key = Key.make();
        DKV.put(withAppendedFrame);

        accFrame.delete();
        accFrame = withAppendedFrame.deepCopy(Key.make().toString());
        DKV.put(accFrame);
        withAppendedFrame.delete();
//        printOutFrameAsTable(accFrame);
      }

      accFrame.delete();
    }

  @Test
  public void referentialTransparencyTest() {
    Frame fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA")
            .withVecTypes(Vec.T_NUM)
            .withDataForCol(0, ar(1,2))
            .build();

    Frame fr2 = new TestFrameBuilder()
            .withName("testFrame2")
            .withColNames("ColA")
            .withVecTypes(Vec.T_NUM)
            .withDataForCol(0, ar(3, 4))
            .build();

    Frame newReferenceFrame = fr;
    assertEquals(1, newReferenceFrame.vec(0).at(0), 1e-5);

    newReferenceFrame = fr2;

    assertEquals(3, newReferenceFrame.vec(0).at(0), 1e-5);

    newReferenceFrame.delete(); // And we should not delete fr2 explicitly since it will be deleted by reference.
    assertEquals(1, fr.vec(0).at(0), 1e-5);
    fr.delete();
  }

  @After
  public void afterEach() {
    if (fr != null) fr.delete();
  }

  private void encodingMapCleanUp(Map<String, Frame> encodingMap) {
    for (Map.Entry<String, Frame> map : encodingMap.entrySet()) {
      map.getValue().delete();
    }
  }

}
