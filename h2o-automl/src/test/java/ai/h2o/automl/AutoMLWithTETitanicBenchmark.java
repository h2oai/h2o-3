package ai.h2o.automl;

import ai.h2o.automl.targetencoding.BlendingParams;
import ai.h2o.automl.targetencoding.TargetEncoder;
import ai.h2o.automl.targetencoding.TargetEncoderFrameHelper;
import ai.h2o.automl.targetencoding.TargetEncodingParams;
import ai.h2o.automl.targetencoding.strategy.*;
import hex.Model;
import hex.splitframe.ShuffleSplitFrame;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;

import java.util.Random;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

/**
 * Have to keep test class here since most of the methods in AutoML, LeaderBoard are package private.
 */
public class AutoMLWithTETitanicBenchmark extends TestUtil {


  @BeforeClass public static void setup() {
    stall_till_cloudsize(1);
  }

  int numberOfModelsToCompareWith = 4;

  Algo[] excludeAlgos = {Algo.DeepLearning, Algo.DRF/*, Algo.GLM*/, Algo.XGBoost, Algo.GBM, Algo.StackedEnsemble};

  private Frame getPreparedTitanicFrame(String responseColumnName) {
    Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
    fr.remove(new String[]{"name", "ticket", "boat", "body"});
    asFactor(fr, responseColumnName);
    return fr;
  }

  private Frame[] getSplitsFromTitanicDataset(String responseColumnName, long splitSeed) {
    Frame fr = getPreparedTitanicFrame(responseColumnName);

    double[] ratios = ard(0.7, 0.15, 0.15);
    Key<Frame>[] keys = aro(Key.<Frame>make(), Key.<Frame>make(), Key.<Frame>make());
    Frame[] splits = null;
    splits = ShuffleSplitFrame.shuffleSplitFrame(fr, keys, ratios, splitSeed);

    return splits;
  }

  // Each run will get us one leader out of `numberOfModelsToCompareWith` number of competitors. We can average this over `numberOfRuns` runs.
  @Test public void averageLeaderMetricBenchmark() {
    AutoML aml=null;
    Frame[] splitsForWithoutTE=null;
    Model leader = null;
    Frame trainingFrame = null;
    String responseColumnName = "survived";
    Random generator = new Random();
    double avgAUCWith = 0.0;
    double avgAUCWithoutTE = 0.0;
    double avgCumulativeAUCWith = 0.0;
    double avgCumulativeWithoutTE = 0.0;

    int numberOfRuns = 2;
    for (int seedAttempt = 0; seedAttempt < numberOfRuns; seedAttempt++) {
      long splitSeed = generator.nextLong();
      try {
        AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();

        Frame[] splits = getSplitsFromTitanicDataset(responseColumnName, splitSeed);
        Frame train = splits[0];
        Frame valid = splits[1];
        Frame leaderboard = splits[2];

        autoMLBuildSpec.input_spec.training_frame = train._key;
        autoMLBuildSpec.input_spec.validation_frame = valid._key;
        autoMLBuildSpec.input_spec.leaderboard_frame = leaderboard._key;
        autoMLBuildSpec.build_control.nfolds = 0;
        autoMLBuildSpec.input_spec.response_column = responseColumnName;

        Vec responseColumn = train.vec(responseColumnName);
        TEApplicationStrategy thresholdTEApplicationStrategy = new ThresholdTEApplicationStrategy(train, responseColumn, 5);

        int numberOfIterations = 378;
        autoMLBuildSpec.te_spec.seed = 3456;
        long seed = autoMLBuildSpec.te_spec.seed;
        TEParamsSelectionStrategy gridSearchTEParamsSelectionStrategy =
                new GridSearchTEParamsSelectionStrategy(leaderboard, numberOfIterations, responseColumnName, thresholdTEApplicationStrategy.getColumnsToEncode(), true, seed);

        autoMLBuildSpec.te_spec.application_strategy = thresholdTEApplicationStrategy;
        autoMLBuildSpec.te_spec.params_selection_strategy = gridSearchTEParamsSelectionStrategy;

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
        cumulativeLeaderboardScoreWithTE = getCumulativeLeaderboardScore(splits[2], leaderboardWithTE);
        assertTrue(  leaderboardWithTE.getModels().length == numberOfModelsToCompareWith);

        double aucWithTE = getScoreBasedOn(splits[2], leader);

        trainingFrame = aml.getTrainingFrame();

        splitsForWithoutTE = getSplitsFromTitanicDataset(responseColumnName, splitSeed);
        Leaderboard leaderboardWithoutTE = trainBaselineAutoMLWithoutTE(splitsForWithoutTE, responseColumnName, seed, splitSeed);
        double cumulativeLeaderboardScoreWithoutTE = 0;
        cumulativeLeaderboardScoreWithoutTE = getCumulativeLeaderboardScore(splits[2], leaderboardWithoutTE);
        Model leaderFromWithoutTE = leaderboardWithoutTE.getLeader();
        double aucWithoutTE = getScoreBasedOn(splits[2], leaderFromWithoutTE);

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

  private double getCumulativeLeaderboardScore(Frame split, Leaderboard leaderboardWithTE) {
    double cumulative = 0.0;
    for( Model model : leaderboardWithTE.getModels()) {
      cumulative += getScoreBasedOn(split, model);
    }
    return cumulative;
  }

  private Leaderboard trainBaselineAutoMLWithoutTE(Frame[] splits, String responseColumnName, long seed, long splitSeed) {
    Leaderboard leaderboard=null;
    Scope.enter();
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();

      autoMLBuildSpec.input_spec.training_frame = splits[0]._key;
      autoMLBuildSpec.input_spec.validation_frame = splits[1]._key;
      autoMLBuildSpec.input_spec.leaderboard_frame = splits[2]._key;
      autoMLBuildSpec.build_control.nfolds = 0;
      autoMLBuildSpec.input_spec.response_column = responseColumnName;

      autoMLBuildSpec.te_spec.enabled = false;

      autoMLBuildSpec.build_control.project_name = "without_te_" + splitSeed;
      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(numberOfModelsToCompareWith);
      autoMLBuildSpec.build_control.stopping_criteria.set_seed(7890);
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

  private double getScoreBasedOn(Frame fr, Model model) {
    model.score(fr);
    hex.ModelMetricsBinomial mmWithoutTE = hex.ModelMetricsBinomial.getFromDKV(model, fr);
    return mmWithoutTE.auc();
  }
  
}
