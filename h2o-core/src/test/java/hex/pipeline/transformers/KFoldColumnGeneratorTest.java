package hex.pipeline.transformers;

import hex.Model.Parameters.FoldAssignmentScheme;
import hex.pipeline.DataTransformer;
import hex.pipeline.PipelineContext;
import hex.pipeline.PipelineModel.PipelineParameters;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.junit.rules.ScopeTracker;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;

import static hex.pipeline.DataTransformer.FrameType.Training;
import static hex.pipeline.DataTransformer.FrameType.Validation;
import static org.junit.Assert.*;
import static water.TestUtil.ar;
import static water.TestUtil.ard;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class KFoldColumnGeneratorTest {

  @Rule
  public ScopeTracker scope = new ScopeTracker();
  
  @Test
  public void test_transformer_modifies_only_training_frames() {
    String foldc = "foldc";
    int nfolds = 3;
    DataTransformer kfold = new KFoldColumnGenerator(foldc, FoldAssignmentScheme.AUTO, nfolds, 0);
    final Frame fr = scope.track(new TestFrameBuilder()
            .withColNames("one", "two", "target")
            .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
            .withDataForCol(0, ard(1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3))
            .withDataForCol(1, ard(3, 2, 1, 3, 2, 1, 3, 2, 1, 3, 2, 1, 3, 2, 1, 3, 2, 1, 3, 2, 1))
            .withDataForCol(2, ar("y", "n", "y", "n", "y", "n", "y", "n", "y", "n", "y", "n", "y", "n", "y", "n", "y", "n", "y", "n", "y"))
            .build());
    
    Frame transformed = kfold.transform(fr);
    assertSame(fr, transformed);

    Frame validTransformed = kfold.transform(fr, Validation, null);
    assertSame(fr, validTransformed);
    
    Frame trainTransformed = kfold.transform(fr, Training, null);
    TestUtil.printOutFrameAsTable(trainTransformed);
    assertArrayEquals(new String[] {"one", "two", "target", foldc}, trainTransformed.names());
    assertEquals(0, (int) trainTransformed.vec(3).min());
    assertEquals(nfolds-1, (int) trainTransformed.vec(3).max());
  }


  @Test
  public void test_transformer_autodetection_in_pipeline_context() {
    DataTransformer kfold = Scope.<DataTransformer>track_generic(new KFoldColumnGenerator());
    final Frame fr = scope.track(new TestFrameBuilder()
            .withColNames("one", "two", "target")
            .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
            .withDataForCol(0, ard(1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3))
            .withDataForCol(1, ard(3, 2, 1, 3, 2, 1, 3, 2, 1, 3, 2, 1, 3, 2, 1, 3, 2, 1, 3, 2, 1))
            .withDataForCol(2, ar("y", "n", "y", "n", "y", "n", "y", "n", "y", "n", "y", "n", "y", "n", "y", "n", "y", "n", "y", "n", "y"))
            .build());

    PipelineParameters params = new PipelineParameters();
    params._nfolds = 4;
    params._fold_assignment = FoldAssignmentScheme.Stratified;
    params._response_column = "target";
    PipelineContext context = new PipelineContext(params);
    kfold.prepare(context);
    assertFalse(ArrayUtils.contains(fr.names(), context._params._fold_column));
    Frame trainTransformed = kfold.transform(fr, Training, context);
    TestUtil.printOutFrameAsTable(trainTransformed);
    assertArrayEquals(new String[] {"one", "two", "target", context._params._fold_column}, trainTransformed.names());
    assertEquals(0, (int) trainTransformed.vec(3).min());
    assertEquals(params._nfolds-1, (int) trainTransformed.vec(3).max());
    //hard to verify that it's properly stratified on a smaller dataset given the algo being used, but trusting the utility method that is used is other places.
  }
  
  @Test
  public void test_transformer_transforms_context_train_frame_during_prepare() {
    DataTransformer kfold = Scope.<DataTransformer>track_generic(new KFoldColumnGenerator());
    final Frame fr = scope.track(new TestFrameBuilder()
            .withColNames("one", "two", "target")
            .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
            .withDataForCol(0, ard(1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3))
            .withDataForCol(1, ard(3, 2, 1, 3, 2, 1, 3, 2, 1, 3, 2, 1, 3, 2, 1, 3, 2, 1, 3, 2, 1))
            .withDataForCol(2, ar("y", "n", "y", "n", "y", "n", "y", "n", "y", "n", "y", "n", "y", "n", "y", "n", "y", "n", "y", "n", "y"))
            .build());

    PipelineParameters params = new PipelineParameters();
    params._nfolds = 3;
    params._response_column = "target";
    PipelineContext context = new PipelineContext(params);
    context.setTrain(fr);
    kfold.prepare(context);
    assertFalse(ArrayUtils.contains(fr.names(), context._params._fold_column));
    assertTrue(ArrayUtils.contains(context.getTrain().names(), context._params._fold_column));
    TestUtil.printOutFrameAsTable(context.getTrain());
  }

}
