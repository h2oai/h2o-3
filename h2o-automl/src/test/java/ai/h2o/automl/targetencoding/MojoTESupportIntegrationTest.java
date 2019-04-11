package ai.h2o.automl.targetencoding;

import hex.Model;
import hex.ScoreKeeper;
import hex.genmodel.MojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.prediction.BinomialModelPrediction;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

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
    Model gbm = null;
    Map<String, Frame> testEncodingMap = null;
    Scope.enter();
    try {
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

      String responseColumnName = "survived";
      
      asFactor(fr, responseColumnName);

      // Preparing Target encoding 
      BlendingParams params = new BlendingParams(3, 1);
      String[] teColumns = {"home.dest", "embarked"};
      String[] ignoredColumns = {"home.dest", "embarked", "name", "ticket", "boat", "body"};

      TargetEncoder tec = new TargetEncoder(teColumns, params);

      testEncodingMap = tec.prepareEncodingMap(fr, responseColumnName, null);
      Frame trainEncodedSplit = tec.applyTargetEncoding(fr, responseColumnName, testEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, true, true, 1234);
      Scope.track(trainEncodedSplit);
      
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = trainEncodedSplit._key;
      parms._response_column = responseColumnName;
      parms._score_tree_interval = 10;
      parms._ntrees = 1000;
      parms._max_depth = 5;
      parms._distribution = DistributionFamily.AUTO;
      parms._stopping_tolerance = 0.001;
      parms._stopping_metric = ScoreKeeper.StoppingMetric.AUC;
      parms._stopping_rounds = 5;
      parms._ignored_columns = ignoredColumns;
      parms._seed = 1234L;

      GBM job = new GBM(parms);
      gbm = job.trainModel().get();
      
      gbm.addTargetEncodingMap(testEncodingMap); // Maybe we should also do this through ModelBuilder

      System.out.println("Training Results");
      System.out.println(gbm._output);
      System.out.println("Model AUC " + gbm.auc());

      FileOutputStream modelOutput = new FileOutputStream(mojoFileName);
      gbm.getMojo().writeTo(modelOutput);
      modelOutput.close();
      System.out.println("Model written out as a mojo to file " + mojoFileName);

      // Let's load model that we just have written and use it for prediction.
      EasyPredictModelWrapper model = null;

      model = new EasyPredictModelWrapper(MojoModel.load(mojoFileName)); // TODO why we store GenModel even though we pass MojoModel?

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

      // Here in `predictBinomial` we will perform encoding for rowToPredictFor
      BinomialModelPrediction predictionFromLoadedModel = model.predictBinomial(rowToPredictFor);

      //Check that specified in the test categorical columns have been encoded in accordance with targetEncodingMap
      Map<String, Map<String, int[]>> targetEncodingMap = model.getTargetEncodingMap();
      
      int[] encodingComponentsForHomeDest = targetEncodingMap.get("home.dest").get(homeDestFactorValue);
      double encodingForHomeDest = (double) encodingComponentsForHomeDest[0] / encodingComponentsForHomeDest[1];
      
      int[] encodingComponentsForEmbarked = targetEncodingMap.get("embarked").get(embarkedFactorValue);
      double encodingForHomeEmbarked = (double) encodingComponentsForEmbarked[0] / encodingComponentsForEmbarked[1];
      
      assertEquals((double) rowToPredictFor.get("home.dest_te"), encodingForHomeDest, 1e-5);

      // Check that prediction from both original model and genmodel are the same. Here we maually substituted factors.
      // We can do this transformation with target encoder either but for one row it is inconvenient as we will have to deal with half-defined domains.
      Frame testFrameAsRowData = new TestFrameBuilder()
              .withName("testRow")
              .withColNames("home.dest_te", "sex", "age", "fare", "cabin", "embarked_te", "sibsp", "parch", "pclass", "survived")
              .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_NUM, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ard(encodingForHomeDest)) // <--- encoded ---- "Montreal  PQ / Chesterville  ON"
              .withDataForCol(1, ar("female"))
              .withDataForCol(2, ar(2))
              .withDataForCol(3, ard(151.55))
              .withDataForCol(4, ar("C22 C26"))
              .withDataForCol(5, ard(encodingForHomeEmbarked)) // <--- encoded ----  "S"
              .withDataForCol(6, ar(1))
              .withDataForCol(7, ar(2))
              .withDataForCol(8, ar(1))
              .withDataForCol(9, ar("0"))
              .build();


      Frame predictionFromOriginalModel = gbm.score(testFrameAsRowData);
      Scope.track(predictionFromOriginalModel);

      System.out.println("Class probabilities from original model: ");
      printOutFrameAsTable(predictionFromOriginalModel);
      System.out.println("Class probabilities from loaded model: " + predictionFromLoadedModel.classProbabilities[0] + "  " +  predictionFromLoadedModel.classProbabilities[1]);

      assertEquals(predictionFromOriginalModel.vec("p0").at(0), predictionFromLoadedModel.classProbabilities[0], 1e-5);
      assertEquals(predictionFromOriginalModel.vec("p1").at(0), predictionFromLoadedModel.classProbabilities[1], 1e-5);

    } finally {
      if(testEncodingMap != null) 
        TargetEncoderFrameHelper.encodingMapCleanUp(testEncodingMap);
      if( gbm != null ) {
        gbm.delete();
        gbm.deleteCrossValidationModels();
      }
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
