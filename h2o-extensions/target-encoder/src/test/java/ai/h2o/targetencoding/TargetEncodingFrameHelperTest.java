package ai.h2o.targetencoding;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.util.IcedHashMap;
import water.util.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;
import static ai.h2o.targetencoding.TargetEncoderFrameHelper.*;

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

  private Map<String, Frame> getTEMapForTitanicDataset(boolean withFoldColumn) {
    String foldColumnNameForTE = "te_fold_column";

    Frame trainFrame = null;
    try {
      trainFrame = parse_test_file("./smalldata/gbm_test/titanic.csv");
      String responseColumnName = "survived";
      asFactor(trainFrame, responseColumnName);

      if(withFoldColumn) {
        int nfolds = 5;
        addKFoldColumn(trainFrame, foldColumnNameForTE, nfolds, 1234);
      }

      String[] teColumns = {"home.dest", "embarked"};
      TargetEncoder targetEncoder = new TargetEncoder(teColumns);
      Map<String, Frame> testEncodingMap = targetEncoder.prepareEncodingMap(trainFrame, responseColumnName, withFoldColumn ? foldColumnNameForTE: null);
      return testEncodingMap;
    } finally {
      if(trainFrame != null) trainFrame.delete();
    }
  }

  // Checking that dfork is faster. This is a proof that dfork in this particular scenario is faster.
  @Ignore()
  @Test public void conversion_of_frame_into_table_doAll_vs_dfork_performance_test() {
    Map<String, Frame> encodingMap = getTEMapForTitanicDataset(false);

    for (int i = 0; i < 10; i++) { // Number of columns with encoding maps will be 2+10
      encodingMap.put(UUID.randomUUID().toString(), encodingMap.get("home.dest"));
    }
    int numberOfIterations = 50;

    //doAll
    long startTimeDoAll = System.currentTimeMillis();
    for (int i = 0; i < numberOfIterations; i++) {

      for (Map.Entry<String, Frame> entry : encodingMap.entrySet()) {
        String key = entry.getKey();
        Frame encodingsForParticularColumn = entry.getValue();
        IcedHashMap<String, TEComponents> table = new FrameToTETableTask().doAll(encodingsForParticularColumn).getResult()._table;

      }
    }
    long totalTimeDoAll = System.currentTimeMillis() - startTimeDoAll;
    Log.info("Total time doAll:" + totalTimeDoAll);

    //DFork
    long startTimeDFork = System.currentTimeMillis();
    for (int i = 0; i < numberOfIterations; i++) {
      Map<String, FrameToTETableTask> tasks = new HashMap<>();

      for (Map.Entry<String, Frame> entry : encodingMap.entrySet()) {
        Frame encodingsForParticularColumn = entry.getValue();
        FrameToTETableTask task = new FrameToTETableTask().dfork(encodingsForParticularColumn);

        tasks.put(entry.getKey(), task);
      }

      for (Map.Entry<String, FrameToTETableTask> taskEntry : tasks.entrySet()) {
        IcedHashMap<String, TEComponents> table = taskEntry.getValue().getResult()._table;
      }
    }
    long totalTimeDFork = System.currentTimeMillis() - startTimeDFork;

    TargetEncoderFrameHelper.encodingMapCleanUp(encodingMap);
    Log.info("Total time dfork:" + totalTimeDFork);

    assertTrue(totalTimeDFork < totalTimeDoAll);
  }

  @Test
  public void factorColumnTest() {
    Frame fr = null;
    Vec colA = null;
    try {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_NUM)
              .withDataForCol(0, ar(0, 1))
              .build();

      colA = fr.vec("ColA");
      assertFalse(colA.isCategorical());

      TargetEncoderFrameHelper.factorColumn(fr, "ColA");

      colA = fr.vec("ColA");
      assertTrue(colA.isCategorical());
    } finally {
      if(fr!=null) fr.delete();
      if(colA!=null) colA.remove();
    }
  }
}
