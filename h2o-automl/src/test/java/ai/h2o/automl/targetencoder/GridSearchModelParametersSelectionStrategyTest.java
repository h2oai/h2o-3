package ai.h2o.automl.targetencoder;

import ai.h2o.automl.AutoMLBuildSpec;
import ai.h2o.automl.targetencoder.strategy.GridSearchModelParametersSelectionStrategy;
import ai.h2o.automl.targetencoder.strategy.ModelParametersSelectionStrategy;
import ai.h2o.automl.targetencoder.strategy.ModelValidationMode;
import ai.h2o.targetencoding.TargetEncoderModel;
import ai.h2o.targetencoding.strategy.TEApplicationStrategy;
import ai.h2o.targetencoding.strategy.ThresholdTEApplicationStrategy;
import hex.*;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.*;
import water.fvec.Frame;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Random;

import static ai.h2o.automl.targetencoder.strategy.ModelValidationMode.CV;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Enclosed.class)
public class GridSearchModelParametersSelectionStrategyTest {

  public static class DummyModel extends Model<DummyModel, TargetEncoderModel.TargetEncoderParameters, TargetEncoderModel.Output> {
    public DummyModel(TargetEncoderModel.TargetEncoderParameters parms) {
      super(Key.make(), parms, null);
    }

    @Override
    public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
      return new ModelMetricsBinomial.MetricBuilderBinomial(domain);
    }
    @Override
    protected double[] score0(double[] data, double[] preds) { return preds; }
    @Override
    protected Futures remove_impl(Futures fs, boolean cascade) {
//      super.remove_impl(fs, cascade);
//      DKV.remove(_parms._trgt);
      return fs;
    }
  }

  public static class GridSearchModelParametersSelectionStrategyNonParametrizedTest extends water.TestUtil {

    @Test
    public void priorityQueueOrderingWithEvaluatedTest() {
      boolean theBiggerTheBetter = true;
      Comparator comparator = new GridSearchModelParametersSelectionStrategy.EvaluatedComparator(theBiggerTheBetter);
      PriorityQueue<GridSearchModelParametersSelectionStrategy.Evaluated<DummyModel>> evaluatedQueue = new PriorityQueue<>(comparator);


      TargetEncoderModel.TargetEncoderParameters params1 = TargetEncodingTestFixtures.randomTEParams();
      TargetEncoderModel.TargetEncoderParameters params2 = TargetEncodingTestFixtures.randomTEParams();
      TargetEncoderModel.TargetEncoderParameters params3 = TargetEncodingTestFixtures.randomTEParams();


      evaluatedQueue.add(new GridSearchModelParametersSelectionStrategy.Evaluated<>(new DummyModel(params1), 0.9984));
      evaluatedQueue.add(new GridSearchModelParametersSelectionStrategy.Evaluated<>(new DummyModel(params2), 0.9996));
      evaluatedQueue.add(new GridSearchModelParametersSelectionStrategy.Evaluated<>(new DummyModel(params3), 0.9784));

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
        teBuildSpec.max_models = 0;


        ModelBuilder mb = null;
        switch (validationMode) {
          case CV:
            mb = TargetEncodingTestFixtures.modelBuilderGBMWithCVFixture(train, responseColumnName, seed);
            break;
          case VALIDATION_FRAME:
            mb = TargetEncodingTestFixtures.modelBuilderGBMWithValidFrameFixture(train, valid, responseColumnName, seed);
        }
        mb.init(false);

        ModelParametersEvaluator<TargetEncoderModel, TargetEncoderModel.TargetEncoderParameters> evaluator = new DummyEvaluator();


        GridSearchModelParametersSelectionStrategy gridSearchTEParamsSelectionStrategy =
                new GridSearchModelParametersSelectionStrategy(mb, teBuildSpec, leaderboard, columnsToEncode, validationMode, evaluator);

        ModelParametersSelectionStrategy.Evaluated<TargetEncoderModel> bestParamsWithEvaluation =
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
              CV/*, ModelValidationMode.VALIDATION_FRAME*/
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
        teBuildSpec.max_models = 0;


        ModelBuilder mb = null;
        switch (validationMode) {
          case CV:
            mb = TargetEncodingTestFixtures.modelBuilderGBMWithCVFixture(train, responseColumnName, seed);
            break;
          case VALIDATION_FRAME:
            mb = TargetEncodingTestFixtures.modelBuilderGBMWithValidFrameFixture(train, valid, responseColumnName, seed);
        }
        mb.init(false);

        ModelParametersEvaluator<TargetEncoderModel, TargetEncoderModel.TargetEncoderParameters> evaluator = new DummyEvaluator();


        GridSearchModelParametersSelectionStrategy gridSearchTEParamsSelectionStrategy =
                new GridSearchModelParametersSelectionStrategy(mb, teBuildSpec, leaderboard, columnsToEncode, validationMode, evaluator);

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
        teBuildSpec.max_models = 5;

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

        assertEquals(teBuildSpec.max_models, gridSearchTEParamsSelectionStrategy.getEvaluatedModelParameters().size());

      } finally {
        Scope.exit();
      }
    }

  }


  private static class DummyEvaluator extends ModelParametersEvaluator<TargetEncoderModel, TargetEncoderModel.TargetEncoderParameters> {

    @Override
    public ModelParametersSelectionStrategy.Evaluated<TargetEncoderModel> evaluate(TargetEncoderModel.TargetEncoderParameters modelParameters, ModelBuilder modelBuilder, ModelValidationMode modelValidationMode, Frame leaderboard, String[] columnNamesToEncode, long seedForFoldColumn) {
      return new ModelParametersSelectionStrategy.Evaluated<>(null, new Random().nextDouble());
    }
  }

}