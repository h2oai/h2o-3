package ai.h2o.automl;

import ai.h2o.automl.targetencoding.BlendingParams;
import ai.h2o.automl.targetencoding.TargetEncoder;
import ai.h2o.automl.targetencoding.TargetEncodingParams;
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

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * We want to test here the cases when we use Validation frame and Leaderboard frame is defined( either set explicitly or taken from validation when nfolds = 0)
 */
public class TEGridSearchIntegrationWithAutoMLValidationFrameTest extends water.TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

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
  
  @Test public void withValidationFrameScenarioTest() {
    AutoML aml=null;
    Frame[] splitsForWithoutTE=null;
    Model leader = null;
    Frame trainingFrame = null;
    String responseColumnName = "survived";
    Random generator = new Random();
    double avgAUCWith = 0.0;
    double avgAUCWithoutTE = 0.0;
    int numberOfRuns = 1;
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
                new GridSearchTEParamsSelectionStrategy(train, numberOfIterations, responseColumnName, thresholdTEApplicationStrategy.getColumnsToEncode(), true, seed);
        ;

        autoMLBuildSpec.te_spec.application_strategy = thresholdTEApplicationStrategy;
        autoMLBuildSpec.te_spec.params_selection_strategy = gridSearchTEParamsSelectionStrategy;

        autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
        autoMLBuildSpec.build_control.stopping_criteria.set_seed(7890);
        autoMLBuildSpec.build_control.keep_cross_validation_models = false;
        autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

        aml = AutoML.startAutoML(autoMLBuildSpec);
        aml.get();

        leader = aml.leader();

        double aucWithTE = getScoreBasedOn(splits[2], leader);

        trainingFrame = aml.getTrainingFrame();

        splitsForWithoutTE = getSplitsFromTitanicDataset(responseColumnName, splitSeed);
        Leaderboard leaderboardWithoutTE = trainBaselineAutoMLWithoutTE(splitsForWithoutTE, responseColumnName, seed);
        Model leaderFromWithoutTE = leaderboardWithoutTE.getLeader();
        double aucWithoutTE = getScoreBasedOn(splits[2], leaderFromWithoutTE);

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

  private Leaderboard trainBaselineAutoMLWithoutTE(Frame[] splits, String responseColumnName, long seed) {
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

      autoMLBuildSpec.build_control.project_name = "without_te";
      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
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

  // in contrast to withValidationFrameScenarioTest where best hyper parameter is unknown(any of 3), here we will test all 3 cases for every DataLeakageHandlingStrategy strategy.
  // We can do it with `FixedTEParamsStrategy` and use parameters that I found by searching through whole hyperspace.
  @Test public void KFoldTest() {
    long splitSeed = 1234;
    TargetEncodingParams targetEncodingParams = new TargetEncodingParams(new BlendingParams(50, 5), TargetEncoder.DataLeakageHandlingStrategy.KFold, 0.01);

    TEParamsSelectionStrategy selectionStrategy = new FixedTEParamsStrategy(targetEncodingParams);
    testWithStrategy(selectionStrategy, splitSeed);
  }
  
  
  @Test public void LOOTest() {
    long splitSeed = 1234;
    TargetEncodingParams targetEncodingParams = new TargetEncodingParams(new BlendingParams(100, 10), TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, 0.05);

    TEParamsSelectionStrategy selectionStrategy = new FixedTEParamsStrategy(targetEncodingParams);
    testWithStrategy(selectionStrategy, splitSeed);
  }
  
  @Test public void NoneTest() {
    long splitSeed = 1234;
    TargetEncodingParams targetEncodingParams = new TargetEncodingParams(new BlendingParams(3, 20), TargetEncoder.DataLeakageHandlingStrategy.None, 0.0);

    TEParamsSelectionStrategy selectionStrategy = new FixedTEParamsStrategy(targetEncodingParams);
    testWithStrategy(selectionStrategy, splitSeed);
  }
  
  private void testWithStrategy(TEParamsSelectionStrategy selectionStrategy, long splitSeed) {
      AutoML aml=null;
      Frame[] splitsForWithoutTE=null;
      Model leader = null;
      Frame trainingFrame = null;
      String responseColumnName = "survived";
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

        autoMLBuildSpec.te_spec.seed = 3456;
        long seed = autoMLBuildSpec.te_spec.seed;
        
        autoMLBuildSpec.build_control.project_name = "with_strategy";

        autoMLBuildSpec.te_spec.application_strategy = thresholdTEApplicationStrategy;
        autoMLBuildSpec.te_spec.params_selection_strategy = selectionStrategy;

        autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
        autoMLBuildSpec.build_control.stopping_criteria.set_seed(7890);
        autoMLBuildSpec.build_control.keep_cross_validation_models = false;
        autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

        aml = AutoML.startAutoML(autoMLBuildSpec);
        aml.get();

        leader = aml.leader();

        double aucWithTE = getScoreBasedOn(splits[2], leader);

        trainingFrame = aml.getTrainingFrame();

        splitsForWithoutTE = getSplitsFromTitanicDataset(responseColumnName, splitSeed);
        Leaderboard leaderboardWithoutTE = trainBaselineAutoMLWithoutTE( splitsForWithoutTE, responseColumnName, seed);
        Model leaderFromWithoutTE = leaderboardWithoutTE.getLeader();
        double aucWithoutTE = getScoreBasedOn(splits[2], leaderFromWithoutTE);

        System.out.println("Performance on leaderboard frame with TE: AUC = " + aucWithTE);
        System.out.println("Performance on leaderboard frame without TE: AUC = " + aucWithoutTE);

        assertTrue(aucWithTE > aucWithoutTE);

      } finally {
        if(leader!=null) leader.delete();
        if(aml!=null) aml.delete();
        if(trainingFrame != null)  trainingFrame.delete();
      }
  }

}
