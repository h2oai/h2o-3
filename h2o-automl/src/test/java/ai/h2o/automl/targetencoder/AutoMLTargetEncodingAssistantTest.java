package ai.h2o.automl.targetencoder;

import ai.h2o.automl.AutoMLBuildSpec;
import ai.h2o.automl.targetencoder.strategy.FixedTEParamsStrategy;
import ai.h2o.automl.targetencoder.strategy.GridBasedTEParamsSelectionStrategy;
import ai.h2o.automl.targetencoder.strategy.HPsSelectionStrategy;
import ai.h2o.targetencoding.BlendingParams;
import ai.h2o.targetencoding.TargetEncoder;
import ai.h2o.targetencoding.strategy.TEApplicationStrategy;
import ai.h2o.targetencoding.strategy.ThresholdTEApplicationStrategy;
import hex.ModelBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import water.fvec.Frame;
import water.fvec.Vec;

import static ai.h2o.automl.targetencoder.AutoMLBenchmarkHelper.getPreparedTitanicFrame;
import static org.junit.Assert.*;

public class AutoMLTargetEncodingAssistantTest extends water.TestUtil{

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  // Check that we added encoded column to the training frame
  /*@Test
  public void performAutoTargetEncoding_CV() throws AutoMLTargetEncodingAssistant.NoColumnsToEncodeException {
    String columnNameToEncode = "home.dest";

    String responseColumnName = "survived";
    Frame train = getPreparedTitanicFrame(responseColumnName);

    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    autoMLBuildSpec.input_spec.training_frame = train._key;
    autoMLBuildSpec.build_control.nfolds = 5;
    autoMLBuildSpec.input_spec.response_column = responseColumnName;

    Vec responseColumn = train.vec(responseColumnName);
    TEApplicationStrategy thresholdTEApplicationStrategy = new ThresholdTEApplicationStrategy(train, 5, new String[]{responseColumnName});

    autoMLBuildSpec.te_spec.ratio_of_hyperspace_to_explore = 0.1;
    autoMLBuildSpec.te_spec.early_stopping_ratio = 0.05;
    autoMLBuildSpec.te_spec.seed = 1234;

    autoMLBuildSpec.te_spec.application_strategy = thresholdTEApplicationStrategy;
    autoMLBuildSpec.te_spec.params_selection_strategy = HPsSelectionStrategy.RGS;

    ModelBuilder modelBuilder = TargetEncodingTestFixtures.modelBuilderGBMWithCVFixture(train, responseColumnName, 1234);
    modelBuilder.init(false);

    AutoMLTargetEncodingAssistant teAssistant = new AutoMLTargetEncodingAssistant(train,
            null,
            null,
            autoMLBuildSpec,
            modelBuilder);

    AutoMLTargetEncodingAssistant teAssistantSpy = spy(teAssistant);

    TargetEncodingParams bestTEParams = new TargetEncodingParams(new String[]{columnNameToEncode}, new BlendingParams(10, 1), TargetEncoder.DataLeakageHandlingStrategy.KFold, 0.1);

    GridBasedTEParamsSelectionStrategy selectionStrategyMock = mock(GridBasedTEParamsSelectionStrategy.class);
    when(selectionStrategyMock.getBestParams(any(ModelBuilder.class))).thenReturn(bestTEParams);

    Mockito.doReturn(selectionStrategyMock).when(teAssistantSpy).getTeParamsSelectionStrategy();

    teAssistantSpy.init();

    teAssistantSpy.performAutoTargetEncoding();

    assertTrue(modelBuilder.train().vec("home.dest_te") != null);

    modelBuilder.train().delete();
    train.delete();
  }

  @Test
  public void performAutoTargetEncoding_validation_frame_KFOLD() throws AutoMLTargetEncodingAssistant.NoColumnsToEncodeException {
    String columnNameToEncode = "home.dest";

    String responseColumnName = "survived";
    Frame fr = getPreparedTitanicFrame(responseColumnName);

    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    Frame[] splits = AutoMLBenchmarkHelper.getRandomSplitsFromDataframe(fr, new double[]{0.7, 0.15, 0.15}, 1234);
    Frame trainSplit = splits[0];
    Frame validSplit = splits[1];
    Frame leaderboardSplit = splits[2];

    autoMLBuildSpec.input_spec.training_frame = trainSplit._key;
    autoMLBuildSpec.input_spec.validation_frame = validSplit._key;
    autoMLBuildSpec.input_spec.leaderboard_frame = leaderboardSplit._key;
    autoMLBuildSpec.build_control.nfolds = 0;
    autoMLBuildSpec.input_spec.response_column = responseColumnName;

    TEApplicationStrategy thresholdTEApplicationStrategy = new ThresholdTEApplicationStrategy(fr,5, new String[]{responseColumnName});

    autoMLBuildSpec.te_spec.ratio_of_hyperspace_to_explore = 0.1;
    autoMLBuildSpec.te_spec.early_stopping_ratio = 0.05;
    autoMLBuildSpec.te_spec.seed = 1234;

    autoMLBuildSpec.te_spec.application_strategy = thresholdTEApplicationStrategy;
    autoMLBuildSpec.te_spec.params_selection_strategy = HPsSelectionStrategy.RGS;

    ModelBuilder modelBuilder = TargetEncodingTestFixtures.modelBuilderGBMWithValidFrameFixture(trainSplit, validSplit, responseColumnName, 1234);
    modelBuilder.init(false);

    AutoMLTargetEncodingAssistant teAssistant = new AutoMLTargetEncodingAssistant(fr,
            null,
            null,
            autoMLBuildSpec,
            modelBuilder);

    AutoMLTargetEncodingAssistant teAssistantSpy = spy(teAssistant);

    TargetEncodingParams bestTEParams = new TargetEncodingParams(new String[]{columnNameToEncode}, new BlendingParams(10, 1), TargetEncoder.DataLeakageHandlingStrategy.KFold, 0.1);

    GridBasedTEParamsSelectionStrategy selectionStrategyMock = mock(GridBasedTEParamsSelectionStrategy.class);
    when(selectionStrategyMock.getBestParams(any(ModelBuilder.class))).thenReturn(bestTEParams);

    Mockito.doReturn(selectionStrategyMock).when(teAssistantSpy).getTeParamsSelectionStrategy();

    teAssistantSpy.init();

    teAssistantSpy.performAutoTargetEncoding();

    assertTrue(modelBuilder.train().vec("home.dest_te") != null);

    modelBuilder.train().delete();
    fr.delete();
    trainSplit.delete();
    validSplit.delete();
    leaderboardSplit.delete();

  }

  @Test
  public void performAutoTargetEncoding_validation_frame_LOO() throws AutoMLTargetEncodingAssistant.NoColumnsToEncodeException {
    String columnNameToEncode = "home.dest";

    String responseColumnName = "survived";
    Frame fr = getPreparedTitanicFrame(responseColumnName);

    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    Frame[] splits = AutoMLBenchmarkHelper.getRandomSplitsFromDataframe(fr, new double[]{0.7, 0.15, 0.15}, 1234);
    Frame trainSplit = splits[0];
    Frame validSplit = splits[1];
    Frame leaderboardSplit = splits[2];

    autoMLBuildSpec.input_spec.training_frame = trainSplit._key;
    autoMLBuildSpec.input_spec.validation_frame = validSplit._key;
    autoMLBuildSpec.input_spec.leaderboard_frame = leaderboardSplit._key;
    autoMLBuildSpec.build_control.nfolds = 0;
    autoMLBuildSpec.input_spec.response_column = responseColumnName;

    TEApplicationStrategy thresholdTEApplicationStrategy = new ThresholdTEApplicationStrategy(fr,5, new String[]{responseColumnName});

    autoMLBuildSpec.te_spec.ratio_of_hyperspace_to_explore = 0.1;
    autoMLBuildSpec.te_spec.early_stopping_ratio = 0.05;
    autoMLBuildSpec.te_spec.seed = 1234;

    autoMLBuildSpec.te_spec.application_strategy = thresholdTEApplicationStrategy;
    autoMLBuildSpec.te_spec.params_selection_strategy = HPsSelectionStrategy.RGS;

    ModelBuilder modelBuilder = TargetEncodingTestFixtures.modelBuilderGBMWithValidFrameFixture(trainSplit, validSplit, responseColumnName, 1234);
    modelBuilder.init(false);

    AutoMLTargetEncodingAssistant teAssistant = new AutoMLTargetEncodingAssistant(fr,
            null,
            null,
            autoMLBuildSpec,
            modelBuilder);

    AutoMLTargetEncodingAssistant teAssistantSpy = spy(teAssistant);

    TargetEncodingParams bestTEParams = new TargetEncodingParams(new String[]{columnNameToEncode}, new BlendingParams(10, 1), TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, 0.1);

    GridBasedTEParamsSelectionStrategy selectionStrategyMock = mock(GridBasedTEParamsSelectionStrategy.class);
    when(selectionStrategyMock.getBestParams(any(ModelBuilder.class))).thenReturn(bestTEParams);

    Mockito.doReturn(selectionStrategyMock).when(teAssistantSpy).getTeParamsSelectionStrategy();

    teAssistantSpy.init();

    teAssistantSpy.performAutoTargetEncoding();

    assertTrue(modelBuilder.train().vec("home.dest_te") != null);

    modelBuilder.train().delete();
    fr.delete();
    trainSplit.delete();
    validSplit.delete();
    leaderboardSplit.delete();

  }

  @Test
  public void performAutoTargetEncoding_validation_frame_NONE() throws AutoMLTargetEncodingAssistant.NoColumnsToEncodeException {
    String columnNameToEncode = "home.dest";

    String responseColumnName = "survived";
    Frame fr = getPreparedTitanicFrame(responseColumnName);

    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    Frame[] splits = AutoMLBenchmarkHelper.getRandomSplitsFromDataframe(fr, new double[]{0.7, 0.15, 0.15}, 1234);
    Frame trainSplit = splits[0];
    Frame validSplit = splits[1];
    Frame leaderboardSplit = splits[2];

    autoMLBuildSpec.input_spec.training_frame = trainSplit._key;
    autoMLBuildSpec.input_spec.validation_frame = validSplit._key;
    autoMLBuildSpec.input_spec.leaderboard_frame = leaderboardSplit._key;
    autoMLBuildSpec.build_control.nfolds = 0;
    autoMLBuildSpec.input_spec.response_column = responseColumnName;

    Vec responseColumn = fr.vec(responseColumnName);
    TEApplicationStrategy thresholdTEApplicationStrategy = new ThresholdTEApplicationStrategy(fr, responseColumn, 5);

    autoMLBuildSpec.te_spec.ratio_of_hyperspace_to_explore = 0.1;
    autoMLBuildSpec.te_spec.early_stopping_ratio = 0.05;
    autoMLBuildSpec.te_spec.seed = 1234;

    autoMLBuildSpec.te_spec.application_strategy = thresholdTEApplicationStrategy;
    autoMLBuildSpec.te_spec.params_selection_strategy = HPsSelectionStrategy.RGS;

    ModelBuilder modelBuilder = TargetEncodingTestFixtures.modelBuilderGBMWithValidFrameFixture(trainSplit, validSplit, responseColumnName, 1234);
    modelBuilder.init(false);

    AutoMLTargetEncodingAssistant teAssistant = new AutoMLTargetEncodingAssistant(fr,
            null,
            null,
            autoMLBuildSpec,
            modelBuilder);

    AutoMLTargetEncodingAssistant teAssistantSpy = spy(teAssistant);

    TargetEncodingParams bestTEParams = new TargetEncodingParams(new String[]{columnNameToEncode}, new BlendingParams(10, 1), TargetEncoder.DataLeakageHandlingStrategy.None, 0.1);

    GridBasedTEParamsSelectionStrategy selectionStrategyMock = mock(GridBasedTEParamsSelectionStrategy.class);
    when(selectionStrategyMock.getBestParams(any(ModelBuilder.class))).thenReturn(bestTEParams);

    Mockito.doReturn(selectionStrategyMock).when(teAssistantSpy).getTeParamsSelectionStrategy();

    teAssistantSpy.init();

    teAssistantSpy.performAutoTargetEncoding();

    assertTrue(modelBuilder.train().vec("home.dest_te") != null);

    modelBuilder.train().delete();
    fr.delete();
    trainSplit.delete();
    validSplit.delete();
    leaderboardSplit.delete();

  }*/

  // Fixed selection strategy
  @Test
  public void performAutoTargetEncoding_validation_frame_fixed_application_strategy() throws AutoMLTargetEncodingAssistant.NoColumnsToEncodeException {
    String responseColumnName = "survived";
    Frame fr = getPreparedTitanicFrame(responseColumnName);

    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    Frame[] splits = AutoMLBenchmarkHelper.getRandomSplitsFromDataframe(fr, new double[]{0.7, 0.15, 0.15}, 1234);
    Frame trainSplit = splits[0];
    Frame validSplit = splits[1];
    Frame leaderboardSplit = splits[2];

    autoMLBuildSpec.input_spec.training_frame = trainSplit._key;
    autoMLBuildSpec.input_spec.validation_frame = validSplit._key;
    autoMLBuildSpec.input_spec.leaderboard_frame = leaderboardSplit._key;
    autoMLBuildSpec.build_control.nfolds = 0;
    autoMLBuildSpec.input_spec.response_column = responseColumnName;

    TEApplicationStrategy thresholdTEApplicationStrategy = new ThresholdTEApplicationStrategy(fr,5, new String[]{responseColumnName});

    autoMLBuildSpec.te_spec.ratio_of_hyperspace_to_explore = 0.1;
    autoMLBuildSpec.te_spec.early_stopping_ratio = 0.05;
    autoMLBuildSpec.te_spec.seed = 1234;

    autoMLBuildSpec.te_spec.application_strategy = thresholdTEApplicationStrategy;

    ModelBuilder modelBuilder = TargetEncodingTestFixtures.modelBuilderGBMWithValidFrameFixture(trainSplit, validSplit, responseColumnName, 1234);
    modelBuilder.init(false);

    // Fixed selection strategy
    autoMLBuildSpec.te_spec.params_selection_strategy = HPsSelectionStrategy.Fixed;
    TargetEncodingParams targetEncodingParams = new TargetEncodingParams(new BlendingParams(5, 1), TargetEncoder.DataLeakageHandlingStrategy.KFold, 0.01);
    autoMLBuildSpec.te_spec.fixedTEParams = targetEncodingParams;

    AutoMLTargetEncodingAssistant teAssistant = new AutoMLTargetEncodingAssistant(fr,
            null,
            null,
            autoMLBuildSpec,
            modelBuilder);

    teAssistant.init();

    assertTrue(teAssistant.getTeParamsSelectionStrategy() instanceof FixedTEParamsStrategy);

    TargetEncodingParams bestParams = teAssistant.getTeParamsSelectionStrategy().getBestParams(null);
    assertArrayEquals(thresholdTEApplicationStrategy.getColumnsToEncode(), bestParams.getColumnsToEncode());
    assertEquals(targetEncodingParams.getBlendingParams().getK(), bestParams.getBlendingParams().getK(), 1e-5);
    assertEquals(targetEncodingParams.getBlendingParams().getF(), bestParams.getBlendingParams().getF(), 1e-5);

    modelBuilder.train().delete();
    fr.delete();
    trainSplit.delete();
    validSplit.delete();
    leaderboardSplit.delete();
  }
}