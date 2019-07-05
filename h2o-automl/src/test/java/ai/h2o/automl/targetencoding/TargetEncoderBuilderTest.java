package ai.h2o.automl.targetencoding;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import java.util.Map;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static org.junit.Assert.assertTrue;

public class TargetEncoderBuilderTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void getTargetEncodingMapByTrainingTEBuilder(){

    Map<String, Frame> encodingMapFromTargetEncoder = null;
    Map<String, Frame> targetEncodingMapFromBuilder = null;
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

      TargetEncoderModel targetEncoderModel = job.trainModel().get();
      Scope.track_generic(targetEncoderModel);
      
      //Stage 2: 
      // Let's create encoding map by TargetEncoder directly

      TargetEncoder tec = new TargetEncoder(teColumns, params);

      Frame fr2 = parse_test_file("./smalldata/gbm_test/titanic.csv");
      asFactor(fr2, responseColumnName);
      Scope.track(fr2);

      encodingMapFromTargetEncoder = tec.prepareEncodingMap(fr2, responseColumnName, null);

      targetEncodingMapFromBuilder = targetEncoderModel._output._target_encoding_map;

      areEncodingMapsIdentical(encodingMapFromTargetEncoder, targetEncodingMapFromBuilder);

    } finally {
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

      TargetEncoderBuilder job = new TargetEncoderBuilder(targetEncoderParameters);

      TargetEncoderModel targetEncoderModel = job.trainModel().get();
      Scope.track_generic(targetEncoderModel);

      //Stage 2: 
      // Let's create encoding map by TargetEncoder directly
      TargetEncoder tec = new TargetEncoder(teColumns, params);

      Frame fr2 = parse_test_file("./smalldata/gbm_test/titanic.csv");
      addKFoldColumn(fr2, foldColumnName, 5, 1234L);
      asFactor(fr2, responseColumnName);
      Scope.track(fr2);

      encodingMapFromTargetEncoder = tec.prepareEncodingMap(fr2, responseColumnName, foldColumnName);

      targetEncodingMapFromBuilder = targetEncoderModel._output._target_encoding_map;

      areEncodingMapsIdentical(encodingMapFromTargetEncoder, targetEncodingMapFromBuilder);

    } finally {
      removeEncodingMaps(encodingMapFromTargetEncoder, targetEncodingMapFromBuilder);
      Scope.exit();
    }
  }

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

      TargetEncoderBuilder job = new TargetEncoderBuilder(targetEncoderParameters);

      TargetEncoderModel targetEncoderModel = job.trainModel().get();
      Scope.track_generic(targetEncoderModel);
      
      long seed = 1234;
      byte strategy = TargetEncoder.DataLeakageHandlingStrategy.KFold;
      Frame transformedTrainWithModelFromBuilder = targetEncoderModel.transform(fr, strategy, seed);
      Scope.track(transformedTrainWithModelFromBuilder);
      targetEncodingMapFromBuilder = targetEncoderModel._output._target_encoding_map;
      
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
