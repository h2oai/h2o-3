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

    Frame airlinesTrainFrame = parse_test_file(Key.make("airlines_parsed"), "smalldata/airlines/AirlinesTrain.csv.zip");
    Frame airlinesTestFrame = parse_test_file(Key.make("airlines_test_parsed"), "smalldata/airlines/AirlinesTest.csv.zip");

    // Adding fold column to train and test frames
    String foldColumnName = "fold";
    FrameUtils.addKFoldColumn(airlinesTrainFrame, foldColumnName, 5, 1234L);
    FrameUtils.addKFoldColumn(airlinesTestFrame, foldColumnName, 5, 1234L);


    //Split training into training and validation sets
    double[] ratios = ard(0.8f);
    Frame[] splits = null;
    FrameSplitter fs = new FrameSplitter(airlinesTrainFrame, ratios, generateNumKeys(airlinesTrainFrame._key, ratios.length + 1), null);
    H2O.submitTask(fs).join();
    splits = fs.getResult();
    Frame train = splits[0];
    Frame valid = splits[1];

    TargetEncoder tec = new TargetEncoder();
    String[] teColumns = {"Origin", "Dest"};
    String targetColumnName = "IsDepDelayed";

    // Create encoding
    Map<String, Frame> encodingMap = tec.prepareEncodingMap(train, teColumns, targetColumnName, foldColumnName);

    // Apply encoding to the training set
    Frame trainEncoded = tec.applyTargetEncoding(train, teColumns, targetColumnName, encodingMap, TargetEncoder.HoldoutType.KFold, foldColumnName, false, 0, 1234.0);

    printOutFrameAsTable(trainEncoded, true);

    // Applying encoding to the valid set
    Frame validEncoded = tec.applyTargetEncoding(valid, teColumns, targetColumnName, encodingMap, TargetEncoder.HoldoutType.None, false, 0, 1234.0);
    validEncoded = tec.ensureTargetColumnIsNumericOrBinaryCategorical(validEncoded, 10);

    // Applying encoding to the test set
    Frame testEncoded = tec.applyTargetEncoding(airlinesTestFrame, teColumns, targetColumnName, encodingMap, TargetEncoder.HoldoutType.None, false, 0, 1234.0);
    testEncoded = tec.ensureTargetColumnIsNumericOrBinaryCategorical(testEncoded, 10);


    printOutColumnsMeta(testEncoded);
    printOutFrameAsTable(trainEncoded, false);

    // With target encoded columns

    GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
    parms._train = trainEncoded._key;
    parms._response_column = targetColumnName;
    parms._ntrees = 10;
    parms._max_depth = 3;
    parms._distribution = DistributionFamily.quasibinomial;
    parms._fold_column = foldColumnName;
    parms._valid = validEncoded._key;
    parms._stopping_tolerance = 0.001;
    parms._stopping_metric = ScoreKeeper.StoppingMetric.AUC;
    parms._stopping_rounds = 5;
    parms._ignored_columns = new String[]{"IsDepDelayed_REC", "Origin", "Dest"};
    GBM job = new GBM(parms);
    GBMModel gbm = job.trainModel().get();

    Assert.assertTrue(job.isStopped());

    Frame preds = gbm.score(testEncoded);
    printOutFrameAsTable(preds, false);
    hex.ModelMetricsBinomial mm = ModelMetricsBinomial.make(preds.vec(2), testEncoded.vec(parms._response_column));
    double auc = mm._auc._auc;


    // Without target encoding

    Frame airlinesTrainFrame2 = parse_test_file(Key.make("airlines_parsed2"), "smalldata/airlines/AirlinesTrain.csv.zip");
    Frame airlinesTestFrame2 = parse_test_file(Key.make("airlines_test_parsed2"), "smalldata/airlines/AirlinesTest.csv.zip");
    FrameUtils.addKFoldColumn(airlinesTrainFrame2, foldColumnName, 5, 1234L);
    FrameUtils.addKFoldColumn(airlinesTestFrame2, foldColumnName, 5, 1234L);

    printOutFrameAsTable(airlinesTrainFrame2, false);
    airlinesTrainFrame2 = tec.ensureTargetColumnIsNumericOrBinaryCategorical(airlinesTrainFrame2, 10);

    double[] ratios2 = ard(0.8f);
    Frame[] splits2 = null;
    FrameSplitter fs2 = new FrameSplitter(airlinesTrainFrame2, ratios2, generateNumKeys(airlinesTrainFrame._key, ratios.length + 1), null);
    H2O.submitTask(fs2).join();
    splits2 = fs2.getResult();
    Frame train2 = splits2[0];
    Frame valid2 = splits2[1];

    train2 = tec.ensureTargetColumnIsNumericOrBinaryCategorical(train2, 10);
    valid2 = tec.ensureTargetColumnIsNumericOrBinaryCategorical(valid2, 10);

    GBMModel.GBMParameters parms2 = new GBMModel.GBMParameters();
    parms2._train = train2._key;
    parms2._response_column = targetColumnName;
    parms2._ntrees = 10;
    parms2._max_depth = 3;
    parms2._fold_column = foldColumnName;
    parms2._distribution = DistributionFamily.quasibinomial;
    parms2._valid = valid2._key;
    parms._stopping_tolerance = 0.001;
    parms._stopping_metric = ScoreKeeper.StoppingMetric.AUC;
    parms._stopping_rounds = 5;
    parms2._ignored_columns = new String[]{"IsDepDelayed_REC"};
    GBM job2 = new GBM(parms2);
    GBMModel gbm2 = job2.trainModel().get();

    Assert.assertTrue(job2.isStopped());

    airlinesTestFrame2 = tec.ensureTargetColumnIsNumericOrBinaryCategorical(airlinesTestFrame2, 10); // TODO we  need here pseudobinary numerical(quasibinomial).

    Frame preds2 = gbm2.score(airlinesTestFrame2);

    printOutFrameAsTable(preds2, false);

    hex.ModelMetricsBinomial mm2 = ModelMetricsBinomial.make(preds2.vec(2), airlinesTestFrame2.vec(parms2._response_column));
    double auc2 = mm2._auc._auc;

    System.out.println("AUC with encoding:" + auc);
    System.out.println("AUC without encoding:" + auc2);

    Assert.assertTrue(auc2 < auc);
  }

  @Test
  public void noneHoldoutTypeTest() {

    Frame airlinesTrainFrame = parse_test_file(Key.make("airlines_parsed"), "smalldata/airlines/AirlinesTrain.csv.zip");
    Frame airlinesTestFrame = parse_test_file(Key.make("airlines_test_parsed"), "smalldata/airlines/AirlinesTest.csv.zip");

    //Split training into training and validation sets
    double[] ratios = ard(0.8f);
    Frame[] splits = null;
    FrameSplitter fs = new FrameSplitter(airlinesTrainFrame, ratios, generateNumKeys(airlinesTrainFrame._key, ratios.length + 1), null);
    H2O.submitTask(fs).join();
    splits = fs.getResult();
    Frame train = splits[0];
    Frame valid = splits[1];

    TargetEncoder tec = new TargetEncoder();
    int[] teColumns = {7, 8}; // 7 stands for Origin column

    // Create encoding
    Map<String, Frame> encodingMap = tec.prepareEncodingMap(train, teColumns, 10); // 10 stands for IsDepDelayed column

    // Apply encoding to the training set
    Frame trainEncoded = tec.applyTargetEncoding(train, teColumns, 10, encodingMap, TargetEncoder.HoldoutType.None, false, 0, 1234.0);

    printOutFrameAsTable(trainEncoded, true);

    // Applying encoding to the valid set
    Frame validEncoded = tec.applyTargetEncoding(valid, teColumns, 10, encodingMap, TargetEncoder.HoldoutType.None, false, 0, 1234.0);
    validEncoded = tec.ensureTargetColumnIsNumericOrBinaryCategorical(validEncoded, 10);

    // Applying encoding to the test set
    Frame testEncoded = tec.applyTargetEncoding(airlinesTestFrame, teColumns, 10, encodingMap, TargetEncoder.HoldoutType.None, false, 0, 1234.0);
    testEncoded = tec.ensureTargetColumnIsNumericOrBinaryCategorical(testEncoded, 10);


    printOutColumnsMeta(testEncoded);
    printOutFrameAsTable(trainEncoded);

    // With target encoded Origin column

    GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
    parms._train = trainEncoded._key;
    parms._response_column = "IsDepDelayed";
    parms._ntrees = 10;
    parms._max_depth = 3;
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
    printOutFrameAsTable(preds);
    hex.ModelMetricsBinomial mm = ModelMetricsBinomial.make(preds.vec(2), testEncoded.vec(parms._response_column));
    double auc = mm._auc._auc;

    // Without target encoded Origin column

    Frame airlinesTrainFrame2 = parse_test_file(Key.make("airlines_parsed2"), "smalldata/airlines/AirlinesTrain.csv.zip");
    Frame airlinesTestFrame2 = parse_test_file(Key.make("airlines_test_parsed2"), "smalldata/airlines/AirlinesTest.csv.zip");

    printOutFrameAsTable(airlinesTrainFrame2);
    airlinesTrainFrame2 = tec.ensureTargetColumnIsNumericOrBinaryCategorical(airlinesTrainFrame2, 10);

    double[] ratios2 = ard(0.8f);
    Frame[] splits2 = null;
    FrameSplitter fs2 = new FrameSplitter(airlinesTrainFrame2, ratios2, generateNumKeys(airlinesTrainFrame._key, ratios.length + 1), null);
    H2O.submitTask(fs2).join();
    splits2 = fs2.getResult();
    Frame train2 = splits2[0];
    Frame valid2 = splits2[1];

    train2 = tec.ensureTargetColumnIsNumericOrBinaryCategorical(train2, 10);
    valid2 = tec.ensureTargetColumnIsNumericOrBinaryCategorical(valid2, 10);

    GBMModel.GBMParameters parms2 = new GBMModel.GBMParameters();
    parms2._train = train2._key;
    parms2._response_column = "IsDepDelayed";
    parms2._ntrees = 10;
    parms2._max_depth = 3;
    parms2._distribution = DistributionFamily.quasibinomial;
    parms2._valid = valid2._key;
    parms2._stopping_tolerance = 0.001;
    parms2._stopping_metric = ScoreKeeper.StoppingMetric.AUC;
    parms2._stopping_rounds = 5;
    parms2._ignored_columns = new String[]{"IsDepDelayed_REC"};
    GBM job2 = new GBM(parms2);
    GBMModel gbm2 = job2.trainModel().get();

    Assert.assertTrue(job2.isStopped());

    airlinesTestFrame2 = tec.ensureTargetColumnIsNumericOrBinaryCategorical(airlinesTestFrame2, 10); // TODO we  need here pseudobinary numerical(quasibinomial).

    Frame preds2 = gbm2.score(airlinesTestFrame2);

    Assert.assertTrue(gbm2.testJavaScoring(airlinesTestFrame2, preds2, 1e-6));
    printOutFrameAsTable(preds2);
    hex.ModelMetricsBinomial mm2 = ModelMetricsBinomial.make(preds2.vec(2), airlinesTestFrame2.vec(parms2._response_column));
    double auc2 = mm2._auc._auc;

    System.out.println("AUC with encoding:" + auc);
    System.out.println("AUC without encoding:" + auc2);

    Assert.assertTrue(auc2 < auc);
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

    TwoDimTable twoDimTable = fr.toTwoDimTable(0, 100, false);
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
