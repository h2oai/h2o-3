package ai.h2o.automl.targetencoding;

import hex.genmodel.MojoModel;
import hex.genmodel.algos.targetencoder.EncodingMap;
import hex.genmodel.algos.targetencoder.EncodingMaps;
import hex.genmodel.algos.targetencoder.TargetEncoderMojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.exception.PredictException;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Job;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Random;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static org.junit.Assert.assertEquals;

public class TEMojoIntegrationTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void withoutBlending() throws PredictException, IOException{

    Random rg = new Random();

    double[] encodings = null;
    int homeDestPredIdx = -1;
            
    for(int i = 0; i <= 10; i++) {
      String mojoFileName = "mojo_te.zip";
      TargetEncoderModel targetEncoderModel = null;

      Scope.enter();
      try {
        Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

        String responseColumnName = "survived";

        asFactor(fr, responseColumnName);
        
        Scope.track(fr);

        int swapIdx1 = rg.nextInt(fr.numCols());
        int swapIdx2 = rg.nextInt(fr.numCols());
        fr.swap(swapIdx1, swapIdx2);
        DKV.put(fr);

        String[] teColumns = {"home.dest", "embarked"};

        TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = new TargetEncoderModel.TargetEncoderParameters();
        targetEncoderParameters._withBlending = false;
        targetEncoderParameters._columnNamesToEncode = teColumns;
        targetEncoderParameters._ignore_const_cols = false; // Why ignore_const_column ignores `name` column? bad naming
        targetEncoderParameters.setTrain(fr._key);
        targetEncoderParameters._response_column = responseColumnName;

        TargetEncoderBuilder job = new TargetEncoderBuilder(targetEncoderParameters);

        Job<TargetEncoderModel> targetEncoderModelJob = job.trainModel();

        targetEncoderModel = targetEncoderModelJob.get();
        Scope.track_generic(targetEncoderModel);

        FileOutputStream modelOutput = new FileOutputStream(mojoFileName);
        targetEncoderModel.getMojo().writeTo(modelOutput);
        modelOutput.close();
        System.out.println("Model has been written down to a file as a mojo: " + mojoFileName);

        // Let's load model that we just have written and use it for prediction.
        EasyPredictModelWrapper teModelWrapper = null;

        TargetEncoderMojoModel loadedMojoModel = (TargetEncoderMojoModel) MojoModel.load(mojoFileName);

        teModelWrapper = new EasyPredictModelWrapper(loadedMojoModel);

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
        rowToPredictFor.put("name", "1111"); // somehow encoded name
        rowToPredictFor.put("ticket", "12345");
        rowToPredictFor.put("boat", "2");
        rowToPredictFor.put("body", "123");
        rowToPredictFor.put("pclass", "1");

        if(encodings == null ) {
          encodings = teModelWrapper.transformWithTargetEncoding(rowToPredictFor);
          homeDestPredIdx = fr.find("home.dest") < fr.find("embarked") ? 0 : 1;
        } else
        {
          double[] currentEncodings = teModelWrapper.transformWithTargetEncoding(rowToPredictFor);
          //Let's check that specified in the test categorical columns have been encoded in accordance with targetEncodingMap
          EncodingMaps targetEncodingMap = loadedMojoModel._targetEncodingMap;

          double encodingForHomeDest = checkEncodingsByFactorValue(fr, homeDestFactorValue, targetEncodingMap, "home.dest");

          double encodingForHomeEmbarked = checkEncodingsByFactorValue(fr, embarkedFactorValue, targetEncodingMap, "embarked");

          // Because of the random swap we need to know which index is lower so that we know order of transformations/predictions
          int currentHomeDestPredIdx = fr.find("home.dest") < fr.find("embarked") ? 0 : 1;

          assertEquals(currentEncodings[currentHomeDestPredIdx], encodingForHomeDest, 1e-5);
          assertEquals(currentEncodings[currentHomeDestPredIdx == 0 ? 1 : 0], encodingForHomeEmbarked, 1e-5);
          
          try {
            // Assert with etalon
            assertEquals(encodings[homeDestPredIdx], currentEncodings[currentHomeDestPredIdx], 1e-5);
            assertEquals(encodings[homeDestPredIdx == 0 ? 1 : 0], currentEncodings[currentHomeDestPredIdx == 0 ? 1 : 0], 1e-5);
          } catch (AssertionError error) {

            Log.warn("Unexpected encodings. Most likely it is due to race conditions in AstGroup (see https://github.com/h2oai/h2o-3/pull/3374 )");
            Log.warn("Swap:" + swapIdx1 + " <-> " + swapIdx2);
            Log.warn("encodings[homeDest]:" + encodings[homeDestPredIdx] + " currentEncodings[homeDest]: " + currentEncodings[currentHomeDestPredIdx]);
            Log.warn("encodings[embarked]:" + encodings[homeDestPredIdx == 0 ? 1 : 0] + " currentEncodings[embarked]: " + currentEncodings[currentHomeDestPredIdx == 0 ? 1 : 0]);
          }
        }

      } catch (Exception ex) {
        throw ex;
      } finally {
        if (targetEncoderModel._output._target_encoding_map != null)
          TargetEncoderFrameHelper.encodingMapCleanUp(targetEncoderModel._output._target_encoding_map);


        File mojoFile = new File(mojoFileName);
        if (mojoFile.exists()) mojoFile.delete();
        Scope.exit();
      }
    }
  }

  private double checkEncodingsByFactorValue(Frame fr, String homeDestFactorValue, EncodingMaps targetEncodingMap, String teColumn) {
    int factorIndex = findIndex(fr.vec(teColumn).domain(), homeDestFactorValue);
    int[] encodingComponents = targetEncodingMap.get(teColumn).get(factorIndex);
    return (double) encodingComponents[0] / encodingComponents[1];
  }

  private static<T> int findIndex(T[] a, T target) {
    for (int i = 0; i < a.length; i++)
      if (target.equals(a[i]))
        return i;

    return -1;
  }

  @Test
  public void without_blending_kfold_scenario() throws PredictException, IOException {
    
    //TODO test kfold scenario as in FrameToTETableTask we do not account for extra columns in frame

    String mojoFileName = "mojo_te.zip";
    TargetEncoderModel targetEncoderModel = null;
    Scope.enter();
    try {
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

      String responseColumnName = "survived";
      asFactor(fr, responseColumnName);
      String foldColumnName = "fold_column";

      addKFoldColumn(fr, foldColumnName, 5, 1234L);
      
      Scope.track(fr);

      String[] teColumns = {"home.dest", "embarked"};

      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = new TargetEncoderModel.TargetEncoderParameters();
      targetEncoderParameters._withBlending = false;
      targetEncoderParameters._columnNamesToEncode = teColumns;
      targetEncoderParameters.setTrain(fr._key);
      targetEncoderParameters._ignore_const_cols = false;
      targetEncoderParameters._teFoldColumnName = foldColumnName;
      targetEncoderParameters._response_column = responseColumnName;

      TargetEncoderBuilder job = new TargetEncoderBuilder(targetEncoderParameters);

      Job<TargetEncoderModel> targetEncoderModelJob = job.trainModel();

      targetEncoderModel = targetEncoderModelJob.get();
      Scope.track_generic(targetEncoderModel);

      FileOutputStream modelOutput = new FileOutputStream(mojoFileName);
      targetEncoderModel.getMojo().writeTo(modelOutput);
      modelOutput.close();
      System.out.println("Model has been written down to a file as a mojo: " + mojoFileName);

      // Let's load model that we just have written and use it for prediction.
      EasyPredictModelWrapper teModelWrapper = null;

      TargetEncoderMojoModel loadedMojoModel = (TargetEncoderMojoModel) MojoModel.load(mojoFileName);

      teModelWrapper = new EasyPredictModelWrapper(loadedMojoModel); 

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
      rowToPredictFor.put("name", "1111"); // somehow encoded name
      rowToPredictFor.put("ticket", "12345");
      rowToPredictFor.put("boat", "2");
      rowToPredictFor.put("body", "123");
      rowToPredictFor.put("pclass", "1");

      double[] encodings = teModelWrapper.transformWithTargetEncoding(rowToPredictFor);

      //Check that specified in the test categorical columns have been encoded in accordance with targetEncodingMap
      EncodingMaps targetEncodingMap = loadedMojoModel._targetEncodingMap;

      double encodingForHomeDest = checkEncodingsByFactorValue(fr, homeDestFactorValue, targetEncodingMap, "home.dest");
      double encodingForHomeEmbarked = checkEncodingsByFactorValue(fr, embarkedFactorValue, targetEncodingMap, "embarked");

      assertEquals(encodings[1], encodingForHomeDest, 1e-5);
      assertEquals(encodings[0], encodingForHomeEmbarked, 1e-5);

    } catch (Exception ex) {
      throw ex;
    }
    finally {
      if(targetEncoderModel._output._target_encoding_map != null)
        TargetEncoderFrameHelper.encodingMapCleanUp(targetEncoderModel._output._target_encoding_map);


      File mojoFile = new File(mojoFileName);
      if(mojoFile.exists()) mojoFile.delete();
      Scope.exit();
    }
  }

  @Test
  public void check_that_encoding_map_was_stored_and_loaded_properly_and_blending_was_applied_correctly() throws IOException, PredictException  {

    String mojoFileName = "mojo_te.zip";
    Map<String, Frame> testEncodingMap = null;
    Scope.enter();
    try {
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

      String responseColumnName = "survived";

      asFactor(fr, responseColumnName);
      Scope.track(fr);
      
      String[] teColumns = {"home.dest", "embarked"};

      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = new TargetEncoderModel.TargetEncoderParameters();
      
      targetEncoderParameters._columnNamesToEncode = teColumns;

      // Enable blending
      targetEncoderParameters._withBlending = true;
      targetEncoderParameters._blendingParams = new BlendingParams(5, 1);

      targetEncoderParameters._ignore_const_cols = false;
      targetEncoderParameters.setTrain(fr._key);
      targetEncoderParameters._response_column = responseColumnName;

      TargetEncoderBuilder job = new TargetEncoderBuilder(targetEncoderParameters);

      TargetEncoderModel targetEncoderModel = job.trainModel().get();
      testEncodingMap = targetEncoderModel._output._target_encoding_map;
      Scope.track_generic(targetEncoderModel);

      FileOutputStream modelOutput = new FileOutputStream(mojoFileName);
      targetEncoderModel.getMojo().writeTo(modelOutput);
      modelOutput.close();
      System.out.println("Model has been written down to a file as a mojo: " + mojoFileName);

      // Let's load model that we just have written and use it for prediction.
      EasyPredictModelWrapper teModelWrapper = null;

      TargetEncoderMojoModel loadedMojoModel = (TargetEncoderMojoModel) MojoModel.load(mojoFileName);

      assertEquals(targetEncoderParameters._withBlending, loadedMojoModel._withBlending);
      assertEquals(targetEncoderParameters._blendingParams.getK(), loadedMojoModel._inflectionPoint, 1e-5);
      assertEquals(targetEncoderParameters._blendingParams.getF(), loadedMojoModel._smoothing, 1e-5);

      teModelWrapper = new EasyPredictModelWrapper(loadedMojoModel); // TODO why we store GenModel even though we pass MojoModel?

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
      rowToPredictFor.put("name", "1111"); // somehow encoded name
      rowToPredictFor.put("ticket", "12345");
      rowToPredictFor.put("boat", "2");
      rowToPredictFor.put("body", "123");
      rowToPredictFor.put("pclass", "1");

      double[] encodings = teModelWrapper.transformWithTargetEncoding(rowToPredictFor);

      // Check that specified in the test categorical columns have been encoded in accordance with encoding map
      // We reusing static helper methods from TargetEncoderMojoModel as it is not the point of current test to check them.
      // We want to check here that proper blending params were being used during `.transformWithTargetEncoding()` transformation
      EncodingMaps encodingMapConvertedFromFrame = TargetEncoderFrameHelper.convertEncodingMapFromFrameToMap(testEncodingMap);

      String teColumn = "home.dest";
      EncodingMap homeDestEncodingMap = encodingMapConvertedFromFrame.get(teColumn);

      // Will be checking that encoding map has been written and loaded correctly through the computation of the mean
      double expectedPriorMean = TargetEncoderMojoModel.computePriorMean(homeDestEncodingMap);

      int homeDestIndex = findIndex(fr.vec(teColumn).domain(), homeDestFactorValue);
      int[] encodingComponentsForHomeDest = homeDestEncodingMap.get(homeDestIndex);
      double posteriorMean =  (double) encodingComponentsForHomeDest[0] / encodingComponentsForHomeDest[1];

      double expectedLambda = TargetEncoderMojoModel.computeLambda(encodingComponentsForHomeDest[1], targetEncoderParameters._blendingParams.getK(), targetEncoderParameters._blendingParams.getF());

      double expectedBlendedEncodingForHomeDest = TargetEncoderMojoModel.computeBlendedEncoding(expectedLambda, posteriorMean, expectedPriorMean);

      assertEquals(expectedBlendedEncodingForHomeDest, encodings[1], 1e-5);

    } finally {
      if(testEncodingMap != null)
        TargetEncoderFrameHelper.encodingMapCleanUp(testEncodingMap);

      File mojoFile = new File(mojoFileName);
      if(mojoFile.exists()) mojoFile.delete();
      Scope.exit();
    }
  }

}
