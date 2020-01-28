package ai.h2o.automl.targetencoder;

import ai.h2o.automl.targetencoder.strategy.ModelValidationMode;
import ai.h2o.targetencoding.TargetEncoder;
import ai.h2o.targetencoding.TargetEncoderModel;
import ai.h2o.targetencoding.strategy.AllCategoricalTEApplicationStrategy;
import ai.h2o.targetencoding.strategy.TEApplicationStrategy;
import ai.h2o.targetencoding.strategy.ThresholdTEApplicationStrategy;
import hex.Model;
import hex.ModelBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Frame;

import java.util.Random;

import static ai.h2o.automl.targetencoder.TargetEncodingTestFixtures.modelBuilderGBMWithCVFixture;
import static ai.h2o.automl.targetencoder.TargetEncodingTestFixtures.modelBuilderGBMWithValidFrameFixture;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TargetEncodingHyperparamsEvaluatorTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void fixtureForMBTest() {

    Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
    String responseColumnName = "survived";

    asFactor(fr, responseColumnName);

    Frame[] splits = AutoMLBenchmarkHelper.getRandomSplitsFromDataframe(fr, new double[]{0.7, 0.3}, 2345);
    Frame train = splits[0];
    Frame valid = splits[1];
    long builderSeed = 3456;
    ModelBuilder modelBuilder = modelBuilderGBMWithValidFrameFixture(train, valid, responseColumnName, builderSeed);

    // Just testing that we can train a model from the fixture
    Keyed model = modelBuilder.trainModel().get();

    // Checking that we can't reuse model builder as after the training it is being left in unusable state
    assertTrue(modelBuilder.train().vec("Tree_0") != null);

    fr.delete();
    train.delete();
    valid.delete();

    Model retrievedModel = DKV.getGet(model._key);
    retrievedModel.delete();
  }

  // We test here that we can reuse model builder by cloning it.
  // Also we check that two evaluations with the same TE params return same result.
  @Test
  public void evaluate_method_works_with_cloned_modelBuilder_validation_case() {

    ModelBuilder modelBuilder = null;
    Scope.enter();
    try {
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      Scope.track(fr);
      String responseColumnName = "survived";

      asFactor(fr, responseColumnName);

      Frame[] splits = AutoMLBenchmarkHelper.getRandomSplitsFromDataframe(fr, new double[]{0.7, 0.15, 0.15}, 2345);
      Frame train = splits[0];
      Frame valid = splits[1];
      Frame leaderboard = splits[2];
      Scope.track(train, valid, leaderboard);

      TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(train,4, new String[]{responseColumnName});
      String[] columnsToEncode = strategy.getColumnsToEncode();

      TargetEncodingHyperparamsEvaluator evaluator = new TargetEncodingHyperparamsEvaluator();

      TargetEncoderModel.TargetEncoderParameters randomTEParams = TargetEncodingTestFixtures.randomTEParams(1234);
      long builderSeed = 3456;
      modelBuilder = modelBuilderGBMWithValidFrameFixture(train, valid, responseColumnName, builderSeed);

      modelBuilder.init(false); //verifying that we can call findBestTEParams and then modify builder in evaluator

      int seedForFoldColumn = 2345;

      // Copying frames outside of an evaluation
      Frame trainCopy = train.deepCopy(Key.make("train_frame_copy_for_mb_validation_case" + Key.make()).toString());
      DKV.put(trainCopy);
      modelBuilder.setTrain(trainCopy);

      Frame validCopy = valid.deepCopy(Key.make("valid_frame_copy_for_mb_validation_case" + Key.make()).toString());
      DKV.put(validCopy);
      modelBuilder.setValid(validCopy);

      Frame leaderboardCopy = leaderboard.deepCopy(Key.make("leaderboard_frame_copy_for_mb_validation_case" + Key.make()).toString());
      DKV.put(leaderboardCopy);
      Scope.track(trainCopy, validCopy, leaderboardCopy);

      double auc = evaluator.evaluate(randomTEParams, modelBuilder, ModelValidationMode.VALIDATION_FRAME, leaderboardCopy, columnsToEncode, seedForFoldColumn);

      ModelBuilder clonedModelBuilder = ModelBuilder.make(modelBuilder._parms);
      clonedModelBuilder.init(false);

      Frame trainCopy2 = train.deepCopy(Key.make("train_frame_copy_for_mb_validation_case" + Key.make()).toString());
      DKV.put(trainCopy2);
      clonedModelBuilder.setTrain(trainCopy2);

      Frame validCopy2 = valid.deepCopy(Key.make("valid_frame_copy_for_mb_validation_case" + Key.make()).toString());
      DKV.put(validCopy2);
      clonedModelBuilder.setValid(validCopy2);

      Frame leaderboardCopy2 = leaderboard.deepCopy(Key.make("leaderboard_frame_copy_for_mb_validation_case" + Key.make()).toString());
      DKV.put(leaderboardCopy2);
      Scope.track(trainCopy2, validCopy2, leaderboardCopy2);

      // checking that we can clone/reuse modelBuilder
      double auc2 = evaluator.evaluate(randomTEParams, clonedModelBuilder, ModelValidationMode.VALIDATION_FRAME, leaderboardCopy2, columnsToEncode, seedForFoldColumn);

      assertBitIdentical(clonedModelBuilder._parms.train(), modelBuilder._parms.train());
      assertTrue(auc > 0);
      assertEquals(auc, auc2, 1e-5);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void evaluate_method_works_with_cloned_modelBuilder_CV_case() {
    Frame leaderboard = null;
    ModelBuilder modelBuilder = null;
    Scope.enter();
    try {
      Frame train = parse_test_file("./smalldata/gbm_test/titanic.csv");
      Scope.track(train);
      String responseColumnName = "survived";
      asFactor(train, responseColumnName);

      leaderboard = null;

      TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(train,4, new String[]{responseColumnName});
      String[] columnsToEncode = strategy.getColumnsToEncode();

      TargetEncodingHyperparamsEvaluator evaluator = new TargetEncodingHyperparamsEvaluator();

      TargetEncoderModel.TargetEncoderParameters randomTEParams = TargetEncodingTestFixtures.randomTEParams(TargetEncoder.DataLeakageHandlingStrategy.KFold, 1234);
      long builderSeed = 3456;
      modelBuilder = modelBuilderGBMWithCVFixture(train,responseColumnName, builderSeed);


      modelBuilder.init(false); //verifying that we can call findBestTEParams and then modify builder in evaluator

      int seedForFoldColumn = 2345;

      // Model builder should contain copies of original frames and we should not care about cleanup inside evaluator
      Frame trainCopy = train.deepCopy(Key.make("train_frame_copy_for_mb" + Key.make()).toString());
      DKV.put(trainCopy);
      Scope.track(trainCopy);
      modelBuilder.setTrain(trainCopy);

      double auc = evaluator.evaluate(randomTEParams, modelBuilder, ModelValidationMode.CV, leaderboard, columnsToEncode, seedForFoldColumn);

      ModelBuilder clonedModelBuilder = ModelBuilder.make(modelBuilder._parms);
      clonedModelBuilder.init(false);

      Frame trainCopy2 = train.deepCopy(Key.make("train_frame_copy_for_mb_2" + Key.make()).toString());
      DKV.put(trainCopy2);
      Scope.track(trainCopy2);
      clonedModelBuilder.setTrain(trainCopy2);

      // checking that we can clone/reuse modelBuilder
      double auc2 = evaluator.evaluate(randomTEParams, clonedModelBuilder, ModelValidationMode.CV, leaderboard, columnsToEncode, seedForFoldColumn);

      assertBitIdentical(clonedModelBuilder._parms.train(), modelBuilder._parms.train());
      assertTrue(auc > 0);
      assertEquals(auc, auc2, 1e-5);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void checkThatForAnyHyperParametersCombinationWeGetConsistentEvaluationsFromModelBuilderFixture() {

    try {
      Scope.enter();
    //Important variable as AUC are not consistent with precision 1e-4 and less
    double precisionForAUCEvaluations = 1e-5;
    long builderSeed = new Random().nextLong();

    TargetEncodingHyperparamsEvaluator targetEncodingHyperparamsEvaluator = new TargetEncodingHyperparamsEvaluator();

    Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
    String responseColumnName = "survived";
    asFactor(fr, responseColumnName);
    Scope.track(fr);
    ModelBuilder modelBuilder = modelBuilderGBMWithCVFixture(fr, responseColumnName, builderSeed);
    modelBuilder.init(false); // Should we findBestTEParams before cloning? Like in real use case we clone after initialisation of the original modelBuilder.

    for (int teParamAttempt = 0; teParamAttempt < 1; teParamAttempt++) {
      long testSeed = new Random().nextLong();

      TargetEncoderModel.TargetEncoderParameters teParams = TargetEncodingTestFixtures.randomTEParams(TargetEncoder.DataLeakageHandlingStrategy.KFold, testSeed);

      AllCategoricalTEApplicationStrategy allCategoricalTEApplicationStrategy = new AllCategoricalTEApplicationStrategy(fr, new String[]{responseColumnName});

      double lastResult = 0.0;
      for (int evaluationAttempt = 0; evaluationAttempt < 1; evaluationAttempt++) {
        ModelBuilder clonedModelBuilder = ModelBuilder.make(modelBuilder._parms);
        clonedModelBuilder.init(false);
        double evaluationResult = targetEncodingHyperparamsEvaluator.evaluate(teParams,
                clonedModelBuilder,
                ModelValidationMode.CV,
                null,
                allCategoricalTEApplicationStrategy.getColumnsToEncode(),
                testSeed);

        if(lastResult == 0.0) lastResult = evaluationResult;
        else {
          assertEquals("evaluationAttempt #" + evaluationAttempt + " for teParamAttempt #" +
                  teParamAttempt + " has failed. " + teParams , lastResult, evaluationResult, precisionForAUCEvaluations);
        }
      }
    }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void evaluateMethodWorksWithModelBuilderAndIgnoredColumns() {
    //TODO check case when original model builder has ignored columns
    // how to check which columns have been used during training? through variable importance object?
  }

}