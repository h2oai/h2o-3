package ai.h2o.targetencoding;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import water.*;
import water.fvec.*;
import water.rapids.Rapids;
import water.rapids.Val;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.*;

public class TargetEncodingTest extends TestUtil {

  @BeforeClass public static void setup() {
    stall_till_cloudsize(1);
  }

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();


    @Test(expected = IllegalArgumentException.class)
    public void targetEncoderPrepareEncodingFrameValidationDataIsNotNullTest() {

      String[] teColumns = {"ColA"};
      TargetEncoder tec = new TargetEncoder(teColumns);

      tec.prepareEncodingMap(null, "ColB", null);
    }


    @Test(expected = IllegalArgumentException.class)
    public void targetEncoderPrepareEncodingFrameValidationTEColumnsIsNotEmptyTest() {

      String[] teColumns = {};
      TargetEncoder tec = new TargetEncoder(teColumns);

      tec.prepareEncodingMap(null, "2", null);
    }

    @Test
    public void teColumnExistsTest() {
      try {
        Scope.enter();
        final Frame fr = new TestFrameBuilder()
                .withColNames("ColA", "ColB")
                .withVecTypes(Vec.T_CAT, Vec.T_CAT)
                .withDataForCol(0, ar("a", "b"))
                .withDataForCol(1, ar("yes", "no"))
                .build();
        String teColumnName = "ColThatNotExist";
        String[] teColumns = {teColumnName};
        TargetEncoder tec = new TargetEncoder(teColumns);

        try {
          tec.prepareEncodingMap(fr, "ColB", null);
          fail();
        } catch (AssertionError ex) {
          assertEquals("Column name `" + teColumnName + "` was not found in the provided data frame", ex.getMessage());
        }
      } finally {
        Scope.exit();
      }
    }

    @Test
    public void changeKeyFrameTest() {
      try {
        Scope.enter();
        final Frame fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA")
                .withVecTypes(Vec.T_NUM)
                .withDataForCol(0, ard(1, 2))
                .build();
        String tree = "( append testFrame 42 'appended' )";
        Val val = Rapids.exec(tree);
        final Frame res = val.getFrame();
        res._key = fr._key;
        DKV.put(fr._key, res);
        Scope.track(res);

      } finally {
        Scope.exit();
      }
    }

    @Test
    public void imputationWorksForBinaryCategoricalColumnsTest() {
      long seed = 42L;
      try {
        Scope.enter();
        final Frame fr = new TestFrameBuilder()
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

        tec.imputeCategoricalColumn(fr, "ColA", "ColA_NA");

        Vec colA = fr.vec("ColA");

        assertTrue(colA.isCategorical());
        assertEquals(3, colA.cardinality());

        //Checking here that we have replaced NA with index of the new category
        assertEquals(2, colA.at(2), 1e-5);
        assertEquals("ColA_NA", colA.domain()[2]);
      } finally {
        Scope.exit();
      }
    }

  @Test
  public void imputationWorksForMultiCategoricalColumnsTest() {
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames("ColA")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "c", "d", null, null, null))
              .withChunkLayout(2, 2, 2, 1)
              .build();

      String[] teColumns = {""};
      TargetEncoder tec = new TargetEncoder(teColumns);

      assertTrue(fr.vec("ColA").isCategorical());
      assertEquals(4, fr.vec("ColA").cardinality());

      tec.imputeCategoricalColumn(fr, "ColA", "ColA_NA");

      Vec colA = fr.vec("ColA");

      assertTrue(colA.isCategorical());
      assertEquals(5, colA.cardinality());

      //Checking here that we have replaced NA with index of the new category
      assertEquals(4, colA.at(4), 1e-5);
      assertEquals("ColA_NA", colA.domain()[4]);
    } finally {
      Scope.exit();
    }
  }

    @Test
    public void allTEColumnsAreCategoricalTest() {
      try {
        Scope.enter();
        TestFrameBuilder baseBuilder = new TestFrameBuilder()
                .withColNames("ColA", "ColB", "ColC")
                .withDataForCol(0, ar("1", "0"))
                .withDataForCol(2, ar("1", "6"));

        String[] teColumns = {"ColA", "ColB"};
        String targetColumnName = "ColC";
        TargetEncoder tec = new TargetEncoder(teColumns);
        Map<String, Frame> encodingMap = null;

        Frame fr = baseBuilder
                .withDataForCol(1, ar(0, 1))
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
                .build();
        try {
          tec.prepareEncodingMap(fr, targetColumnName, null);
          fail();
        } catch (IllegalArgumentException ex) {
          assertEquals("Argument 'columnsToEncode' should contain only names of categorical columns", ex.getMessage());
        }

        Frame fr2 = baseBuilder
                .withDataForCol(1, ar("a", "b"))
                .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT)
                .build();

        try {
          encodingMap = tec.prepareEncodingMap(fr2, targetColumnName, null);
        } catch (IllegalArgumentException ex) {
          fail(String.format("All columns were categorical but something else went wrong: %s", ex.getMessage()));
        }

        encodingMapCleanUp(encodingMap);
      } finally {
        Scope.exit();
      }
    }

    @Test
    public void checkAllTEColumnsExistAndAreCategoricalTest() {
      try {
        Scope.enter();
        Frame fr = new TestFrameBuilder()
                .withColNames("ColA")
                .withDataForCol(0, ar("1", "0"))
                .withVecTypes(Vec.T_CAT)
                .build();

        String[] teColumns = {"ColA", "ColNonExist"};
        String targetColumnName = "ColC";
        TargetEncoder tec = new TargetEncoder(teColumns);

        try {
          tec.prepareEncodingMap(fr, targetColumnName, null);
          fail();
        } catch (AssertionError ex) {
          assertEquals("Column name `ColNonExist` was not found in the provided data frame", ex.getMessage());
        }

      } finally {
        Scope.exit();
      }

    }

    @Test
    public void prepareEncodingMapWithoutFoldColumnCaseTest() {
      try {
        Scope.enter();
        final Frame fr = new TestFrameBuilder()
                .withColNames("ColA", "ColB", "ColC")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
                .withDataForCol(0, ar("a", "b", "b", "b"))
                .withDataForCol(1, ard(1, 1, 4, 7))
                .withDataForCol(2, ar("2", "6", "6", "6"))
                .build();

        String[] teColumns = {"ColA"};
        String targetColumnName = "ColC";
        TargetEncoder tec = new TargetEncoder(teColumns);

        Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, targetColumnName, null);

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
      final Frame fr = new TestFrameBuilder()
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b"))
              .withDataForCol(1, ar("yes", "no"))
              .build();

      final boolean flag = false;
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
      try {
        Scope.enter();
        final Frame fr = new TestFrameBuilder()
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

        tec.imputeMissingValues(fr, 0, 1.5);
        Vec expected = dvec(1, 2, 1.5);
        Vec resultVec = fr.vec(0);
        assertVecEquals(expected, resultVec, 1e-5);

        expected.remove();
        strVec.remove();
        resultVec.remove();
        numericVec.remove();
      } finally {
        Scope.exit();
      }
    }

    @Test
    public void rbindTest() {
      try {
        Scope.enter();
        final Frame fr = new TestFrameBuilder()
                .withColNames("ColA")
                .withVecTypes(Vec.T_NUM)
                .withDataForCol(0, ar(1))
                .build();

        String[] teColumns = {""};
        TargetEncoder tec = new TargetEncoder(teColumns);

        final Frame result = tec.rBind(null, fr);
        Scope.track(result);
        assertEquals(fr._key, result._key);

        final Frame fr2 = new TestFrameBuilder()
                .withColNames("ColA")
                .withVecTypes(Vec.T_NUM)
                .withDataForCol(0, ar(42))
                .build();

        Frame result2 = tec.rBind(fr, fr2);
        Scope.track(result2);

        assertEquals(1, result2.vec("ColA").at(0), 1e-5);
        assertEquals(42, result2.vec("ColA").at(1), 1e-5);
      } finally {
        Scope.exit();
      }
    }

    @Test
    public void calculateSingleNumberResultTest() {
      try {
        Scope.enter();
        final Frame fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA")
                .withVecTypes(Vec.T_NUM)
                .withDataForCol(0, ard(1, 2, 3))
                .build();
        String tree = "(sum (cols testFrame [0.0] ))";
        Val val = Rapids.exec(tree);
        assertEquals(val.getNum(), 6.0, 1e-5);
      } finally {
        Scope.exit();
      }
    }

    @Test
    public void calculateGlobalMeanTest() {
      try {
        Scope.enter();
        final Frame fr = new TestFrameBuilder()
                .withColNames("numerator", "denominator")
                .withVecTypes(Vec.T_NUM, Vec.T_NUM)
                .withDataForCol(0, ard(1, 2, 3))
                .withDataForCol(1, ard(3, 4, 5))
                .build();
        String[] teColumns = {""};
        TargetEncoder tec = new TargetEncoder(teColumns);
        double result = tec.calculatePriorMean(fr);

        assertEquals(result, 0.5, 1e-5);
      } finally {
        Scope.exit();
      }
    }

    @Test
    public void groupEncodingsByCategoryTest() {
      try {
        Scope.enter();
        final Frame fr = new TestFrameBuilder()
                .withColNames("teColumn", "numerator", "denominator")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM)
                .withDataForCol(0, ar("a", "a", "b"))
                .withDataForCol(1, ard(1, 2, 3))
                .withDataForCol(2, ard(3, 4, 5))
                .build();
        Frame result = TargetEncoder.groupEncodingsByCategory(fr, 0);
        Scope.track(result);

        Vec expectedNum = vec(3, 3);
        assertVecEquals(expectedNum, result.vec("numerator"), 1e-5);
        Vec expectedDen = vec(7, 5);
        assertVecEquals(expectedDen, result.vec("denominator"), 1e-5);

        result.delete();
        expectedNum.remove();
        expectedDen.remove();
      } finally {
        Scope.exit();
      }
    }


    @Test
    public void mapOverTheFrameWithImmutableApproachTest() {
      try {
        Scope.enter();
        final Frame fr = new TestFrameBuilder()
                .withColNames("ColA", "ColB", "ColC")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "c"))
                .withDataForCol(1, ar(1, 2, 3))
                .withDataForCol(2, ar(4, 5, 6))
                .build();

        Frame oneColumnMultipliedOnly = new CalculatedColumnTask(1).doAll(Vec.T_NUM, fr).outputFrame();
        Scope.track(oneColumnMultipliedOnly);
//      printOutFrameAsTable(oneColumnMultipliedOnly, false, oneColumnMultipliedOnly.numRows());
        assertEquals(1, oneColumnMultipliedOnly.numCols());

        Vec expectedVec = vec(2, 4, 6);
        Vec outcomeVec = oneColumnMultipliedOnly.vec(0);
        assertVecEquals(expectedVec, outcomeVec, 1e-5);

        expectedVec.remove();
        outcomeVec.remove();
        oneColumnMultipliedOnly.delete();
      } finally {
        Scope.exit();
      }
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
      try {
        Scope.enter();
        final Frame fr = new TestFrameBuilder()
                .withColNames("ColA", "ColB", "ColC")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "c"))
                .withDataForCol(1, ar(1, 2, 3))
                .withDataForCol(2, ar(4, 5, 6))
                .build();

        new TestMutableTask(1).doAll(fr);

//      printOutFrameAsTable(fr, false, fr.numRows());
        assertEquals(3, fr.numCols());

        Vec expected = vec(2, 4, 6);
        assertVecEquals(expected, fr.vec(1), 1e-5);

        expected.remove();
      } finally {
        Scope.exit();
      }
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
    public void calculateAndAppendBlendedTEEncodingTest() throws Exception {
      final File tmpFile = temporaryFolder.newFile();
      Map<String, Frame> targetEncodingMap = null;
      try {
        Scope.enter();

        final Frame fr = new TestFrameBuilder()
                .withColNames("ColA", "ColB")
                .withVecTypes(Vec.T_CAT, Vec.T_CAT)
                .withDataForCol(0, ar("a", "b", "a"))
                .withDataForCol(1, ar("yes", "no", "yes"))
                .withChunkLayout(1, 1, 1)
                .build();

        Job export = Frame.export(fr, tmpFile.getAbsolutePath(), fr._key.toString(), true, 1);
        export.get();

        final Frame reimportedFrame = parse_test_file(Key.make("parsed"), tmpFile.getAbsolutePath(), true);
        Scope.track(reimportedFrame);

        String columnToEncode = "ColA";
        String targetColumn = "ColB";
        TargetEncoder tec = new TargetEncoder(new String[]{columnToEncode});
        targetEncodingMap = tec.prepareEncodingMap(reimportedFrame, targetColumn, null);

        Frame encodings = targetEncodingMap.get(columnToEncode); 
        final Frame encoded = tec.mergeEncodings(reimportedFrame, encodings, 0, 0);
        Scope.track(encoded);

        double priorMean = tec.calculatePriorMean(targetEncodingMap.get(columnToEncode));
        tec.calculateAndAppendEncodedColumn(encoded, targetColumn+"_te", priorMean, TargetEncoder.DEFAULT_BLENDING_PARAMS);

//      String[] dom = resultWithEncoding.vec(1).domain();
        // k <- 10
        // f <- 20
        // global_mean <- sum(x_map$numerator)/sum(x_map$denominator)
        // lambda <- 1/(1 + exp((-1)* (te_frame$denominator - k)/f))
        // te_frame$target_encode <- ((1 - lambda) * global_mean) + (lambda * te_frame$numerator/te_frame$denominator)

        double globalMean = 2.0 / 3;
        double k = 10.0;
        int f = 20;
        double lambda1 = 1.0 / (1.0 + (Math.exp((k - 2) / f)));
        double te1 = (1.0 - lambda1) * globalMean + (lambda1 * 2 / 2);

        double lambda2 = 1.0 / (1 + Math.exp((k - 1) / f));
        double te2 = (1.0 - lambda2) * globalMean + (lambda2 * 0 / 1);

        double lambda3 = 1.0 / (1.0 + (Math.exp((k - 2) / f)));
        double te3 = (1.0 - lambda3) * globalMean + (lambda3 * 2 / 2);

        assertEquals(te1, encoded.vec(4).at(0), 1e-5);
        assertEquals(te2, encoded.vec(4).at(1), 1e-5);
        assertEquals(te3, encoded.vec(4).at(2), 1e-5);

      } finally {
        Scope.exit();
        encodingMapCleanUp(targetEncodingMap);
      }
    }

  @Test
  public void calculateAndAppendEncodingsOrderIsPreservedWhenWeUseAddMethodTest() {

    int sizeOfDataset = 1000000;
    long seed = 42L;
    double[] arr = new double[sizeOfDataset];
      Arrays.fill(arr, 0.5);
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames("numerator", "denominator", "target", "encodings_to_compare_with")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withRandomDoubleDataForCol(0, sizeOfDataset, 1, 1, seed)
              .withRandomDoubleDataForCol(1, sizeOfDataset, 2, 2, seed + 1)
              .withRandomBinaryDataForCol(2, sizeOfDataset, seed + 2)
              .withDataForCol(3, arr) // vec that is used for comparing with encodings
              .withChunkLayout(100, 200, 300, sizeOfDataset - 600)
              .build();
      Vec zeroVec = Vec.makeZero(sizeOfDataset);
      fr.add("placeholder_for_encodings", zeroVec); // should get 4th index

      int changedIndex = new Random().nextInt(sizeOfDataset);
      fr.vec(0).set(changedIndex, 0);
      fr.vec(3).set(changedIndex, 0);


      new TargetEncoder.ApplyEncodings(4, 0, 1, 42, null).doAll(fr);

      zeroVec.remove();
      assertEquals(0, fr.vec("placeholder_for_encodings").at(changedIndex), 1e-5);
      assertVecEquals(fr.vec(3), fr.vec("placeholder_for_encodings"), 1e-5);
    } finally {
      Scope.exit();
    }
  }

    @Test
    public void calculateAndAppendBlendedTEEncodingPerformanceTest() {
      long startTimeEncoding = System.currentTimeMillis();

      int numberOfRuns = 10;
      long seed = 42L;
      for(int i = 0; i < numberOfRuns; i ++) {
        try {
          Scope.enter();
          int dataframeSize = 1000000;
          Frame fr = new TestFrameBuilder()
                  .withColNames("numerator", "denominator", "target")
                  .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
                  .withRandomDoubleDataForCol(0, dataframeSize, 0, 50, seed)
                  .withRandomDoubleDataForCol(1, dataframeSize, 1, 100, seed + 1)
                  .withRandomBinaryDataForCol(2, dataframeSize, seed + 2)
                  .build();

          BlendingParams blendingParams = new BlendingParams(20, 10);

          Vec zeroVec = Vec.makeZero(dataframeSize);
          fr.add("placeholder_for_encodings", zeroVec);
          int encodedColumnIdx = 3;

          new TargetEncoder.ApplyEncodings(encodedColumnIdx, 0, 1, 42, blendingParams).doAll(fr);
          zeroVec.remove();
        } finally {
          Scope.exit();
        }
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
      try {
        Scope.enter();
        int dataframeSize = 1000000;

        final Frame fr = new TestFrameBuilder()
                .withColNames("numerator", "denominator", "target")
                .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
                .withRandomDoubleDataForCol(0, dataframeSize, 0, 50, seed)
                .withRandomDoubleDataForCol(1, dataframeSize, 1, 100, seed + 1)
                .withRandomBinaryDataForCol(2, dataframeSize, seed + 2)
                .build();

        Vec zeroVec = Vec.makeZero(dataframeSize);
        fr.add("placeholder_for_encodings", zeroVec);

        new TargetEncoder.ApplyEncodings(3, 0, 1, 42, null).doAll(fr);
        zeroVec.remove();
      } finally {
        Scope.exit();
      }
    }
    long finishTimeEncoding = System.currentTimeMillis();
    System.out.println("Calculation of encodings took(ms): " + (finishTimeEncoding - startTimeEncoding));
    System.out.println("Avg calculation of encodings took(ms): " + (double)(finishTimeEncoding - startTimeEncoding) / numberOfRuns);
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
      try {
        Scope.enter();
        final Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "fold")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "a"))
              .withDataForCol(1, ar(1,1,2))
              .build();

        Frame encodingsFrame = new TestFrameBuilder()
              .withName("encodingsFrame")
              .withColNames("ColA", "foldValueForMerge", "numerator", "denominator")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "a"))
              .withDataForCol(1, ar(1, 2, 2))
              .withDataForCol(2, ar(22, 55, 88))
              .withDataForCol(3, ar(33, 66, 99))
              .build();

        String[] teColumns = {""};
        TargetEncoder tec = new TargetEncoder(teColumns);

        Frame merged = tec.mergeEncodings(fr, encodingsFrame, 0, 1, 0, 1, 2);
        Scope.track(merged);
          
        Vec expectedStr = svec("22", null, "88");
        Vec expected = expectedStr.toNumericVec();
        Vec actualNumerator = merged.vec("numerator");
        assertVecEquals(expected, actualNumerator, 1e-5);

        expectedStr.remove();
      } finally {
        Scope.exit();
      }
    }

    @Test
    public void AddNoiseLevelTest() {
      try {
        Scope.enter();
        final Frame fr = new TestFrameBuilder()
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
        } catch (AssertionError ex) {
        }

        //Vectors with the noises generated from the same seeds should be equal
        assertVecEquals(fr.vec(0), fr.vec(2), 0.0);

      } finally {
        Scope.exit();
      }
    }

    @Test
    public void getColumnNamesByIndexesTest() {
      try {
        Scope.enter();
        final Frame fr = new TestFrameBuilder()
                .withColNames("ColA", "ColB", "ColC")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
                .withDataForCol(0, ar("a", "b"))
                .withDataForCol(1, ard(1, 1))
                .withDataForCol(2, ar("2", "6"))
                .build();
        String[] teColumns = {""};
        TargetEncoder tec = new TargetEncoder(teColumns);
        int[] columns = ari(0, 2);
        String[] columnNames = tec.getColumnNamesBy(fr, columns);
        assertEquals("ColA", columnNames[0]);
        assertEquals("ColC", columnNames[1]);
      } finally {
        Scope.exit();
      }
    }

    @Test
    public void ensureTargetColumnIsNumericOrBinaryCategoricalTest() {
      try {
        Scope.enter();
        final Frame fr = new TestFrameBuilder()
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
          assertTrue(ex.getMessage().startsWith("`target` must be a binary vector"));
        }

        // Check that string column will be rejected.
        try {
          tec.ensureTargetColumnIsBinaryCategorical(fr, "ColC");
          fail();
        } catch (Exception ex) {
          assertTrue(ex.getMessage().startsWith("`target` must be a categorical vector"));
        }

        // Check that numerical column is not supported for now
        try {
          tec.ensureTargetColumnIsBinaryCategorical(fr, "ColB");
          fail();
        } catch (Exception ex) {
          assertTrue(ex.getMessage().startsWith("`target` must be a categorical vector"));
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
      try {
        Scope.enter();
        final Frame fr = new TestFrameBuilder()
                .withColNames(targetColumnName)
                .withVecTypes(Vec.T_CAT)
                .withDataForCol(0, ar("2", "6", "6", null))
                .build();

        String[] teColumns = {""};
        TargetEncoder tec = new TargetEncoder(teColumns);

        Frame tmp1 = tec.filterOutNAsFromTargetColumn(fr, 0);
        Scope.track(tmp1);
        Frame tmp2 = tec.ensureTargetColumnIsBinaryCategorical(tmp1, targetColumnName);
        Scope.track(tmp2);

        Vec expected = vec(0, 1, 1);
        assertVecEquals(expected, tmp2.vec(0), 1e-5);

        expected.remove();
      } finally {
        Scope.exit();
      }
    }

  @Test
  public void isBinaryTest() {
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("NO", "YES", "NO"))
              .withDataForCol(1, ard(0, 0.5, 1))
              .build();

      assertTrue(fr.vec(0).isBinary());
      assertFalse(fr.vec(1).isBinary());
    } finally {
      Scope.exit();
    }
  }

    // Can we do it simply ? with mutation?
    @Test
    public void appendingColumnsInTheLoopTest() {
      try {
        Scope.enter();
        final Frame fr = new TestFrameBuilder()
                .withColNames("ColA")
                .withVecTypes(Vec.T_NUM)
                .withDataForCol(0, ar(1, 2))
                .build();

        Frame accFrame = fr.deepCopy(Key.make().toString());
        DKV.put(accFrame);
        Scope.track(accFrame);

//      printOutFrameAsTable(accFrame, true, false);

        for (int i = 0; i < 3; i++) {

          String tree = String.format("( append %s %d 'col_%d' )", accFrame._key, i, i);
          final Frame withAppendedFrame = Rapids.exec(tree).getFrame();
          withAppendedFrame._key = Key.make();
          DKV.put(withAppendedFrame);
          Scope.track(withAppendedFrame);


          final Frame accFrame2 = withAppendedFrame.deepCopy(Key.make().toString());
          DKV.put(accFrame2);
          Scope.track(accFrame2);
        }

      } finally {
        Scope.exit();
      }
    }

  @Test
  public void referentialTransparencyTest() {
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames("ColA")
              .withVecTypes(Vec.T_NUM)
              .withDataForCol(0, ar(1, 2))
              .build();

      final Frame fr2 = new TestFrameBuilder()
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

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void transformFrameWithoutResponseColumn() {
    try {
      Scope.enter();
      String teColumnName = "ColA";
      final Frame fr = new TestFrameBuilder()
              .withColNames(teColumnName, "y")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "b", "b"))
              .withDataForCol(1, ar("2", "6", "6", "2"))
              .build();

      final Frame frameWithoutResponse = new TestFrameBuilder()
              .withColNames(teColumnName)
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "b", "b"))
              .build();

      String[] teColumns = {teColumnName};
      TargetEncoder tec = new TargetEncoder(teColumns);

      Map<String, Frame> encodingMap = tec.prepareEncodingMap(fr, "y", null);
      Scope.track(encodingMap.get(teColumnName));

      Frame encoded = tec.applyTargetEncoding(
              frameWithoutResponse, 
              "y",
              encodingMap,
              TargetEncoder.DataLeakageHandlingStrategy.None,
              null,
              null,
              1234
      );
      Scope.track(encoded);
    } finally {
      Scope.exit();
    }
  }

  
  public static class ImputeNATestMRTask extends MRTask<ImputeNATestMRTask>{
    @Override
    public void map(Chunk[] cs) {
      for (int i = 0; i < cs[0].len(); i++) {
        cs[0].vec().factor(2);
      }
    }
  }
    
  @Test
  public void imputeNAsForColumnDistributedTest() {
    Scope.enter();
    try {
      String teColumnName = "ColA";
      String targetColumnName = "ColB";
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames(teColumnName, targetColumnName)
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", null, null, null))
              .withDataForCol(1, ar("2", "6", "6", "2", "6"))
              .withChunkLayout(3, 2)
              .build();

      String nullStr = null;
      fr.vec(0).set(2, nullStr);

      String[] teColumns = {""};
      TargetEncoder tec = new TargetEncoder(teColumns);

      assertTrue(fr.vec("ColA").isCategorical());
      assertEquals(2, fr.vec("ColA").cardinality());

      tec.imputeCategoricalColumn(fr, "ColA", "ColA_NA");

      new ImputeNATestMRTask().doAll(fr);

      // assumption is that domain is being properly distributed over nodes 
      // and there will be no exception while attempting to access new domain's value in `cs[0].vec().factor(2);`

      assertEquals(3, fr.vec("ColA").cardinality());

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void emptyStringsAndNAsAreTreatedAsDifferentCategoriesTest() {
    Scope.enter();
    Map<String, Frame> targetEncodingMap = null;
    try {
      String teColumnName = "ColA";
      String targetColumnName = "ColB";
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames(teColumnName, targetColumnName)
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "", "", null)) // null and "" are different categories even though they look the same in printout
              .withDataForCol(1, ar("2", "6", "6", "2", "6"))
              .withChunkLayout(3, 2)
              .build();

      String[] teColumns = {teColumnName};
      TargetEncoder tec = new TargetEncoder(teColumns);

      targetEncodingMap = tec.prepareEncodingMap(fr, targetColumnName, null);
      Frame resultWithEncoding = tec.applyTargetEncoding(
              fr,
              targetColumnName,
              targetEncodingMap,
              TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut,
              null,
              null,
              0.0,
              1234);
      Scope.track(resultWithEncoding);

      assertEquals(4, resultWithEncoding.vec("ColA").cardinality());
    } finally {
      if (targetEncodingMap != null) encodingMapCleanUp(targetEncodingMap);
      Scope.exit();
    }
  }

  private void encodingMapCleanUp(Map<String, Frame> encodingMap) {
    for (Map.Entry<String, Frame> map : encodingMap.entrySet()) {
      map.getValue().delete();
    }
  }

}
