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
import static org.junit.Assert.*;

/**
 * We want to test here the cases when we use CV for Early Stopping
 */
public class TEIntegrationWithAutoMLCVBenchmark extends water.TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  int numberOfModelsToCompareWith = 1;
  Algo[] excludeAlgos = {Algo.DeepLearning , Algo.DRF, Algo.GLM,  Algo.XGBoost,/* Algo.GBM,*/ Algo.StackedEnsemble};
//  Algo[] excludeAlgos = {Algo.DeepLearning , Algo.DRF, Algo.GLM,  /*Algo.XGBoost,*/ Algo.GBM, Algo.StackedEnsemble};
  
  
  @Test public void customFoldColumnCVScenarioTest() {
  }

  @Test public void gridSearchEndedUpWithKFoldStrategyTest() {
  }
  
  @Test public void testThatDifferentTESearchSpacesAreBeingChosenDependingOnValidationModeOfAutoMLTest() {
    
  }

  // Note: don't forget to keep cv predictions so that auc() method returns cv metric.
  @Test public void te_vs_withoutTE_benchmark_withCVPredictionUsedForComparison() {
    AutoML aml=null;
    Frame fr=null;
    Model leader = null;
    Frame trainingFrame = null;
    String responseColumnName = "survived";

    Random generator = new Random();
    double avgAUCWith = 0.0;
    double avgAUCWithoutTE = 0.0;

    int numberOfRuns = 1;

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

        autoMLBuildSpec.te_spec.ratio_of_hyperspace_to_explore = 0.05;
        autoMLBuildSpec.te_spec.early_stopping_ratio = 0.15;
        autoMLBuildSpec.te_spec.seed = nextSeed;

        autoMLBuildSpec.te_spec.application_strategy = thresholdTEApplicationStrategy;
        autoMLBuildSpec.te_spec.params_selection_strategy = HPsSelectionStrategy.RGS;

        autoMLBuildSpec.build_control.project_name = "with_te" + nextSeed;
        autoMLBuildSpec.build_control.stopping_criteria.set_max_models(numberOfModelsToCompareWith);
        autoMLBuildSpec.build_control.stopping_criteria.set_seed(nextSeed);
        autoMLBuildSpec.build_control.keep_cross_validation_models = true;
        autoMLBuildSpec.build_control.keep_cross_validation_predictions = true;

        aml = AutoML.startAutoML(autoMLBuildSpec);
        aml.get();

        leader = aml.leader();

        double aucWithTE = leader.auc();

        Frame fr2 = getPreparedTitanicFrame(responseColumnName);
        Leaderboard leaderboardWithoutTE = train_AutoML_withoutTE_with_auto_assigned_folds(fr2, responseColumnName, nextSeed);
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
      autoMLBuildSpec.build_control.keep_cross_validation_models = true;
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = true;

      AutoML aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();
      leaderboard = aml.leaderboard();
    } finally {
      Scope.exit();

    }

    return leaderboard;
  }

}
