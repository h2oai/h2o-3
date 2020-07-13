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
  public void TargetEncoder_constructor_should_fail_on_empty_teColumns() {
    String[] teColumns = {};
    new TargetEncoder(teColumns);
  }

  @Test(expected = IllegalArgumentException.class)
    public void prepareEncoding_should_fail_on_null_data() {
      String[] teColumns = {"ColA"};
      TargetEncoder tec = new TargetEncoder(teColumns);
      tec.prepareEncodingMap(null, "ColB", null);
    }

  @Test
  public void prepareEncoding_should_fail_on_unknown_teColumn() {
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
  public void frame_key_can_be_changed() {
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
  public void test_imputation_of_binary_categorical_column() {
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
  public void test_imputation_of_multiclass_categorical_column() {
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
    public void all_teColumns_must_be_categorical() {
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
    public void all_teColumns_must_be_present_in_training_frame() {
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
    public void test_prepareEncodingMap_without_foldColumn() {
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

    @Test
    public void test_imputeMissingValues() {
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
    public void test_calculatePriorMean() {
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
    public void test_groupEncodingsByCategory() {
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


    // ----------------------------- blended average -----------------------------------------------------------------//
    @Test
    public void test_applyEncodings_with_blending() throws Exception {
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
        tec.applyEncodings(encoded, targetColumn+"_te", priorMean, TargetEncoder.DEFAULT_BLENDING_PARAMS);

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
  public void applyEncodings_should_keep_row_order() {

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
      
      int changedIndex = new Random().nextInt(sizeOfDataset);
      fr.vec(0).set(changedIndex, 0);
      fr.vec(3).set(changedIndex, 0);

      String encodedColumn = "encoded";
      TargetEncoder te = new TargetEncoder(new String[] {"foo"});
      te.applyEncodings(fr, encodedColumn, 42, null);

      assertEquals(0, fr.vec(encodedColumn).at(changedIndex), 1e-5);
      assertVecEquals(fr.vec(3), fr.vec(encodedColumn), 1e-5);
    } finally {
      Scope.exit();
    }
  }

    @Test
    public void evaluate_applyEncodings_perf_with_blending() {
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

          String encodedColumn = "encoded";
          TargetEncoder te = new TargetEncoder(new String[] {"foo"});
          te.applyEncodings(fr, encodedColumn, 42, blendingParams);
        } finally {
          Scope.exit();
        }
      }
      long finishTimeEncoding = System.currentTimeMillis();
      System.out.println("Calculation of encodings took(ms): " + (finishTimeEncoding - startTimeEncoding));
      System.out.println("Avg calculation of encodings took(ms): " + (double)(finishTimeEncoding - startTimeEncoding) / numberOfRuns);

    }

  @Test
  public void evaluate_applyEncodings_perf_without_blending() {
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

        String encodedColumn = "encoded";
        TargetEncoder te = new TargetEncoder(new String[] {"foo"});
        te.applyEncodings(fr, encodedColumn, 42, null);
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
    public void mergeEncodings_should_append_numerator_and_denominator_columns() {
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
    public void addNoise_should_not_be_loud() {
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
    public void test_target_column_support() {
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
          tec.ensureTargetColumnIsSupported(fr, "ColA");
          fail();
        } catch (Exception ex) {
          assertTrue(ex.getMessage().startsWith("`target` must be a binary vector"));
        }

        // Check that string column will be rejected.
        try {
          tec.ensureTargetColumnIsSupported(fr, "ColC");
          fail();
        } catch (Exception ex) {
          assertTrue(ex.getMessage().startsWith("`target` must be a categorical vector"));
        }

        // Check that numerical column is not supported for now
        try {
          tec.ensureTargetColumnIsSupported(fr, "ColB");
          fail();
        } catch (Exception ex) {
          assertTrue(ex.getMessage().startsWith("`target` must be a categorical vector"));
        }

        // Check that binary categorical is ok (transformation is checked in another test)
        Frame tmp4 = tec.ensureTargetColumnIsSupported(fr, "ColD");
        Scope.track(tmp4);

        assertTrue(tmp4.vec(3).isNA(3));
      } finally {
        Scope.exit();
      }
    }

    @Test
    public void test_filterOutNAsFromTargetColumn() {
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
        Frame tmp2 = tec.ensureTargetColumnIsSupported(tmp1, targetColumnName);
        Scope.track(tmp2);

        Vec expected = vec(0, 1, 1);
        assertVecEquals(expected, tmp2.vec(0), 1e-5);

        expected.remove();
      } finally {
        Scope.exit();
      }
    }

  @Test
  public void applyTargetEncoding_can_be_applied_to_frames_without_target() {
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
  public void empty_strings_and_NAs_should_be_treated_as_new_categories() {
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
