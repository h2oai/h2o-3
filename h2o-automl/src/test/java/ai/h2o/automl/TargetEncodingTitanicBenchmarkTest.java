package ai.h2o.automl;

import hex.FrameSplitter;
import hex.ModelMetricsBinomial;
import hex.ScoreKeeper;
import hex.genmodel.utils.DistributionFamily;
import hex.splitframe.ShuffleSplitFrame;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.*;
import water.DKV;
import water.H2O;
import water.Key;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;
import water.util.FrameUtils;
import water.util.TwoDimTable;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.*;
import static water.util.FrameUtils.generateNumKeys;

public class TargetEncodingTitanicBenchmarkTest extends TestUtil{


  @BeforeClass public static void setup() {
    stall_till_cloudsize(1);
  }

  private Frame fr = null;

  @Before
  public void beforeEach() {
        System.out.println("Before each setup");
    }


  @Test
  public void assertionErrorDuringMergeDueToNAsTest() {
    TargetEncoder tec = new TargetEncoder();

    Frame titanicFrame = parse_test_file(Key.make("titanic_parsed"), "smalldata/gbm_test/titanic.csv");

    titanicFrame.remove("name");
    titanicFrame.remove("ticket");
    titanicFrame.remove("boat");
    titanicFrame.remove("body");

    double[] ratios = ard(0.7f, 0.1f);
    Frame[] splits = null;
    FrameSplitter fs = new FrameSplitter(titanicFrame, ratios, generateNumKeys(titanicFrame._key, ratios.length + 1), null);
    H2O.submitTask(fs).join();
    splits = fs.getResult();
    Frame train = splits[0];
    Frame valid = splits[1];
    Frame test = splits[2];


    String[] teColumns = {"home.dest"};

    String targetColumnName = "survived";

    Map<String, Frame> encodingMap = tec.prepareEncodingMap(train, teColumns, targetColumnName, null);
    Frame trainEncoded = tec.applyTargetEncoding(train, teColumns, targetColumnName, encodingMap, TargetEncoder.HoldoutType.None, false, 0, 1234.0);

    printOutFrameAsTable(test, true);
    printOutFrameAsTable(valid, true);
    printOutColumnsMeta(valid);
    assertEquals( valid.vec("home.dest").max(), Double.NaN, 1e-5);

    assertTrue( valid.vec("home.dest").isNA(0));
    assertEquals("null", valid.vec("home.dest").stringAt(0)); // "null" is just a string representation of NA. It could have been easy to merge by this string.

    // Preparing valid frame
    Frame validEncoded = tec.applyTargetEncoding(valid, teColumns, targetColumnName, encodingMap, TargetEncoder.HoldoutType.None, false, 0, 1234.0);

  }

  @Test
  public void KFoldHoldoutTypeTest() {

    TargetEncoder tec = new TargetEncoder();

    Frame trainFrame = parse_test_file(Key.make("titanic_train_parsed"), "smalldata/gbm_test/titanic_train.csv");
    Frame validFrame = parse_test_file(Key.make("titanic_valid_parsed"), "smalldata/gbm_test/titanic_valid.csv");
    Frame testFrame = parse_test_file(Key.make("titanic_test_parsed"), "smalldata/gbm_test/titanic_test.csv");

    trainFrame.remove(new String[] {"name", "ticket", "boat", "body"});
    validFrame.remove(new String[] {"name", "ticket", "boat", "body"});
    testFrame.remove(new String[] {"name", "ticket", "boat", "body"});

    String foldColumnName = "fold";
    FrameUtils.addKFoldColumn(trainFrame, foldColumnName, 5, 1234L);

    System.out.println("Training frame with fold columns");
    printOutFrameAsTable(trainFrame, true);
    FrameUtils.addKFoldColumn(validFrame, foldColumnName, 5, 1234L);
    FrameUtils.addKFoldColumn(testFrame, foldColumnName, 5, 1234L);

    String targetColumnName = "survived";


    String[] teColumns = {"cabin", "embarked", "home.dest"};
    String[] teColumnsWithFold = {"cabin", "embarked", "home.dest", foldColumnName};


    Map<String, Frame> encodingMap = tec.prepareEncodingMap(trainFrame, teColumns, targetColumnName, foldColumnName);
    Frame trainEncoded = tec.applyTargetEncoding(trainFrame, teColumns, targetColumnName, encodingMap, TargetEncoder.HoldoutType.KFold, foldColumnName, false, 0, 1234.0);

    // Preparing valid frame
    Frame validEncoded = tec.applyTargetEncoding(validFrame, teColumns, targetColumnName, encodingMap, TargetEncoder.HoldoutType.None, foldColumnName, false, 0, 1234.0);

    // Preparing test frame
    Frame testEncoded = tec.applyTargetEncoding(testFrame, teColumns, targetColumnName, encodingMap, TargetEncoder.HoldoutType.None, foldColumnName, false, 0, 1234.0);

    printOutColumnsMeta(trainEncoded);

    // With target encoded Origin column
    GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
    parms._train = trainEncoded._key;
    parms._response_column = targetColumnName;
    parms._score_tree_interval = 10;
    parms._ntrees = 1000;
    parms._max_depth = 5;
    parms._distribution = DistributionFamily.quasibinomial;
    parms._valid = validEncoded._key;
    parms._stopping_tolerance = 0.001;
    parms._stopping_metric = ScoreKeeper.StoppingMetric.AUC;
    parms._stopping_rounds = 5;
    parms._ignored_columns = teColumnsWithFold;
    GBM job = new GBM(parms);
    GBMModel gbm = job.trainModel().get();

    System.out.println(gbm._output._variable_importances.toString(2, true));
    Assert.assertTrue(job.isStopped());

    Frame preds = gbm.score(testEncoded);

    hex.ModelMetricsBinomial mm = ModelMetricsBinomial.make(preds.vec(2), testEncoded.vec(parms._response_column));
    double auc = mm._auc._auc;

    // Without target encoding
    double auc2 = trainDefaultGBM(targetColumnName);


    System.out.println("AUC with encoding:" + auc);
    System.out.println("AUC without encoding:" + auc2);

    Assert.assertTrue(auc2 < auc);
  }

  @Test
  public void leaveOneOutHoldoutTypeTest() {

    TargetEncoder tec = new TargetEncoder();

    Frame trainFrame = parse_test_file(Key.make("titanic_train_parsed"), "smalldata/gbm_test/titanic_train.csv");
    Frame validFrame = parse_test_file(Key.make("titanic_valid_parsed"), "smalldata/gbm_test/titanic_valid.csv");
    Frame testFrame = parse_test_file(Key.make("titanic_test_parsed"), "smalldata/gbm_test/titanic_test.csv");

    trainFrame.remove(new String[] {"name", "ticket", "boat", "body"});
    validFrame.remove(new String[] {"name", "ticket", "boat", "body"});
    testFrame.remove(new String[] {"name", "ticket", "boat", "body"});


    String[] teColumns = {"cabin" ,"embarked", "home.dest"};

    String targetColumnName = "survived";

    Map<String, Frame> encodingMap = tec.prepareEncodingMap(trainFrame, teColumns, targetColumnName, null);

    Frame trainEncoded = tec.applyTargetEncoding(trainFrame, teColumns, targetColumnName, encodingMap, TargetEncoder.HoldoutType.LeaveOneOut, false, 0, 1234.0);


    printOutFrameAsTable(trainEncoded, true, true);
    printOutColumnsMeta(trainEncoded);

    // Preparing valid frame
    Frame validEncoded = tec.applyTargetEncoding(validFrame, teColumns, targetColumnName, encodingMap, TargetEncoder.HoldoutType.None, false, 0, 1234.0);

    printOutFrameAsTable(validEncoded, true, true);

    // Preparing test frame
    Frame testEncoded = tec.applyTargetEncoding(testFrame, teColumns, targetColumnName, encodingMap, TargetEncoder.HoldoutType.None, false, 0, 1234.0);

    printOutFrameAsTable(testEncoded, true, true);

    // With target encoded Origin column
    GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
    parms._train = trainEncoded._key;
    parms._response_column = targetColumnName;
    parms._score_tree_interval = 10;
    parms._ntrees = 1000;
    parms._max_depth = 5;
    parms._distribution = DistributionFamily.quasibinomial;
    parms._valid = validEncoded._key;
    parms._stopping_tolerance = 0.001;
    parms._stopping_metric = ScoreKeeper.StoppingMetric.AUC;
    parms._stopping_rounds = 5;
    parms._ignored_columns = teColumns;
    GBM job = new GBM(parms);
    GBMModel gbm = job.trainModel().get();

    Assert.assertTrue(job.isStopped());

    System.out.println(gbm._output._variable_importances.toString(2, true));

    Frame preds = gbm.score(testEncoded);

    hex.ModelMetricsBinomial mm = ModelMetricsBinomial.make(preds.vec(2), testEncoded.vec(parms._response_column));
    double auc = mm._auc._auc;

    // Without target encoding
    double auc2 = trainDefaultGBM(targetColumnName);

    System.out.println("AUC with encoding:" + auc);
    System.out.println("AUC without encoding:" + auc2);

    Assert.assertTrue(auc2 < auc);
  }

  @Test
  public void noneHoldoutTypeTest() {

    TargetEncoder tec = new TargetEncoder();

    Frame trainFrame = parse_test_file(Key.make("titanic_train_parsed"), "smalldata/gbm_test/titanic_train_wteh.csv");
    Frame teHoldoutFrame = parse_test_file(Key.make("titanic_te_holdout_parsed"), "smalldata/gbm_test/titanic_te_holdout.csv");
    Frame validFrame = parse_test_file(Key.make("titanic_valid_parsed"), "smalldata/gbm_test/titanic_valid.csv");
    Frame testFrame = parse_test_file(Key.make("titanic_test_parsed"), "smalldata/gbm_test/titanic_test.csv");

    trainFrame.remove(new String[] {"name", "ticket", "boat", "body"});
    teHoldoutFrame.remove(new String[] {"name", "ticket", "boat", "body"});
    validFrame.remove(new String[] {"name", "ticket", "boat", "body"});
    testFrame.remove(new String[] {"name", "ticket", "boat", "body"});

    teHoldoutFrame = FrameUtils.asFactor(teHoldoutFrame, "cabin");

    String[] teColumns = {"cabin" ,"embarked", "home.dest"};

    String targetColumnName = "survived";

    Map<String, Frame> encodingMap = tec.prepareEncodingMap(teHoldoutFrame, teColumns, targetColumnName, null);

    Frame trainEncoded = tec.applyTargetEncoding(trainFrame, teColumns, targetColumnName, encodingMap, TargetEncoder.HoldoutType.None, false, 0, 1234.0);

    // Preparing valid frame
    Frame validEncoded = tec.applyTargetEncoding(validFrame, teColumns, targetColumnName, encodingMap, TargetEncoder.HoldoutType.None, false, 0, 1234.0);

    printOutFrameAsTable(validEncoded, true, true);

    // Preparing test frame
    Frame testEncoded = tec.applyTargetEncoding(testFrame, teColumns, targetColumnName, encodingMap, TargetEncoder.HoldoutType.None, false, 0, 1234.0);

    printOutFrameAsTable(testEncoded, true, true);

    // With target encoded Origin column
    GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
    parms._train = trainEncoded._key;
    parms._response_column = targetColumnName;
    parms._score_tree_interval = 10;
    parms._ntrees = 1000;
    parms._max_depth = 5;
    parms._distribution = DistributionFamily.quasibinomial;
    parms._valid = validEncoded._key;
    parms._stopping_tolerance = 0.001;
    parms._stopping_metric = ScoreKeeper.StoppingMetric.AUC;
    parms._stopping_rounds = 5;
    parms._ignored_columns = teColumns;
    GBM job = new GBM(parms);
    GBMModel gbm = job.trainModel().get();

    Assert.assertTrue(job.isStopped());

    System.out.println(gbm._output._variable_importances.toString(2, true));

    Frame preds = gbm.score(testEncoded);

    hex.ModelMetricsBinomial mm = ModelMetricsBinomial.make(preds.vec(2), testEncoded.vec(parms._response_column));
    double auc = mm._auc._auc;

    // Without target encoding
    double auc2 = trainDefaultGBM(targetColumnName);

    System.out.println("AUC with encoding:" + auc);
    System.out.println("AUC without encoding:" + auc2);

    Assert.assertTrue(auc2 < auc);
  }


  private double trainDefaultGBM(String targetColumnName) {
    Frame trainFrame2 = parse_test_file(Key.make("titanic_train_parsed"), "smalldata/gbm_test/titanic_train.csv");
    Frame validFrame2 = parse_test_file(Key.make("titanic_valid_parsed2"), "smalldata/gbm_test/titanic_valid.csv");
    Frame testFrame2 = parse_test_file(Key.make("titanic_test_parsed2"), "smalldata/gbm_test/titanic_test.csv");

    trainFrame2.remove(new String[] {"name", "ticket", "boat", "body"});
    validFrame2.remove(new String[] {"name", "ticket", "boat", "body"});
    testFrame2.remove(new String[] {"name", "ticket", "boat", "body"});

    GBMModel.GBMParameters parms2 = new GBMModel.GBMParameters();
    parms2._train = trainFrame2._key;
    parms2._response_column = targetColumnName;
    parms2._score_tree_interval = 10;
    parms2._ntrees = 1000;
    parms2._max_depth = 5;
    parms2._distribution = DistributionFamily.quasibinomial;
    parms2._valid = validFrame2._key;
    parms2._stopping_tolerance = 0.001;
    parms2._stopping_metric = ScoreKeeper.StoppingMetric.AUC;
    parms2._stopping_rounds = 5;
    parms2._seed = 1234L;

    GBM job2 = new GBM(parms2);
    GBMModel gbm2 = job2.trainModel().get();

    Assert.assertTrue(job2.isStopped());

    Frame preds2 = gbm2.score(testFrame2);

    printOutFrameAsTable(preds2, false, false);
    hex.ModelMetricsBinomial mm2 = ModelMetricsBinomial.make(preds2.vec(2), testFrame2.vec(parms2._response_column));
    double auc2 = mm2._auc._auc;
    return auc2;
  }

  @After
  public void afterEach() {
    System.out.println("After each test we do H2O.STORE.clear() and Vec.ESPC.clear()");
    Vec.ESPC.clear();
    H2O.STORE.clear();
  }

  private void printOutFrameAsTable(Frame fr, boolean full) {
    printOutFrameAsTable(fr, full, false);
  }

  private void printOutFrameAsTable(Frame fr, boolean full, boolean rollups) {

    TwoDimTable twoDimTable = fr.toTwoDimTable(0, 10000, rollups);
    System.out.println(twoDimTable.toString(2, full));
  }

  private void printOutColumnsMeta(Frame fr) {
    for (String header : fr.toTwoDimTable().getColHeaders()) {
      String type = fr.vec(header).get_type_str();
      int cardinality = fr.vec(header).cardinality();
      System.out.println(header + " - " + type + String.format("; Cardinality = %d", cardinality));

    }
  }
}
