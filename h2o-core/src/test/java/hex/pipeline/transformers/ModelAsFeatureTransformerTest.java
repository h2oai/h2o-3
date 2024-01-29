package hex.pipeline.transformers;

import hex.Model;
import hex.pipeline.PipelineContext;
import hex.pipeline.PipelineModel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.junit.rules.ScopeTracker;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.test.dummy.DummyModel;
import water.test.dummy.DummyModelBuilder;
import water.test.dummy.DummyModelParameters;

import static org.junit.Assert.*;
import static water.TestUtil.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class ModelAsFeatureTransformerTest {
  
  private static class DummyModelAsFeatureTransformer extends ModelAsFeatureTransformer<DummyModelAsFeatureTransformer, DummyModel, DummyModelParameters> {
    
    boolean cvSensitive = false;

    public DummyModelAsFeatureTransformer(DummyModelParameters params) {
      super(params);
    }

    public DummyModelAsFeatureTransformer(DummyModelParameters params, Key<DummyModel> modelKey) {
      super(params, modelKey);
    }

    @Override
    public boolean isCVSensitive() {
      return cvSensitive;
    }
  }


  @Rule
  public ScopeTracker scope = new ScopeTracker();

  @Test
  public void test_delegate_model_trained_and_cached_in_prepare() {
    DummyModelParameters params = makeModelParams(null);
    DummyModelAsFeatureTransformer transformer = new DummyModelAsFeatureTransformer(params);
    assertNull(transformer.getModel());

    Frame fr = makeTrain();
    PipelineModel.PipelineParameters pParams = new PipelineModel.PipelineParameters();
    assignTrainingParams(pParams, fr);
    PipelineContext context = new PipelineContext(pParams);
    transformer.prepare(context);
    DummyModel m1 = Scope.track_generic(transformer.getModel());
    assertNotNull(m1);
    long m1Checksum = m1.checksum();
    
    transformer.prepare(context);
    DummyModel m2 = transformer.getModel();
    assertSame(m1, m2);
    assertEquals("model shouldn't be modified during second prepare", m1Checksum, m2.checksum());
  }
  
  @Test
  public void test_transform_delegates_to_provided_pretrained_model() {
    Frame fr = makeTrain();
    DummyModelParameters params = makeModelParams(fr);
    DummyModel model = Scope.track_generic(new DummyModelBuilder(params).trainModel().get());
    long oriChecksum = model.checksum();
    DummyModelAsFeatureTransformer transformer = new DummyModelAsFeatureTransformer(params, model._key);
    assertNotNull(transformer.getModel());
    
    PipelineContext context = new PipelineContext(new PipelineModel.PipelineParameters());
    transformer.prepare(context);
    DummyModel m = transformer.getModel();
    assertSame(model, m);
    assertEquals("model shouldn't be modified during prepare", oriChecksum, m.checksum());
  }


  @Test
  public void test_transform_delegates_to_internally_trained_model() {
    Frame fr = makeTrain();
    DummyModelParameters params = makeModelParams(fr);
    DummyModel model = Scope.track_generic(new DummyModelBuilder(params).trainModel().get());
    DummyModelAsFeatureTransformer transformer = new DummyModelAsFeatureTransformer(params, model._key);
    assertNotNull(transformer.getModel());

    PipelineContext context = new PipelineContext(new PipelineModel.PipelineParameters());
    transformer.prepare(context);
    DummyModel m = transformer.getModel();
    assertSame(model, m);
    assertEquals("model shouldn't be modified during prepare", model.checksum(), m.checksum());
    
    Frame trans = transformer.transform(fr);
    assertEquals(fr.getKey()+"_stats", trans.getKey().toString());
  }


  @Test
  public void test_data_input_model_params_are_detected_from_context() {
    DummyModelParameters params = makeModelParams(null);
    DummyModelAsFeatureTransformer transformer = new DummyModelAsFeatureTransformer(params);
    assertNull(transformer.getModel());

    PipelineModel.PipelineParameters pParams = new PipelineModel.PipelineParameters();
    pParams._response_column = "target";
    PipelineContext context = new PipelineContext(pParams);
    Frame train = makeTrain();
    context.setTrain(train);
    transformer.prepare(context);
    DummyModel m = Scope.track_generic(transformer.getModel());
    assertNotNull(m);
    assertEquals("target", m._parms._response_column);
    assertEquals(train._key, m._parms._train);
    
    Frame trans = transformer.transform(train);
    TestUtil.printOutFrameAsTable(trans);
    assertEquals(train.getKey()+"_stats", trans.getKey().toString());
  }
  
  @Test
  public void test_grid_like_scenario() {

  }

  private DummyModelParameters makeModelParams(Frame train) {
    DummyModelParameters params = new DummyModelParameters();
    params._makeModel = true;
    if (train != null) assignTrainingParams(params, train);
    return params;
  }
  
  private void assignTrainingParams(Model.Parameters params, Frame train) {
    params._train = train.getKey();
    params._response_column = train.name(train._names.length - 1);
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
