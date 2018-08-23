package ai.h2o.automl;

import hex.FrameSplitter;
import hex.ModelMetricsBinomial;
import hex.ScoreKeeper;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.*;
import water.H2O;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.FrameUtils;
import water.util.TwoDimTable;

import java.util.Map;

import static water.util.FrameUtils.generateNumKeys;

public class TargetEncodingAirlinesBenchmarkTest extends TestUtil {


  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void KFoldHoldoutTypeTest() {

    Frame airlinesTrainWithTEH = parse_test_file(Key.make("airlines_train"), "smalldata/airlines/target_encoding/airlines_train_with_teh.csv");
    Frame airlinesValid = parse_test_file(Key.make("airlines_valid"), "smalldata/airlines/target_encoding/airlines_valid.csv");
    Frame airlinesTestFrame = parse_test_file(Key.make("airlines_test"), "smalldata/airlines/AirlinesTest.csv.zip");

    String foldColumnName = "fold";
    FrameUtils.addKFoldColumn(airlinesTrainWithTEH, foldColumnName, 5, 1234L);

    TargetEncoder tec = new TargetEncoder();
    String[] teColumns = {"Origin", "Dest"};
    String targetColumnName = "IsDepDelayed";

    // Create encoding
    Map<String, Frame> encodingMap = tec.prepareEncodingMap(airlinesTrainWithTEH, teColumns, targetColumnName, foldColumnName);

    // Apply encoding to the training set
    Frame trainEncoded = tec.applyTargetEncoding(airlinesTrainWithTEH, teColumns, targetColumnName, encodingMap, TargetEncoder.HoldoutType.KFold, foldColumnName, true);

    printOutFrameAsTable(trainEncoded, true);

    // Applying encoding to the valid set
    Frame validEncoded = tec.applyTargetEncoding(airlinesValid, teColumns, targetColumnName, encodingMap, TargetEncoder.HoldoutType.None, foldColumnName, true, 0, 1234.0);
    validEncoded = tec.ensureTargetColumnIsNumericOrBinaryCategorical(validEncoded, 10);

    // Applying encoding to the test set
    Frame testEncoded = tec.applyTargetEncoding(airlinesTestFrame, teColumns, targetColumnName, encodingMap, TargetEncoder.HoldoutType.None, foldColumnName,true, 0, 1234.0);
    testEncoded = tec.ensureTargetColumnIsNumericOrBinaryCategorical(testEncoded, 10);


    printOutColumnsMeta(testEncoded);
    printOutFrameAsTable(trainEncoded, false);

    // With target encoded columns

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
    parms._ignored_columns = new String[]{"IsDepDelayed_REC", "Origin", "Dest", foldColumnName};
    GBM job = new GBM(parms);
    GBMModel gbm = job.trainModel().get();

    Assert.assertTrue(job.isStopped());

    Frame preds = gbm.score(testEncoded);
    printOutFrameAsTable(preds, false);
    hex.ModelMetricsBinomial mm = ModelMetricsBinomial.make(preds.vec(2), testEncoded.vec(parms._response_column));
    double auc = mm._auc._auc;


    // Without target encoding
    double auc2 = trainDefaultGBM(targetColumnName, tec);

    System.out.println("AUC with encoding:" + auc);
    System.out.println("AUC without encoding:" + auc2);

    Assert.assertTrue(auc2 < auc);
  }

  @Test
  public void noneHoldoutTypeTest() {

    Frame airlinesTrainWithoutTEH = parse_test_file(Key.make("airlines_train"), "smalldata/airlines/target_encoding/airlines_train_without_teh.csv");
    Frame airlinesTEHoldout = parse_test_file(Key.make("airlines_te_holdout"), "smalldata/airlines/target_encoding/airlines_te_holdout.csv");
    Frame airlinesValid = parse_test_file(Key.make("airlines_valid"), "smalldata/airlines/target_encoding/airlines_valid.csv");
    Frame airlinesTestFrame = parse_test_file(Key.make("airlines_test"), "smalldata/airlines/AirlinesTest.csv.zip");

    TargetEncoder tec = new TargetEncoder();
    String[] teColumns = {"Origin", "Dest"};
    String targetColumnName = "IsDepDelayed";

    // Create encoding
    Map<String, Frame> encodingMap = tec.prepareEncodingMap(airlinesTEHoldout, teColumns, targetColumnName, null);

    printOutFrameAsTable(encodingMap.get("Origin"), true);
    printOutFrameAsTable(encodingMap.get("Dest"), true);
    // Apply encoding to the training set
    Frame trainEncoded = tec.applyTargetEncoding(airlinesTrainWithoutTEH, teColumns, targetColumnName, encodingMap, TargetEncoder.HoldoutType.None, true, 0, 1234.0);

    // Applying encoding to the valid set
    Frame validEncoded = tec.applyTargetEncoding(airlinesValid, teColumns, targetColumnName, encodingMap, TargetEncoder.HoldoutType.None,true, 0, 1234.0);
    validEncoded = tec.ensureTargetColumnIsNumericOrBinaryCategorical(validEncoded, 10);

    // Applying encoding to the test set
    Frame testEncoded = tec.applyTargetEncoding(airlinesTestFrame, teColumns, targetColumnName, encodingMap, TargetEncoder.HoldoutType.None,true, 0, 1234.0);
    testEncoded = tec.ensureTargetColumnIsNumericOrBinaryCategorical(testEncoded, 10);

    // With target encoded  columns

    tec.checkNumRows(airlinesTrainWithoutTEH, trainEncoded);
    tec.checkNumRows(airlinesValid, validEncoded);
    tec.checkNumRows(airlinesTestFrame, testEncoded);

    GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
    parms._train = trainEncoded._key;
    parms._response_column = "IsDepDelayed";
    parms._score_tree_interval = 10;
    parms._ntrees = 1000;
    parms._max_depth = 5;
    parms._distribution = DistributionFamily.quasibinomial;
    parms._valid = validEncoded._key;
    parms._stopping_tolerance = 0.001;
    parms._stopping_metric = ScoreKeeper.StoppingMetric.AUC;
    parms._stopping_rounds = 5;
    parms._ignored_columns = new String[]{"IsDepDelayed_REC", "Origin", "Dest"};
    GBM job = new GBM(parms);
    GBMModel gbm = job.trainModel().get();

    Assert.assertTrue(job.isStopped());

    Frame preds = gbm.score(testEncoded);
    hex.ModelMetricsBinomial mm = ModelMetricsBinomial.make(preds.vec(2), testEncoded.vec(parms._response_column));
    double auc = mm._auc._auc;

    // Without target encoded Origin column
    double auc2 = trainDefaultGBM(targetColumnName, tec);

    System.out.println("AUC with encoding:" + auc);
    System.out.println("AUC without encoding:" + auc2);

    Assert.assertTrue(auc2 < auc);
  }

  private double trainDefaultGBM(String targetColumnName, TargetEncoder tec) {
    GBMModel gbm2 = null;
    Scope.enter();
    try {
      Frame airlinesTrainWithTEHDefault = parse_test_file(Key.make("airlines_train_d"), "smalldata/airlines/target_encoding/airlines_train_with_teh.csv");
      Frame airlinesValidDefault = parse_test_file(Key.make("airlines_valid_d"), "smalldata/airlines/target_encoding/airlines_valid.csv");
      Frame airlinesTestFrameDefault = parse_test_file(Key.make("airlines_test_d"), "smalldata/airlines/AirlinesTest.csv.zip");

      airlinesTrainWithTEHDefault = tec.ensureTargetColumnIsNumericOrBinaryCategorical(airlinesTrainWithTEHDefault, 10);
      airlinesValidDefault = tec.ensureTargetColumnIsNumericOrBinaryCategorical(airlinesValidDefault, 10);
      airlinesTestFrameDefault = tec.ensureTargetColumnIsNumericOrBinaryCategorical(airlinesTestFrameDefault, 10);

      printOutFrameAsTable(airlinesTrainWithTEHDefault, true);

      GBMModel.GBMParameters parms2 = new GBMModel.GBMParameters();
      parms2._train = airlinesTrainWithTEHDefault._key;
      parms2._response_column = "IsDepDelayed";
      parms2._score_tree_interval = 10;
      parms2._ntrees = 1000;
      parms2._max_depth = 5;
      parms2._distribution = DistributionFamily.quasibinomial;
      parms2._valid = airlinesValidDefault._key;
      parms2._stopping_tolerance = 0.001;
      parms2._stopping_metric = ScoreKeeper.StoppingMetric.AUC;
      parms2._stopping_rounds = 5;
      parms2._ignored_columns = new String[]{"IsDepDelayed_REC"};
      GBM job2 = new GBM(parms2);
      gbm2 = job2.trainModel().get();

      Assert.assertTrue(job2.isStopped());

      Frame preds2 = gbm2.score(airlinesTestFrameDefault);

      hex.ModelMetricsBinomial mm2 = ModelMetricsBinomial.make(preds2.vec(2), airlinesTestFrameDefault.vec(parms2._response_column));
      double auc2 = mm2._auc._auc;
      return auc2;
    } finally {
      if( gbm2 != null ) {
        gbm2.delete();
        gbm2.deleteCrossValidationModels();
      }
      Scope.exit();
    }
  }

  @After
  public void afterEach() {
    System.out.println("After each test we do H2O.STORE.clear() and Vec.ESPC.clear()");
    Vec.ESPC.clear();
    H2O.STORE.clear();
  }

  private void printOutFrameAsTable(Frame fr) {
    printOutFrameAsTable(fr, false);
  }

  private void printOutFrameAsTable(Frame fr, boolean full) {

    TwoDimTable twoDimTable = fr.toTwoDimTable(0, (int)fr.numRows(), false);
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
