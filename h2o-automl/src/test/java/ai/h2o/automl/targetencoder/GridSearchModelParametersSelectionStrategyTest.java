package ai.h2o.automl.targetencoder;

import ai.h2o.automl.AutoMLBuildSpec;
import ai.h2o.automl.targetencoder.strategy.GridSearchModelParametersSelectionStrategy;
import ai.h2o.automl.targetencoder.strategy.ModelParametersSelectionStrategy;
import ai.h2o.automl.targetencoder.strategy.ModelValidationMode;
import ai.h2o.targetencoding.TargetEncoderModel;
import ai.h2o.targetencoding.strategy.TEApplicationStrategy;
import ai.h2o.targetencoding.strategy.ThresholdTEApplicationStrategy;
import hex.ModelBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Random;

import static ai.h2o.automl.targetencoder.strategy.ModelValidationMode.CV;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Enclosed.class)
public class GridSearchModelParametersSelectionStrategyTest {

  public static class GridSearchModelParametersSelectionStrategyNonParametrizedTest extends water.TestUtil {

    @Test
    public void priorityQueueOrderingWithEvaluatedTest() {
      boolean theBiggerTheBetter = true;
      Comparator comparator = new GridSearchModelParametersSelectionStrategy.EvaluatedComparator(theBiggerTheBetter);
      PriorityQueue<GridSearchModelParametersSelectionStrategy.Evaluated<TargetEncoderModel.TargetEncoderParameters>> evaluatedQueue = new PriorityQueue<>(comparator);


      TargetEncoderModel.TargetEncoderParameters params1 = TargetEncodingTestFixtures.randomTEParams();
      TargetEncoderModel.TargetEncoderParameters params2 = TargetEncodingTestFixtures.randomTEParams();
      TargetEncoderModel.TargetEncoderParameters params3 = TargetEncodingTestFixtures.randomTEParams();
      evaluatedQueue.add(new GridSearchModelParametersSelectionStrategy.Evaluated<>(params1, 0.9984, 0));
      evaluatedQueue.add(new GridSearchModelParametersSelectionStrategy.Evaluated<>(params2, 0.9996, 1));
      evaluatedQueue.add(new GridSearchModelParametersSelectionStrategy.Evaluated<>(params3, 0.9784, 2));

      assertEquals(0.9996, evaluatedQueue.poll().getScore(), 1e-5);
      assertEquals(0.9984, evaluatedQueue.poll().getScore(), 1e-5);
      assertEquals(0.9784, evaluatedQueue.poll().getScore(), 1e-5);

    }
  }

  @RunWith(Parameterized.class)
  public static class GridSearchModelParametersSelectionReturnsBestParametrizedTest extends TestUtil {

    @BeforeClass
    public static void setup() {
      stall_till_cloudsize(1);
    }

    @Parameterized.Parameters(name = "Validation mode = {0}, task type = {1}")
    public static Object[][] validationMode() {
      return new Object[][]{
              {CV, "regression"},
              {CV, "classification"},
              {ModelValidationMode.VALIDATION_FRAME, "regression"},
              {ModelValidationMode.VALIDATION_FRAME, "classification"},
      };
    }

    @Parameterized.Parameter
    public ModelValidationMode validationMode;

    @Parameterized.Parameter (value = 1)
    public String taskType;

    @Test
    public void strategy_returns_best_parameters() {
      Scope.enter();
      try {
        Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
        Scope.track(fr);
        long seed = 1234;

        String responseColumnName;
        boolean theBiggerTheBetter;
        if (taskType.equals("classification")) {
          responseColumnName = "survived";
          asFactor(fr, responseColumnName);
          theBiggerTheBetter = true; // accuracy, f1 but not true for logloss
        } else {
          responseColumnName = "age";
          theBiggerTheBetter = false;
        }

        Frame train = null;
        Frame valid = null;
        Frame leaderboard = null;
        switch (validationMode) {
          case CV:
            train = fr;
            break;
          case VALIDATION_FRAME:
            Frame[] splits = AutoMLBenchmarkHelper.getRandomSplitsFromDataframe(fr, new double[]{0.7, 0.15, 0.15}, 2345L);
            train = splits[0];
            valid = splits[1];
            leaderboard = splits[2];
            Scope.track(train, valid, leaderboard);
        }

        TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(fr, 4, new String[]{"survived"});
        String[] columnsToEncode = strategy.getColumnsToEncode();


        AutoMLBuildSpec.AutoMLTEControl teBuildSpec = new AutoMLBuildSpec.AutoMLTEControl();
        teBuildSpec.seed = seed;
        teBuildSpec.te_max_models = 0;


        ModelBuilder mb = null;
        switch (validationMode) {
          case CV:
            mb = TargetEncodingTestFixtures.modelBuilderGBMWithCVFixture(train, responseColumnName, seed);
            break;
          case VALIDATION_FRAME:
            mb = TargetEncodingTestFixtures.modelBuilderGBMWithValidFrameFixture(train, valid, responseColumnName, seed);
        }
        mb.init(false);

        ModelParametersEvaluator<TargetEncoderModel.TargetEncoderParameters> evaluator = new DummyEvaluator();


        GridSearchModelParametersSelectionStrategy gridSearchTEParamsSelectionStrategy =
                new GridSearchModelParametersSelectionStrategy(mb, teBuildSpec, leaderboard, columnsToEncode, validationMode, evaluator);

        ModelParametersSelectionStrategy.Evaluated<TargetEncoderModel.TargetEncoderParameters> bestParamsWithEvaluation =
                gridSearchTEParamsSelectionStrategy.getBestParamsWithEvaluation();

        assertTrue(gridSearchTEParamsSelectionStrategy.getEvaluatedModelParameters()
                .stream()
                .allMatch(mp -> {
                  if (theBiggerTheBetter)
                    return mp.getScore() <= bestParamsWithEvaluation.getScore();
                  else
                    return mp.getScore() >= bestParamsWithEvaluation.getScore();
                })
        );
      } finally {
        Scope.exit();
      }
    }
  }


  @RunWith(Parameterized.class)
  public static class GridSearchModelParametersSelectionStrategyParametrizedTest extends TestUtil {

    @BeforeClass
    public static void setup() {
      stall_till_cloudsize(1);
    }

    @Parameterized.Parameters(name = "Validation mode = {0}")
    public static Object[] validationMode() {
      return new ModelValidationMode[]{
              CV, ModelValidationMode.VALIDATION_FRAME
      };
    }

    @Parameterized.Parameter
    public ModelValidationMode validationMode;

    @Test
    public void hyper_space_depends_on_validation_mode() {
      Scope.enter();
      try {
        Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
        Scope.track(fr);
        long seed = 1234;

        String responseColumnName = "survived";

        asFactor(fr, responseColumnName);
        Frame train = null;
        Frame valid = null;
        Frame leaderboard = null;
        switch (validationMode) {
          case CV:
            train = fr;
            break;
          case VALIDATION_FRAME:
            Frame[] splits = AutoMLBenchmarkHelper.getRandomSplitsFromDataframe(fr, new double[]{0.7, 0.15, 0.15}, 2345L);
            train = splits[0];
            valid = splits[1];
            leaderboard = splits[2];
            Scope.track(train, valid, leaderboard);
        }

        TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(fr, 4, new String[]{"survived"});
        String[] columnsToEncode = strategy.getColumnsToEncode();


        AutoMLBuildSpec.AutoMLTEControl teBuildSpec = new AutoMLBuildSpec.AutoMLTEControl();
        teBuildSpec.seed = seed;
        teBuildSpec.te_max_models = 0;


        ModelBuilder mb = null;
        switch (validationMode) {
          case CV:
            mb = TargetEncodingTestFixtures.modelBuilderGBMWithCVFixture(train, responseColumnName, seed);
            break;
          case VALIDATION_FRAME:
            mb = TargetEncodingTestFixtures.modelBuilderGBMWithValidFrameFixture(train, valid, responseColumnName, seed);
        }
        mb.init(false);

        ModelParametersEvaluator<TargetEncoderModel.TargetEncoderParameters> evaluator = new DummyEvaluator();


        GridSearchModelParametersSelectionStrategy gridSearchTEParamsSelectionStrategy =
                new GridSearchModelParametersSelectionStrategy(mb, teBuildSpec, leaderboard, columnsToEncode, validationMode, evaluator);

        ModelParametersSelectionStrategy.Evaluated<TargetEncoderModel.TargetEncoderParameters> bestParamsWithEvaluation =
                gridSearchTEParamsSelectionStrategy.getBestParamsWithEvaluation();

        if(validationMode == CV) {
          int sizeOfEmbeddedTEHyperSpaceForCV = 63;
          assertEquals(sizeOfEmbeddedTEHyperSpaceForCV, gridSearchTEParamsSelectionStrategy.getEvaluatedModelParameters().size());
        }
        else {
          int sizeOfEmbeddedTEHyperSpaceForValidation = 63 * 3;
          assertEquals(sizeOfEmbeddedTEHyperSpaceForValidation, gridSearchTEParamsSelectionStrategy.getEvaluatedModelParameters().size());
        }

      } finally {
        Scope.exit();
      }
    }

    @Test
    public void max_models_is_being_respected() {
      Scope.enter();
      try {
        Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
        Scope.track(fr);
        long seed = 1234;

        String responseColumnName = "survived";

        asFactor(fr, responseColumnName);
        Frame train = null;
        Frame valid = null;
        Frame leaderboard = null;
        switch (validationMode) {
          case CV:
            train = fr;
            break;
          case VALIDATION_FRAME:
            Frame[] splits = AutoMLBenchmarkHelper.getRandomSplitsFromDataframe(fr, new double[]{0.7, 0.15, 0.15}, 2345L);
            train = splits[0];
            valid = splits[1];
            leaderboard = splits[2];
            Scope.track(train, valid, leaderboard);
        }

        TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(fr, 4, new String[]{"survived"});
        String[] columnsToEncode = strategy.getColumnsToEncode();


        AutoMLBuildSpec.AutoMLTEControl teBuildSpec = new AutoMLBuildSpec.AutoMLTEControl();
        teBuildSpec.seed = seed;
        teBuildSpec.te_max_models = 5;

        ModelBuilder mb = null;
        switch (validationMode) {
          case CV:
            mb = TargetEncodingTestFixtures.modelBuilderGBMWithCVFixture(train, responseColumnName, seed);
            break;
          case VALIDATION_FRAME:
            mb = TargetEncodingTestFixtures.modelBuilderGBMWithValidFrameFixture(train, valid, responseColumnName, seed);
        }
        mb.init(false);

        TargetEncodingHyperparamsEvaluator evaluator = new TargetEncodingHyperparamsEvaluator();
        GridSearchModelParametersSelectionStrategy gridSearchTEParamsSelectionStrategy =
                new GridSearchModelParametersSelectionStrategy(mb, teBuildSpec, leaderboard, columnsToEncode, validationMode, evaluator);

        gridSearchTEParamsSelectionStrategy.getBestParamsWithEvaluation();

        assertEquals(teBuildSpec.te_max_models, gridSearchTEParamsSelectionStrategy.getEvaluatedModelParameters().size());

      } finally {
        Scope.exit();
      }
    }

  }


  private static class DummyEvaluator extends ModelParametersEvaluator<TargetEncoderModel.TargetEncoderParameters> {
    @Override
    public double evaluate(TargetEncoderModel.TargetEncoderParameters modelParameters, ModelBuilder modelBuilder, ModelValidationMode modelValidationMode, Frame leaderboard, String[] columnNamesToEncode, long seedForFoldColumn) {
      return new Random().nextDouble();
    }
  }

}