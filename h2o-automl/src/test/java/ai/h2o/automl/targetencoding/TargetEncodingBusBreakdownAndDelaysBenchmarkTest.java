package ai.h2o.automl.targetencoding;

import hex.FrameSplitter;
import hex.ModelMetricsBinomial;
import hex.ScoreKeeper;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.H2O;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.util.FrameUtils;
import water.util.TwoDimTable;

import java.util.Arrays;
import java.util.Map;

import static water.util.FrameUtils.generateNumKeys;

public class TargetEncodingBusBreakdownAndDelaysBenchmarkTest extends TestUtil {


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
      // @link https://www.kaggle.com/new-york-city/ny-bus-breakdown-and-delays
      Frame busBaDMain = parse_test_file(Key.make("bus_main"), "smalldata/gbm_test/bus-breakdown-and-delays_balanced.csv");
      Scope.track(busBaDMain);

      busBaDMain.remove(new String[]{"Busbreakdown_ID", "Occurred_On", "Created_On", "Boro", "Informed_On", "Incident_Number", "Last_Updated_On", "School_Age_or_PreK", "Has_Contractor_Notified_Parents", "How_Long_Delayed"});
      Frame busBaD = FrameUtils.asFactor(busBaDMain, "Bus_No");
      Scope.track(busBaD);

      String foldColumnName = "fold";
      FrameUtils.addKFoldColumn(busBaD, foldColumnName, 5, 1234L);

      double[] ratios = ard(0.8f, 0.1f);
      Frame[] splits = null;
      FrameSplitter fs = new FrameSplitter(busBaD, ratios, generateNumKeys(busBaD._key, ratios.length + 1), null);
      H2O.submitTask(fs).join();
      splits = fs.getResult();
      Frame train = splits[0];
      Frame valid = splits[1];
      Frame test = splits[2];
      Scope.track(train, valid, test);

      long startTimeEncoding = System.currentTimeMillis();

      String[] teColumns = {"Bus_Company_Name", "Route_Number", "Bus_No", "Run_Type"};
      TargetEncoder tec = new TargetEncoder(teColumns);
      String targetColumnName = "Breakdown_or_Running_Late";

      boolean withBlendedAvg = true;
      boolean withNoise = false;

      // Create encoding
      encodingMap = tec.prepareEncodingMap(train, targetColumnName, foldColumnName);

      Frame busCompanyName = encodingMap.get("Bus_Company_Name");
      long numOfGroups = busCompanyName.numRows();

      printOutFrameAsTable(busCompanyName, true, true);
      // Apply encoding to the training set
      Frame trainEncoded;
      if (withNoise) {
        trainEncoded = tec.applyTargetEncoding(train, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.KFold, foldColumnName, withBlendedAvg, false, 1234, true);
      } else {
        trainEncoded = tec.applyTargetEncoding(train, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.KFold, foldColumnName, withBlendedAvg, 0, false,1234, true);
      }
      // Applying encoding to the valid set
      Frame validEncoded = tec.applyTargetEncoding(valid, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnName, withBlendedAvg, 0, false, 1234, true);
      validEncoded = tec.ensureTargetColumnIsNumericOrBinaryCategorical(validEncoded, 10);

      // Applying encoding to the test set
      Frame testEncoded = tec.applyTargetEncoding(test, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnName, withBlendedAvg, 0, false, 1234, false);
      testEncoded = tec.ensureTargetColumnIsNumericOrBinaryCategorical(testEncoded, 10);

      Scope.track(trainEncoded, validEncoded, testEncoded);
//      Frame.export(trainEncoded, "bus_train_kfold_dest_noise_noblend.csv", trainEncoded._key.toString(), true, 1);
//      Frame.export(validEncoded, "bus_valid_kfold_dest_noise_noblend.csv", validEncoded._key.toString(), true, 1);
//      Frame.export(testEncoded, "bus_test_kfold_dest_noise_noblend.csv", testEncoded._key.toString(), true, 1);

      printOutFrameAsTable(trainEncoded, false, true);



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
      parms._distribution = DistributionFamily.quasibinomial;
      parms._valid = validEncoded._key;
      parms._stopping_tolerance = 0.001;
      parms._stopping_metric = ScoreKeeper.StoppingMetric.AUC;
      parms._stopping_rounds = 5;
      parms._ignored_columns = concat(new String[]{ foldColumnName}, teColumns);
      parms._seed = 1234L;

      GBM job = new GBM(parms);
      gbm = job.trainModel().get();

      Assert.assertTrue(job.isStopped());

      long finishTime = System.currentTimeMillis();
      System.out.println("Calculation took: " + (finishTime - startTime));

      Frame preds = gbm.score(testEncoded);
      Scope.track(preds);

      ModelMetricsBinomial mm = ModelMetricsBinomial.make(preds.vec(2), testEncoded.vec(parms._response_column));
      double auc = mm._auc._auc;

      // Without target encoding
      double auc2 = trainDefaultGBM(targetColumnName, tec, splits);

      System.out.println("AUC with encoding:" + auc);
      System.out.println("AUC without encoding:" + auc2);

      Assert.assertTrue(auc2 <= auc);
    } finally {
      encodingMapCleanUp(encodingMap);
      if (gbm != null) {
        gbm.delete();
        gbm.deleteCrossValidationModels();
      }
      Scope.exit();
    }
  }

  private double trainDefaultGBM(String targetColumnName, TargetEncoder tec, Frame[] splits) {
    GBMModel gbm2 = null;
    Scope.enter();
    try {
      Frame train = tec.ensureTargetColumnIsNumericOrBinaryCategorical(splits[0], 10);
      Frame valid = tec.ensureTargetColumnIsNumericOrBinaryCategorical(splits[1], 10);
      Frame test = tec.ensureTargetColumnIsNumericOrBinaryCategorical(splits[2], 10);

      Scope.track(train, valid, test);

      GBMModel.GBMParameters parms2 = new GBMModel.GBMParameters();
      parms2._train = train._key;
      parms2._response_column = targetColumnName;
      parms2._score_tree_interval = 10;
      parms2._ntrees = 1000;
      parms2._max_depth = 5;
      parms2._distribution = DistributionFamily.quasibinomial;
      parms2._valid = valid._key;
      parms2._stopping_tolerance = 0.001;
      parms2._stopping_metric = ScoreKeeper.StoppingMetric.AUC;
      parms2._stopping_rounds = 5;
      parms2._ignored_columns = new String[]{"fold"};
      parms2._seed = 1234L;

      GBM job2 = new GBM(parms2);
      gbm2 = job2.trainModel().get();

      Assert.assertTrue(job2.isStopped());

      Frame preds2 = gbm2.score(test);
      Scope.track(preds2);

      ModelMetricsBinomial mm2 = ModelMetricsBinomial.make(preds2.vec(2), test.vec(parms2._response_column));
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

  private void encodingMapCleanUp(Map<String, Frame> encodingMap) {
    if(encodingMap != null) {
      for (Map.Entry<String, Frame> map : encodingMap.entrySet()) {
        map.getValue().delete();
      }
    }
  }

  private void printOutFrameAsTable(Frame fr, boolean full, boolean rollups) {
    TwoDimTable twoDimTable = fr.toTwoDimTable(0, (int)fr.numRows(), rollups);
    System.out.println(twoDimTable.toString(2, full));
  }

  private static <T> T[] concat(T[] first, T[] second) {
    T[] result = Arrays.copyOf(first, first.length + second.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }
}
