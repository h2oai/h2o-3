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

import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * This test should be moved to h2o-core module once we move all TargetEncoding related classes there as well. 
 */
public class ModelTest extends TestUtil implements ModelTestCommons {

  @BeforeClass public static void stall() { stall_till_cloudsize(1); }


  private Map<String, Frame> getTEMapForTitanicDataset() {
    Frame trainFrame = null;
    try {
      trainFrame = parse_test_file("./smalldata/gbm_test/titanic.csv");
      String responseColumnName = "survived";
      asFactor(trainFrame, responseColumnName);

      BlendingParams params = new BlendingParams(3, 1);
      String[] teColumns = {"home.dest", "embarked"};
      TargetEncoder targetEncoder = new TargetEncoder(teColumns, params);
      Map<String, Frame> testEncodingMap = targetEncoder.prepareEncodingMap(trainFrame, responseColumnName, null);
      return testEncodingMap;
    } finally {
      if(trainFrame != null) trainFrame.delete();
    }
  }
  
  @Test public void addTargetEncodingMap() {
    TestModel.TestParam p = new TestModel.TestParam();
    TestModel.TestOutput o = new TestModel.TestOutput();
    TestModel testModel = new TestModel(Key.make(),p,o);
    Map<String, Frame> teMap = getTEMapForTitanicDataset();
    testModel.addTargetEncodingMap(teMap);

    checkEncodings(testModel._output._target_encoding_map);
    TargetEncoderFrameHelper.encodingMapCleanUp(teMap);
  }

  @Test public void modelDescriptor() {
    TestModel.TestParam p = new TestModel.TestParam();
    TestModel.TestOutput o = new TestModel.TestOutput();
    TestModel testModel = new TestModel(Key.make(),p,o);
    Map<String, Frame> teMap = getTEMapForTitanicDataset();
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
    Map<String, Frame> teMap = getTEMapForTitanicDataset();
    testModel.addTargetEncodingMap(teMap);

    ModelDescriptor md = testModel.getMojo().model.modelDescriptor();

    Map<String, Map<String, int[]>> targetEncodingMap = md.targetEncodingMap();

    checkEncodingsInts(targetEncodingMap);
    TargetEncoderFrameHelper.encodingMapCleanUp(teMap);
  }

  private void checkEncodings(Map<String, Map<String, Model.TEComponents>> target_encoding_map) {
    Map<String, Model.TEComponents> embarkedEncodings = target_encoding_map.get("embarked");
    Map<String, Model.TEComponents> homeDestEncodings = target_encoding_map.get("home.dest");

    assertArrayEquals(embarkedEncodings.get("S").getComponents(), new int[]{304, 914});
    assertArrayEquals(embarkedEncodings.get("embarked_NA").getComponents(), new int[]{2, 2});
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
