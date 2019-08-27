package ai.h2o.automl.targetencoding;

import ai.h2o.automl.AutoMLBenchmarkingHelper;
import hex.ModelMetricsBinomial;
import hex.ScoreKeeper;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.*;
import water.TestUtil;
import water.Scope;
import water.fvec.Frame;

import java.util.Map;
import java.util.Random;

import static ai.h2o.automl.AutoMLBenchmarkingHelper.getPreparedTitanicFrame;
import static ai.h2o.automl.AutoMLBenchmarkingHelper.getScoreBasedOn;
import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;

/*
  Be aware that `smalldata/gbm_test/titanic_*.csv` files are not present in the repo. Replace with your own splits.
  Note that this is a benchmark and we ignore tests from it as they should not affect builds
 */
@Ignore("Ignoring benchmark tests")
public class TargetEncodingTitanicBenchmark extends TestUtil {

  @BeforeClass public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void KFoldHoldoutTypeTest() {
    Scope.enter();
    GBMModel gbm = null;
    long splittingSeed = 2345;
    try {

      //1, isWithBlending = true, smoothing = 10.0, inflection_point = 100.0 // AUC = 0.8212778782399036 as best from RGS with given seed
      BlendingParams params = new BlendingParams(100, 10);
      String targetColumnName = "survived";
      String[] teColumns = {"cabin", "home.dest"/*, "embarked"*/};
      String foldColumnName = "fold";
      String[] teColumnsWithFold = {"cabin", "home.dest"/*, "embarked"*/, foldColumnName};

      TargetEncoder tec = new TargetEncoder(teColumns, params);

      Frame fr = getPreparedTitanicFrame(targetColumnName);
      Frame[] splits = AutoMLBenchmarkingHelper.getRandomSplitsFromDataframe(fr, new double[]{0.7, 0.15, 0.15}, splittingSeed);
      Frame trainFrame = splits[0];
      Frame validFrame = splits[1];
      Frame testFrame = splits[2];

      Scope.track(trainFrame, validFrame, testFrame);

      addKFoldColumn(trainFrame, foldColumnName, 5, splittingSeed);

      boolean withBlendedAvg = true;
      boolean withImputationForNAsInOriginalColumns = true;

      double noiseLevelForTraining = 0.0;

      Map<String, Frame> encodingMap = tec.prepareEncodingMap(trainFrame, targetColumnName, foldColumnName, withImputationForNAsInOriginalColumns);

      Frame trainEncoded = tec.applyTargetEncoding(trainFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.KFold, foldColumnName, withBlendedAvg, noiseLevelForTraining, withImputationForNAsInOriginalColumns,splittingSeed);

      // Preparing valid frame
      Frame validEncoded = tec.applyTargetEncoding(validFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnName, withBlendedAvg,0.0, withImputationForNAsInOriginalColumns, splittingSeed);

      // Preparing test frame
      Frame testEncoded = tec.applyTargetEncoding(testFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnName, withBlendedAvg, 0.0, withImputationForNAsInOriginalColumns,splittingSeed);

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
      parms._seed = splittingSeed;
      parms._keep_cross_validation_models = false;
      parms._keep_cross_validation_predictions = false;
      parms._keep_cross_validation_fold_assignment = false;
      GBM job = new GBM(parms);
      gbm = job.trainModel().get();

      System.out.println(gbm._output._variable_importances.toString(2, true));
      Assert.assertTrue(job.isStopped());

      Frame preds = gbm.score(testEncoded);
      Scope.track(preds);

//      double validationMetrics = gbm._output._validation_metrics.auc_obj()._auc;

      double auc = getScoreBasedOn(testEncoded, gbm);

      double aucWithoutTE = trainDefaultGBM(targetColumnName, splittingSeed);

      System.out.println("AUC with encoding:" + auc);
      System.out.println("AUC without encoding:" + aucWithoutTE);

      Assert.assertTrue(aucWithoutTE < auc);
      encodingMapCleanUp(encodingMap);
      
      fr.delete();
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
    Frame fr = null;
    Random generator = new Random();
    double avgAUCWith = 0.0;
    double avgAUCWithoutTE = 0.0;
    int numberOfRuns = 2;
    for (int seedAttempt = 0; seedAttempt < numberOfRuns; seedAttempt++) {
        Scope.enter();
        long splitSeed = generator.nextLong();
        Frame[] splits = null;
        
        try {
          BlendingParams params = new BlendingParams(2, 10);
          String[] teColumns = {/*"cabin", */"embarked", "home.dest"};
          String targetColumnName = "survived";

          TargetEncoder tec = new TargetEncoder(teColumns, params);

          fr = getPreparedTitanicFrame(targetColumnName);
          splits = AutoMLBenchmarkingHelper.getRandomSplitsFromDataframe(fr, new double[]{0.7, 0.15, 0.15}, splitSeed);
          Frame trainFrame = splits[0];
          Frame validFrame = splits[1];
          Frame testFrame = splits[2];

          Scope.track(trainFrame, validFrame, testFrame);
          boolean withBlendedAvg = true;
          boolean withImputationForNAsInOriginalColumns = true;

          Map<String, Frame> encodingMap = tec.prepareEncodingMap(trainFrame, targetColumnName, null, withImputationForNAsInOriginalColumns);

          Frame trainEncoded;
          trainEncoded = tec.applyTargetEncoding(trainFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, withBlendedAvg, withImputationForNAsInOriginalColumns, 1234);

          Frame validEncoded = tec.applyTargetEncoding(validFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg, 0, withImputationForNAsInOriginalColumns, 1234);

          Frame testEncoded = tec.applyTargetEncoding(testFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg, 0, withImputationForNAsInOriginalColumns, 1234);

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

          double auc  = getScoreBasedOn(testEncoded, gbm);

          double aucWithoutTE = trainDefaultGBM(targetColumnName, splitSeed);

          System.out.println("AUC with encoding:" + auc);
          System.out.println("AUC without encoding:" + aucWithoutTE);

          avgAUCWith += auc;
          avgAUCWithoutTE += aucWithoutTE;

          encodingMapCleanUp(encodingMap);
        } finally {
          if(fr!=null) fr.delete();
          if (gbm != null) {
            gbm.delete();
            gbm.deleteCrossValidationModels();
          }
          Scope.exit();
        }
      }
    avgAUCWith = avgAUCWith / numberOfRuns;
    avgAUCWithoutTE = avgAUCWithoutTE / numberOfRuns;
    System.out.println("Average AUC with encoding:" + avgAUCWith);
    System.out.println("Average AUC without encoding:" + avgAUCWithoutTE);
    Assert.assertTrue(avgAUCWith > avgAUCWithoutTE);  
  }
  
  @Test
  public void noneHoldoutTypeTest() {
    Scope.enter();
    long splittingSeed = 1234;
    try {

      BlendingParams params = new BlendingParams(3, 1); 
      String[] teColumns = {"cabin", "embarked", "home.dest"};
      String targetColumnName = "survived";

      TargetEncoder tec = new TargetEncoder(teColumns, params);

      Frame fr = getPreparedTitanicFrame(targetColumnName);
      Scope.track(fr);
      
      Frame[] splits = AutoMLBenchmarkingHelper.getRandomSplitsFromDataframe(fr, new double[]{0.7, 0.1, 0.1, 0.1}, splittingSeed);
      Frame trainFrame = splits[0];
      Frame teHoldoutFrame = splits[1];
      Frame validFrame = splits[2];
      Frame testFrame = splits[3];

      Scope.track(trainFrame, teHoldoutFrame, validFrame, testFrame);

      boolean withNoiseOnlyForTraining = true;
      boolean withImputation = true;

      Map<String, Frame> encodingMap = tec.prepareEncodingMap(teHoldoutFrame, targetColumnName, null, withImputation);

      Frame trainEncoded;
      if (withNoiseOnlyForTraining) {
        trainEncoded = tec.applyTargetEncoding(trainFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, true, withImputation, 1234);
      } else {
        trainEncoded = tec.applyTargetEncoding(trainFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, true, 0.0, withImputation, 1234);
      }

      Frame validEncoded = tec.applyTargetEncoding(validFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, true, 0.0, withImputation, 1234);

      Frame testEncoded = tec.applyTargetEncoding(testFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, true, 0.0, withImputation, 1234);

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
      double auc2 = trainDefaultGBM(targetColumnName, splittingSeed);

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
    Frame fr = null;
    try {
      fr = getPreparedTitanicFrame(targetColumnName);
      
      Frame[] splits = AutoMLBenchmarkingHelper.getRandomSplitsFromDataframe(fr, new double[]{0.7, 0.15, 0.15}, seed);
      Frame trainFrame = splits[0];
      Frame validFrame = splits[1];
      Frame testFrame = splits[2];

      Scope.track(trainFrame, validFrame, testFrame);

      GBMModel.GBMParameters parms2 = new GBMModel.GBMParameters();
      parms2._train = trainFrame._key;
      parms2._response_column = targetColumnName;
      parms2._score_tree_interval = 10;
      parms2._ntrees = 1000;
      parms2._max_depth = 5;
      parms2._distribution = DistributionFamily.AUTO;
      parms2._valid = validFrame._key;
      parms2._stopping_tolerance = 0.001;
      parms2._stopping_metric = ScoreKeeper.StoppingMetric.AUC;
      parms2._stopping_rounds = 5;
      parms2._seed = 1234L;

      GBM job2 = new GBM(parms2);
      gbm2 = job2.trainModel().get();

      Assert.assertTrue(job2.isStopped());

      Frame preds2 = gbm2.score(testFrame);
      Scope.track(preds2);
      printOutFrameAsTable(preds2, false, preds2.numRows());
      hex.ModelMetricsBinomial mm2 = ModelMetricsBinomial.make(preds2.vec(2), testFrame.vec(parms2._response_column));
      double auc2 = mm2._auc._auc;
      return auc2;
    } finally {
      if(fr!=null) fr.delete();
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
