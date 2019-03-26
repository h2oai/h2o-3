package ai.h2o.automl;

import ai.h2o.automl.targetencoding.strategy.*;
import hex.Model;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.StratificationAssistant;
import water.util.Log;

import java.util.Random;

import static ai.h2o.automl.AutoMLBenchmarkingHelper.*;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class StratificationCVBenchmark extends water.TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  // TE is disabled. Purpose of the test is to see how helpful stratification is for CV case 
  @Test public void stratified_vs_auto_assigned_random_folds_CV_scenario_benchmark() {
    AutoML aml=null;
    Frame fr=null;
    Frame frForWithoutTE=null;
    Model leader = null;
    Frame trainingFrame = null;
    String responseColumnName = "survived";
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      autoMLBuildSpec.te_spec.seed = 3456;
      long seed = autoMLBuildSpec.te_spec.seed;

      fr = getPreparedTitanicFrame(responseColumnName);

      Frame[] splits = AutoMLBenchmarkingHelper.split2ByRatio(fr, 0.8, 0.2, seed);
      Frame train = splits[0];
      Frame leaderboard = splits[1];

      Frame withAssignments = StratificationAssistant.assignKFolds(train, 5, responseColumnName, seed);

      autoMLBuildSpec.input_spec.training_frame = withAssignments._key;
      autoMLBuildSpec.input_spec.response_column = responseColumnName;
      autoMLBuildSpec.input_spec.fold_column = "fold";
      
      autoMLBuildSpec.te_spec.enabled = false;
      
      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.stopping_criteria.set_seed(7891);
      autoMLBuildSpec.build_control.keep_cross_validation_models = false;
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      leader = aml.leader();

      double aucWithTE = getScoreBasedOn(leaderboard, leader);

      trainingFrame = aml.getTrainingFrame();

      frForWithoutTE = train.deepCopy(Key.make().toString());
      DKV.put(frForWithoutTE);
      
      Leaderboard leaderboardWithoutTE = train_AutoML_withoutTE_with_auto_assigned_folds( frForWithoutTE, responseColumnName, seed);
//      double aucWithoutTE = leaderboardWithoutTE.getLeader().auc(); // In case of CV early stopping our leader model should return auc that was used for sorting in leaderboard. 
      double aucWithoutTE = getScoreBasedOn(leaderboard, leaderboardWithoutTE.getLeader()); // In case of CV early stopping our leader model should return auc that was used for sorting in leaderboard. 

      System.out.println("Performance with TE: AUC = " + aucWithTE);
      System.out.println("Performance without TE: AUC = " + aucWithoutTE);

      assertTrue(aucWithTE > aucWithoutTE);

      assertNotEquals(" Two frames should be different.", fr, trainingFrame);

    } finally {
      if(leader!=null) leader.delete();
      if(aml!=null) aml.delete();
      if(trainingFrame != null)  trainingFrame.delete();
      if(fr != null) fr.delete();
      if(frForWithoutTE != null) frForWithoutTE.delete();
    }
  }

  int numberOfModelsToCompareWith = 1;
//  Algo[] excludeAlgos = {Algo.DeepLearning, Algo.DRF, Algo.GLM,  Algo.XGBoost /* Algo.GBM,*/, Algo.StackedEnsemble};
//  Algo[] excludeAlgos = {Algo.DeepLearning , Algo.DRF, Algo.GLM, /* Algo.XGBoost*/ Algo.GBM, Algo.StackedEnsemble};
  Algo[] excludeAlgos = {Algo.DeepLearning , Algo.DRF, Algo.GLM,  Algo.XGBoost, /*Algo.GBM,*/ Algo.StackedEnsemble};

  // not TE related test!!!
  // Testing CV Early stopping case with  explicitly assigned stratified kfolds vs. 
  // AutoAssigned kfolds (default schema is Modulo and we can set it through parameters, only in AutoML.setCommonModelBuilderParams).
  @Test public void averageLeaderMetricBenchmark_for_CV_early_stopping_case() {
    AutoML aml=null;
    Frame fr = null;
    Frame frForWithoutTE = null;
    Frame[] splitsForWithoutTE=null;
    Model leader = null;
    Frame trainingFrame = null;
    String responseColumnName = "survived";
    Random generator = new Random();
    double avgAUCWith = 0.0;
    double avgAUCWithoutTE = 0.0;
    double avgCumulativeAUCWith = 0.0;
    double avgCumulativeWithoutTE = 0.0;

    int numberOfRuns = 5;

    for (int seedAttempt = 0; seedAttempt < numberOfRuns; seedAttempt++) {
      long splitSeed = generator.nextLong();
      try {
        AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();

        fr = getPreparedTitanicFrame(responseColumnName);

        Frame[] splits = AutoMLBenchmarkingHelper.split2ByRatio(fr, 0.8, 0.2, splitSeed);
        Frame train = splits[0];
        Frame leaderboardFrame = splits[1];

        Frame withAssignments = StratificationAssistant.assignKFolds(train, 5, responseColumnName, splitSeed);

        autoMLBuildSpec.input_spec.training_frame = withAssignments._key;
        autoMLBuildSpec.build_control.nfolds = 5;
        autoMLBuildSpec.build_models.exclude_algos = excludeAlgos;

        autoMLBuildSpec.input_spec.response_column = responseColumnName;
        autoMLBuildSpec.input_spec.fold_column = "fold";

        Vec responseColumn = train.vec(responseColumnName);
        TEApplicationStrategy thresholdTEApplicationStrategy = new ThresholdTEApplicationStrategy(train, responseColumn, 5);

        autoMLBuildSpec.te_spec.seed = 3456;
        long seed = autoMLBuildSpec.te_spec.seed;

        autoMLBuildSpec.te_spec.enabled = false;
//        TEParamsSelectionStrategy gridSearchTEParamsSelectionStrategy =
//                new GridSearchTEParamsSelectionStrategy(leaderboard, numberOfIterations, responseColumnName, thresholdTEApplicationStrategy.getColumnsToEncode(), true, seed);

        autoMLBuildSpec.te_spec.application_strategy = thresholdTEApplicationStrategy;
//        autoMLBuildSpec.te_spec.params_selection_strategy = gridSearchTEParamsSelectionStrategy;

        autoMLBuildSpec.build_control.project_name = "with_te_" + splitSeed;
        autoMLBuildSpec.build_control.stopping_criteria.set_max_models(numberOfModelsToCompareWith);
        autoMLBuildSpec.build_control.stopping_criteria.set_seed(7890);
        autoMLBuildSpec.build_control.keep_cross_validation_models = false;
        autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

        aml = AutoML.startAutoML(autoMLBuildSpec);
        aml.get();

        leader = aml.leader();
        Leaderboard leaderboardWithTE = aml.leaderboard();
        double cumulativeLeaderboardScoreWithTE = 0;
        cumulativeLeaderboardScoreWithTE = getCumulativeLeaderboardScore(leaderboardFrame, leaderboardWithTE);
        assertTrue(  leaderboardWithTE.getModels().length == numberOfModelsToCompareWith);

        double aucWithTE = getScoreBasedOn(leaderboardFrame, leader);

        trainingFrame = aml.getTrainingFrame();

        frForWithoutTE = train.deepCopy(Key.make().toString());
        DKV.put(frForWithoutTE);
        Leaderboard leaderboardWithoutTE = train_AutoML_withoutTE_with_auto_assigned_folds(frForWithoutTE, responseColumnName, splitSeed);
        double cumulativeLeaderboardScoreWithoutTE = 0;
        cumulativeLeaderboardScoreWithoutTE = getCumulativeLeaderboardScore(leaderboardFrame, leaderboardWithoutTE);
        Model leaderFromWithoutTE = leaderboardWithoutTE.getLeader();
        double aucWithoutTE = getScoreBasedOn(leaderboardFrame, leaderFromWithoutTE);

        Log.info("Leader:" + leader._parms.fullName());
        Log.info("Leader:" + leaderFromWithoutTE._parms.fullName());
        Log.info("Performance on leaderboard frame with TE: AUC = " + aucWithTE);
        Log.info("Performance on leaderboard frame without TE: AUC = " + aucWithoutTE);
        Log.info("Cumulative performance on leaderboard frame with TE: AUC = " + cumulativeLeaderboardScoreWithTE);
        Log.info("Cumulative performance on leaderboard frame without TE: AUC = " + cumulativeLeaderboardScoreWithoutTE);


        avgAUCWith += aucWithTE;
        avgAUCWithoutTE += aucWithoutTE;

        avgCumulativeAUCWith += cumulativeLeaderboardScoreWithTE;
        avgCumulativeWithoutTE += cumulativeLeaderboardScoreWithoutTE;

      } finally {
        if (leader != null) leader.delete();
        if (aml != null) aml.delete();
        if (trainingFrame != null) trainingFrame.delete();
      }
    }

    avgAUCWith = avgAUCWith / numberOfRuns;
    avgAUCWithoutTE = avgAUCWithoutTE / numberOfRuns;
    System.out.println("Average AUC with encoding: " + avgAUCWith);
    System.out.println("Average AUC without encoding: " + avgAUCWithoutTE);

    avgCumulativeAUCWith = avgCumulativeAUCWith / numberOfRuns;
    avgCumulativeWithoutTE = avgCumulativeWithoutTE / numberOfRuns;
    System.out.println("Average cumulative AUC with encoding: " + avgCumulativeAUCWith);
    System.out.println("Average cumulative AUC without encoding: " + avgCumulativeWithoutTE);

    Assert.assertTrue(avgAUCWith > avgAUCWithoutTE);
  }
  
  
  private Leaderboard train_AutoML_withoutTE_with_auto_assigned_folds(Frame trainingSplit, String responseColumnName, long seed) {
    Leaderboard leaderboard=null;
    Scope.enter();
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();

      autoMLBuildSpec.input_spec.training_frame = trainingSplit._key;
      autoMLBuildSpec.build_control.nfolds = 5;
      autoMLBuildSpec.input_spec.response_column = responseColumnName;
      autoMLBuildSpec.build_models.exclude_algos = excludeAlgos;


      autoMLBuildSpec.te_spec.enabled = false;

      autoMLBuildSpec.build_control.project_name = "without_te" + seed;
      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(numberOfModelsToCompareWith);
      autoMLBuildSpec.build_control.stopping_criteria.set_seed(seed);
      autoMLBuildSpec.build_control.keep_cross_validation_models = false;
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

      AutoML aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();
      leaderboard = aml.leaderboard();
    } finally {
      Scope.exit();

    }

    return leaderboard;
  }

}
