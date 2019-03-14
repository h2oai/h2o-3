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
import water.fvec.Vec;

import java.util.Random;

import static ai.h2o.automl.AutoMLBenchmarkingHelper.getCumulativeLeaderboardScore;
import static ai.h2o.automl.AutoMLBenchmarkingHelper.getScoreBasedOn;
import static org.junit.Assert.assertTrue;

/**
 * We want to test here the cases when we use Validation frame for Early Stopping
 */
public class TEIntegrationWithAutoMLValidationFrameBenchmark extends water.TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  private Frame getPreparedTitanicFrame(String responseColumnName) {
    Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
    fr.remove(new String[]{"name", "ticket", "boat", "body"});
    asFactor(fr, responseColumnName);
    return fr;
  }
  
  long autoMLSeed = 7890;

  int numberOfModelsToCompareWith = 1;
  Algo[] excludeAlgos = {Algo.DeepLearning /*Algo.DRF*//*, Algo.GLM,*/,  Algo.XGBoost /* Algo.GBM,*/, Algo.StackedEnsemble};


  @Test
  public void random_split_benchmark_with_holdout_evaluation() {
    // We can't do holdout testing for TE as we don't have encoding map to apply it to testFrame. After Mojo task [PUBDEV-6255]  it will be possible
  }
  
  @Test
  public void random_split_benchmark_with_leaderboard_evaluation() {
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
    
    int numberOfRuns = 3;
    for (int seedAttempt = 0; seedAttempt < numberOfRuns; seedAttempt++) {
      long splitSeed = generator.nextLong();
      try {
        AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();

        fr = getPreparedTitanicFrame(responseColumnName);
        Frame[] splits = AutoMLBenchmarkingHelper.getRandomSplitsFromDataframe(fr, new double[] {0.8, 0.1, 0.1}, splitSeed);
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

        int numberOfIterations = 378;
        autoMLBuildSpec.te_spec.seed = 3456;
        long seed = autoMLBuildSpec.te_spec.seed;
        TEParamsSelectionStrategy gridSearchTEParamsSelectionStrategy =
                new GridSearchTEParamsSelectionStrategy(leaderboardFrame, numberOfIterations, responseColumnName, thresholdTEApplicationStrategy.getColumnsToEncode(), true, seed);

        autoMLBuildSpec.te_spec.application_strategy = thresholdTEApplicationStrategy;
        autoMLBuildSpec.te_spec.params_selection_strategy = gridSearchTEParamsSelectionStrategy;

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
        splitsForWithoutTE = AutoMLBenchmarkingHelper.getRandomSplitsFromDataframe(frForWithoutTE, new double[] {0.8, 0.1, 0.1}, splitSeed);
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

    @Test  
    public void stratified_tvl_splits_benchmark_with_holdout_evaluation() {
      // We can't do holdout testing for TE as we don't have encoding map to apply it to testFrame. After Mojo task [PUBDEV-6255]  it will be possible
    }
    
    @Test  
    public void stratified_tvl_splits_benchmark_with_leaderboard_evaluation() {
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

      double avgCumulativeAUCWith = 0.0;
      double avgCumulativeWithoutTE = 0.0;
      
      int numberOfRuns = 3;
      for (int seedAttempt = 0; seedAttempt < numberOfRuns; seedAttempt++) {
        long splitSeed = generator.nextLong();
        try {
          AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();

          fr = getPreparedTitanicFrame(responseColumnName);

          Frame[] splits = AutoMLBenchmarkingHelper.getStratifiedSplits(fr, responseColumnName, 0.8, splitSeed);
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

          int numberOfIterations = 378;
          autoMLBuildSpec.te_spec.seed = 3456;
          long seed = autoMLBuildSpec.te_spec.seed;
          TEParamsSelectionStrategy gridSearchTEParamsSelectionStrategy =
                  new GridSearchTEParamsSelectionStrategy(leaderboardFrame, numberOfIterations, responseColumnName, thresholdTEApplicationStrategy.getColumnsToEncode(), true, seed);

          autoMLBuildSpec.te_spec.application_strategy = thresholdTEApplicationStrategy;
          autoMLBuildSpec.te_spec.params_selection_strategy = gridSearchTEParamsSelectionStrategy;

          autoMLBuildSpec.build_control.project_name = "with_te_" + splitSeed;
          autoMLBuildSpec.build_control.stopping_criteria.set_max_models(numberOfModelsToCompareWith);
          autoMLBuildSpec.build_control.stopping_criteria.set_seed(autoMLSeed);
          autoMLBuildSpec.build_control.keep_cross_validation_models = false;
          autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

          aml = AutoML.startAutoML(autoMLBuildSpec);
          aml.get();

          leader = aml.leader();
          Leaderboard leaderboardWithTE = aml.leaderboard();
          assertTrue(  leaderboardWithTE.getModels().length == numberOfModelsToCompareWith);
          double cumulativeLeaderboardScoreWithTE = 0;
          cumulativeLeaderboardScoreWithTE = getCumulativeLeaderboardScore(leaderboardFrame, leaderboardWithTE);
          
          double aucWithTE = getScoreBasedOn(leaderboardFrame, leader);

          trainingFrame = aml.getTrainingFrame();

          frForWithoutTE = fr.deepCopy(Key.make().toString());
          DKV.put(frForWithoutTE);
          splitsForWithoutTE = AutoMLBenchmarkingHelper.getStratifiedSplits(frForWithoutTE, responseColumnName, 0.8, splitSeed);
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
        Leaderboard leaderboardWithoutTE = trainBaselineAutoMLWithoutTE(splitsForWithoutTE, responseColumnName, splitSeed);
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

  @Test
  public void random_split_with_SMBO_benchmark_with_leaderboard_evaluation() {
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
        Frame[] splits = AutoMLBenchmarkingHelper.getRandomSplitsFromDataframe(fr, new double[] {0.8, 0.1, 0.1}, splitSeed);
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

        autoMLBuildSpec.te_spec.seed = 3456;
        long seed = autoMLBuildSpec.te_spec.seed;
        TEParamsSelectionStrategy gridSearchTEParamsSelectionStrategy =
                new SMBOTEParamsSelectionStrategy(leaderboardFrame, 0.2, responseColumnName, thresholdTEApplicationStrategy.getColumnsToEncode(), true, seed);

        autoMLBuildSpec.te_spec.application_strategy = thresholdTEApplicationStrategy;
        autoMLBuildSpec.te_spec.params_selection_strategy = gridSearchTEParamsSelectionStrategy;

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
        splitsForWithoutTE = AutoMLBenchmarkingHelper.getRandomSplitsFromDataframe(frForWithoutTE, new double[] {0.8, 0.1, 0.1}, splitSeed);
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

  // in contrast to `validation_frame_early_stopping_with_random_split_benchmark` where best hyper parameter is unknown(any of 3), here we will test all 3 cases for every DataLeakageHandlingStrategy strategy.
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
  
  // need to make it also averaged over N runs
  private void testWithStrategy(TEParamsSelectionStrategy selectionStrategy, long splitSeed) {
      /*AutoML aml=null;
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
        Leaderboard leaderboardWithoutTE = trainBaselineAutoMLWithoutTE( splitsForWithoutTE, responseColumnName, seed, splitSeed);
        Model leaderFromWithoutTE = leaderboardWithoutTE.getLeader();
        double aucWithoutTE = getScoreBasedOn(splits[2], leaderFromWithoutTE);

        System.out.println("Performance on leaderboard frame with TE: AUC = " + aucWithTE);
        System.out.println("Performance on leaderboard frame without TE: AUC = " + aucWithoutTE);

        assertTrue(aucWithTE > aucWithoutTE);

      } finally {
        if(leader!=null) leader.delete();
        if(aml!=null) aml.delete();
        if(trainingFrame != null)  trainingFrame.delete();
      }*/
  }

}
