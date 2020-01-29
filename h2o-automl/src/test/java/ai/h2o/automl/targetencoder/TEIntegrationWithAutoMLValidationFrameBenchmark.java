package ai.h2o.automl.targetencoder;

import ai.h2o.automl.Algo;
import ai.h2o.automl.AutoML;
import ai.h2o.automl.AutoMLBuildSpec;
import ai.h2o.automl.leaderboard.Leaderboard;
import ai.h2o.targetencoding.strategy.TEApplicationStrategy;
import ai.h2o.targetencoding.strategy.ThresholdTEApplicationStrategy;
import hex.Model;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.Random;

import static ai.h2o.automl.targetencoder.AutoMLBenchmarkHelper.*;
import static org.junit.Assert.assertTrue;

/**
 * We want to test here the cases when in AutoML we use Validation frame for Early Stopping
 */
@Ignore
public class TEIntegrationWithAutoMLValidationFrameBenchmark extends water.TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  long autoMLSeed = 2345;

  int numberOfModelsToCompareWith = 1;

  Algo[] excludeAlgos = {/*Algo.DeepLearning,*/ Algo.DRF, Algo.GLM, Algo.XGBoost, Algo.GBM, Algo.StackedEnsemble};
//  Algo[] excludeAlgos = {Algo.DeepLearning/*, Algo.DRF*/, Algo.GLM , Algo.XGBoost , Algo.GBM, Algo.StackedEnsemble};
//  Algo[] excludeAlgos = {Algo.DeepLearning, /*Algo.DRF,*/ Algo.GLM /*Algo.XGBoost*/ /* Algo.GBM,*/, Algo.StackedEnsemble};


  @Test
  public void random_tvl_split_with_RGS_vs_random_tvl_split_withoutTE_benchmark_with_leaderboard_evaluation() {
    AutoML aml = null;
    AutoML amlWithoutTE = null;
    Frame fr = null;
    Leaderboard leaderboardWithTE = null;
    Leaderboard leaderboardWithoutTE = null;
    Frame[] splitsForWithoutTE = null;
    Frame frForWithoutTE = null;
    Model leader = null;

    Frame trainSplit = null;
    Frame validSplit = null;
    Frame leaderboardSplit = null;

    String responseColumnName = "survived";
    Random generator = new Random();
    double avgAUCWith = 0.0;
    double avgAUCWithoutTE = 0.0;

    double avgCumulativeAUCWith = 0.0;
    double avgCumulativeWithoutTE = 0.0;

    double averageTimeWithTE = 0;
    double averageTimeWithoutTE = 0;

    int numberOfRuns = 5;
    for (int seedAttempt = 0; seedAttempt < numberOfRuns; seedAttempt++) {
      long splitSeed = generator.nextLong();  // Note: DL's predictions are not stable so we can get different bestHPs from selection strategy even when we pass same seeds everywhere.
      try {
        AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();

        fr = getPreparedTitanicFrame(responseColumnName);
        Frame[] splits = AutoMLBenchmarkHelper.getRandomSplitsFromDataframe(fr, new double[]{0.7, 0.15, 0.15}, splitSeed);
        trainSplit = splits[0];
        validSplit = splits[1];
        leaderboardSplit = splits[2];

        autoMLBuildSpec.input_spec.training_frame = trainSplit._key;
        autoMLBuildSpec.input_spec.validation_frame = validSplit._key;
        autoMLBuildSpec.input_spec.leaderboard_frame = leaderboardSplit._key;
        autoMLBuildSpec.build_control.nfolds = 0;
        autoMLBuildSpec.input_spec.response_column = responseColumnName;

        autoMLBuildSpec.build_models.exclude_algos = excludeAlgos;

        TEApplicationStrategy thresholdTEApplicationStrategy = new ThresholdTEApplicationStrategy(trainSplit, 5, new String[]{responseColumnName});

        autoMLBuildSpec.te_spec.seed = splitSeed;
        autoMLBuildSpec.te_spec.enabled = true;

        autoMLBuildSpec.te_spec.application_strategy = thresholdTEApplicationStrategy;

        autoMLBuildSpec.build_control.project_name = "with_te_" + splitSeed;
        autoMLBuildSpec.build_control.stopping_criteria.set_max_models(numberOfModelsToCompareWith);
        autoMLBuildSpec.build_control.stopping_criteria.set_seed(autoMLSeed);
        autoMLBuildSpec.build_control.keep_cross_validation_models = false;
        autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

        autoMLBuildSpec.build_control.stopping_criteria.set_seed(splitSeed);

        long start1 = System.currentTimeMillis();
        aml = AutoML.startAutoML(autoMLBuildSpec);
        aml.get();
        long timeWithTE = System.currentTimeMillis() - start1;

        leader = aml.leader();
        assertTrue(leader._output._cross_validation_predictions == null);

        leaderboardWithTE = aml.leaderboard();

        assertTrue(leaderboardWithTE.getModels().length == numberOfModelsToCompareWith);
        double cumulativeLeaderboardScoreWithTE = 0;
        cumulativeLeaderboardScoreWithTE = getCumulativeLeaderboardScore(leaderboardSplit, leaderboardWithTE);

        double aucWithTE = getScoreBasedOn(leaderboardSplit, leader);

        frForWithoutTE = fr.deepCopy(Key.make().toString());
        DKV.put(frForWithoutTE);
        splitsForWithoutTE = AutoMLBenchmarkHelper.getRandomSplitsFromDataframe(frForWithoutTE, new double[]{0.7, 0.15, 0.15}, splitSeed);
        long start2 = System.currentTimeMillis();
        amlWithoutTE = trainBaselineAutoMLWithoutTE(splitsForWithoutTE, responseColumnName, splitSeed);
        leaderboardWithoutTE = amlWithoutTE.leaderboard();
        long timeWithoutTE = System.currentTimeMillis() - start2;

        double cumulativeLeaderboardScoreWithoutTE = 0;
        cumulativeLeaderboardScoreWithoutTE = getCumulativeLeaderboardScore(leaderboardSplit, leaderboardWithoutTE);

        Model leaderFromWithoutTE = leaderboardWithoutTE.getLeader();
        double aucWithoutTE = getScoreBasedOn(leaderboardSplit, leaderFromWithoutTE);

        System.out.println("Performance on leaderboardFrame frame with TE ( attempt " + seedAttempt + ") : AUC = " + aucWithTE);
        System.out.println("Performance on leaderboardFrame frame without TE ( attempt " + seedAttempt + ") : AUC = " + aucWithoutTE);

        avgAUCWith += aucWithTE;
        avgAUCWithoutTE += aucWithoutTE;

        avgCumulativeAUCWith += cumulativeLeaderboardScoreWithTE;
        avgCumulativeWithoutTE += cumulativeLeaderboardScoreWithoutTE;

        averageTimeWithTE += timeWithTE;
        averageTimeWithoutTE += timeWithoutTE;

      } finally {
        if (fr != null) fr.delete();
        if (trainSplit != null) trainSplit.delete();
        if (validSplit != null) validSplit.delete();
        if (leaderboardSplit != null) leaderboardSplit.delete();


        if (aml != null) {
          aml.leaderboard().remove();
          aml.delete();
        }
        if (leaderboardWithoutTE != null) leaderboardWithoutTE.remove();
        if (amlWithoutTE != null) amlWithoutTE.delete();

        if (frForWithoutTE != null) frForWithoutTE.delete();
        if (splitsForWithoutTE != null) {
          for (Frame split : splitsForWithoutTE) {
            split.delete();
          }
        }
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
  }

  @Test
  public void random_split_benchmark_with_holdout_evaluation() {
    // We can't do holdout testing for TE as we don't have encoding map to apply it to testFrame. After Mojo task [PUBDEV-6255]  it will be possible
  }

  private AutoML trainBaselineAutoMLWithoutTE(Frame[] splits, String responseColumnName, long splitSeed) {
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
      return aml;

    } finally {
      Scope.exit();
    }
  }

}