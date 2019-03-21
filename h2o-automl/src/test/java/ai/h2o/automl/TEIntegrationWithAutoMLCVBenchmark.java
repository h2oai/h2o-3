package ai.h2o.automl;

import ai.h2o.automl.targetencoding.TargetEncoderFrameHelper;
import ai.h2o.automl.targetencoding.strategy.*;
import hex.Model;
import hex.splitframe.ShuffleSplitFrame;
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

import static ai.h2o.automl.AutoMLBenchmarkingHelper.getCumulativeLeaderboardScore;
import static ai.h2o.automl.AutoMLBenchmarkingHelper.getScoreBasedOn;
import static org.junit.Assert.*;

public class TEIntegrationWithAutoMLCVBenchmark extends water.TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  private Frame getPreparedTitanicFrame(String responseColumnName) {
    Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
    fr.remove(new String[]{"name", "ticket", "boat", "body"});
    asFactor(fr, responseColumnName);
    return fr;
  }

  @Test public void te_vs_without_te_with_CV_early_stopping() {
    AutoML aml=null;
    Frame fr=null;
    Frame frForWithoutTE=null;
    Model leader = null;
    Frame trainingFrame = null;
    String responseColumnName = "survived";
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      
      fr = getPreparedTitanicFrame(responseColumnName);

      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.build_control.nfolds = 5;
      autoMLBuildSpec.input_spec.response_column = responseColumnName;

      Vec responseColumn = fr.vec(responseColumnName);
      TEApplicationStrategy thresholdTEApplicationStrategy = new ThresholdTEApplicationStrategy(fr, responseColumn, 5);
      
      int numberOfIterations = 100;
      long seed = autoMLBuildSpec.te_spec.seed;
      autoMLBuildSpec.te_spec.enabled = true;
      TEParamsSelectionStrategy gridSearchTEParamsSelectionStrategy =  
              new GridSearchTEParamsSelectionStrategy(fr, numberOfIterations, responseColumnName, thresholdTEApplicationStrategy.getColumnsToEncode(), true, seed);;

      autoMLBuildSpec.te_spec.application_strategy = thresholdTEApplicationStrategy;
      autoMLBuildSpec.te_spec.params_selection_strategy = gridSearchTEParamsSelectionStrategy;

      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.keep_cross_validation_models = false;
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      leader = aml.leader();

      double aucWithTE = leader.auc();

      trainingFrame = aml.getTrainingFrame();

      frForWithoutTE = getPreparedTitanicFrame(responseColumnName);
      Leaderboard leaderboardWithoutTE = trainAutoMLWithoutTE( frForWithoutTE,responseColumnName, seed);
      double aucWithoutTE = leaderboardWithoutTE.getLeader().auc();
      
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

  // TE is disabled. Purpose of the test is to see how helpful is stratification for CV case 
  @Test public void stratifiedAutoAssignedFoldsCVScenarioTest() {
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
//      autoMLBuildSpec.build_control.nfolds = 5;

      autoMLBuildSpec.input_spec.response_column = responseColumnName;
      autoMLBuildSpec.input_spec.fold_column = "fold";

      Vec responseColumn = withAssignments.vec(responseColumnName);
      TEApplicationStrategy thresholdTEApplicationStrategy = new ThresholdTEApplicationStrategy(withAssignments, responseColumn, 5);

      int numberOfIterations = 378;
      
      autoMLBuildSpec.te_spec.enabled = false;
      TEParamsSelectionStrategy gridSearchTEParamsSelectionStrategy =
              new GridSearchTEParamsSelectionStrategy(withAssignments, numberOfIterations, responseColumnName, thresholdTEApplicationStrategy.getColumnsToEncode(), true, seed);;

      autoMLBuildSpec.te_spec.application_strategy = thresholdTEApplicationStrategy;
      autoMLBuildSpec.te_spec.params_selection_strategy = gridSearchTEParamsSelectionStrategy;

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
      
      Leaderboard leaderboardWithoutTE = trainAutoMLWithoutTE( frForWithoutTE, responseColumnName, seed);
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
        Leaderboard leaderboardWithoutTE = trainAutoMLWithoutTE(frForWithoutTE, responseColumnName, splitSeed);
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
  
  
  @Test public void customFoldColumnCVScenarioTest() {
  }

  @Test public void gridSearchEndedUpWithKFoldStrategyTest() {
  }
  
  @Test public void testThatDifferentTESearchSpacesAreBeingChosenDependingOnValidationModeOfAutoMLTest() {
    
  }

  @Test public void withTE_vs_withoutTE_benchmark_withCVPredictionUsedForComparison() {
    AutoML aml=null;
    Frame fr=null;
    Model leader = null;
    Frame trainingFrame = null;
    String responseColumnName = "survived";

    Random generator = new Random();
    double avgAUCWith = 0.0;
    double avgAUCWithoutTE = 0.0;

    int numberOfRuns = 3;

    for (int seedAttempt = 0; seedAttempt < numberOfRuns; seedAttempt++) {
      long nextSeed = generator.nextLong(); 
      try {
        AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();

        fr = getPreparedTitanicFrame(responseColumnName);

        autoMLBuildSpec.input_spec.training_frame = fr._key;
        autoMLBuildSpec.build_control.nfolds = 5;
        autoMLBuildSpec.input_spec.response_column = responseColumnName;
        autoMLBuildSpec.build_models.exclude_algos = excludeAlgos;

        Vec responseColumn = fr.vec(responseColumnName);
        TEApplicationStrategy thresholdTEApplicationStrategy = new ThresholdTEApplicationStrategy(fr, responseColumn, 5);

        double SMBOEarlyStoppingRatio = 0.2;
        autoMLBuildSpec.te_spec.seed = nextSeed;
        TEParamsSelectionStrategy gridSearchTEParamsSelectionStrategy =
                new SMBOTEParamsSelectionStrategy(fr, SMBOEarlyStoppingRatio, responseColumnName, thresholdTEApplicationStrategy.getColumnsToEncode(), true, nextSeed);

//        TEParamsSelectionStrategy gridSearchTEParamsSelectionStrategy =
//                new GridSearchTEParamsSelectionStrategy(fr, 63, responseColumnName, thresholdTEApplicationStrategy.getColumnsToEncode(), true, nextSeed);


        autoMLBuildSpec.te_spec.application_strategy = thresholdTEApplicationStrategy;
        autoMLBuildSpec.te_spec.params_selection_strategy = gridSearchTEParamsSelectionStrategy;


        autoMLBuildSpec.build_control.project_name = "with_te" + nextSeed;
        autoMLBuildSpec.build_control.stopping_criteria.set_max_models(numberOfModelsToCompareWith);
        autoMLBuildSpec.build_control.stopping_criteria.set_seed(nextSeed);
        autoMLBuildSpec.build_control.keep_cross_validation_models = false;
        autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

        aml = AutoML.startAutoML(autoMLBuildSpec);
        aml.get();

        leader = aml.leader();

        double aucWithTE = leader.auc();

        Frame fr2 = getPreparedTitanicFrame(responseColumnName);
        Leaderboard leaderboardWithoutTE = trainAutoMLWithoutTE(fr2, responseColumnName, nextSeed);
        Model withoutTELeader = leaderboardWithoutTE.getLeader();

        double aucWithoutTE = withoutTELeader.auc();

        System.out.println("Performance on holdout split with TE: AUC = " + aucWithTE);
        System.out.println("Performance on holdout split without TE: AUC = " + aucWithoutTE);

        avgAUCWith += aucWithTE;
        avgAUCWithoutTE += aucWithoutTE;
        

      } finally {
        if (leader != null) leader.delete();
        if (aml != null) aml.delete();
        if (trainingFrame != null) trainingFrame.delete();
        if (fr != null) fr.delete();
      }
    }

    avgAUCWith = avgAUCWith / numberOfRuns;
    avgAUCWithoutTE = avgAUCWithoutTE / numberOfRuns;
    System.out.println("Average AUC with encoding: " + avgAUCWith);
    System.out.println("Average AUC without encoding: " + avgAUCWithoutTE);
    
    assertTrue(avgAUCWith > avgAUCWithoutTE);

  }

  private Leaderboard trainAutoMLWithoutTE(Frame trainingSplit, String responseColumnName, long seed) {
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
