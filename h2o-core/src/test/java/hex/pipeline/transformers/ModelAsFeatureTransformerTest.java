package hex.pipeline.transformers;

import hex.pipeline.PipelineContext;
import hex.pipeline.PipelineModel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Key;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.junit.rules.ScopeTracker;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.test.dummy.DummyModel;
import water.test.dummy.DummyModelBuilder;
import water.test.dummy.DummyModelParameters;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static water.TestUtil.ar;
import static water.TestUtil.ard;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class ModelAsFeatureTransformerTest {
  
  private static class DummyModelAsFeatureTransformer extends ModelAsFeatureTransformer<DummyModelAsFeatureTransformer, DummyModel, DummyModelParameters> {

    public DummyModelAsFeatureTransformer(DummyModelParameters params) {
      super(params);
    }

    public DummyModelAsFeatureTransformer(DummyModelParameters params, Key<DummyModel> modelKey) {
      super(params, modelKey);
    }

    @Override
    protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
      validateTransform();
      return fr == null ? null : getModel().score(fr);
    }
  }


  @Rule
  public ScopeTracker scope = new ScopeTracker();

  @Test
  public void test_delegate_model_trained_and_cached_in_prepare() {
    DummyModelParameters params = new DummyModelParameters();
    DummyModelAsFeatureTransformer transformer = new DummyModelAsFeatureTransformer(params);
    assertNull(transformer.getModel());
    PipelineContext context = new PipelineContext(new PipelineModel.PipelineParameters());
    context.setTrain(makeTrain());
    transformer.prepare(context);
    DummyModel m = transformer.getModel();
    assertNotNull(m);
    
    
  }
  
  @Test
  public void test_data_input_model_params_are_detected_from_context() {
    DummyModelParameters params = new DummyModelParameters();
    DummyModelAsFeatureTransformer transformer = new DummyModelAsFeatureTransformer(params);
    assertNull(transformer.getModel());
    PipelineContext context = new PipelineContext(new PipelineModel.PipelineParameters());
    context.setTrain(makeTrain());
    transformer.prepare(context);
    DummyModel m = transformer.getModel();
    assertNotNull(m);
    
  }
  
  @Test
  public void test_grid_like_scenario() {
    
  }
  
  @Test
  public void test_transform_delegates_to_trained_model() {
    Frame fr = makeTrain();
    DummyModelParameters params = new DummyModelParameters();
    params._train = fr._key;
    DummyModel model = new DummyModelBuilder(params).trainModel().get();
    DummyModelAsFeatureTransformer transformer = new DummyModelAsFeatureTransformer(model._key);
    assertNull(transformer.getModel());
    PipelineContext context = new PipelineContext(new PipelineModel.PipelineParameters());
    transformer.prepare(context);
    DummyModel m = transformer.getModel();
    Frame expected = model.score(fr);
  }
  
  private Frame makeTrain() {
    return scope.track(new TestFrameBuilder()
            .withColNames("one", "two", "target")
            .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
            .withDataForCol(0, ard(1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3))
            .withDataForCol(1, ard(3, 2, 1, 3, 2, 1, 3, 2, 1, 3, 2, 1, 3, 2, 1, 3, 2, 1, 3, 2, 1))
            .withDataForCol(2, ar("y", "n", "y", "n", "y", "n", "y", "n", "y", "n", "y", "n", "y", "n", "y", "n", "y", "n", "y", "n", "y"))
            .build());
  }
  
}
