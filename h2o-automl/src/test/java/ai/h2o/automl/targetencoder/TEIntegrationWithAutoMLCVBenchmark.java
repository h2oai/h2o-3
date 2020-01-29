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
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.Random;

import static ai.h2o.automl.targetencoder.AutoMLBenchmarkHelper.getCumulativeAUCScore;
import static ai.h2o.automl.targetencoder.AutoMLBenchmarkHelper.getPreparedTitanicFrame;
import static org.junit.Assert.assertTrue;

/**
 * We want to test here the cases when in AutoML we use CV for Early Stopping
 */
@Ignore
public class TEIntegrationWithAutoMLCVBenchmark extends water.TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  long autoMLSeed = 2345;

  int numberOfModelsToCompareWith = 1;
  //  Algo[] excludeAlgos = {Algo.DeepLearning, /*Algo.DRF,*/ Algo.GLM /*Algo.XGBoost*/ /* Algo.GBM,*/, Algo.StackedEnsemble};
  Algo[] excludeAlgos = {/*Algo.DeepLearning,*/ Algo.DRF, Algo.GLM, Algo.XGBoost, Algo.GBM, Algo.StackedEnsemble};

  @Test
  public void random_tvl_split_with_RGS_vs_random_tvl_split_withoutTE_benchmark_with_leaderboard_evaluation() {
    AutoML aml = null;
    AutoML amlWithoutTE = null;
    Frame fr = null;
    Frame frForWithoutTE = null;

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
      long splitSeed = generator.nextLong();
      try {
        AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();

        fr = getPreparedTitanicFrame(responseColumnName);

        autoMLBuildSpec.input_spec.training_frame = fr._key;
        autoMLBuildSpec.build_control.nfolds = 5;
        autoMLBuildSpec.input_spec.response_column = responseColumnName;
        autoMLBuildSpec.build_models.exclude_algos = excludeAlgos;

        Vec responseColumn = fr.vec(responseColumnName);
        TEApplicationStrategy thresholdTEApplicationStrategy = new ThresholdTEApplicationStrategy(fr,5, new String[]{responseColumnName});

        autoMLBuildSpec.te_spec.seed = splitSeed;

        autoMLBuildSpec.te_spec.application_strategy = thresholdTEApplicationStrategy;

        autoMLBuildSpec.build_control.project_name = "with_te_" + splitSeed;
        autoMLBuildSpec.build_control.stopping_criteria.set_max_models(numberOfModelsToCompareWith);
        autoMLBuildSpec.build_control.stopping_criteria.set_seed(autoMLSeed);
        autoMLBuildSpec.build_control.keep_cross_validation_models = true;
        autoMLBuildSpec.build_control.keep_cross_validation_predictions = true;

        long start1 = System.currentTimeMillis();
        aml = AutoML.startAutoML(autoMLBuildSpec);
        aml.get();
        long timeWithTE = System.currentTimeMillis() - start1;

        Leaderboard leaderboardWithTE = aml.leaderboard();
        assertTrue(leaderboardWithTE.getModels().length == numberOfModelsToCompareWith);
        double cumulativeLeaderboardScoreWithTE = 0;
        cumulativeLeaderboardScoreWithTE = getCumulativeAUCScore(leaderboardWithTE);

        double aucWithTE = leaderboardWithTE.getLeader().auc();

        frForWithoutTE = fr.deepCopy(Key.make().toString());
        DKV.put(frForWithoutTE);
        long start2 = System.currentTimeMillis();
        amlWithoutTE = train_AutoML_withoutTE_with_auto_assigned_folds(frForWithoutTE, responseColumnName, splitSeed);
        long timeWithoutTE = System.currentTimeMillis() - start2;

        double cumulativeLeaderboardScoreWithoutTE = 0;
        cumulativeLeaderboardScoreWithoutTE = getCumulativeAUCScore(amlWithoutTE.leaderboard());

        Model leaderFromWithoutTE = amlWithoutTE.leader();
        double aucWithoutTE = leaderFromWithoutTE.auc();

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
        if (aml != null) {
          for (Model model : aml.leaderboard().getModels()) {
            model.deleteCrossValidationPreds();
            model.deleteCrossValidationModels();
          }
          aml.leaderboard().remove();
          aml.delete();
        }

        if (amlWithoutTE != null) {
          for (Model model : amlWithoutTE.leaderboard().getModels()) {
            model.deleteCrossValidationPreds();
            model.deleteCrossValidationModels();
          }
          amlWithoutTE.leaderboard().remove();
          amlWithoutTE.delete();
        }

        if (frForWithoutTE != null) frForWithoutTE.delete();
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

  private AutoML train_AutoML_withoutTE_with_auto_assigned_folds(Frame train, String responseColumnName, long seed) {
    AutoML aml = null;
    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();

    autoMLBuildSpec.input_spec.training_frame = train._key;
    autoMLBuildSpec.build_control.nfolds = 5;
    autoMLBuildSpec.input_spec.response_column = responseColumnName;
    autoMLBuildSpec.build_models.exclude_algos = excludeAlgos;


    autoMLBuildSpec.te_spec.enabled = false;

    autoMLBuildSpec.build_control.project_name = "without_te" + seed;
    autoMLBuildSpec.build_control.stopping_criteria.set_max_models(numberOfModelsToCompareWith);
    autoMLBuildSpec.build_control.stopping_criteria.set_seed(seed);
    autoMLBuildSpec.build_control.keep_cross_validation_models = true;
    autoMLBuildSpec.build_control.keep_cross_validation_predictions = true;

    aml = AutoML.startAutoML(autoMLBuildSpec);
    aml.get();
    return aml;
  }
}
