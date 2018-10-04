package ai.h2o.automl.targetencoding;

import ai.h2o.automl.TestUtil;
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
import water.fvec.Frame;
import water.util.FrameUtils;
import water.util.TwoDimTable;

import java.util.Map;

public class TargetEncodingTitanicBenchmarkTest extends TestUtil {


  @BeforeClass public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void KFoldHoldoutTypeTest() {
    Scope.enter();
    GBMModel gbm = null;
    try {

      BlendingParams params = new BlendingParams(3, 1);
      TargetEncoder tec = new TargetEncoder(params);

      //TODO remove following files from repo later.
      Frame trainFrame = parse_test_file(Key.make("titanic_train_parsed"), "h2o-automl/src/test/resources/ai.h2o.automl/targetencoding/titanic_train.csv");
      Frame validFrame = parse_test_file(Key.make("titanic_valid_parsed"), "h2o-automl/src/test/resources/ai.h2o.automl/targetencoding/titanic_valid.csv");
      Frame testFrame = parse_test_file(Key.make("titanic_test_parsed"), "h2o-automl/src/test/resources/ai.h2o.automl/targetencoding/titanic_test.csv");

      Scope.track(trainFrame, validFrame, testFrame);

      String foldColumnName = "fold";
      long seed = 8765;
      FrameUtils.addKFoldColumn(trainFrame, foldColumnName, 5, seed);

      trainFrame.remove(new String[]{"name", "ticket", "boat", "body"});
      validFrame.remove(new String[]{"name", "ticket", "boat", "body"});
      testFrame.remove(new String[]{"name", "ticket", "boat", "body"});

      String targetColumnName = "survived";

      String[] teColumns = {"cabin", "home.dest", "embarked"};
      String[] teColumnsWithFold = {"cabin", "home.dest", "embarked", foldColumnName};

      boolean withBlendedAvg = true;
      boolean withNoiseOnlyForTraining = true;
      boolean withImputationForNAsInOriginalColumns = true;


      Map<String, Frame> encodingMap = tec.prepareEncodingMap(trainFrame, teColumns, targetColumnName, foldColumnName, withImputationForNAsInOriginalColumns);

      Frame trainEncoded;
      if (withNoiseOnlyForTraining) {
        trainEncoded = tec.applyTargetEncoding(trainFrame, teColumns, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.KFold, foldColumnName, withBlendedAvg, withImputationForNAsInOriginalColumns, seed, true);
      } else {
        trainEncoded = tec.applyTargetEncoding(trainFrame, teColumns, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.KFold, foldColumnName, withBlendedAvg, 0.0, withImputationForNAsInOriginalColumns, seed, true);
      }

      Frame validEncoded = tec.applyTargetEncoding(validFrame, teColumns, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnName, withBlendedAvg,0.0, withImputationForNAsInOriginalColumns, seed, true);

      Frame testEncoded = tec.applyTargetEncoding(testFrame, teColumns, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnName,withBlendedAvg, 0.0, withImputationForNAsInOriginalColumns, seed, false);

      Scope.track(trainEncoded, validEncoded, testEncoded);

      printOutFrameAsTable(trainEncoded, true, false);

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
      parms._seed = seed;
      GBM job = new GBM(parms);
      gbm = job.trainModel().get();

      System.out.println(gbm._output._variable_importances.toString(2, true));
      Assert.assertTrue(job.isStopped());

      Frame preds = gbm.score(testEncoded);
      Scope.track(preds);

      hex.ModelMetricsBinomial mm = ModelMetricsBinomial.make(preds.vec(2), testEncoded.vec(parms._response_column));
      double auc = mm._auc._auc;

      // Without target encoding
      double auc2 = trainDefaultGBM(targetColumnName, seed);

      System.out.println("AUC with encoding:" + auc);
      System.out.println("AUC without encoding:" + auc2);

      Assert.assertTrue(auc2 < auc);
      encodingMapCleanUp(encodingMap);
    } finally {
      if( gbm != null ) {
        gbm.delete();
        gbm.deleteCrossValidationModels();
      }
      Scope.exit();
    }
  }

  @Test
  public void leaveOneOutHoldoutTypeTest() {
    GBMModel gbm = null;
    Scope.enter();
    try {
      long seed = 1234L;

      BlendingParams params = new BlendingParams(3, 1);
//      BlendingParams params = new BlendingParams(20, 10);
      TargetEncoder tec = new TargetEncoder(params);

      Frame trainFrame = parse_test_file(Key.make("titanic_train_parsed"), "smalldata/gbm_test/titanic_train.csv");
      Frame validFrame = parse_test_file(Key.make("titanic_valid_parsed"), "smalldata/gbm_test/titanic_valid.csv");
      Frame testFrame = parse_test_file(Key.make("titanic_test_parsed"), "smalldata/gbm_test/titanic_test.csv");

      Scope.track(trainFrame, validFrame, testFrame);

      trainFrame.remove(new String[]{"name", "ticket", "boat", "body"});
      validFrame.remove(new String[]{"name", "ticket", "boat", "body"});
      testFrame.remove(new String[]{"name", "ticket", "boat", "body"});

      String[] teColumns = {"cabin", "embarked", "home.dest"};

      boolean withBlendedAvg = true;
      boolean withBlendedAvgOnlyForTraining = false;
      boolean withNoiseOnlyForTraining = true;
      boolean withImputationForNAsInOriginalColumns = true;

      String targetColumnName = "survived";

      Map<String, Frame> encodingMap = tec.prepareEncodingMap(trainFrame, teColumns, targetColumnName, null, withImputationForNAsInOriginalColumns);

      Frame trainEncoded;
      if (withNoiseOnlyForTraining) {
        trainEncoded = tec.applyTargetEncoding(trainFrame, teColumns, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, withBlendedAvg, withImputationForNAsInOriginalColumns, 1234, true);
      } else {
        trainEncoded = tec.applyTargetEncoding(trainFrame, teColumns, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, withBlendedAvg,  0, withImputationForNAsInOriginalColumns,1234, true);
      }

      Frame validEncoded = tec.applyTargetEncoding(validFrame, teColumns, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg && !withBlendedAvgOnlyForTraining, 0, withImputationForNAsInOriginalColumns,1234, true);

      Frame testEncoded = tec.applyTargetEncoding(testFrame, teColumns, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg && !withBlendedAvgOnlyForTraining, 0, withImputationForNAsInOriginalColumns,1234, false);

      Scope.track(trainEncoded, validEncoded, testEncoded);

      // NOTE: when we impute NA with some value before calculation of encoding map... we will end up with good category that has this NA-ness in common.
      // On the other hand for high cardinality domains we will get NAs after merging of original data with encoding map.
      // And those NAs we could only impute with prior average. In this particular dataset it hurts maybe because encodings from NA-category are closer by meaning( if we have not seen some levels on training set then they could be considered rather as unknown/NA than average)


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
      parms._seed = seed;
      GBM job = new GBM(parms);
      gbm = job.trainModel().get();

      Assert.assertTrue(job.isStopped());

      System.out.println(gbm._output._variable_importances.toString(2, true));

      Frame preds = gbm.score(testEncoded);
      Scope.track(preds);

      hex.ModelMetricsBinomial mm = ModelMetricsBinomial.make(preds.vec(2), testEncoded.vec(parms._response_column));
      double auc = mm._auc._auc;

      // Without target encoding
      double auc2 = trainDefaultGBM(targetColumnName, seed);
//
      System.out.println("AUC with encoding:" + auc);
      System.out.println("AUC without encoding:" + auc2);

      Assert.assertTrue(auc2 < auc);

      encodingMapCleanUp(encodingMap);
    } finally {
      if( gbm != null ) {
        gbm.delete();
        gbm.deleteCrossValidationModels();
      }
      Scope.exit();
    }
  }

  @Test
  public void noneHoldoutTypeTest() {
    Scope.enter();
    try {
      long seedForGBM = 1234L;

      BlendingParams params = new BlendingParams(3, 1); //k = 3, f = 1 AUC=0.8664  instead of for k = 20, f = 10 -> AUC=0.8523
      TargetEncoder tec = new TargetEncoder(params);

      Frame trainFrame = parse_test_file(Key.make("titanic_train_parsed"), "smalldata/gbm_test/titanic_train_wteh.csv");
      Frame teHoldoutFrame = parse_test_file(Key.make("titanic_te_holdout_parsed"), "smalldata/gbm_test/titanic_te_holdout.csv");
      Frame validFrame = parse_test_file(Key.make("titanic_valid_parsed"), "smalldata/gbm_test/titanic_valid.csv");
      Frame testFrame = parse_test_file(Key.make("titanic_test_parsed"), "smalldata/gbm_test/titanic_test.csv");

      Scope.track(trainFrame, teHoldoutFrame, validFrame, testFrame);

      trainFrame.remove(new String[]{"name", "ticket", "boat", "body"});
      teHoldoutFrame.remove(new String[]{"name", "ticket", "boat", "body"});
      validFrame.remove(new String[]{"name", "ticket", "boat", "body"});
      testFrame.remove(new String[]{"name", "ticket", "boat", "body"});

      // TODO we need to make it automatically just in case if user will try to load frames from separate sources like we did here
      Frame teHoldoutFrameFactorized = FrameUtils.asFactor(teHoldoutFrame, "cabin");
      Scope.track(teHoldoutFrameFactorized);

      String[] teColumns = {"cabin", "embarked", "home.dest"};

      String targetColumnName = "survived";

      boolean withNoiseOnlyForTraining = true;
      boolean withImputation = true;

      Map<String, Frame> encodingMap = tec.prepareEncodingMap(teHoldoutFrameFactorized, teColumns, targetColumnName, null, withImputation);

      Frame trainEncoded;
      if (withNoiseOnlyForTraining) {
        trainEncoded = tec.applyTargetEncoding(trainFrame, teColumns, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, true, withImputation, 1234, true);
      } else {
        trainEncoded = tec.applyTargetEncoding(trainFrame, teColumns, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, true, 0.0, withImputation, 1234, true);
      }

      Frame validEncoded = tec.applyTargetEncoding(validFrame, teColumns, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, true, 0.0, withImputation, 1234, true);

      Frame testEncoded = tec.applyTargetEncoding(testFrame, teColumns, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, true, 0.0, withImputation, 1234, false);

      Scope.track(trainEncoded, validEncoded, testEncoded);

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
      parms._seed = seedForGBM;
      GBM job = new GBM(parms);
      GBMModel gbm = job.trainModel().get();

      Assert.assertTrue(job.isStopped());

      System.out.println(gbm._output._variable_importances.toString(2, true));

      Frame preds = gbm.score(testEncoded);
      Scope.track(preds);

      hex.ModelMetricsBinomial mm = ModelMetricsBinomial.make(preds.vec(2), testEncoded.vec(parms._response_column));
      double auc = mm._auc._auc;

      // Without target encoding
      double auc2 = trainDefaultGBM(targetColumnName, seedForGBM);

      System.out.println("AUC with encoding:" + auc);
      System.out.println("AUC without encoding:" + auc2);
      Assert.assertTrue(auc2 < auc);

      encodingMapCleanUp(encodingMap);
      if( gbm != null ) {
        gbm.delete();
        gbm.deleteCrossValidationModels();
      }
    } finally {
      Scope.exit();
    }
  }

  private double trainDefaultGBM(String targetColumnName, long seed) {
    GBMModel gbm2 = null;
    Scope.enter();
    try {
      Frame trainFrame2 = parse_test_file(Key.make("titanic_train_parsed"), "smalldata/gbm_test/titanic_train.csv");
      Frame validFrame2 = parse_test_file(Key.make("titanic_valid_parsed2"), "smalldata/gbm_test/titanic_valid.csv");
      Frame testFrame2 = parse_test_file(Key.make("titanic_test_parsed2"), "smalldata/gbm_test/titanic_test.csv");
      Scope.track(trainFrame2, testFrame2, validFrame2);

      trainFrame2.remove(new String[]{"name", "ticket", "boat", "body"});
      validFrame2.remove(new String[]{"name", "ticket", "boat", "body"});
      testFrame2.remove(new String[]{"name", "ticket", "boat", "body"});

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
      parms2._seed = seed;

      GBM job2 = new GBM(parms2);
      gbm2 = job2.trainModel().get();

      Assert.assertTrue(job2.isStopped());

      Frame preds2 = gbm2.score(testFrame2);
      Scope.track(preds2);
      printOutFrameAsTable(preds2, false, false);
      hex.ModelMetricsBinomial mm2 = ModelMetricsBinomial.make(preds2.vec(2), testFrame2.vec(parms2._response_column));
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
  }

  private void encodingMapCleanUp(Map<String, Frame> encodingMap) {
    for( Map.Entry<String, Frame> map : encodingMap.entrySet()) {
      map.getValue().delete();
    }
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