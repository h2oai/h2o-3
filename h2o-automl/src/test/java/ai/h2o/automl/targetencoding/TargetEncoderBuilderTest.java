package ai.h2o.automl.targetencoding;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.Job;
import water.*;
import water.fvec.Frame;

import java.util.Map;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static org.junit.Assert.assertTrue;

public class TargetEncoderBuilderTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  // Also checking that TargetEncoderModel could be stored in DKV and later retrieved without loosing ability to transform
  @Test
  public void getTargetEncodingMapByTrainingTEBuilder(){

    Map<String, Frame> encodingMapFromTargetEncoder = null;
    Map<String, Frame> targetEncodingMapFromBuilder = null;
    TargetEncoderModel targetEncoderModel = null;
    Scope.enter();
    try {
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      Scope.track(fr);
      String responseColumnName = "survived";

      asFactor(fr, responseColumnName);

      BlendingParams params = new BlendingParams(3, 1);
      String[] teColumns = {"home.dest", "embarked"};

      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = new TargetEncoderModel.TargetEncoderParameters();
      targetEncoderParameters._withBlending = false;
      targetEncoderParameters._columnNamesToEncode = teColumns;
      targetEncoderParameters.setTrain(fr._key);
      targetEncoderParameters._response_column = responseColumnName;

      TargetEncoderBuilder job = new TargetEncoderBuilder(targetEncoderParameters);
      Job<TargetEncoderModel> targetEncoderModelJob = job.trainModel();

      // Make sure with .get() that computation is finished
      targetEncoderModel = targetEncoderModelJob.get();
      Scope.track_generic(targetEncoderModel);

      Assume.assumeFalse(targetEncoderModel._key.home());

      // Removing from local cache to make sure model was retrieved from DKV 
      H2O.STORE.remove(targetEncoderModel._key);
      System.out.println(targetEncoderModel._key.home_node().getIpPortString());

      TargetEncoderModel retrievedNotFromCacheModel = DKV.getGet(targetEncoderModel._key);
      targetEncodingMapFromBuilder = retrievedNotFromCacheModel._output._targetEncodingMap;
      Map<String, Integer> teColumnNameToIdx = retrievedNotFromCacheModel._output._teColumnNameToIdx;

      Assert.assertNotNull(teColumnNameToIdx);

      // Let's create encoding map by TargetEncoder directly
      TargetEncoder tec = new TargetEncoder(teColumns, params);

      Frame fr2 = parse_test_file("./smalldata/gbm_test/titanic.csv");
      asFactor(fr2, responseColumnName);
      Scope.track(fr2);

      encodingMapFromTargetEncoder = tec.prepareEncodingMap(fr2, responseColumnName, null);

      // Compare
      areEncodingMapsIdentical(encodingMapFromTargetEncoder, targetEncodingMapFromBuilder);

    } finally {
      if(targetEncoderModel!=null) TargetEncoderFrameHelper.encodingMapCleanUp( targetEncoderModel._output._targetEncodingMap);
      removeEncodingMaps(encodingMapFromTargetEncoder, targetEncodingMapFromBuilder);
      Scope.exit();
    }
  }

  @Test
  public void getTargetEncodingMapByTrainingTEBuilder_KFold_scenario(){

    Map<String, Frame> encodingMapFromTargetEncoder = null;
    Map<String, Frame> targetEncodingMapFromBuilder = null;
    Scope.enter();
    try {
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      String foldColumnName = "fold_column";

      addKFoldColumn(fr, foldColumnName, 5, 1234L);
      
      Scope.track(fr);
      String responseColumnName = "survived";

      asFactor(fr, responseColumnName);

      BlendingParams params = new BlendingParams(3, 1);
      String[] teColumns = {"home.dest", "embarked"};

      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = new TargetEncoderModel.TargetEncoderParameters();
      targetEncoderParameters._withBlending = false;
      targetEncoderParameters._columnNamesToEncode = teColumns;
      targetEncoderParameters._teFoldColumnName = foldColumnName;
      targetEncoderParameters.setTrain(fr._key);
      targetEncoderParameters._response_column = responseColumnName;

      TargetEncoderBuilder builder = new TargetEncoderBuilder(targetEncoderParameters);

      builder.trainModel().get(); // Waiting for training to be finished
      TargetEncoderModel targetEncoderModel = builder.getTargetEncoderModel(); // TODO change the way of how we getting model after PUBDEV-6670. We should be able to get it from DKV with .trainModel().get()

      //Stage 2: 
      // Let's create encoding map by TargetEncoder directly
      TargetEncoder tec = new TargetEncoder(teColumns, params);

      Frame fr2 = parse_test_file("./smalldata/gbm_test/titanic.csv");
      addKFoldColumn(fr2, foldColumnName, 5, 1234L);
      asFactor(fr2, responseColumnName);
      Scope.track(fr2);

      encodingMapFromTargetEncoder = tec.prepareEncodingMap(fr2, responseColumnName, foldColumnName);

      targetEncodingMapFromBuilder = targetEncoderModel._output._targetEncodingMap;

      areEncodingMapsIdentical(encodingMapFromTargetEncoder, targetEncodingMapFromBuilder);

    } finally {
      removeEncodingMaps(encodingMapFromTargetEncoder, targetEncodingMapFromBuilder);
      Scope.exit();
    }
  }

  @Ignore("Enable when TargetEncoderModel is a full fledged model: PUBDEV-6670")
  @Test
  public void transform_KFold_scenario(){

    Map<String, Frame> encodingMapFromTargetEncoder = null;
    Map<String, Frame> targetEncodingMapFromBuilder = null;
    Scope.enter();
    try {
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      String foldColumnName = "fold_column";

      addKFoldColumn(fr, foldColumnName, 5, 1234L);

      Scope.track(fr);
      String responseColumnName = "survived";

      asFactor(fr, responseColumnName);

      BlendingParams params = new BlendingParams(3, 1);
      String[] teColumns = {"home.dest", "embarked"};

      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = new TargetEncoderModel.TargetEncoderParameters();
      targetEncoderParameters._withBlending = false;
      targetEncoderParameters._columnNamesToEncode = teColumns;
      targetEncoderParameters._teFoldColumnName = foldColumnName;
      targetEncoderParameters.setTrain(fr._key);
      targetEncoderParameters._response_column = responseColumnName;

      TargetEncoderBuilder builder = new TargetEncoderBuilder(targetEncoderParameters);

      builder.trainModel().get(); // Waiting for training to be finished
      TargetEncoderModel targetEncoderModel = builder.getTargetEncoderModel(); // TODO change the way of how we getting model after PUBDEV-6670. We should be able to get it from DKV with .trainModel().get()
      
      long seed = 1234;
      byte strategy = TargetEncoder.DataLeakageHandlingStrategy.KFold;
      Frame transformedTrainWithModelFromBuilder = targetEncoderModel.transform(fr, strategy, seed);
      Scope.track(transformedTrainWithModelFromBuilder);
      targetEncodingMapFromBuilder = targetEncoderModel._output._targetEncodingMap;
      
      //Stage 2: 
      // Let's create encoding map by TargetEncoder directly and transform with it
      TargetEncoder tec = new TargetEncoder(teColumns, params);

      Frame fr2 = parse_test_file("./smalldata/gbm_test/titanic.csv");
      addKFoldColumn(fr2, foldColumnName, 5, 1234L);
      asFactor(fr2, responseColumnName);
      Scope.track(fr2);

      encodingMapFromTargetEncoder = tec.prepareEncodingMap(fr2, responseColumnName, foldColumnName);

      Frame transformedTrainWithTargetEncoder = tec.applyTargetEncoding(fr2, responseColumnName, encodingMapFromTargetEncoder, strategy, foldColumnName, targetEncoderParameters._withBlending, true, seed);
     
      Scope.track(transformedTrainWithTargetEncoder);
      
      assertTrue("Transformed by `TargetEncoderModel` and `TargetEncoder` train frames should be identical", isBitIdentical(transformedTrainWithModelFromBuilder, transformedTrainWithTargetEncoder));

    } finally {
      removeEncodingMaps(encodingMapFromTargetEncoder, targetEncodingMapFromBuilder);
      Scope.exit();
    }
  }


  private void removeEncodingMaps(Map<String, Frame> encodingMapFromTargetEncoder, Map<String, Frame> targetEncodingMapFromBuilder) {
    if (encodingMapFromTargetEncoder != null)
      TargetEncoderFrameHelper.encodingMapCleanUp(encodingMapFromTargetEncoder);
    if (targetEncodingMapFromBuilder != null)
      TargetEncoderFrameHelper.encodingMapCleanUp(targetEncodingMapFromBuilder);
  }

  private void areEncodingMapsIdentical(Map<String, Frame> encodingMapFromTargetEncoder, Map<String, Frame> targetEncodingMapFromBuilder) {
    for (Map.Entry<String, Frame> entry : targetEncodingMapFromBuilder.entrySet()) {
      String teColumn = entry.getKey();
      Frame correspondingEncodingFrameFromTargetEncoder = encodingMapFromTargetEncoder.get(teColumn);
      assertTrue(isBitIdentical(entry.getValue(), correspondingEncodingFrameFromTargetEncoder));
    }
  }
}
