package ai.h2o.automl.targetencoding;

import hex.genmodel.MojoModel;
import hex.genmodel.algos.targetencoder.TargetEncoderMojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.exception.PredictException;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class MojoTESupportIntegrationTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  private Frame fr = null;

  @Test
  public void withTargetEncodingsFromGenModelEnd2EndTest() throws IOException, PredictException  {

    String mojoFileName = "gbm_mojo_te.zip";
    Map<String, Frame> testEncodingMap = null;
    Scope.enter();
    try {
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

      String responseColumnName = "survived";
      
      asFactor(fr, responseColumnName);

      // Preparing Target encoding 
      BlendingParams params = new BlendingParams(3, 1);
      String[] teColumns = {"home.dest", "embarked"};

      TargetEncoder tec = new TargetEncoder(teColumns, params);

      testEncodingMap = tec.prepareEncodingMap(fr, responseColumnName, null);
      
      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = new TargetEncoderModel.TargetEncoderParameters();
      targetEncoderParameters.addTargetEncodingMap(testEncodingMap);
      targetEncoderParameters.setTrain(fr._key);
      targetEncoderParameters._response_column = responseColumnName;

      TargetEncoderBuilder job = new TargetEncoderBuilder(targetEncoderParameters);

      TargetEncoderModel targetEncoderModel = job.trainModel().get();
      Scope.track_generic(targetEncoderModel);
      
      FileOutputStream modelOutput = new FileOutputStream(mojoFileName);
      targetEncoderModel.getMojo().writeTo(modelOutput);
      modelOutput.close();
      System.out.println("Model written out as a mojo to file " + mojoFileName);

      // Let's load model that we just have written and use it for prediction.
      EasyPredictModelWrapper modelWrapper = null;

      TargetEncoderMojoModel loadedMojoModel = (TargetEncoderMojoModel) MojoModel.load(mojoFileName);
      
      modelWrapper = new EasyPredictModelWrapper(loadedMojoModel); // TODO why we store GenModel even though we pass MojoModel?

      // RowData that is not encoded yet
      RowData rowToPredictFor = new RowData();
      String homeDestFactorValue = "Montreal  PQ / Chesterville  ON";
      String embarkedFactorValue = "S";
      
      rowToPredictFor.put("home.dest", homeDestFactorValue);
      rowToPredictFor.put("sex", "female");
      rowToPredictFor.put("age", "2.0");
      rowToPredictFor.put("fare", "151.55");
      rowToPredictFor.put("cabin", "C22 C26");
      rowToPredictFor.put("embarked", embarkedFactorValue);
      rowToPredictFor.put("sibsp", "1");
      rowToPredictFor.put("parch", "2");
      rowToPredictFor.put("pclass", "1");

      modelWrapper.transformWithTargetEncoding(rowToPredictFor);

      //Check that specified in the test categorical columns have been encoded in accordance with targetEncodingMap
      Map<String, Map<String, int[]>> targetEncodingMap = loadedMojoModel._targetEncodingMap;
      
      int[] encodingComponentsForHomeDest = targetEncodingMap.get("home.dest").get(homeDestFactorValue);
      double encodingForHomeDest = (double) encodingComponentsForHomeDest[0] / encodingComponentsForHomeDest[1];
      
      int[] encodingComponentsForEmbarked = targetEncodingMap.get("embarked").get(embarkedFactorValue);
      double encodingForHomeEmbarked = (double) encodingComponentsForEmbarked[0] / encodingComponentsForEmbarked[1];
      
      assertEquals((double) rowToPredictFor.get("home.dest_te"), encodingForHomeDest, 1e-5);
      assertEquals((double) rowToPredictFor.get("embarked_te"), encodingForHomeEmbarked, 1e-5);

    } finally {
      if(testEncodingMap != null) 
        TargetEncoderFrameHelper.encodingMapCleanUp(testEncodingMap);

      File mojoFile = new File(mojoFileName);
      if(mojoFile.exists()) mojoFile.delete();
      Scope.exit();
    }
  }

  @After
  public void afterEach() {
    if (fr != null) fr.delete();
  }

}
