package ai.h2o.automl;

import ai.h2o.automl.targetencoding.BlendingParams;
import ai.h2o.automl.targetencoding.TargetEncoder;
import ai.h2o.automl.targetencoding.TargetEncodingParams;
import ai.h2o.automl.targetencoding.TargetEncodingTestFixtures;
import ai.h2o.automl.targetencoding.strategy.GridBasedTEParamsSelectionStrategy;
import ai.h2o.automl.targetencoding.strategy.HPsSelectionStrategy;
import ai.h2o.automl.targetencoding.strategy.TEApplicationStrategy;
import ai.h2o.automl.targetencoding.strategy.ThresholdTEApplicationStrategy;
import hex.ModelBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import water.fvec.Frame;
import water.fvec.Vec;

import static ai.h2o.automl.AutoMLBenchmarkingHelper.getPreparedTitanicFrame;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


public class AutoMLTargetEncodingAssistantTest extends water.TestUtil{

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }
  
  // Check that we added encoded column to the training frame
  @Test
  public void performAutoTargetEncoding_CV() {
    String columnNameToEncode = "home.dest";

    String responseColumnName = "survived";
    Frame train = getPreparedTitanicFrame(responseColumnName);
    
    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
    autoMLBuildSpec.input_spec.training_frame = train._key;
    autoMLBuildSpec.build_control.nfolds = 5;
    autoMLBuildSpec.input_spec.response_column = responseColumnName;

    Vec responseColumn = train.vec(responseColumnName);
    TEApplicationStrategy thresholdTEApplicationStrategy = new ThresholdTEApplicationStrategy(train, responseColumn, 5);

    autoMLBuildSpec.te_spec.ratio_of_hyperspace_to_explore = 0.1;
    autoMLBuildSpec.te_spec.early_stopping_ratio = 0.05;
    autoMLBuildSpec.te_spec.seed = 1234;

    autoMLBuildSpec.te_spec.application_strategy = thresholdTEApplicationStrategy;
    autoMLBuildSpec.te_spec.params_selection_strategy = HPsSelectionStrategy.RGS;

    ModelBuilder modelBuilder = TargetEncodingTestFixtures.modelBuilderWithCVFixture(train, responseColumnName, 1234);
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
  public void performAutoTargetEncoding_validation_frame() {


  }
}
