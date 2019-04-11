package hex;

import ai.h2o.automl.targetencoding.BlendingParams;
import ai.h2o.automl.targetencoding.TargetEncoder;
import ai.h2o.automl.targetencoding.TargetEncoderFrameHelper;
import hex.genmodel.ModelDescriptor;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.util.IcedHashMap;
import water.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static org.junit.Assert.*;

/**
 * This test should be moved to h2o-core module or dedicated for TE module once we move all TargetEncoding related classes there as well. 
 */
public class ModelTest extends TestUtil implements ModelStubs {

  @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  String foldColumnNameForTE = "te_fold_column";
  
  private Map<String, Frame> getTEMapForTitanicDataset(boolean withFoldColumn) {
    Frame trainFrame = null;
    try {
      trainFrame = parse_test_file("./smalldata/gbm_test/titanic.csv");
      String responseColumnName = "survived";
      asFactor(trainFrame, responseColumnName);
      
      if(withFoldColumn) {
        int nfolds = 5;
        addKFoldColumn(trainFrame, foldColumnNameForTE, nfolds, 1234);
      }

      BlendingParams params = new BlendingParams(3, 1);
      String[] teColumns = {"home.dest", "embarked"};
      TargetEncoder targetEncoder = new TargetEncoder(teColumns, params);
      Map<String, Frame> testEncodingMap = targetEncoder.prepareEncodingMap(trainFrame, responseColumnName, withFoldColumn ? foldColumnNameForTE: null);
      return testEncodingMap;
    } finally {
      if(trainFrame != null) trainFrame.delete();
    }
  }
  
  @Test public void addTargetEncodingMap() {
    TestModel.TestParam p = new TestModel.TestParam();
    TestModel.TestOutput o = new TestModel.TestOutput();
    TestModel testModel = new TestModel(Key.make(),p,o);
    Map<String, Frame> teMap = getTEMapForTitanicDataset(false);
    testModel.addTargetEncodingMap(teMap);

    checkEncodings(testModel._output._target_encoding_map);
    TargetEncoderFrameHelper.encodingMapCleanUp(teMap);
  }

  @Test public void modelDescriptor() {
    TestModel.TestParam p = new TestModel.TestParam();
    TestModel.TestOutput o = new TestModel.TestOutput();
    TestModel testModel = new TestModel(Key.make(),p,o);
    Map<String, Frame> teMap = getTEMapForTitanicDataset(false);
    testModel.addTargetEncodingMap(teMap);

    ModelDescriptor md = testModel.modelDescriptor();

    Map<String, Map<String, int[]>> targetEncodingMap = md.targetEncodingMap();

    checkEncodingsInts(targetEncodingMap);
    TargetEncoderFrameHelper.encodingMapCleanUp(teMap);
  }
  
  @Test public void getMojo() {
    TestModel.TestParam p = new TestModel.TestParam();
    TestModel.TestOutput o = new TestModel.TestOutput();
    TestModel testModel = new TestModel(Key.make(),p,o);
    Map<String, Frame> teMap = getTEMapForTitanicDataset(false);
    testModel.addTargetEncodingMap(teMap);

    ModelDescriptor md = testModel.getMojo().model.modelDescriptor();

    Map<String, Map<String, int[]>> targetEncodingMap = md.targetEncodingMap();

    checkEncodingsInts(targetEncodingMap);
    TargetEncoderFrameHelper.encodingMapCleanUp(teMap);
  }

  @Test public void getMojoFoldCase() {
    TestModel.TestParam p = new TestModel.TestParam();
    TestModel.TestOutput o = new TestModel.TestOutput();
    TestModel testModel = new TestModel(Key.make(),p,o);
    Map<String, Frame> teMap = getTEMapForTitanicDataset(true);

    
    // Note:  following block should be used by user if the want to add  encoding map to model and to mojo. 
    // Following iteration over encoding maps and regrouping without folds could be hidden inside `model.addTargetEncodingMap()` 
    // but we need TargetEncoder in h2o-core package then so that we can reuse its functionality
    for(Map.Entry<String, Frame> entry : teMap.entrySet()) {
      Frame grouped = TargetEncoder.groupingIgnoringFoldColumn(foldColumnNameForTE, entry.getValue(), entry.getKey());
      entry.getValue().delete();
      teMap.put(entry.getKey(), grouped);
    }

    testModel.addTargetEncodingMap(teMap);

    ModelDescriptor md = testModel.getMojo().model.modelDescriptor();

    Map<String, Map<String, int[]>> targetEncodingMap = md.targetEncodingMap();

    checkEncodingsInts(targetEncodingMap);
    TargetEncoderFrameHelper.encodingMapCleanUp(teMap);
  }

  // Checking that dfork is faster
  @Test public void conversion_of_frame_into_table_doAll_vs_dfork_performance_test() {
    Map<String, Frame> encodingMap = getTEMapForTitanicDataset(false);

    for (int i = 0; i < 10; i++) { // Number of columns with encoding maps will be 2+10
      encodingMap.put(UUID.randomUUID().toString(), encodingMap.get("home.dest"));
    }
    int numberOfIterations = 20;

    //doAll
    long startTimeDoAll = System.currentTimeMillis();
    for (int i = 0; i < numberOfIterations; i++) {

      IcedHashMap<String, Map<String, Model.TEComponents>> transformedEncodingMap = new IcedHashMap<>();
      for (Map.Entry<String, Frame> entry : encodingMap.entrySet()) {
        String key = entry.getKey();
        Frame encodingsForParticularColumn = entry.getValue();
        IcedHashMap<String, Model.TEComponents> table = new Model.FrameToTETable().doAll(encodingsForParticularColumn).getResult().table;

        transformedEncodingMap.put(key, table);
      }
    }
    long totalTimeDoAll = System.currentTimeMillis() - startTimeDoAll;
    Log.info("Total time doAll:" + totalTimeDoAll);

    //DFork
    long startTimeDFork = System.currentTimeMillis();
    for (int i = 0; i < numberOfIterations; i++) {
      Map<String, Model.FrameToTETable> tasks = new HashMap<>();

      for (Map.Entry<String, Frame> entry : encodingMap.entrySet()) {
        Frame encodingsForParticularColumn = entry.getValue();
        Model.FrameToTETable task = new Model.FrameToTETable().dfork(encodingsForParticularColumn);

        tasks.put(entry.getKey(), task);
      }

      IcedHashMap<String, Map<String, Model.TEComponents>> transformedEncodingMap = new IcedHashMap<>();

      for (Map.Entry<String, Model.FrameToTETable> taskEntry : tasks.entrySet()) {
        transformedEncodingMap.put(taskEntry.getKey(), taskEntry.getValue().getResult().table);
      }
    }
    long totalTimeDFork = System.currentTimeMillis() - startTimeDFork;

    TargetEncoderFrameHelper.encodingMapCleanUp(encodingMap);
    Log.info("Total time dfork:" + totalTimeDFork);
    
    assertTrue(totalTimeDFork < totalTimeDoAll);
  }

  private void checkEncodings(Map<String, Map<String, Model.TEComponents>> target_encoding_map) {
    Map<String, Model.TEComponents> embarkedEncodings = target_encoding_map.get("embarked");
    Map<String, Model.TEComponents> homeDestEncodings = target_encoding_map.get("home.dest");

    assertArrayEquals(embarkedEncodings.get("S").getNumeratorAndDenominator(), new int[]{304, 914});
    assertArrayEquals(embarkedEncodings.get("embarked_NA").getNumeratorAndDenominator(), new int[]{2, 2});
    assertEquals(homeDestEncodings.size(), 370);
  }

  private void checkEncodingsInts(Map<String, Map<String, int[]>> target_encoding_map) {
    Map<String, int[]> embarkedEncodings = target_encoding_map.get("embarked");
    Map<String, int[]> homeDestEncodings = target_encoding_map.get("home.dest");

    assertArrayEquals(embarkedEncodings.get("S"), new int[]{304, 914});
    assertArrayEquals(embarkedEncodings.get("embarked_NA"), new int[]{2, 2});
    assertEquals(homeDestEncodings.size(), 370);
  }

}
