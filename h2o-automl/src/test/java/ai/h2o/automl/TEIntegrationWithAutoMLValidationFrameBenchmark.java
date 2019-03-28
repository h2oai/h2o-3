package ai.h2o.automl;

import ai.h2o.automl.targetencoding.strategy.*;
import hex.Model;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.H2O;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.Random;

import static ai.h2o.automl.AutoMLBenchmarkingHelper.*;
import static org.junit.Assert.assertTrue;

/**
 * We want to test here the cases when we use Validation frame for Early Stopping
 */
public class TEIntegrationWithAutoMLValidationFrameBenchmark extends water.TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  long autoMLSeed = 2345;

  int numberOfModelsToCompareWith = 1;
//  Algo[] excludeAlgos = {Algo.DeepLearning, Algo.DRF, Algo.GLM /*Algo.XGBoost*/ , Algo.GBM, Algo.StackedEnsemble}; // only XGB
  Algo[] excludeAlgos = {Algo.DeepLearning, /*Algo.DRF,*/ Algo.GLM, Algo.XGBoost /* Algo.GBM,*/, Algo.StackedEnsemble};


  @Test
  public void random_tvl_split_with_RGS_vs_random_tvl_split_withoutTE_benchmark_with_leaderboard_evaluation() {
    AutoML aml = null;
    Frame fr = null;
    Frame[] splitsForWithoutTE = null;
    Frame frForWithoutTE = null;
    Model leader = null;
    Frame trainingFrame = null;
    String responseColumnName = "survived";
    Random generator = new Random();
    double avgAUCWith = 0.0;
    double avgAUCWithoutTE = 0.0;

    double avgCumulativeAUCWith = 0.0;
    double avgCumulativeWithoutTE = 0.0;

    double averageTimeWithTE = 0;
    double averageTimeWithoutTE = 0;

    int numberOfRuns = 10;
    for (int seedAttempt = 0; seedAttempt < numberOfRuns; seedAttempt++) {
      long splitSeed = generator.nextLong(); 
      try {
        AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();

        fr = getPreparedTitanicFrame(responseColumnName);
        Frame[] splits = AutoMLBenchmarkingHelper.getRandomSplitsFromDataframe(fr, new double[]{0.7, 0.15, 0.15}, splitSeed);
        Frame train = splits[0];
        Frame valid = splits[1];
        Frame leaderboardFrame = splits[2];

        autoMLBuildSpec.input_spec.training_frame = train._key;
        autoMLBuildSpec.input_spec.validation_frame = valid._key;
        autoMLBuildSpec.input_spec.leaderboard_frame = leaderboardFrame._key;
        autoMLBuildSpec.build_control.nfolds = 0;
        autoMLBuildSpec.input_spec.response_column = responseColumnName;

        autoMLBuildSpec.build_models.exclude_algos = excludeAlgos;

        Vec responseColumn = train.vec(responseColumnName);
        TEApplicationStrategy thresholdTEApplicationStrategy = new ThresholdTEApplicationStrategy(train, responseColumn, 5);

        autoMLBuildSpec.te_spec.ratio_of_hyperspace_to_explore = 0.4;
        autoMLBuildSpec.te_spec.early_stopping_ratio = 0.15;
        autoMLBuildSpec.te_spec.seed = splitSeed;

        autoMLBuildSpec.te_spec.application_strategy = thresholdTEApplicationStrategy;
        autoMLBuildSpec.te_spec.params_selection_strategy = HPsSelectionStrategy.RGS;

        autoMLBuildSpec.build_control.project_name = "with_te_" + splitSeed;
        autoMLBuildSpec.build_control.stopping_criteria.set_max_models(numberOfModelsToCompareWith);
        autoMLBuildSpec.build_control.stopping_criteria.set_seed(autoMLSeed);
        autoMLBuildSpec.build_control.keep_cross_validation_models = false;
        autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

        long start1 = System.currentTimeMillis();
        aml = AutoML.startAutoML(autoMLBuildSpec);
        aml.get();
        long timeWithTE = System.currentTimeMillis() - start1;
        
        leader = aml.leader();
        Leaderboard leaderboardWithTE = aml.leaderboard();
        assertTrue(leaderboardWithTE.getModels().length == numberOfModelsToCompareWith);
        double cumulativeLeaderboardScoreWithTE = 0;
        cumulativeLeaderboardScoreWithTE = getCumulativeLeaderboardScore(leaderboardFrame, leaderboardWithTE);

        double aucWithTE = getScoreBasedOn(leaderboardFrame, leader);

        trainingFrame = aml.getTrainingFrame();

        frForWithoutTE = fr.deepCopy(Key.make().toString());
        DKV.put(frForWithoutTE);
        splitsForWithoutTE = AutoMLBenchmarkingHelper.getRandomSplitsFromDataframe(frForWithoutTE, new double[]{0.7, 0.15, 0.15}, splitSeed);

        long start2 = System.currentTimeMillis();
        Leaderboard leaderboardWithoutTE = trainBaselineAutoMLWithoutTE(splitsForWithoutTE, responseColumnName, splitSeed);
        long timeWithoutTE = System.currentTimeMillis() - start2;

        double cumulativeLeaderboardScoreWithoutTE = 0;
        cumulativeLeaderboardScoreWithoutTE = getCumulativeLeaderboardScore(leaderboardFrame, leaderboardWithoutTE);

        Model leaderFromWithoutTE = leaderboardWithoutTE.getLeader();
        double aucWithoutTE = getScoreBasedOn(leaderboardFrame, leaderFromWithoutTE);

        System.out.println("Performance on leaderboardFrame frame with TE ( attempt " + seedAttempt + ") : AUC = " + aucWithTE);
        System.out.println("Performance on leaderboardFrame frame without TE ( attempt " + seedAttempt + ") : AUC = " + aucWithoutTE);
        avgAUCWith += aucWithTE;
        avgAUCWithoutTE += aucWithoutTE;

        avgCumulativeAUCWith += cumulativeLeaderboardScoreWithTE;
        avgCumulativeWithoutTE += cumulativeLeaderboardScoreWithoutTE;

        averageTimeWithTE += timeWithTE;
        averageTimeWithoutTE += timeWithoutTE;

      } finally {
        if (leader != null) leader.delete();
        if (aml != null) aml.delete();
        if (trainingFrame != null) trainingFrame.delete();
      }
    }
    avgAUCWith = avgAUCWith / numberOfRuns;
    avgAUCWithoutTE = avgAUCWithoutTE / numberOfRuns;

    averageTimeWithTE = averageTimeWithTE / numberOfRuns;
    averageTimeWithoutTE = averageTimeWithoutTE / numberOfRuns;
    
    System.out.println("Average AUC by leader with encoding:" + avgAUCWith);
    System.out.println("Average AUC by leader without encoding:" + avgAUCWithoutTE);

    avgCumulativeAUCWith = avgCumulativeAUCWith / numberOfRuns;
    avgCumulativeWithoutTE = avgCumulativeWithoutTE / numberOfRuns;
    System.out.println("Average cumulative AUC with encoding: " + avgCumulativeAUCWith);
    System.out.println("Average cumulative AUC without encoding: " + avgCumulativeWithoutTE);
    
    System.out.println("Average time with target encoding: " + averageTimeWithTE);
    System.out.println("Average time without target encoding: " + averageTimeWithoutTE);
    
    Assert.assertTrue(avgAUCWith > avgAUCWithoutTE);
    H2O.STORE.clear();
  }
  
  @Test
  public void random_split_benchmark_with_holdout_evaluation() {
    // We can't do holdout testing for TE as we don't have encoding map to apply it to testFrame. After Mojo task [PUBDEV-6255]  it will be possible
  }
  
  @Test
  public void stratified_tvl_splits_benchmark_with_holdout_evaluation() {
    // We can't do holdout testing for TE as we don't have encoding map to apply it to testFrame. After Mojo task [PUBDEV-6255]  it will be possible
  }

  @Test
  public void stratified_tvl_splits_withTE_vs_stratified_tvl_withoutTE_benchmark_with_leaderboard_evaluation() {
    AutoML aml = null;
    Frame fr = null;
    Frame[] splitsForWithoutTE = null;
    Frame frForWithoutTE = null;
    Model leader = null;
    Frame trainingFrame = null;
    String responseColumnName = "survived";
    Random generator = new Random();
    double avgAUCWith = 0.0;
    double avgAUCWithoutTE = 0.0;

    double avgCumulativeAUCWith = 0.0;
    double avgCumulativeWithoutTE = 0.0;

    int numberOfRuns = 1;
    for (int seedAttempt = 0; seedAttempt < numberOfRuns; seedAttempt++) {
      long splitSeed = generator.nextLong();
      try {
        AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();

        fr = getPreparedTitanicFrame(responseColumnName);

        Frame[] splits = AutoMLBenchmarkingHelper.getStratifiedTVLSplits(fr, responseColumnName, 0.8, splitSeed);
        Frame train = splits[0];
        Frame valid = splits[1];
        Frame leaderboardFrame = splits[2];

        autoMLBuildSpec.input_spec.training_frame = train._key;
        autoMLBuildSpec.input_spec.validation_frame = valid._key;
        autoMLBuildSpec.input_spec.leaderboard_frame = leaderboardFrame._key;
        autoMLBuildSpec.build_control.nfolds = 0;
        autoMLBuildSpec.input_spec.response_column = responseColumnName;

        autoMLBuildSpec.build_models.exclude_algos = excludeAlgos;

        Vec responseColumn = train.vec(responseColumnName);
        TEApplicationStrategy thresholdTEApplicationStrategy = new ThresholdTEApplicationStrategy(train, responseColumn, 5);

        autoMLBuildSpec.te_spec.ratio_of_hyperspace_to_explore = 0.4;
        autoMLBuildSpec.te_spec.early_stopping_ratio = 0.15;
        autoMLBuildSpec.te_spec.seed = splitSeed;

        autoMLBuildSpec.te_spec.application_strategy = thresholdTEApplicationStrategy;
        autoMLBuildSpec.te_spec.params_selection_strategy = HPsSelectionStrategy.RGS;

        autoMLBuildSpec.build_control.project_name = "with_te_" + splitSeed;
        autoMLBuildSpec.build_control.stopping_criteria.set_max_models(numberOfModelsToCompareWith);
        autoMLBuildSpec.build_control.stopping_criteria.set_seed(autoMLSeed);
        autoMLBuildSpec.build_control.keep_cross_validation_models = false;
        autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

        aml = AutoML.startAutoML(autoMLBuildSpec);
        aml.get();

        leader = aml.leader();
        Leaderboard leaderboardWithTE = aml.leaderboard();
        assertTrue(leaderboardWithTE.getModels().length == numberOfModelsToCompareWith);
        double cumulativeLeaderboardScoreWithTE = 0;
        cumulativeLeaderboardScoreWithTE = getCumulativeLeaderboardScore(leaderboardFrame, leaderboardWithTE);

        double aucWithTE = getScoreBasedOn(leaderboardFrame, leader);

        trainingFrame = aml.getTrainingFrame();

        frForWithoutTE = fr.deepCopy(Key.make().toString());
        DKV.put(frForWithoutTE);
        splitsForWithoutTE = AutoMLBenchmarkingHelper.getStratifiedTVLSplits(frForWithoutTE, responseColumnName, 0.8, splitSeed);
        Leaderboard leaderboardWithoutTE = trainBaselineAutoMLWithoutTE(splitsForWithoutTE, responseColumnName, splitSeed);
        double cumulativeLeaderboardScoreWithoutTE = 0;
        cumulativeLeaderboardScoreWithoutTE = getCumulativeLeaderboardScore(leaderboardFrame, leaderboardWithoutTE);
        Model leaderFromWithoutTE = leaderboardWithoutTE.getLeader();
        double aucWithoutTE = getScoreBasedOn(leaderboardFrame, leaderFromWithoutTE);

        System.out.println("Performance on leaderboardFrame frame with TE: AUC = " + aucWithTE);
        System.out.println("Performance on leaderboardFrame frame without TE: AUC = " + aucWithoutTE);
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
    System.out.println("Average AUC with encoding:" + avgAUCWith);
    System.out.println("Average AUC without encoding:" + avgAUCWithoutTE);

    avgCumulativeAUCWith = avgCumulativeAUCWith / numberOfRuns;
    avgCumulativeWithoutTE = avgCumulativeWithoutTE / numberOfRuns;
    System.out.println("Average cumulative AUC with encoding: " + avgCumulativeAUCWith);
    System.out.println("Average cumulative AUC without encoding: " + avgCumulativeWithoutTE);
    Assert.assertTrue(avgAUCWith > avgAUCWithoutTE);
  }


  private Leaderboard trainBaselineAutoMLWithoutTE(Frame[] splits, String responseColumnName, long splitSeed) {
    Leaderboard leaderboard = null;
    Scope.enter();
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();

      autoMLBuildSpec.input_spec.training_frame = splits[0]._key;
      autoMLBuildSpec.input_spec.validation_frame = splits[1]._key;
      autoMLBuildSpec.input_spec.leaderboard_frame = splits[2]._key;
      autoMLBuildSpec.build_control.nfolds = 0;
      autoMLBuildSpec.input_spec.response_column = responseColumnName;

      autoMLBuildSpec.build_models.exclude_algos = excludeAlgos;

      autoMLBuildSpec.te_spec.enabled = false;

      autoMLBuildSpec.build_control.project_name = "without_te_" + splitSeed;
      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(numberOfModelsToCompareWith);
      autoMLBuildSpec.build_control.stopping_criteria.set_seed(autoMLSeed);
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
