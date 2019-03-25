package ai.h2o.automl;

import ai.h2o.automl.targetencoding.BlendingParams;
import ai.h2o.automl.targetencoding.TargetEncoder;
import ai.h2o.automl.targetencoding.TargetEncodingParams;
import ai.h2o.automl.targetencoding.strategy.*;
import hex.Model;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.fvec.Frame;

import java.util.Random;

import static ai.h2o.automl.AutoMLBenchmarkingHelper.getPreparedTitanicFrame;
import static ai.h2o.automl.AutoMLBenchmarkingHelper.getScoreBasedOn;
import static org.junit.Assert.assertTrue;

/**
 * We want to test here the cases when we use Validation frame for Early Stopping
 */
public class StratificationValidationFrameBenchmark extends water.TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }
  
  long autoMLSeed = 7890;

  int numberOfModelsToCompareWith = 1;
  Algo[] excludeAlgos = {Algo.DeepLearning, Algo.DRF, Algo.GLM,  Algo.XGBoost /* Algo.GBM,*/, Algo.StackedEnsemble};

    @Test  
    public void stratified_tvl_splits_benchmark_with_holdout_evaluation() {
      // We can't do holdout testing for TE as we don't have encoding map to apply it to testFrame. After Mojo task [PUBDEV-6255]  it will be possible
    }

  //Test shows that we should consider stratified sampling as default for case when we splitting training frame into valid and leaderboard frames
  @Test
  public void no_TE_stratified_tvl_splits_vs_random_tvl_splits_benchmark_with_holdout_evaluation() {
    AutoML aml=null;
    Frame fr = null;
    Frame[] splitsForWithoutTE=null;
    Frame frForWithoutTE = null;
    Model leader = null;
    Frame trainingFrame = null;
    String responseColumnName = "survived";
    Random generator = new Random();
    double avgAUCWith = 0.0;
    double avgAUCWithoutTE = 0.0;
    int numberOfRuns = 10;
    for (int seedAttempt = 0; seedAttempt < numberOfRuns; seedAttempt++) {
      long splitSeed = generator.nextLong();
      try {
        AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();

        fr = getPreparedTitanicFrame(responseColumnName);
        Frame[] splitsTrainTest = AutoMLBenchmarkingHelper.split2ByRatio(fr, 0.8, 0.2, splitSeed);
        Frame trainOrig = splitsTrainTest[0];
        Frame testFrame = splitsTrainTest[1];

        double trainRatio = 0.7; // This split ration proved to give more advantage to stratified strategy. but if we need just max performance on testFrame -> 0.8 is better
        Frame[] splits = AutoMLBenchmarkingHelper.getStratifiedSplits(trainOrig, responseColumnName, trainRatio, splitSeed);
        Frame train = splits[0];
        Frame valid = splits[1];
        Frame leaderboard = splits[2];

        autoMLBuildSpec.input_spec.training_frame = train._key;
        autoMLBuildSpec.input_spec.validation_frame = valid._key;
        autoMLBuildSpec.input_spec.leaderboard_frame = leaderboard._key;
        autoMLBuildSpec.build_control.nfolds = 0;
        autoMLBuildSpec.input_spec.response_column = responseColumnName;

        autoMLBuildSpec.build_models.exclude_algos = excludeAlgos;

        autoMLBuildSpec.te_spec.enabled = false;

        autoMLBuildSpec.build_control.project_name = "stratified_split_" + splitSeed;
        autoMLBuildSpec.build_control.stopping_criteria.set_max_models(numberOfModelsToCompareWith);
        autoMLBuildSpec.build_control.stopping_criteria.set_seed(autoMLSeed);
        autoMLBuildSpec.build_control.keep_cross_validation_models = false;
        autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

        aml = AutoML.startAutoML(autoMLBuildSpec);
        aml.get();

        leader = aml.leader();
        assertTrue(  aml.leaderboard().getModels().length == 1);

        double aucWithTE = getScoreBasedOn(testFrame, leader);

        trainingFrame = aml.getTrainingFrame();

        frForWithoutTE = trainOrig.deepCopy(Key.make().toString());
        DKV.put(frForWithoutTE);
        splitsForWithoutTE = AutoMLBenchmarkingHelper.getRandomSplitsFromDataframe(frForWithoutTE, new double[]{trainRatio, (1 - trainRatio) / 2, (1 - trainRatio) / 2} , splitSeed);
        Leaderboard leaderboardWithoutTE = trainBaselineAutoML_with_random_splits_for_tvl(splitsForWithoutTE, responseColumnName, splitSeed);
        Model leaderFromWithoutTE = leaderboardWithoutTE.getLeader();
        double aucWithoutTE = getScoreBasedOn(testFrame, leaderFromWithoutTE);

        System.out.println("Performance on leaderboard frame with TE: AUC = " + aucWithTE);
        System.out.println("Performance on leaderboard frame without TE: AUC = " + aucWithoutTE);
        avgAUCWith += aucWithTE;
        avgAUCWithoutTE += aucWithoutTE;

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
    Assert.assertTrue(avgAUCWith > avgAUCWithoutTE);
  }
  
  private Leaderboard trainBaselineAutoML_with_random_splits_for_tvl(Frame[] splits, String responseColumnName, long splitSeed) {
    Leaderboard leaderboard=null;
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
