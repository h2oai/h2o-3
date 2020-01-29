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
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import java.util.Date;
import java.util.Optional;

import static ai.h2o.automl.targetencoder.AutoMLBenchmarkHelper.getPreparedTitanicFrame;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(Enclosed.class)
public class AutoMLTargetEncodingAssistantTest {

  public static class AutoMLTargetEncodingAssistantNonParametrizedTest extends water.TestUtil {
    @BeforeClass
    public static void setup() {
      stall_till_cloudsize(1);
    }

    @Test
    public void findBestTEParams_returns_none_if_no_columns_for_encoding() {

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
  }

  @RunWith(Parameterized.class)
  public static class AutoMLTargetEncodingAssistantParametrizedTest extends TestUtil {

    @BeforeClass
    public static void setup() {
      stall_till_cloudsize(1);
    }

    @Parameterized.Parameters(name = "Assistant applies TE parameters: strategy = {0}")
    public static Object[] strategy() {
      return new TargetEncoder.DataLeakageHandlingStrategy[]{
              TargetEncoder.DataLeakageHandlingStrategy.KFold, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, TargetEncoder.DataLeakageHandlingStrategy.None
      };
    }

    @Parameterized.Parameter
    public TargetEncoder.DataLeakageHandlingStrategy strategy;

    @Test
    public void applyTe_encoded_training_when_validation_frame_is_being_used() {

      AutoML aml = null;
      Scope.enter();
      try {

        String responseColumnName = "survived";
        Frame train = getPreparedTitanicFrame(responseColumnName);

        AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
        autoMLBuildSpec.input_spec.training_frame = train._key;
        Frame[] splits = AutoMLBenchmarkHelper.getRandomSplitsFromDataframe(train, new double[]{0.7, 0.15, 0.15}, 1234);
        Frame trainSplit = splits[0];
        Frame validSplit = splits[1];
        Frame leaderboardSplit = splits[2];
        Scope.track(train, trainSplit, validSplit, leaderboardSplit);


        autoMLBuildSpec.input_spec.training_frame = trainSplit._key;
        autoMLBuildSpec.input_spec.validation_frame = validSplit._key;
        autoMLBuildSpec.input_spec.leaderboard_frame = leaderboardSplit._key;
        autoMLBuildSpec.build_control.nfolds = 0;
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
        bestTEParams._data_leakage_handling = strategy;
        bestTEParams._noise_level = 0.1;

        GridSearchModelParametersSelectionStrategy selectionStrategyMock = mock(GridSearchModelParametersSelectionStrategy.class);
        when(selectionStrategyMock.getBestParams()).thenReturn(bestTEParams);

        Mockito.doReturn(selectionStrategyMock).when(teAssistantSpy).getTeParamsSelectionStrategy();

        Optional<TargetEncoderModel.TargetEncoderParameters> bestTEParams1 = teAssistantSpy.findBestTEParams();

        teAssistantSpy.applyTE(bestTEParams1.get());

        assertNotNull(modelBuilder._parms.train().vec("home.dest_te"));

        Scope.track(modelBuilder._parms.train());
      } finally {
        if (aml != null) aml.delete();
        Scope.exit();
      }
    }
  }
}