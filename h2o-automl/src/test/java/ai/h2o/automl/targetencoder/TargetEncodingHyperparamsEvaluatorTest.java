package ai.h2o.automl.targetencoder;

import ai.h2o.automl.targetencoder.strategy.GridSearchTEParamsSelectionStrategy;
import ai.h2o.automl.targetencoder.strategy.ModelValidationMode;
import ai.h2o.automl.targetencoder.strategy.TEParamsSelectionStrategy;
import ai.h2o.targetencoding.TargetEncoder;
import ai.h2o.targetencoding.strategy.TEApplicationStrategy;
import ai.h2o.targetencoding.strategy.ThresholdTEApplicationStrategy;
import hex.Model;
import hex.ModelBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Frame;

import java.util.HashMap;
import java.util.Random;

import static ai.h2o.automl.targetencoder.TargetEncodingTestFixtures.modelBuilderGBMWithCVFixture;
import static ai.h2o.automl.targetencoder.TargetEncodingTestFixtures.modelBuilderGBMWithValidFrameFixture;
import static org.junit.Assert.*;

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
  public void evaluateMethodWorksWithModelBuilder() {
    Frame fr = null;
    Frame train = null;
    Frame valid = null;
    Frame leaderboard = null;
    ModelBuilder modelBuilder = null;
    try {
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      String responseColumnName = "survived";

      asFactor(fr, responseColumnName);

      Frame[] splits = AutoMLBenchmarkHelper.getRandomSplitsFromDataframe(fr, new double[]{0.7, 0.15, 0.15}, 2345);
      train = splits[0];
      valid = splits[1];
      leaderboard = splits[2];

      TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(train,4, new String[]{responseColumnName});
      String[] columnsToEncode = strategy.getColumnsToEncode();

      TargetEncodingHyperparamsEvaluator evaluator = new TargetEncodingHyperparamsEvaluator();

      TargetEncodingParams randomTEParams = TargetEncodingTestFixtures.randomTEParams(columnsToEncode);
      long builderSeed = 3456;
      modelBuilder = modelBuilderGBMWithValidFrameFixture(train, valid, responseColumnName, builderSeed);


      modelBuilder.init(false); //verifying that we can call init and then modify builder in evaluator
      ModelBuilder clonedModelBuilder = ModelBuilder.make(modelBuilder._parms);

      int seedForFoldColumn = 2345;
      double auc = evaluator.evaluate(randomTEParams, modelBuilder, ModelValidationMode.VALIDATION_FRAME, leaderboard, seedForFoldColumn);

      clonedModelBuilder.init(false);

      // checking that we can clone/reuse modelBuilder
      double auc2 = evaluator.evaluate(randomTEParams, clonedModelBuilder, ModelValidationMode.VALIDATION_FRAME, leaderboard, seedForFoldColumn);

      assertTrue(isBitIdentical(clonedModelBuilder._parms.train(), modelBuilder._parms.train()));
      assertTrue(auc > 0);
      assertEquals(auc, auc2, 1e-5);
    } finally {
      if(fr!=null) fr.delete();
      if(train!=null) train.delete();
      if(leaderboard!=null) leaderboard.delete();
    }
  }

  @Test
  public void evaluateMethodWorksWithModelBuilder_CV_case() {
    Frame train = null;
    Frame leaderboard = null;
    ModelBuilder modelBuilder = null;
    try {
      train = parse_test_file("./smalldata/gbm_test/titanic.csv");
      String responseColumnName = "survived";

      asFactor(train, responseColumnName);

      leaderboard = null;

      TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(train,4, new String[]{responseColumnName});
      String[] columnsToEncode = strategy.getColumnsToEncode();

      TargetEncodingHyperparamsEvaluator evaluator = new TargetEncodingHyperparamsEvaluator();

      TargetEncodingParams randomTEParams = TargetEncodingTestFixtures.randomTEParams(columnsToEncode, TargetEncoder.DataLeakageHandlingStrategy.KFold, 1234);
      long builderSeed = 3456;
      modelBuilder = modelBuilderGBMWithCVFixture(train,responseColumnName, builderSeed);


      modelBuilder.init(false); //verifying that we can call init and then modify builder in evaluator
      ModelBuilder clonedModelBuilder = ModelBuilder.make(modelBuilder._parms);

      int seedForFoldColumn = 2345;
      double auc = evaluator.evaluate(randomTEParams, modelBuilder, ModelValidationMode.CV, leaderboard, seedForFoldColumn);

      clonedModelBuilder.init(false);

      // checking that we can clone/reuse modelBuilder
      double auc2 = evaluator.evaluate(randomTEParams, clonedModelBuilder, ModelValidationMode.CV, leaderboard, seedForFoldColumn);

      assertTrue(isBitIdentical(clonedModelBuilder._parms.train(), modelBuilder._parms.train()));
      assertTrue(auc > 0);
      assertEquals(auc, auc2, 1e-5);
    } finally {
      if(train!=null) train.delete();
    }
  }

  @Test
  public void checkThatForAnyHyperParametersCombinationWeGetConsistentEvaluationsFromModelBuilderFixture() {

    //Important variable as AUC are not consistent with precision 1e-4 and less
    double precisionForAUCEvaluations = 1e-5;

    HashMap<String, Object[]> _grid = new HashMap<>();
    _grid.put("_withBlending", new Double[]{1.0});
    _grid.put("_noise_level", new Double[]{0.0, 0.1, 0.01});
    _grid.put("_inflection_point", new Double[]{1.0, 2.0, 3.0, 5.0, 10.0, 50.0, 100.0});
    _grid.put("_smoothing", new Double[]{5.0, 10.0, 20.0});
    _grid.put("_holdoutType", new Double[]{0.0, 1.0, 2.0});

    long builderSeed = new Random().nextLong();

    TargetEncodingHyperparamsEvaluator targetEncodingHyperparamsEvaluator = new TargetEncodingHyperparamsEvaluator();

    Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
    String responseColumnName = "survived";
    asFactor(fr, responseColumnName);
    ModelBuilder modelBuilder = modelBuilderGBMWithCVFixture(fr, responseColumnName, builderSeed);
    modelBuilder.init(false); // Should we init before cloning? Like in real use case we clone after initialisation of the original modelBuilder.

    for (int teParamAttempt = 0; teParamAttempt < 3; teParamAttempt++) {
      long testSeed = new Random().nextLong();

      TEParamsSelectionStrategy.RandomGridEntrySelector randomGridEntrySelector = new TEParamsSelectionStrategy.RandomGridEntrySelector(_grid, testSeed);
      GridSearchTEParamsSelectionStrategy.GridEntry selected = null;
      try {
        selected = randomGridEntrySelector.getNext();
      } catch (TEParamsSelectionStrategy.RandomGridEntrySelector.GridSearchCompleted ex) {

      }

      TargetEncodingParams param = new TargetEncodingParams(selected.getItem());

      double lastResult = 0.0;
      for (int evaluationAttempt = 0; evaluationAttempt < 3; evaluationAttempt++) {
        ModelBuilder clonedModelBuilder = ModelBuilder.make(modelBuilder._parms);
        clonedModelBuilder.init(false);

        double evaluationResult = targetEncodingHyperparamsEvaluator.evaluate(param, clonedModelBuilder, ModelValidationMode.CV, null, testSeed);
        if(lastResult == 0.0) lastResult = evaluationResult;
        else {

          assertEquals("evaluationAttempt #" + evaluationAttempt + " for teParamAttempt #" +
                  teParamAttempt + " has failed. " + param , lastResult, evaluationResult, precisionForAUCEvaluations);
        }
      }
    }

    fr.delete();
  }

  @Test
  public void evaluateMethodWorksWithModelBuilderAndIgnoredColumns() {
    //TODO check case when original model builder has ignored columns
    // how to check which columns have been used during training? through variable importance object?
  }

}