package ai.h2o.automl.targetencoding;


import hex.Model;
import hex.ScoreKeeper;
import hex.genmodel.MojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.BinomialModelPrediction;
import hex.genmodel.utils.DistributionFamily;
import hex.splitframe.ShuffleSplitFrame;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.io.FileOutputStream;

import static org.junit.Assert.assertEquals;

public class MojoTESupportIntegrationTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  private Frame fr = null;

  @Test
  public void writeAndReadMojoTest() {

    Scope.enter();
    try {
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

      Frame removed = fr.remove(new String[]{"name", "ticket", "boat", "body"});
      removed.delete();
      
      printOutColumnsMetadata(fr);
      printOutFrameAsTable(fr);

      Frame[] splits = ShuffleSplitFrame.shuffleSplitFrame(fr, new Key[]{Key.make(), Key.make()}, new double[]{0.8, 0.2}, 1234);

      Frame trainSplit = splits[0];
      Frame testSplit = splits[1]; // Unused
      String responseColumnName = "survived";

      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = trainSplit._key;
      parms._response_column = responseColumnName;
      parms._score_tree_interval = 10;
      parms._ntrees = 1000;
      parms._max_depth = 5;
      parms._distribution = DistributionFamily.quasibinomial;
      parms._stopping_tolerance = 0.001;
      parms._stopping_metric = ScoreKeeper.StoppingMetric.AUC;
      parms._stopping_rounds = 5;
      parms._seed = 1234L;

      GBM job = new GBM(parms);
      Model gbm = job.trainModel().get();

      System.out.println("Training Results");
      System.out.println(gbm._output);
      System.out.println("Model AUC " + gbm.auc());

      String fileName = "gbm_mojo_te.zip";


      FileOutputStream modelOutput = new FileOutputStream(fileName);
      gbm.getMojo().writeTo(modelOutput);
      modelOutput.close();
      System.out.println("Model written out as a mojo to file " + fileName);

      // Let's load model that we just have written and use it for prediction.
      EasyPredictModelWrapper model = null;

      model = new EasyPredictModelWrapper(MojoModel.load(fileName));

      RowData rowToPredictFor = new RowData();
      rowToPredictFor.put("home.dest", "Montreal  PQ / Chesterville  ON");
      rowToPredictFor.put("sex", "female");
      rowToPredictFor.put("age", "2.0");
      rowToPredictFor.put("fare", "151.55");
      rowToPredictFor.put("cabin", "C22 C26");
      rowToPredictFor.put("embarked", "S");
      rowToPredictFor.put("sibsp", "1");
      rowToPredictFor.put("parch", "2");
      rowToPredictFor.put("pclass", "1");

      BinomialModelPrediction predictionFromLoadedModel = model.predictBinomial(rowToPredictFor);

      System.out.print("Class probabilities: ");
      for (int i = 0; i < predictionFromLoadedModel.classProbabilities.length; i++) {
        if (i > 0) {
          System.out.print(",");
        }
        System.out.print(predictionFromLoadedModel.classProbabilities[i]);
      }

      Frame testFrameAsRowData = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("home.dest", "sex", "age", "fare", "cabin", "embarked", "sibsp", "parch", "pclass")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_NUM, Vec.T_NUM, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ar("Montreal  PQ / Chesterville  ON"))
              .withDataForCol(1, ar("female"))
              .withDataForCol(2, ar(2))
              .withDataForCol(3, ard(151.55))
              .withDataForCol(4, ar("C22 C26"))
              .withDataForCol(5, ar("S"))
              .withDataForCol(6, ar(1))
              .withDataForCol(7, ar(2))
              .withDataForCol(8, ar(1))
              .build();

      Frame predictionFromOriginalModel = gbm.score(testFrameAsRowData);

      assertEquals(predictionFromOriginalModel.vec("p0").at(0), predictionFromLoadedModel.classProbabilities[0], 1e-5);
      assertEquals(predictionFromOriginalModel.vec("p1").at(0), predictionFromLoadedModel.classProbabilities[1], 1e-5);
      printOutFrameAsTable(predictionFromOriginalModel);

    } catch (Exception ex) {
      Scope.exit();
    }
  }

  @After
  public void afterEach() {
    if (fr != null) fr.delete();
  }

}
