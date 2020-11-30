package ai.h2o.targetencoding;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.Arrays;
import java.util.Random;

import static ai.h2o.targetencoding.TargetEncoderHelper.*;
import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class TargetEncodingHelperTest extends TestUtil {
  
  @Test
  public void addVecToFrameTest() {
    Scope.enter();
    try {
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar("a", "b"))
              .build();

      Vec vec = vec(1, 2);
      fr.add("ColB", vec);
      Scope.track(vec);

      assertVecEquals(vec, fr.vec("ColB"), 1e-5);

      // add constant vector
      addCon(fr,"ColC", 42);
      Vec expectedConstVec = vec(42, 42);

      assertVecEquals(expectedConstVec, fr.vec("ColC"), 1e-5);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void filterOutNAsTest() {
    Scope.enter();
    try {
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_NUM, Vec.T_STR)
              .withDataForCol(0, ard(1, 42, 33))
              .withDataForCol(1, ar(null, "Y", null))
              .build();

      Frame result = filterOutNAsInColumn(fr,1);

      Scope.track(result);
      assertEquals(1L, result.numRows());
      assertEquals(42, result.vec(0).at(0), 1e-5);

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void filterByValueTest() {
    Scope.enter();
    try {
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_NUM, Vec.T_STR)
              .withDataForCol(0, ard(1, 42, 33))
              .withDataForCol(1, ar(null, "Y", null))
              .build();

      Frame result = filterByValue(fr, 0, 42);
      Scope.track(result);

      assertEquals(1L, result.numRows());
      assertEquals("Y", result.vec(1).stringAt(0));
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void filterNotByValueTest() {
    Scope.enter();
    try {
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_NUM, Vec.T_STR)
              .withDataForCol(0, ard(1, 42, 33))
              .withDataForCol(1, ar(null, "Y", null))
              .build();
      Frame result = filterNotByValue(fr,0, 42);
      Scope.track(result);

      assertEquals(2L, result.numRows());
      assertVecEquals(vec(1, 33), result.vec(0), 1e-5);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void renameColumnTest() {
    Scope.enter();
    try {
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB", "ColC", "fold_column")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b"))
              .withDataForCol(1, ard(1, 1))
              .withDataForCol(2, ar("N", "Y"))
              .withDataForCol(3, ar(1, 2))
              .build();

      // Case1: Renaming by index
      int indexOfColumnToRename = 0;
      String newName = "NewColA";
      renameColumn(fr, indexOfColumnToRename, newName);

      assertEquals(fr.names()[indexOfColumnToRename], newName);

      // Case2: Renaming by name
      String newName2 = "NewColA-2";
      renameColumn(fr, "NewColA", newName2);
      assertEquals(fr.names()[indexOfColumnToRename], newName2);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testUniqueValuesBy() {
    Scope.enter();
    try {
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("column1")
              .withVecTypes( Vec.T_NUM)
              .withDataForCol(0, ar(1, 2, 2, 3, 2))
              .build();
      Frame uniqueValuesFrame = uniqueValuesBy(fr,0);
      Vec uniqueValuesVec = uniqueValuesFrame.vec(0);
      long numberOfUniqueValues = uniqueValuesVec.length();
      int length = (int) numberOfUniqueValues;
      long[] uniqueValuesArr = new long[length];
      for(int i = 0; i < numberOfUniqueValues; i++) {
        uniqueValuesArr[i] = uniqueValuesVec.at8(i);
      }

      Arrays.sort(uniqueValuesArr);
      assertArrayEquals( ar(1L, 2L, 3L), uniqueValuesArr);
      Scope.track(uniqueValuesFrame);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testAddKFoldColumn() {
    Scope.enter();
    try {
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "c", "d"))
              .build();
      Scope.track(fr);
      int nfolds = 5;
      addKFoldColumn(fr, "fold", nfolds, -1);

      assertTrue(fr.vec(1).at(0) < nfolds);
      assertTrue(fr.vec(1).at(1) < nfolds);
      assertTrue(fr.vec(1).at(2) < nfolds);
      assertTrue(fr.vec(1).at(3) < nfolds);

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void registerTest() {
    Scope.enter();
    try {
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .build();
      Scope.track(fr);

      Key<Frame> keyBefore = fr._key;
      DKV.remove(keyBefore);
      Frame res = register(fr);
      Scope.track(res);

      assertNotSame(res._key, keyBefore);
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

      final Frame result = rBind(null, fr);
      Scope.track(result);
      assertEquals(fr._key, result._key);

      final Frame fr2 = new TestFrameBuilder()
              .withColNames("ColA")
              .withVecTypes(Vec.T_NUM)
              .withDataForCol(0, ar(42))
              .build();

      Frame result2 = rBind(fr, fr2);
      Scope.track(result2);

      assertEquals(1, result2.vec("ColA").at(0), 1e-5);
      assertEquals(42, result2.vec("ColA").at(1), 1e-5);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_imputation_of_binary_categorical_column() {
    long seed = 42;
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames("binary")
              .withVecTypes(Vec.T_CAT)
              .withRandomBinaryDataForCol(0, 1000, seed)
              .withChunkLayout(500, 500) // that way our task could be executed with 2 threads
              .build();
      String nullStr = null;
      int nullIdx = 23;
      fr.vec(0).set(nullIdx, nullStr);

      Vec binaryCol = fr.vec("binary");
      assertTrue(binaryCol.isCategorical());
      assertEquals(2, binaryCol.cardinality());
      
      imputeCategoricalColumn(fr, 0, "NA");

      Vec imputedBinaryCol = fr.vec("binary");
      assertTrue(imputedBinaryCol.isCategorical());
      assertEquals(3, imputedBinaryCol.cardinality());

      //Checking here that we have replaced NA with index of the new category
      assertEquals(2, imputedBinaryCol.at(nullIdx), 0);
      assertEquals("NA", imputedBinaryCol.domain()[2]);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_imputation_of_multiclass_categorical_column() {
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames("multiclass")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "c", "d", null, null, null))
              .withChunkLayout(2, 2, 2, 1)
              .build();

      Vec multiclassCol = fr.vec("multiclass");
      assertTrue(multiclassCol.isCategorical());
      assertEquals(4, multiclassCol.cardinality());
      
      imputeCategoricalColumn(fr, 0, "NA");

      Vec imputedMulticlassCol = fr.vec("multiclass");
      assertTrue(imputedMulticlassCol.isCategorical());
      assertEquals(5, imputedMulticlassCol.cardinality());

      //Checking here that we have replaced NA with index of the new category
      assertEquals(4, imputedMulticlassCol.at(4), 0);
      assertEquals("NA", imputedMulticlassCol.domain()[4]);
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
      double result = TargetEncoderHelper.computePriorMean(fr);
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
      Frame result = groupEncodingsByCategory(fr, 0);
      Scope.track(result);

      Vec expectedTeCol = vec(ar("a", "b"), 0, 1); Scope.track(expectedTeCol);
      Vec expectedNum = vec(3, 3); Scope.track(expectedNum);
      Vec expectedDen = vec(7, 5); Scope.track(expectedDen);
      
      assertCatVecEquals(expectedTeCol, result.vec("teColumn"));
      assertVecEquals(expectedNum, result.vec("numerator"), 1e-5);
      assertVecEquals(expectedDen, result.vec("denominator"), 1e-5);
    } finally {
      Scope.exit();
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
      applyEncodings(fr, encodedColumn, 42, null);

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
        applyEncodings(fr, encodedColumn, 42, blendingParams);
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
        applyEncodings(fr, encodedColumn, 42, null);
      } finally {
        Scope.exit();
      }
    }
    long finishTimeEncoding = System.currentTimeMillis();
    System.out.println("Calculation of encodings took(ms): " + (finishTimeEncoding - startTimeEncoding));
    System.out.println("Avg calculation of encodings took(ms): " + (double)(finishTimeEncoding - startTimeEncoding) / numberOfRuns);
  }

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

     Frame merged = mergeEncodings(fr, encodingsFrame, 0, 1, 0, 1, 2);
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
      addNoise(fr, 0, noiseLevel, 1234);
      addNoise(fr, 1, noiseLevel, 5678);
      addNoise(fr, 2, noiseLevel, 1234);
      Vec expected = vec(1, 2, 3);
      assertVecEquals(expected, fr.vec(0), 1e-2);

      try {
        assertVecEquals(fr.vec(0), fr.vec(1), 1e-5);
        fail("no noise detected");
      } catch (AssertionError ex) {
        assertFalse(ex.getMessage().contains("no noise detected"));
      }

      //Vectors with the noises generated from the same seeds should be equal
      assertVecEquals(fr.vec(0), fr.vec(2), 0.0);

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_filterOutNAsInColumn() {
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames("target")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar("N", "Y", "Y", null))
              .build();

      Frame filtered = filterOutNAsInColumn(fr, 0);
      Scope.track(filtered);

      Vec expected = vec(0, 1, 1); Scope.track(expected);
      assertVecEquals(expected, filtered.vec(0), 1e-5);
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void test_subtractTargetValueForLOO() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("categorical", "numerator", "denominator", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "b", "b", "a", "b"))
              .withDataForCol(1, ard(1, 1, 4, 7, 4, 2))
              .withDataForCol(2, ard(1, 1, 4, 7, 4, 6))
              .withDataForCol(3, ar("N", "Y", "Y", "Y", "Y", null))
              .build();

      subtractTargetValueForLOO(fr, "target", 1);

      // We check here that for  `target column = NA` we do not subtract anything and for other cases we subtract current row's target value
      Vec expectedNum = vec(1, 0, 3, 6, 3, 2); // num is decremented by 1 iff target==1 (here "Y" is 1)
      assertVecEquals(expectedNum, fr.vec("numerator"), 1e-5);
      Vec expectedDen = vec(0, 0, 3, 6, 3, 6); // den is decremented by 1 iff target is not NA
      assertVecEquals(expectedDen, fr.vec("denominator"), 1e-5);
    } finally {
      Scope.exit();
    }
  }


  @Test
  public void test_getUniqueColumnValues() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("fold_column")
              .withVecTypes(Vec.T_NUM)
              .withDataForCol(0, ar(1, 2, 2, 3, 2))
              .build();

      long[] result = getUniqueColumnValues(fr, 0);
      Arrays.sort(result);
      assertArrayEquals(ar(1L, 2L, 3L), result);
    } finally {
      Scope.exit();
    }
  }

}
