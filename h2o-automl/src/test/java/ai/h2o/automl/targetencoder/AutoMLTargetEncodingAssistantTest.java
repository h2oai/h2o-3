package ai.h2o.automl.targetencoder;

import ai.h2o.automl.AutoML;
import ai.h2o.automl.AutoMLBuildSpec;
import ai.h2o.automl.targetencoder.strategy.GridSearchModelParametersSelectionStrategy;
import ai.h2o.targetencoding.TargetEncoder;
import ai.h2o.targetencoding.TargetEncoderModel;
import ai.h2o.targetencoding.strategy.ThresholdTEApplicationStrategy;
import hex.ModelBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import water.Scope;
import water.fvec.Frame;

import java.util.Date;
import java.util.Optional;

import static ai.h2o.automl.targetencoder.AutoMLBenchmarkHelper.getPreparedTitanicFrame;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AutoMLTargetEncodingAssistantTest extends water.TestUtil{

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void findBestTEParams_returns_none_if_no_columns_for_encoding(){

    AutoML aml = null;
    Scope.enter();
    try {

      String responseColumnName = "survived";
      Frame train = getPreparedTitanicFrame(responseColumnName);
      Scope.track(train);

      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      autoMLBuildSpec.input_spec.training_frame = train._key;
      autoMLBuildSpec.input_spec.validation_frame = null;
      autoMLBuildSpec.input_spec.leaderboard_frame = null;
      autoMLBuildSpec.build_control.nfolds = 5;
      autoMLBuildSpec.input_spec.response_column = responseColumnName;

      autoMLBuildSpec.te_spec.seed = 1234;

      //  This will make sure that no columns will be selected for encoding
      int nonExceedableThreshold = Integer.MAX_VALUE;

      autoMLBuildSpec.te_spec.application_strategy = new ThresholdTEApplicationStrategy(train, nonExceedableThreshold, new String[]{responseColumnName});

      ModelBuilder modelBuilder = TargetEncodingTestFixtures.modelBuilderGBMWithCVFixture(train, responseColumnName, 1234);

      aml = new AutoML(null, new Date(), autoMLBuildSpec);

      AutoMLTargetEncoderAssistant teAssistant = new AutoMLTargetEncoderAssistant(aml, autoMLBuildSpec, modelBuilder);

      Optional<TargetEncoderModel.TargetEncoderParameters> bestTEParamsOpt = teAssistant.findBestTEParams();

      assertFalse(bestTEParamsOpt.isPresent());
    } finally {
      if (aml != null) aml.delete();
      Scope.exit();
    }
  }

  @Test
  public void applyTe_encoded_training_frame(){

    AutoML aml = null;
    Scope.enter();
    try {

      String responseColumnName = "survived";
      Frame train = getPreparedTitanicFrame(responseColumnName);
      Scope.track(train);

      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      autoMLBuildSpec.input_spec.training_frame = train._key;
      autoMLBuildSpec.input_spec.validation_frame = null;
      autoMLBuildSpec.input_spec.leaderboard_frame = null;
      autoMLBuildSpec.build_control.nfolds = 5;
      autoMLBuildSpec.input_spec.response_column = responseColumnName;

      autoMLBuildSpec.te_spec.seed = 1234;

      autoMLBuildSpec.te_spec.application_strategy = new ThresholdTEApplicationStrategy(train, 5, new String[]{responseColumnName});

      ModelBuilder modelBuilder = TargetEncodingTestFixtures.modelBuilderGBMWithCVFixture(train, responseColumnName, 1234);

      aml = new AutoML(null, new Date(), autoMLBuildSpec);

      AutoMLTargetEncoderAssistant teAssistant = new AutoMLTargetEncoderAssistant(aml, autoMLBuildSpec, modelBuilder);

      AutoMLTargetEncoderAssistant teAssistantSpy = spy(teAssistant);

      TargetEncoderModel.TargetEncoderParameters bestTEParams = new TargetEncoderModel.TargetEncoderParameters();
      bestTEParams._k = 10;
      bestTEParams._f = 1;
      bestTEParams._data_leakage_handling = TargetEncoder.DataLeakageHandlingStrategy.KFold;
      bestTEParams._noise_level = 0.1;

      GridSearchModelParametersSelectionStrategy selectionStrategyMock = mock(GridSearchModelParametersSelectionStrategy.class);
      when(selectionStrategyMock.getBestParams()).thenReturn(bestTEParams);

      Mockito.doReturn(selectionStrategyMock).when(teAssistantSpy).getTeParamsSelectionStrategy();

      Optional<TargetEncoderModel.TargetEncoderParameters> bestTEParams1 = teAssistantSpy.findBestTEParams();

      teAssistantSpy.applyTE(bestTEParams1.get());

      assertNotNull(modelBuilder._parms.train().vec("home.dest_te"));
    } finally {
      if (aml != null) aml.delete();
      Scope.exit();
    }
  }
/*
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
    modelBuilder.findBestTEParams(false);

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

    teAssistantSpy.findBestTEParams();

    teAssistantSpy.applyTE();

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
    modelBuilder.findBestTEParams(false);

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

    teAssistantSpy.findBestTEParams();

    teAssistantSpy.applyTE();

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
    modelBuilder.findBestTEParams(false);

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

    teAssistantSpy.findBestTEParams();

    teAssistantSpy.applyTE();

    assertTrue(modelBuilder.train().vec("home.dest_te") != null);

    modelBuilder.train().delete();
    fr.delete();
    trainSplit.delete();
    validSplit.delete();
    leaderboardSplit.delete();

  }*/
}