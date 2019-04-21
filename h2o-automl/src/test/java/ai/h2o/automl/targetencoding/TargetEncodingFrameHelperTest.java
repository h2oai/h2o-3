package ai.h2o.automl.targetencoding;

import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.util.Arrays;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.*;
import static org.junit.Assert.*;

public class TargetEncodingFrameHelperTest extends TestUtil {


  @BeforeClass public static void setup() {
    stall_till_cloudsize(1);
  }

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
      Frame tmp = addCon(fr,"ColC", 42);
      Vec expectedConstVec = vec(42, 42);

      assertVecEquals(expectedConstVec, tmp.vec("ColC"), 1e-5);
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
              .withDataForCol(1, ar(null, "6", null))
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
              .withDataForCol(1, ar(null, "6", null))
              .build();

      Frame result = filterByValue(fr, 0, 42);
      Scope.track(result);

      assertEquals(1L, result.numRows());
      assertEquals("6", result.vec(1).stringAt(0));
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
              .withDataForCol(1, ar(null, "6", null))
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
              .withDataForCol(2, ar("2", "6"))
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
}
