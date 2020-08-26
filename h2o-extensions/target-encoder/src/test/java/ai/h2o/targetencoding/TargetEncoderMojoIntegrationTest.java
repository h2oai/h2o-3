package ai.h2o.targetencoding;

import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.InRange;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
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
import org.junit.runner.RunWith;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

import static ai.h2o.targetencoding.TargetEncoderHelper.addKFoldColumn;
import static hex.genmodel.algos.targetencoder.TargetEncoderMojoModel.computeBlendedEncoding;
import static hex.genmodel.algos.targetencoder.TargetEncoderMojoModel.computeLambda;
import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
public class TargetEncoderMojoIntegrationTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void test_mojo_consistency_binary() throws PredictException, IOException {
    String mojoFileName = "mojo_te.zip";
    File mojoFile = folder.newFile(mojoFileName);

    try {
      Scope.enter();
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

      String target = "survived";
      asFactor(fr, target);
      Scope.track(fr);

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._response_column = target;
      teParams._ignored_columns = ignoredColumns(fr, "home.dest", "embarked", teParams._response_column);
      teParams._noise = 0;
      teParams.setTrain(fr._key);

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
        teModel.getMojo().writeTo(modelOutput);
        System.out.println("Model has been written down to a file as a mojo: " + mojoFileName);
      }

      // data that is not encoded yet
      Map<String, Object> row = new HashMap();
      String homeDestCat = "New York  NY";
      String embarkedCat = "S";

      row.put("home.dest", homeDestCat);
      row.put("sex", "female");
      row.put("age", 20);
      row.put("fare", 151.55);
      row.put("cabin", "C22 C26");
      row.put("embarked", embarkedCat);
      row.put("sibsp", 1);
      row.put("parch", "N");
      row.put("name", "1111"); // somehow encoded name
      row.put("ticket", "12345");
      row.put("boat", "N");
      row.put("body", 123);
      row.put("pclass", "1");

      Frame transformations = Scope.track(teModel.transform(Scope.track(asFrame(row))));
      printOutFrameAsTable(transformations);
      double homeDestEnc = transformations.vec("home.dest_te").at(0);
      double homeEmbarkedEnc = transformations.vec("embarked_te").at(0);

      // Let's load model that we just have written and use it for prediction.
      TargetEncoderMojoModel loadedMojoModel = (TargetEncoderMojoModel) MojoModel.load(mojoFile.getPath());
      EasyPredictModelWrapper teModelWrapper = new EasyPredictModelWrapper(loadedMojoModel);

      double[] predictions = teModelWrapper.predictTargetEncoding(asRowData(row)).transformations;
      assertEquals(2, predictions.length);

      // Because of the random swap we need to know which index is lower so that we know order of transformations/predictions
      int homeDestPredIdx = fr.find("home.dest") < fr.find("embarked") ? 0 : 1;
      assertEquals(homeDestEnc, predictions[homeDestPredIdx], 1e-5);
      assertEquals(homeEmbarkedEnc, predictions[1 - homeDestPredIdx], 1e-5);

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_mojo_consistency_multiclass() throws PredictException, IOException {
    String mojoFileName = "mojo_te.zip";
    File mojoFile = folder.newFile(mojoFileName);

    try {
      Scope.enter();
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

      String target = "pclass";
      asFactor(fr, target);
      Scope.track(fr);

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._response_column = target;
      teParams._ignored_columns = ignoredColumns(fr, "home.dest", "embarked", teParams._response_column);
      teParams._noise = 0;
      teParams.setTrain(fr._key);

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
        teModel.getMojo().writeTo(modelOutput);
        System.out.println("Model has been written down to a file as a mojo: " + mojoFileName);
      }

      // data that is not encoded yet
      Map<String, Object> row = new HashMap();
      String homeDestCat = "New York  NY";
      String embarkedCat = "S";

      row.put("home.dest", homeDestCat);
      row.put("sex", "female");
      row.put("age", 20);
      row.put("fare", 151.55);
      row.put("cabin", "C22 C26");
      row.put("embarked", embarkedCat);
      row.put("sibsp", 1);
      row.put("parch", "N");
      row.put("name", "1111"); // somehow encoded name
      row.put("ticket", "12345");
      row.put("boat", "N");
      row.put("body", 123);

      Frame transformations = Scope.track(teModel.transform(Scope.track(asFrame(row))));
      printOutFrameAsTable(transformations);
      double homeDest2Enc = transformations.vec("home.dest_2_te").at(0);
      double homeDest3Enc = transformations.vec("home.dest_3_te").at(0);
      double homeEmbarked2Enc = transformations.vec("embarked_2_te").at(0);
      double homeEmbarked3Enc = transformations.vec("embarked_3_te").at(0);

      // Let's load model that we just have written and use it for prediction.
      TargetEncoderMojoModel loadedMojoModel = (TargetEncoderMojoModel) MojoModel.load(mojoFile.getPath());
      EasyPredictModelWrapper teModelWrapper = new EasyPredictModelWrapper(loadedMojoModel);

      double[] predictions = teModelWrapper.predictTargetEncoding(asRowData(row)).transformations;
      assertEquals(4, predictions.length); //2*2 as pclass has 3 classes, and we have 2 columns to encode

      // Because of the random swap we need to know which index is lower so that we know order of transformations/predictions
      int homeDestPredIdx = fr.find("home.dest") < fr.find("embarked") ? 0 : 2;
      assertEquals(homeDest2Enc, predictions[homeDestPredIdx], 1e-5);
      assertEquals(homeDest3Enc, predictions[homeDestPredIdx+1], 1e-5);
      assertEquals(homeEmbarked2Enc, predictions[2-homeDestPredIdx], 1e-5);
      assertEquals(homeEmbarked3Enc, predictions[2-homeDestPredIdx+1], 1e-5);

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_mojo_consistency_regression() throws PredictException, IOException {
    String mojoFileName = "mojo_te.zip";
    File mojoFile = folder.newFile(mojoFileName);

    try {
      Scope.enter();
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

      String target = "fare";
      Scope.track(fr);

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._response_column = target;
      teParams._ignored_columns = ignoredColumns(fr, "home.dest", "embarked", teParams._response_column);
      teParams._noise = 0;
      teParams.setTrain(fr._key);

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
        teModel.getMojo().writeTo(modelOutput);
        System.out.println("Model has been written down to a file as a mojo: " + mojoFileName);
      }
      
      // data that is not encoded yet
      Map<String, Object> row = new HashMap();
      String homeDestCat = "New York  NY";
      String embarkedCat = "S";

      row.put("home.dest", homeDestCat);
      row.put("sex", "female");
      row.put("age", 20);
      row.put("cabin", "C22 C26");
      row.put("embarked", embarkedCat);
      row.put("sibsp", 1);
      row.put("parch", "N");
      row.put("name", "1111"); // somehow encoded name
      row.put("ticket", "12345");
      row.put("boat", "N");
      row.put("body", 123);
      row.put("pclass", "1");
      
      Frame transformations = Scope.track(teModel.transform(Scope.track(asFrame(row))));
      printOutFrameAsTable(transformations);
      double homeDestEnc = transformations.vec("home.dest_te").at(0);
      double homeEmbarkedEnc = transformations.vec("embarked_te").at(0);

      // Let's load model that we just have written and use it for prediction.
      TargetEncoderMojoModel loadedMojoModel = (TargetEncoderMojoModel) MojoModel.load(mojoFile.getPath());
      EasyPredictModelWrapper teModelWrapper = new EasyPredictModelWrapper(loadedMojoModel);

      double[] predictions = teModelWrapper.predictTargetEncoding(asRowData(row)).transformations;
      assertEquals(2, predictions.length);

      // Because of the random swap we need to know which index is lower so that we know order of transformations/predictions
      int homeDestPredIdx = fr.find("home.dest") < fr.find("embarked") ? 0 : 1;
      assertEquals(homeDestEnc, predictions[homeDestPredIdx], 1e-5);
      assertEquals(homeEmbarkedEnc, predictions[1 - homeDestPredIdx], 1e-5);

    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void prediction_consistency_test() throws PredictException, IOException{
    Random rg = new Random();

    String mojoFileName = "mojo_te.zip";
    File mojoFile = null;
    
    int inconsistencyCounter = 0;
    int numberOfRuns = 50;
    
    double[] predictions = null;
    int homeDestPredIdx = -1;
    for (int i = 0; i <= numberOfRuns; i++) {
      mojoFile = folder.newFile(mojoFileName);

      Scope.enter();
      try {
        Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
        String target = "survived";
        asFactor(fr, target);
        Scope.track(fr);

        int swapIdx1 = rg.nextInt(fr.numCols());
        int swapIdx2 = rg.nextInt(fr.numCols());
        fr.swap(swapIdx1, swapIdx2);
        DKV.put(fr);

        TargetEncoderParameters teParams = new TargetEncoderParameters();
        teParams._response_column = target;
        teParams._ignored_columns = ignoredColumns(fr, "home.dest", "embarked", teParams._response_column);
        teParams._ignore_const_cols = false; // Why ignore_const_column ignores `name` column? bad naming
        teParams.setTrain(fr._key);

        TargetEncoder te = new TargetEncoder(teParams);
        TargetEncoderModel teModel = te.trainModel().get();
        Scope.track_generic(teModel);

        try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)){
          teModel.getMojo().writeTo(modelOutput);
          System.out.println("Model has been written down to a file as a mojo: " + mojoFileName);
        }

        // Let's load model that we just have written and use it for prediction.
        TargetEncoderMojoModel loadedMojoModel = (TargetEncoderMojoModel) MojoModel.load(mojoFile.getPath());
        EasyPredictModelWrapper teModelWrapper = new EasyPredictModelWrapper(loadedMojoModel);

        // RowData that is not encoded yet
        RowData row = new RowData();
        String homeDestCat = "Montreal  PQ / Chesterville  ON";
        String embarkedCat = "S";

        row.put("home.dest", homeDestCat);
        row.put("sex", "female");
        row.put("age", "2.0");
        row.put("fare", "151.55");
        row.put("cabin", "C22 C26");
        row.put("embarked", embarkedCat);
        row.put("sibsp", "1");
        row.put("parch", "N");
        row.put("name", "1111"); // somehow encoded name
        row.put("ticket", "12345");
        row.put("boat", "N");
        row.put("body", "123");
        row.put("pclass", "1");

        if (predictions == null) {
          predictions = teModelWrapper.predictTargetEncoding(row).transformations;
          homeDestPredIdx = fr.find("home.dest") < fr.find("embarked") ? 0 : 1;
        } else {
          double[] currentPredictions = teModelWrapper.predictTargetEncoding(row).transformations;
          // Because of the random swap we need to know which index is lower so that we know order of transformations/predictions
          int currentHomeDestPredIdx = fr.find("home.dest") < fr.find("embarked") ? 0 : 1;
          inconsistencyCounter += isEqualToReferenceValue(predictions, homeDestPredIdx, currentPredictions, currentHomeDestPredIdx, swapIdx1, swapIdx2) ? 0 : 1;
        }

      } finally {
        mojoFile.delete(); // As we are in the loop we need to remove tmp file manually
        Scope.exit();
      }
    }
    
    assertEquals("Transformation failed " + inconsistencyCounter + " times out of " + numberOfRuns + " runs",0, inconsistencyCounter);
  }

  private double getEncodedCategory(Frame fr, String categoricalColumn, String category, EncodingMaps encodingMaps) {
    int catVal = ArrayUtils.find(fr.vec(categoricalColumn).domain(), category);
    double[] numDen = encodingMaps.get(categoricalColumn).getNumDen( catVal);
    return numDen[0] / numDen[1];
  }

  @Test
  public void without_blending_kfold_scenario() throws PredictException, IOException {
    String mojoFileName = "mojo_te.zip";
    File mojoFile = folder.newFile(mojoFileName);
    
    try {
      Scope.enter();
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      Scope.track(fr);

      String target = "survived";
      asFactor(fr, target);
      String foldColumn = "fold_column";
      addKFoldColumn(fr, foldColumn, 5, 1234L);

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._fold_column = foldColumn;
      teParams._response_column = target;
      teParams._ignored_columns = ignoredColumns(fr, "home.dest", "embarked", teParams._fold_column, teParams._response_column);
      teParams.setTrain(fr._key);
      teParams._ignore_const_cols = false;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
        teModel.getMojo().writeTo(modelOutput);
        System.out.println("Model has been written down to a file as a mojo: " + mojoFileName);
      }

      TargetEncoderMojoModel loadedMojoModel = (TargetEncoderMojoModel) MojoModel.load(mojoFile.getPath());
      EasyPredictModelWrapper teModelWrapper = new EasyPredictModelWrapper(loadedMojoModel);

      // RowData that is not encoded yet
      RowData row = new RowData();
      String homeDestCat = "Montreal  PQ / Chesterville  ON";
      String embarkedCat = "S";
      row.put("home.dest", homeDestCat);
      row.put("sex", "female");
      row.put("age", "2.0");
      row.put("fare", "151.55");
      row.put("cabin", "C22 C26");
      row.put("embarked", embarkedCat);
      row.put("sibsp", "1");
      row.put("parch", "N");
      row.put("name", "1111"); // somehow encoded name
      row.put("ticket", "12345");
      row.put("boat", "N");
      row.put("body", "123");
      row.put("pclass", "1");

      double[] encodings = teModelWrapper.predictTargetEncoding(row).transformations;
      //Check that specified in the test categorical columns have been encoded in accordance with teMap
      EncodingMaps teMap = loadedMojoModel._targetEncodingMap;

      double homeDestEnc = getEncodedCategory(fr, "home.dest", homeDestCat, teMap);
      double embarkedEnc = getEncodedCategory(fr, "embarked", embarkedCat, teMap);

      assertEquals(encodings[1], homeDestEnc, 1e-5);
      assertEquals(encodings[0], embarkedEnc, 1e-5);

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void check_that_encoding_map_was_stored_and_loaded_properly_and_blending_was_applied_correctly() throws IOException, PredictException {
    String mojoFileName = "mojo_te.zip";
    File mojoFile = folder.newFile(mojoFileName);
    try {
      Scope.enter();
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      String target = "survived";
      asFactor(fr, target);
      Scope.track(fr);

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._blending = true;
      teParams._response_column = target;
      teParams._ignored_columns = ignoredColumns(fr, "home.dest", "embarked", teParams._response_column);
      teParams._ignore_const_cols = false;
      teParams.setTrain(fr._key);

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);
      Map<String, Frame> teMap = teModel._output._target_encoding_map;

      try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
        teModel.getMojo().writeTo(modelOutput);
        System.out.println("Model has been written down to a file as a mojo: " + mojoFileName);
      }

      TargetEncoderMojoModel loadedMojoModel = (TargetEncoderMojoModel) MojoModel.load(mojoFile.getPath());
      EasyPredictModelWrapper teModelWrapper = new EasyPredictModelWrapper(loadedMojoModel); // TODO why we store GenModel even though we pass MojoModel?

      // RowData that is not encoded yet
      RowData row = new RowData();
      String homeDestCat = "Montreal  PQ / Chesterville  ON";
      String embarkedCat = "S";
      row.put("home.dest", homeDestCat);
      row.put("sex", "female");
      row.put("age", "2.0");
      row.put("fare", "151.55");
      row.put("cabin", "C22 C26");
      row.put("embarked", embarkedCat);
      row.put("sibsp", "1");
      row.put("parch", "N");
      row.put("name", "1111"); // somehow encoded name
      row.put("ticket", "12345");
      row.put("boat", "N");
      row.put("body", "123");
      row.put("pclass", "1");

      double[] predictions = teModelWrapper.predictTargetEncoding(row).transformations;

      // Check that specified in the test categorical columns have been encoded in accordance with encoding map
      // We reusing static helper methods from TargetEncoderMojoModel as it is not the point of current test to check them.
      // We want to check here that proper blending params were being used during `.transformWithTargetEncoding()` transformation
      EncodingMaps loadedEncodingMap = loadedMojoModel._targetEncodingMap;

      String teColumn = "home.dest";
      EncodingMap encodings = loadedEncodingMap.get(teColumn);
      
      double expectedPriorMean = TargetEncoderHelper.computePriorMean(teMap.get("embarked"));
      assertEquals(expectedPriorMean, encodings.getPriorMean(), 1e-6);
      // Checking that predictions from Mojo model and manually computed ones are equal
      int homeDestIndex = ArrayUtils.find(fr.vec(teColumn).domain(), homeDestCat);
      double[] homeDestEncComponents = encodings.getNumDen(homeDestIndex);
      double posteriorMean = homeDestEncComponents[0] / homeDestEncComponents[1];
      double expectedLambda = computeLambda((long)homeDestEncComponents[1], teParams._inflection_point, teParams._smoothing);
      double expectedBlendedEncoding = computeBlendedEncoding(expectedLambda, posteriorMean, expectedPriorMean);
      assertEquals(expectedBlendedEncoding, predictions[1], 1e-5);

    } finally {
      Scope.exit();
    }
  }

  // We need to test only holdout None  case as we predict only for data which were not used for TEModel training 
  @Test
  public void check_that_encodings_for_unexpected_values_are_the_same_in_TargetEncoderModel_and_TargetEncoderMojoModel_big_inflection_point() throws IOException, PredictException {
    String mojoFileName = "mojo_te.zip";
    File mojoFile = folder.newFile(mojoFileName);
    try {
      Scope.enter();
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      String target = "survived";
      asFactor(fr, target);
      Scope.track(fr);

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._response_column = target;
      teParams._ignored_columns = ignoredColumns(fr, "home.dest", teParams._response_column);
      teParams._blending = true;
      // We want to test case when inflection point is higher than number of missing values in the training frame, i.e. blending would favor prior probability
      teParams._inflection_point = 600;
      teParams._smoothing = 1;
      teParams._seed = 1234;
      teParams._noise = 0;
      teParams.setTrain(fr._key);

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
        teModel.getMojo().writeTo(modelOutput);
        System.out.println("Model has been written down to a file as a mojo: " + mojoFileName);
      }

      // Let's load model that we just have written and use it for prediction.
      TargetEncoderMojoModel loadedMojoModel = (TargetEncoderMojoModel) MojoModel.load(mojoFile.getPath());
      EasyPredictModelWrapper teModelWrapper = new EasyPredictModelWrapper(loadedMojoModel); // TODO why we store GenModel even though we pass MojoModel?

      // RowData that is not encoded yet
      RowData row = new RowData();
      row.put("home.dest", Double.NaN);

      double[] mojoPreds = teModelWrapper.predictTargetEncoding(row).transformations;
      
      // Unexpected level value - `null`
      Frame withNullFrame = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("home.dest")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar((String)null))
              .build();
      // Encoding should be coming from posterior probability of NAs in the training frame
      Frame teModelPredsWithNull = teModel.score(withNullFrame);
      Scope.track(teModelPredsWithNull);
      assertEquals(mojoPreds[0], teModelPredsWithNull.vec("home.dest_te").at(0), 1e-5);

      // Unexpected level value - unseen categorical level
      Frame withUnseenLevelFrame = new TestFrameBuilder()
              .withName("testFrame2")
              .withColNames("home.dest")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar("xxx"))
              .build();
      // In case we predict for unseen level, encodings should be coming from prior probability of the response
      Frame teModelPredsWithUnseen = teModel.score(withUnseenLevelFrame);
      Scope.track(teModelPredsWithUnseen);
      // This prediction will essentially be a prior as inflection point is 600 vs only one value in the `withUnseenLevelFrame` frame
      assertEquals(mojoPreds[0], teModelPredsWithUnseen.vec("home.dest_te").at(0), 1e-5);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void check_that_encodings_for_unexpected_values_are_the_same_in_TargetEncoderModel_and_TargetEncoderMojoModel_small_inflection_point() throws IOException, PredictException {

    String mojoFileName = "mojo_te.zip";
    File mojoFile = folder.newFile(mojoFileName);
    try {
      Scope.enter();
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      String target = "survived";
      asFactor(fr, target);
      Scope.track(fr);

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._response_column = target;
      teParams._ignored_columns = ignoredColumns(fr, "home.dest", teParams._response_column);
      teParams._blending = true;
      teParams._seed = 1234;
      teParams._noise = 0;
      // We want to test case when inflection point is lower than number of missing values in the training frame, i.e. blending would favor posterior probability
      teParams._inflection_point = 5;
      teParams._smoothing = 1;
      teParams.setTrain(fr._key);

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
        teModel.getMojo().writeTo(modelOutput);
        System.out.println("Model has been written down to a file as a mojo: " + mojoFileName);
      }

      // Let's load model that we just have written and use it for prediction.
      TargetEncoderMojoModel loadedMojoModel = (TargetEncoderMojoModel) MojoModel.load(mojoFile.getPath());
      EasyPredictModelWrapper teModelWrapper = new EasyPredictModelWrapper(loadedMojoModel); // TODO why we store GenModel even though we pass MojoModel?

      // RowData that is not encoded yet
      RowData row = new RowData();
      row.put("home.dest", Double.NaN);

      double[] mojoPreds = teModelWrapper.predictTargetEncoding(row).transformations;
      
      // Unexpected level value - `null`
      Frame withNullFrame = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("home.dest")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar((String)null))
              .build();
      // Encoding should be coming from posterior probability of NAs in the training frame
      Frame teModelPredsWithNull = teModel.score(withNullFrame);
      Scope.track(teModelPredsWithNull);
      assertEquals(mojoPreds[0], teModelPredsWithNull.vec("home.dest_te").at(0), 1e-5);

      // Unexpected level value - unseen categorical level
      Frame withUnseenLevelFrame = new TestFrameBuilder()
              .withName("testFrame2")
              .withColNames("home.dest")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar("xxx"))
              .build();
      Frame teModelPredsWithUnseen = teModel.score(withUnseenLevelFrame);
      Scope.track(teModelPredsWithUnseen);
      assertEquals(mojoPreds[0], teModelPredsWithUnseen.vec("home.dest_te").at(0), 1e-5);
    } finally {
      Scope.exit();
    }
  }

  @Property(trials = 5)
  public void check_that_encodings_with_blending_are_the_same_in_TargetEncoderModel_and_TargetEncoderMojoModel(
          @InRange(minInt = 1, maxInt = 1000)int randomInflectionPoint
  ) throws IOException, PredictException {

    String mojoFileName = "mojo_te.zip";
    File mojoFile = folder.newFile(mojoFileName);
    try {
      Scope.enter();
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      String target = "survived";
      asFactor(fr, target);
      Scope.track(fr);
      
      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._response_column = target;
      teParams._ignored_columns = ignoredColumns(fr, "home.dest", teParams._response_column);
      teParams._blending = true;
      teParams._seed = 1234;
      teParams._noise = 0;
      teParams._inflection_point = randomInflectionPoint;
      teParams._smoothing = 1;
      teParams.setTrain(fr._key);

      TargetEncoder job = new TargetEncoder(teParams);
      TargetEncoderModel teModel = job.trainModel().get();
      Scope.track_generic(teModel);

      try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
        teModel.getMojo().writeTo(modelOutput);
        System.out.println("Model has been written down to a file as a mojo: " + mojoFileName);
      }

      // Let's load model that we just have written and use it for prediction.
      TargetEncoderMojoModel loadedMojoModel = (TargetEncoderMojoModel) MojoModel.load(mojoFile.getPath());
      EasyPredictModelWrapper teModelWrapper = new EasyPredictModelWrapper(loadedMojoModel); // TODO why we store GenModel even though we pass MojoModel?

      // RowData that is not encoded yet
      RowData row = new RowData();
      row.put("home.dest", "Southampton");

      double[] mojoPreds = teModelWrapper.predictTargetEncoding(row).transformations;

      // Unexpected level value - `null`
      Frame testFrame = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("home.dest")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar("Southampton"))
              .build();
      Frame teModelPreds = teModel.score(testFrame);
      Scope.track(teModelPreds);

      double predictionFromTEModel = teModelPreds.vec("home.dest_te").at(0);
      double predictionFromMojo = mojoPreds[0];
      assertEquals(predictionFromMojo, predictionFromTEModel, 1e-5);

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void check_that_we_can_transform_dataframe_that_contains_only_columns_for_encoding() throws PredictException, IOException {
    String mojoFileName = "mojo_te.zip";
    File mojoFile = folder.newFile(mojoFileName);
    try {
      Scope.enter();
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      String target = "survived";
      asFactor(fr, target);
      Scope.track(fr);

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._blending = false;
      teParams._response_column = target;
      teParams._ignored_columns = ignoredColumns(fr, "home.dest", "embarked", target);
      teParams.setTrain(fr._key);

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
        teModel.getMojo().writeTo(modelOutput);
        System.out.println("Model has been written down to a file as a mojo: " + mojoFileName);
      }

      // Let's load model that we just have written and use it for prediction.
      TargetEncoderMojoModel loadedMojoModel = (TargetEncoderMojoModel) MojoModel.load(mojoFile.getPath());
      EasyPredictModelWrapper teModelWrapper = new EasyPredictModelWrapper(loadedMojoModel);

      // RowData that is not encoded yet
      RowData row = new RowData();
      String homeDestCat = "Montreal  PQ / Chesterville  ON";
      String embarkedCat = "S";
      row.put("home.dest", homeDestCat);
      row.put("embarked", embarkedCat);

      double[] currentEncodings = teModelWrapper.predictTargetEncoding(row).transformations;
      //Let's check that specified in the test categorical columns have been encoded in accordance with teMap
      EncodingMaps teMap = loadedMojoModel._targetEncodingMap;

      double encodingForHomeDest = getEncodedCategory(fr, "home.dest", homeDestCat, teMap);
      double encodingForHomeEmbarked = getEncodedCategory(fr, "embarked", embarkedCat, teMap);
      // Because of the random swap we need to know which index is lower so that we know order of transformations/predictions
      int currentHomeDestPredIdx = fr.find("home.dest") < fr.find("embarked") ? 0 : 1;

      assertEquals(currentEncodings[currentHomeDestPredIdx], encodingForHomeDest, 1e-5);
      assertEquals(currentEncodings[1 - currentHomeDestPredIdx], encodingForHomeEmbarked, 1e-5);
    } finally {
      Scope.exit();
    }
  }

  private boolean isEqualToReferenceValue(double[] lhsEncodings, int lhsRefIdx, 
                                          double[] rhsEncodings, int rhsRefIdx,
                                          int lhsSwapIdx, int rhsSwapIdx) {
    try {
      assertEquals(lhsEncodings[lhsRefIdx], rhsEncodings[rhsRefIdx], 1e-5);
      assertEquals(lhsEncodings[1 - lhsRefIdx], rhsEncodings[1 - rhsRefIdx], 1e-5);
      return true;
    } catch (AssertionError error) {
      Log.warn("Unexpected encodings. Most likely it is due to race conditions in AstGroup (see https://github.com/h2oai/h2o-3/pull/3374 )");
      Log.warn("Swap:" + lhsSwapIdx + " <-> " + rhsSwapIdx);
      Log.warn("encodings[homeDest]:" + lhsEncodings[lhsRefIdx] + " currentEncodings[homeDest]: " + rhsEncodings[rhsRefIdx]);
      Log.warn("encodings[embarked]:" + lhsEncodings[1 - lhsRefIdx] + " currentEncodings[embarked]: " + rhsEncodings[1 - rhsRefIdx]);
      return false;
    }
  }
  
  private RowData asRowData(Map<String,?> data) {
    RowData row = new RowData();
    row.putAll(data);
    return row;
  }
  
  private Frame asFrame(Map<String,?> data) {
    String[] columns = data.keySet().toArray(new String[0]);
    int[] types = Stream.of(columns)
            .mapToInt(c -> data.get(c) instanceof Number ? Vec.T_NUM : Vec.T_CAT)
            .toArray();
    
    TestFrameBuilder builder = new TestFrameBuilder()
            .withColNames(columns)
            .withVecTypes(ArrayUtils.toByteArray(types));
    for (int i=0; i<columns.length; i++) {
        Object v = data.get(columns[i]);
        if (v instanceof Number) {
          builder.withDataForCol(i, new double[] {((Number)v).doubleValue()});
        } else {
          builder.withDataForCol(i, new String[] {(String)v});
        }
    }
    
    return builder.build();
  }
}
