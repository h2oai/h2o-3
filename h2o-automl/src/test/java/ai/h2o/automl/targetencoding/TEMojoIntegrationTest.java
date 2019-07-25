package ai.h2o.automl.targetencoding;

import hex.genmodel.MojoModel;
import hex.genmodel.algos.targetencoder.EncodingMap;
import hex.genmodel.algos.targetencoder.EncodingMaps;
import hex.genmodel.algos.targetencoder.TargetEncoderMojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.exception.PredictException;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.util.ArrayUtils;
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

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void withoutBlending() throws PredictException, IOException {

    String mojoFileName = "mojo_te.zip";
    File mojoFile = folder.newFile(mojoFileName);

    TargetEncoderModel targetEncoderModel = null;

    Scope.enter();
    try {
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

      String responseColumnName = "survived";

      asFactor(fr, responseColumnName);

      Scope.track(fr);

      String[] teColumns = {"home.dest", "embarked"};

      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = new TargetEncoderModel.TargetEncoderParameters();
      targetEncoderParameters._withBlending = false;
      targetEncoderParameters._columnNamesToEncode = teColumns;
      targetEncoderParameters._ignore_const_cols = false; // Why ignore_const_column ignores `name` column? bad naming
      targetEncoderParameters.setTrain(fr._key);
      targetEncoderParameters._response_column = responseColumnName;

      TargetEncoderBuilder targetEncoderBuilder = new TargetEncoderBuilder(targetEncoderParameters);

      targetEncoderBuilder.trainModel().get();

      targetEncoderModel = targetEncoderBuilder.getTargetEncoderModel();

      try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
        targetEncoderModel.getMojo().writeTo(modelOutput);
        System.out.println("Model has been written down to a file as a mojo: " + mojoFileName);
      }

      // Let's load model that we just have written and use it for prediction.
      EasyPredictModelWrapper teModelWrapper = null;

      TargetEncoderMojoModel loadedMojoModel = (TargetEncoderMojoModel) MojoModel.load(mojoFile.getPath());

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

      double[] currentEncodings = teModelWrapper.transformWithTargetEncoding(rowToPredictFor).transformations;
      //Let's check that specified in the test categorical columns have been encoded in accordance with targetEncodingMap
      EncodingMaps targetEncodingMap = loadedMojoModel._targetEncodingMap;

      double encodingForHomeDest = checkEncodingsByFactorValue(fr, homeDestFactorValue, targetEncodingMap, "home.dest");

      double encodingForHomeEmbarked = checkEncodingsByFactorValue(fr, embarkedFactorValue, targetEncodingMap, "embarked");

      // Because of the random swap we need to know which index is lower so that we know order of transformations/predictions
      int currentHomeDestPredIdx = fr.find("home.dest") < fr.find("embarked") ? 0 : 1;

      assertEquals(currentEncodings[currentHomeDestPredIdx], encodingForHomeDest, 1e-5);
      assertEquals(currentEncodings[currentHomeDestPredIdx == 0 ? 1 : 0], encodingForHomeEmbarked, 1e-5);

    } finally {
      targetEncoderModel.remove();
      Scope.exit();
    }
  }
  
  @Test
  public void transformation_consistency_test() throws PredictException, IOException{

    Random rg = new Random();

    double[] encodings = null;
    int homeDestPredIdx = -1;
    String mojoFileName = "mojo_te.zip";
    File mojoFile = null;
    
    int inconsistencyCounter = 0;
    int numberOfRuns = 50;
    
    for(int i = 0; i <= numberOfRuns; i++) {
      TargetEncoderModel targetEncoderModel = null;
      mojoFile = folder.newFile(mojoFileName);

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

        TargetEncoderBuilder targetEncoderBuilder = new TargetEncoderBuilder(targetEncoderParameters);

        targetEncoderBuilder.trainModel().get();

        targetEncoderModel = targetEncoderBuilder.getTargetEncoderModel();

        try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)){
          targetEncoderModel.getMojo().writeTo(modelOutput);
          System.out.println("Model has been written down to a file as a mojo: " + mojoFileName);
        }

        // Let's load model that we just have written and use it for prediction.
        EasyPredictModelWrapper teModelWrapper = null;

        TargetEncoderMojoModel loadedMojoModel = (TargetEncoderMojoModel) MojoModel.load(mojoFile.getPath());

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

        if (encodings == null) {
          encodings = teModelWrapper.transformWithTargetEncoding(rowToPredictFor).transformations;
          homeDestPredIdx = fr.find("home.dest") < fr.find("embarked") ? 0 : 1;
        } else {
          double[] currentEncodings = teModelWrapper.transformWithTargetEncoding(rowToPredictFor).transformations;

          // Because of the random swap we need to know which index is lower so that we know order of transformations/predictions
          int currentHomeDestPredIdx = fr.find("home.dest") < fr.find("embarked") ? 0 : 1;

          inconsistencyCounter += isEqualToReferenceValue(encodings, homeDestPredIdx, swapIdx1, swapIdx2, currentEncodings, currentHomeDestPredIdx) ? 0 : 1;
        }

      } finally {
        targetEncoderModel.remove();
        mojoFile.delete(); // As we are in the loop we need to remove tmp file manually
        Scope.exit();
      }
    }
    
    assertEquals("Transformation failed " + inconsistencyCounter + " times out of " + numberOfRuns + " runs",0, inconsistencyCounter);
  }

  private double checkEncodingsByFactorValue(Frame fr, String homeDestFactorValue, EncodingMaps targetEncodingMap, String teColumn) {
    int factorIndex = ArrayUtils.find(fr.vec(teColumn).domain(), homeDestFactorValue);
    int[] encodingComponents = targetEncodingMap.get(teColumn).get(factorIndex);
    return (double) encodingComponents[0] / encodingComponents[1];
  }

  @Test
  public void without_blending_kfold_scenario() throws PredictException, IOException {

    String mojoFileName = "mojo_te.zip";
    File mojoFile = folder.newFile(mojoFileName);
    
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

      job.trainModel().get();

      targetEncoderModel = job.getTargetEncoderModel();

      try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
        targetEncoderModel.getMojo().writeTo(modelOutput);
        System.out.println("Model has been written down to a file as a mojo: " + mojoFileName);
      }

      // Let's load model that we just have written and use it for prediction.
      EasyPredictModelWrapper teModelWrapper = null;

      TargetEncoderMojoModel loadedMojoModel = (TargetEncoderMojoModel) MojoModel.load(mojoFile.getPath());

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

      double[] encodings = teModelWrapper.transformWithTargetEncoding(rowToPredictFor).transformations;

      //Check that specified in the test categorical columns have been encoded in accordance with targetEncodingMap
      EncodingMaps targetEncodingMap = loadedMojoModel._targetEncodingMap;

      double encodingForHomeDest = checkEncodingsByFactorValue(fr, homeDestFactorValue, targetEncodingMap, "home.dest");
      double encodingForHomeEmbarked = checkEncodingsByFactorValue(fr, embarkedFactorValue, targetEncodingMap, "embarked");

      assertEquals(encodings[1], encodingForHomeDest, 1e-5);
      assertEquals(encodings[0], encodingForHomeEmbarked, 1e-5);

    } finally {
      targetEncoderModel.remove();
      Scope.exit();
    }
  }

  @Test
  public void check_that_encoding_map_was_stored_and_loaded_properly_and_blending_was_applied_correctly() throws IOException, PredictException {

    String mojoFileName = "mojo_te.zip";
    File mojoFile = folder.newFile(mojoFileName);
    ;
    Map<String, Frame> testEncodingMap = null;
    TargetEncoderModel targetEncoderModel = null;
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

      job.trainModel().get();

      targetEncoderModel = job.getTargetEncoderModel();

      testEncodingMap = targetEncoderModel._output._target_encoding_map;

      try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
        targetEncoderModel.getMojo().writeTo(modelOutput);
        System.out.println("Model has been written down to a file as a mojo: " + mojoFileName);
      }

      // Let's load model that we just have written and use it for prediction.
      EasyPredictModelWrapper teModelWrapper = null;

      TargetEncoderMojoModel loadedMojoModel = (TargetEncoderMojoModel) MojoModel.load(mojoFile.getPath());

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

      double[] encodings = teModelWrapper.transformWithTargetEncoding(rowToPredictFor).transformations;

      // Check that specified in the test categorical columns have been encoded in accordance with encoding map
      // We reusing static helper methods from TargetEncoderMojoModel as it is not the point of current test to check them.
      // We want to check here that proper blending params were being used during `.transformWithTargetEncoding()` transformation
      EncodingMaps encodingMapConvertedFromFrame = TargetEncoderFrameHelper.convertEncodingMapFromFrameToMap(testEncodingMap);

      String teColumn = "home.dest";
      EncodingMap homeDestEncodingMap = encodingMapConvertedFromFrame.get(teColumn);

      // Checking that priorMean was written and loaded properly
      assertEquals(targetEncoderModel._output._priorMean, loadedMojoModel._priorMean, 1e-5);
      double expectedPriorMean = loadedMojoModel._priorMean;

      // Checking that encodings from Mojo model and manually computed ones are equal
      int homeDestIndex = ArrayUtils.find(fr.vec(teColumn).domain(), homeDestFactorValue);
      int[] encodingComponentsForHomeDest = homeDestEncodingMap.get(homeDestIndex);
      double posteriorMean = (double) encodingComponentsForHomeDest[0] / encodingComponentsForHomeDest[1];

      double expectedLambda = TargetEncoderMojoModel.computeLambda(encodingComponentsForHomeDest[1], targetEncoderParameters._blendingParams.getK(), targetEncoderParameters._blendingParams.getF());

      double expectedBlendedEncodingForHomeDest = TargetEncoderMojoModel.computeBlendedEncoding(expectedLambda, posteriorMean, expectedPriorMean);

      assertEquals(expectedBlendedEncodingForHomeDest, encodings[1], 1e-5);

    } finally {
      targetEncoderModel.remove();
      Scope.exit();
    }
  }

  // We need to test only holdout None  case as we predict only for data which were not used for TEModel training 
  @Test
  public void check_that_encodings_for_unexpected_values_are_the_same_in_TargetEncoderModel_and_TargetEncoderMojoModel_holdout_none() throws IOException, PredictException {

    String mojoFileName = "mojo_te.zip";
    File mojoFile = folder.newFile(mojoFileName);
    ;
    TargetEncoderModel targetEncoderModel = null;
    Scope.enter();
    try {
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

      String responseColumnName = "survived";

      asFactor(fr, responseColumnName);
      Scope.track(fr);

      String[] teColumns = {"home.dest"};

      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = new TargetEncoderModel.TargetEncoderParameters();

      targetEncoderParameters._columnNamesToEncode = teColumns;

      // Enable blending
      targetEncoderParameters._withBlending = true;
      targetEncoderParameters._blendingParams = new BlendingParams(5, 1);

      targetEncoderParameters._ignore_const_cols = false;
      targetEncoderParameters.setTrain(fr._key);
      targetEncoderParameters._response_column = responseColumnName;

      TargetEncoderBuilder job = new TargetEncoderBuilder(targetEncoderParameters);

      job.trainModel().get();

      targetEncoderModel = job.getTargetEncoderModel();

      try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
        targetEncoderModel.getMojo().writeTo(modelOutput);
        System.out.println("Model has been written down to a file as a mojo: " + mojoFileName);
      }

      // Let's load model that we just have written and use it for prediction.
      EasyPredictModelWrapper teModelWrapper = null;

      TargetEncoderMojoModel loadedMojoModel = (TargetEncoderMojoModel) MojoModel.load(mojoFile.getPath());

      teModelWrapper = new EasyPredictModelWrapper(loadedMojoModel); // TODO why we store GenModel even though we pass MojoModel?

      // RowData that is not encoded yet
      RowData rowToPredictFor = new RowData();
      rowToPredictFor.put("home.dest", Double.NaN);

      double[] encodingsFromMojoModel = teModelWrapper.transformWithTargetEncoding(rowToPredictFor).transformations;
      byte noneHoldoutStrategy = 2;
      
      // Unexpected level value - `null`
      Frame withNullFrame = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("home.dest")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar((String)null))
              .build();

      
      Frame encodingsFromTargetEncoderModel = targetEncoderModel.transform(withNullFrame, noneHoldoutStrategy, 0.0, 1234);
      Scope.track(encodingsFromTargetEncoderModel);
      
      assertEquals(encodingsFromMojoModel[0], encodingsFromTargetEncoderModel.vec("home.dest_te").at(0), 1e-5);
      
      // Unexpected level value - unseen categorical level
      Frame withUnseenLevelFrame = new TestFrameBuilder()
              .withName("testFrame2")
              .withColNames("home.dest")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar("xxx"))
              .build();

      Frame encodingsFromTEModelForUnseenLevel = targetEncoderModel.transform(withUnseenLevelFrame, noneHoldoutStrategy, 0.0, 1234);
      Scope.track(encodingsFromTEModelForUnseenLevel);

      assertEquals(encodingsFromMojoModel[0], encodingsFromTEModelForUnseenLevel.vec("home.dest_te").at(0), 1e-5);

    } finally {
      targetEncoderModel.remove();
      Scope.exit();
    }
  }

  @Test
  public void check_that_we_can_remove_model() {

    Scope.enter();
    try {
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

      String responseColumnName = "survived";

      asFactor(fr, responseColumnName);
      Scope.track(fr);

      String[] teColumns = {"home.dest", "embarked"};

      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = new TargetEncoderModel.TargetEncoderParameters();

      targetEncoderParameters._columnNamesToEncode = teColumns;
      targetEncoderParameters._withBlending = true;
      targetEncoderParameters._blendingParams = new BlendingParams(5, 1);

      targetEncoderParameters._ignore_const_cols = false;
      targetEncoderParameters.setTrain(fr._key);
      targetEncoderParameters._response_column = responseColumnName;

      TargetEncoderBuilder targetEncoderBuilder = new TargetEncoderBuilder(targetEncoderParameters);

      targetEncoderBuilder.trainModel().get();

      TargetEncoderModel targetEncoderModel = targetEncoderBuilder.getTargetEncoderModel();

      targetEncoderModel.remove();

      assertEquals(0, targetEncoderModel._output._target_encoding_map.get("embarked").byteSize());
      assertEquals(0, targetEncoderModel._output._target_encoding_map.get("home.dest").byteSize());
      assertEquals(0, targetEncoderModel._output._teColumnNameToIdx.size());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void check_that_we_can_transform_dataframe_that_contains_only_columns_for_encoding() throws PredictException, IOException {

    String mojoFileName = "mojo_te.zip";
    File mojoFile = folder.newFile(mojoFileName);

    TargetEncoderModel targetEncoderModel = null;

    Scope.enter();
    try {
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

      String responseColumnName = "survived";

      asFactor(fr, responseColumnName);

      Scope.track(fr);

      String[] teColumns = {"home.dest", "embarked"};

      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = new TargetEncoderModel.TargetEncoderParameters();
      targetEncoderParameters._withBlending = false;
      targetEncoderParameters._columnNamesToEncode = teColumns;
      targetEncoderParameters._ignore_const_cols = false; // Why ignore_const_column ignores `name` column? bad naming
      targetEncoderParameters.setTrain(fr._key);
      targetEncoderParameters._response_column = responseColumnName;

      TargetEncoderBuilder targetEncoderBuilder = new TargetEncoderBuilder(targetEncoderParameters);

      targetEncoderBuilder.trainModel().get();

      targetEncoderModel = targetEncoderBuilder.getTargetEncoderModel();

      try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
        targetEncoderModel.getMojo().writeTo(modelOutput);
        System.out.println("Model has been written down to a file as a mojo: " + mojoFileName);
      }

      // Let's load model that we just have written and use it for prediction.
      EasyPredictModelWrapper teModelWrapper = null;

      TargetEncoderMojoModel loadedMojoModel = (TargetEncoderMojoModel) MojoModel.load(mojoFile.getPath());

      teModelWrapper = new EasyPredictModelWrapper(loadedMojoModel);

      // RowData that is not encoded yet
      RowData rowToPredictFor = new RowData();
      String homeDestFactorValue = "Montreal  PQ / Chesterville  ON";
      String embarkedFactorValue = "S";

      rowToPredictFor.put("home.dest", homeDestFactorValue);
      rowToPredictFor.put("embarked", embarkedFactorValue);

      double[] currentEncodings = teModelWrapper.transformWithTargetEncoding(rowToPredictFor).transformations;
      //Let's check that specified in the test categorical columns have been encoded in accordance with targetEncodingMap
      EncodingMaps targetEncodingMap = loadedMojoModel._targetEncodingMap;

      double encodingForHomeDest = checkEncodingsByFactorValue(fr, homeDestFactorValue, targetEncodingMap, "home.dest");

      double encodingForHomeEmbarked = checkEncodingsByFactorValue(fr, embarkedFactorValue, targetEncodingMap, "embarked");

      // Because of the random swap we need to know which index is lower so that we know order of transformations/predictions
      int currentHomeDestPredIdx = fr.find("home.dest") < fr.find("embarked") ? 0 : 1;

      assertEquals(currentEncodings[currentHomeDestPredIdx], encodingForHomeDest, 1e-5);
      assertEquals(currentEncodings[currentHomeDestPredIdx == 0 ? 1 : 0], encodingForHomeEmbarked, 1e-5);
    } finally {
      targetEncoderModel.remove();
      Scope.exit();
    }
  }

  private Boolean isEqualToReferenceValue(double[] encodings, int homeDestPredIdx, int swapIdx1, int swapIdx2, double[] currentEncodings, int currentHomeDestPredIdx) {
    try {
      assertEquals(encodings[homeDestPredIdx], currentEncodings[currentHomeDestPredIdx], 1e-5);
      assertEquals(encodings[homeDestPredIdx == 0 ? 1 : 0], currentEncodings[currentHomeDestPredIdx == 0 ? 1 : 0], 1e-5);
      return true;
    } catch (AssertionError error) {

      Log.warn("Unexpected encodings. Most likely it is due to race conditions in AstGroup (see https://github.com/h2oai/h2o-3/pull/3374 )");
      Log.warn("Swap:" + swapIdx1 + " <-> " + swapIdx2);
      Log.warn("encodings[homeDest]:" + encodings[homeDestPredIdx] + " currentEncodings[homeDest]: " + currentEncodings[currentHomeDestPredIdx]);
      Log.warn("encodings[embarked]:" + encodings[homeDestPredIdx == 0 ? 1 : 0] + " currentEncodings[embarked]: " + currentEncodings[currentHomeDestPredIdx == 0 ? 1 : 0]);
      return false;
    }
  }
}
