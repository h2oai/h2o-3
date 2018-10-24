package ai.h2o.automl.targetencoding;

import hex.ModelMetricsBinomial;
import hex.ScoreKeeper;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.*;
import water.Key;
import water.TestUtil;
import water.Scope;
import water.fvec.Frame;

import java.util.Map;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;

/*
  Be aware that `smalldata/gbm_test/titanic_*.csv` files are not present in the repo. Replace with your own splits.
 */
public class TargetEncodingTitanicBenchmark extends TestUtil {


  @BeforeClass public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void KFoldHoldoutTypeTest() {
    Scope.enter();
    GBMModel gbm = null;
    try {

      BlendingParams params = new BlendingParams(3, 1);
      String targetColumnName = "survived";
      String[] teColumns = {"cabin", "home.dest", "embarked"};
      String foldColumnName = "fold";
      String[] teColumnsWithFold = {"cabin", "home.dest", "embarked", foldColumnName};

      TargetEncoder tec = new TargetEncoder(teColumns, params);

      Frame trainFrame = parse_test_file(Key.make("titanic_train_parsed"), "smalldata/gbm_test/titanic_train.csv");
      Frame validFrame = parse_test_file(Key.make("titanic_valid_parsed"), "smalldata/gbm_test/titanic_valid.csv");
      Frame testFrame = parse_test_file(Key.make("titanic_test_parsed"), "smalldata/gbm_test/titanic_test.csv");
      asFactor(trainFrame, targetColumnName);
      asFactor(validFrame, targetColumnName);
      asFactor(testFrame, targetColumnName);
      printOutColumnsMetadata(testFrame);

      Scope.track(trainFrame, validFrame, testFrame);

      addKFoldColumn(trainFrame, foldColumnName, 5, 1234L);

      trainFrame.remove(new String[]{"name", "ticket", "boat", "body"});
      validFrame.remove(new String[]{"name", "ticket", "boat", "body"});
      testFrame.remove(new String[]{"name", "ticket", "boat", "body"});

      boolean withBlendedAvg = true;
      boolean withNoiseOnlyForTraining = true;
      boolean withImputationForNAsInOriginalColumns = true;


      Map<String, Frame> encodingMap = tec.prepareEncodingMap(trainFrame, targetColumnName, foldColumnName, withImputationForNAsInOriginalColumns);

      Frame trainEncoded;
      if (withNoiseOnlyForTraining) {
        trainEncoded = tec.applyTargetEncoding(trainFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.KFold, foldColumnName, withBlendedAvg, withImputationForNAsInOriginalColumns,1234, true);
      } else {
        trainEncoded = tec.applyTargetEncoding(trainFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.KFold, foldColumnName, withBlendedAvg, 0.0, withImputationForNAsInOriginalColumns,1234, true);
      }

      // Preparing valid frame
      Frame validEncoded = tec.applyTargetEncoding(validFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnName, withBlendedAvg,0.0, withImputationForNAsInOriginalColumns, 1234, true);

      // Preparing test frame
      Frame testEncoded = tec.applyTargetEncoding(testFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnName,withBlendedAvg, 0.0, withImputationForNAsInOriginalColumns,1234, false);

      Scope.track(trainEncoded, validEncoded, testEncoded);
      printOutColumnsMetadata(trainEncoded);

      // With target encoded Origin column
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = trainEncoded._key;
      parms._response_column = targetColumnName;
      parms._score_tree_interval = 10;
      parms._ntrees = 1000;
      parms._max_depth = 5;
      parms._distribution = DistributionFamily.multinomial;
      parms._valid = validEncoded._key;
      parms._stopping_tolerance = 0.001;
      parms._stopping_metric = ScoreKeeper.StoppingMetric.AUC;
      parms._stopping_rounds = 5;
      parms._ignored_columns = teColumnsWithFold;
      parms._seed = 1234L;
      GBM job = new GBM(parms);
      gbm = job.trainModel().get();

      System.out.println(gbm._output._variable_importances.toString(2, true));
      Assert.assertTrue(job.isStopped());

      Frame preds = gbm.score(testEncoded);
      Scope.track(preds);

      hex.ModelMetricsBinomial mm = ModelMetricsBinomial.make(preds.vec(2), testEncoded.vec(parms._response_column));
      double auc = mm._auc._auc;

      // Without target encoding
      double auc2 = trainDefaultGBM(targetColumnName);

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
      BlendingParams params = new BlendingParams(3, 1);
//      BlendingParams params = new BlendingParams(20, 10);
      String[] teColumns = {"cabin", "embarked", "home.dest"};
      String targetColumnName = "survived";

      TargetEncoder tec = new TargetEncoder(teColumns, params);

      Frame trainFrame = parse_test_file(Key.make("titanic_train_parsed"), "smalldata/gbm_test/titanic_train.csv");
      Frame validFrame = parse_test_file(Key.make("titanic_valid_parsed"), "smalldata/gbm_test/titanic_valid.csv");
      Frame testFrame = parse_test_file(Key.make("titanic_test_parsed"), "smalldata/gbm_test/titanic_test.csv");
      asFactor(trainFrame, targetColumnName);
      asFactor(validFrame, targetColumnName);
      asFactor(testFrame, targetColumnName);

      Scope.track(trainFrame, validFrame, testFrame);

      trainFrame.remove(new String[]{"name", "ticket", "boat", "body"});
      validFrame.remove(new String[]{"name", "ticket", "boat", "body"});
      testFrame.remove(new String[]{"name", "ticket", "boat", "body"});

      boolean withBlendedAvg = true;
      boolean withBlendedAvgOnlyForTraining = false;
      boolean withNoiseOnlyForTraining = true;
      boolean withImputationForNAsInOriginalColumns = true;

      Map<String, Frame> encodingMap = tec.prepareEncodingMap(trainFrame, targetColumnName, null, withImputationForNAsInOriginalColumns);

      Frame trainEncoded;
      if (withNoiseOnlyForTraining) {
        trainEncoded = tec.applyTargetEncoding(trainFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, withBlendedAvg, withImputationForNAsInOriginalColumns, 1234, true);
      } else {
        trainEncoded = tec.applyTargetEncoding(trainFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, withBlendedAvg,  0, withImputationForNAsInOriginalColumns,1234, true);
      }

      Frame validEncoded = tec.applyTargetEncoding(validFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg && !withBlendedAvgOnlyForTraining, 0, withImputationForNAsInOriginalColumns,1234, true);

      Frame testEncoded = tec.applyTargetEncoding(testFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg && !withBlendedAvgOnlyForTraining, 0, withImputationForNAsInOriginalColumns,1234, false);

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
      parms._distribution = DistributionFamily.AUTO;
      parms._valid = validEncoded._key;
      parms._stopping_tolerance = 0.001;
      parms._stopping_metric = ScoreKeeper.StoppingMetric.AUC;
      parms._stopping_rounds = 5;
      parms._ignored_columns = teColumns;
      parms._seed = 1234L;
      GBM job = new GBM(parms);
      gbm = job.trainModel().get();

      Assert.assertTrue(job.isStopped());

      System.out.println(gbm._output._variable_importances.toString(2, true));

      Frame preds = gbm.score(testEncoded);
      Scope.track(preds);

      hex.ModelMetricsBinomial mm = ModelMetricsBinomial.make(preds.vec(2), testEncoded.vec(parms._response_column));
      double auc = mm._auc._auc;

      // Without target encoding
      double auc2 = trainDefaultGBM(targetColumnName);
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

      BlendingParams params = new BlendingParams(3, 1); //k = 3, f = 1 AUC=0.8664  instead of for k = 20, f = 10 -> AUC=0.8523
      String[] teColumns = {"cabin", "embarked", "home.dest"};
      String targetColumnName = "survived";

      TargetEncoder tec = new TargetEncoder(teColumns, params);

      Frame trainFrame = parse_test_file(Key.make("titanic_train_parsed"), "smalldata/gbm_test/titanic_train_wteh.csv");
      Frame teHoldoutFrame = parse_test_file(Key.make("titanic_te_holdout_parsed"), "smalldata/gbm_test/titanic_te_holdout.csv");
      Frame validFrame = parse_test_file(Key.make("titanic_valid_parsed"), "smalldata/gbm_test/titanic_valid.csv");
      Frame testFrame = parse_test_file(Key.make("titanic_test_parsed"), "smalldata/gbm_test/titanic_test.csv");
      asFactor(trainFrame, targetColumnName);
      asFactor(teHoldoutFrame, targetColumnName);
      asFactor(validFrame, targetColumnName);
      asFactor(testFrame, targetColumnName);

      Scope.track(trainFrame, teHoldoutFrame, validFrame, testFrame);

      trainFrame.remove(new String[]{"name", "ticket", "boat", "body"});
      teHoldoutFrame.remove(new String[]{"name", "ticket", "boat", "body"});
      validFrame.remove(new String[]{"name", "ticket", "boat", "body"});
      testFrame.remove(new String[]{"name", "ticket", "boat", "body"});

      // TODO we need to make it automatically just in case if user will try to load frames from separate sources like we did here
      Frame teHoldoutFrameFactorized = asFactor(teHoldoutFrame, "cabin");
      Scope.track(teHoldoutFrameFactorized);

      boolean withNoiseOnlyForTraining = true;
      boolean withImputation = true;

      Map<String, Frame> encodingMap = tec.prepareEncodingMap(teHoldoutFrameFactorized, targetColumnName, null, withImputation);

      Frame trainEncoded;
      if (withNoiseOnlyForTraining) {
        trainEncoded = tec.applyTargetEncoding(trainFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, true, withImputation, 1234, true);
      } else {
        trainEncoded = tec.applyTargetEncoding(trainFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, true, 0.0, withImputation, 1234, true);
      }

      Frame validEncoded = tec.applyTargetEncoding(validFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, true, 0.0, withImputation, 1234, true);

      Frame testEncoded = tec.applyTargetEncoding(testFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, true, 0.0, withImputation, 1234, false);

      Scope.track(trainEncoded, validEncoded, testEncoded);

      // With target encoded Origin column
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = trainEncoded._key;
      parms._response_column = targetColumnName;
      parms._score_tree_interval = 10;
      parms._ntrees = 1000;
      parms._max_depth = 5;
      parms._distribution = DistributionFamily.AUTO;
      parms._valid = validEncoded._key;
      parms._stopping_tolerance = 0.001;
      parms._stopping_metric = ScoreKeeper.StoppingMetric.AUC;
      parms._stopping_rounds = 5;
      parms._ignored_columns = teColumns;
      parms._seed = 1234L;
      GBM job = new GBM(parms);
      GBMModel gbm = job.trainModel().get();

      Assert.assertTrue(job.isStopped());

      System.out.println(gbm._output._variable_importances.toString(2, true));

      Frame preds = gbm.score(testEncoded);
      Scope.track(preds);

      hex.ModelMetricsBinomial mm = ModelMetricsBinomial.make(preds.vec(2), testEncoded.vec(parms._response_column));
      double auc = mm._auc._auc;

      // Without target encoding
      double auc2 = trainDefaultGBM(targetColumnName);

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


  private double trainDefaultGBM(String targetColumnName) {
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
      parms2._seed = 1234L;

      GBM job2 = new GBM(parms2);
      gbm2 = job2.trainModel().get();

      Assert.assertTrue(job2.isStopped());

      Frame preds2 = gbm2.score(testFrame2);
      Scope.track(preds2);
      printOutFrameAsTable(preds2, false, preds2.numRows());
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
}