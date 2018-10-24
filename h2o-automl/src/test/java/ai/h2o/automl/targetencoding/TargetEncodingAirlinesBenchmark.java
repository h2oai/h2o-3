package ai.h2o.automl.targetencoding;

import hex.ModelMetricsBinomial;
import hex.ScoreKeeper;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.*;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.util.Log;

import java.util.Arrays;
import java.util.Map;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;

/*
  Be aware that `smalldata/airlines/target_encoding/airlines_*.csv` files are not present in the repo. Replace with your own splits.
 */
public class TargetEncodingAirlinesBenchmark extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void KFoldHoldoutTypeTest() {
    Scope.enter();
    GBMModel gbm = null;
    Map<String, Frame> encodingMap = null;
    try {
      Frame airlinesTrainWithTEH = parse_test_file(Key.make("airlines_train"), "smalldata/airlines/target_encoding/airlines_train_with_teh.csv");
      Frame airlinesValid = parse_test_file(Key.make("airlines_valid"), "smalldata/airlines/target_encoding/airlines_valid.csv");
      Frame airlinesTestFrame = parse_test_file(Key.make("airlines_test"), "smalldata/airlines/target_encoding/airlines_test.csv");
      Scope.track(airlinesTrainWithTEH, airlinesValid, airlinesTestFrame);

      long startTimeEncoding = System.currentTimeMillis();

      String foldColumnName = "fold";
      addKFoldColumn(airlinesTrainWithTEH, foldColumnName, 5, 1234L);

      BlendingParams params = new BlendingParams(5, 1);

      String[] teColumns = {"Origin", "Dest"};
      TargetEncoder tec = new TargetEncoder(teColumns, params);
      String targetColumnName = "IsDepDelayed";

      boolean withBlendedAvg = true;
      boolean withNoiseOnlyForTraining = true;
      boolean withImputationForNAsInOriginalColumns = true;

      // Create encoding
      encodingMap = tec.prepareEncodingMap(airlinesTrainWithTEH, targetColumnName, foldColumnName, true);

      // Apply encoding to the training set
      Frame trainEncoded;
      int seed = 1234;
      int seedForGBM = 1234;

      if (withNoiseOnlyForTraining) {
        trainEncoded = tec.applyTargetEncoding(airlinesTrainWithTEH, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.KFold, foldColumnName, withBlendedAvg, withImputationForNAsInOriginalColumns, seed,  true);
      } else {
        trainEncoded = tec.applyTargetEncoding(airlinesTrainWithTEH, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.KFold, foldColumnName, withBlendedAvg, 0, withImputationForNAsInOriginalColumns, seed, true);
      }

      // Applying encoding to the valid set
      Frame validEncoded = tec.applyTargetEncoding(airlinesValid, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnName, withBlendedAvg, 0, withImputationForNAsInOriginalColumns, seed, true);


      // Applying encoding to the test set
      Frame testEncoded = tec.applyTargetEncoding(airlinesTestFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnName, withBlendedAvg, 0, withImputationForNAsInOriginalColumns, seed, false);
      printOutColumnsMetadata(testEncoded);
      testEncoded = tec.ensureTargetColumnIsBinaryCategorical(testEncoded, targetColumnName);

      Scope.track(trainEncoded, validEncoded, testEncoded);
      //Frame.export(trainEncoded, "airlines_train_kfold_dest_noise_noblend.csv", trainEncoded._key.toString(), true, 1);
      //Frame.export(validEncoded, "airlines_valid_kfold_dest_noise_noblend.csv", validEncoded._key.toString(), true, 1);
      //Frame.export(testEncoded, "airlines_test_kfold_dest_noise_noblend.csv", testEncoded._key.toString(), true, 1);

      long finishTimeEncoding = System.currentTimeMillis();
      System.out.println("Calculation of encodings took: " + (finishTimeEncoding - startTimeEncoding));

      // With target encoded columns
      long startTime = System.currentTimeMillis();

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
      parms._ignored_columns = concat(new String[]{"IsDepDelayed_REC", foldColumnName}, teColumns);
      parms._seed = seedForGBM;

      GBM job = new GBM(parms);
      gbm = job.trainModel().get();

      Assert.assertTrue(job.isStopped());

      long finishTime = System.currentTimeMillis();
      System.out.println("Calculation took: " + (finishTime - startTime));

      Frame preds = gbm.score(testEncoded);
      Scope.track(preds);

      hex.ModelMetricsBinomial mm = ModelMetricsBinomial.make(preds.vec(2), testEncoded.vec(parms._response_column));
      double auc = mm._auc._auc;

      // Without target encoding
      double auc2 = trainDefaultGBM(targetColumnName, tec);

      System.out.println("AUC with encoding:" + auc);
      System.out.println("AUC without encoding:" + auc2);

      Assert.assertTrue(auc2 < auc);
    } finally {
      encodingMapCleanUp(encodingMap);
      if (gbm != null) {
        gbm.delete();
        gbm.deleteCrossValidationModels();
      }
      Scope.exit();
    }
  }

  @Test
  public void noneHoldoutTypeTest() {
    Scope.enter();
    GBMModel gbm;
    try {
      Frame airlinesTrainWithoutTEH = parse_test_file(Key.make("airlines_train"), "smalldata/airlines/target_encoding/airlines_train_without_teh.csv");
      Frame airlinesTEHoldout = parse_test_file(Key.make("airlines_te_holdout"), "smalldata/airlines/target_encoding/airlines_te_holdout.csv");
      Frame airlinesValid = parse_test_file(Key.make("airlines_valid"), "smalldata/airlines/target_encoding/airlines_valid.csv");
      Frame airlinesTestFrame = parse_test_file(Key.make("airlines_test"), "smalldata/airlines/AirlinesTest.csv.zip");
      Scope.track(airlinesTrainWithoutTEH, airlinesTEHoldout, airlinesValid, airlinesTestFrame );

      long startTimeEncoding = System.currentTimeMillis();

      BlendingParams params = new BlendingParams(3, 1);
      String[] teColumns = {"Origin", "Dest"};
      TargetEncoder tec = new TargetEncoder(teColumns, params);
      String targetColumnName = "IsDepDelayed";

      boolean withBlendedAvg = true;
      boolean withImputationForNAsInOriginalColumns = true;

      // Create encoding
      Map<String, Frame> encodingMap = tec.prepareEncodingMap(airlinesTEHoldout, targetColumnName, null);

      // Apply encoding to the training set
      Frame trainEncoded = tec.applyTargetEncoding(airlinesTrainWithoutTEH, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg, 0, withImputationForNAsInOriginalColumns, 1234, true);

      // Applying encoding to the valid set
      Frame validEncoded = tec.applyTargetEncoding(airlinesValid, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg, 0, withImputationForNAsInOriginalColumns,1234, true);

      // Applying encoding to the test set
      Frame testEncoded = tec.applyTargetEncoding(airlinesTestFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg, 0, withImputationForNAsInOriginalColumns, 1234, false);
      //We do it manually just to be able to measure metrics in the end. TargetEncoder should not be aware of target column for test dataset.
      testEncoded = tec.ensureTargetColumnIsBinaryCategorical(testEncoded, targetColumnName);
      Scope.track(trainEncoded, validEncoded, testEncoded);


      long finishTimeEncoding = System.currentTimeMillis();
      System.out.println("Calculation of encodings took: " + (finishTimeEncoding - startTimeEncoding));

      // With target encoded  columns
      checkNumRows(airlinesTrainWithoutTEH, trainEncoded);
      checkNumRows(airlinesValid, validEncoded);
      checkNumRows(airlinesTestFrame, testEncoded);

      long startTime = System.currentTimeMillis();

      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = trainEncoded._key;
      parms._response_column = "IsDepDelayed";
      parms._score_tree_interval = 10;
      parms._ntrees = 1000;
      parms._max_depth = 5;
      parms._distribution = DistributionFamily.AUTO;
      parms._valid = validEncoded._key;
      parms._stopping_tolerance = 0.001;
      parms._stopping_metric = ScoreKeeper.StoppingMetric.AUC;
      parms._stopping_rounds = 5;
      parms._ignored_columns = concat(new String[]{"IsDepDelayed_REC"}, teColumns);
      parms._seed = 1234L;

      GBM job = new GBM(parms);
      gbm = job.trainModel().get();

      Assert.assertTrue(job.isStopped());

      long finishTime = System.currentTimeMillis();
      System.out.println("Calculation took: " + (finishTime - startTime));

      Frame preds = gbm.score(testEncoded);
      Scope.track(preds);

      hex.ModelMetricsBinomial mm = ModelMetricsBinomial.make(preds.vec(2), testEncoded.vec(parms._response_column));
      double auc = mm._auc._auc;

      // Without target encoded Origin column
      double auc2 = trainDefaultGBM(targetColumnName, tec);

      System.out.println("AUC with encoding:" + auc);
      System.out.println("AUC without encoding:" + auc2);

      encodingMapCleanUp(encodingMap);
      if (gbm != null) {
        gbm.delete();
        gbm.deleteCrossValidationModels();
      }

      Assert.assertTrue(auc2 < auc);

    } finally {
      Scope.exit();
    }
  }

  private double trainDefaultGBM(String targetColumnName, TargetEncoder tec) {
    GBMModel gbm2 = null;
    Scope.enter();
    try {
      Frame airlinesTrainWithTEHDefault = parse_test_file(Key.make("airlines_train_d"), "smalldata/airlines/target_encoding/airlines_train_with_teh.csv");
      Frame airlinesValidDefault = parse_test_file(Key.make("airlines_valid_d"), "smalldata/airlines/target_encoding/airlines_valid.csv");
      Frame airlinesTestFrameDefault = parse_test_file(Key.make("airlines_test_d"), "smalldata/airlines/AirlinesTest.csv.zip");

      Scope.track(airlinesTrainWithTEHDefault, airlinesValidDefault, airlinesTestFrameDefault);

      airlinesTrainWithTEHDefault = tec.ensureTargetColumnIsBinaryCategorical(airlinesTrainWithTEHDefault, targetColumnName);
      airlinesValidDefault = tec.ensureTargetColumnIsBinaryCategorical(airlinesValidDefault, targetColumnName);
      airlinesTestFrameDefault = tec.ensureTargetColumnIsBinaryCategorical(airlinesTestFrameDefault, targetColumnName);

      GBMModel.GBMParameters parms2 = new GBMModel.GBMParameters();
      parms2._train = airlinesTrainWithTEHDefault._key;
      parms2._response_column = targetColumnName;
      parms2._score_tree_interval = 10;
      parms2._ntrees = 1000;
      parms2._max_depth = 5;
      parms2._distribution = DistributionFamily.AUTO;
      parms2._valid = airlinesValidDefault._key;
      parms2._stopping_tolerance = 0.001;
      parms2._stopping_metric = ScoreKeeper.StoppingMetric.AUC;
      parms2._stopping_rounds = 5;
      parms2._ignored_columns = new String[]{"IsDepDelayed_REC"};
      parms2._seed = 1234L;

      GBM job2 = new GBM(parms2);
      gbm2 = job2.trainModel().get();

      Assert.assertTrue(job2.isStopped());

      Frame preds2 = gbm2.score(airlinesTestFrameDefault);
      Scope.track(preds2);

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

  public void checkNumRows(Frame before, Frame after) {
    long droppedCount = before.numRows()- after.numRows();
    if(droppedCount != 0) {
      Log.warn(String.format("Number of rows has dropped by %d after manipulations with frame ( %s , %s ).", droppedCount, before._key, after._key));
    }
  }

  private void encodingMapCleanUp(Map<String, Frame> encodingMap) {
    for( Map.Entry<String, Frame> map : encodingMap.entrySet()) {
      map.getValue().delete();
    }
  }

  public static <T> T[] concat(T[] first, T[] second) {
    T[] result = Arrays.copyOf(first, first.length + second.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }
}
