package hex.pipeline;

import hex.Model;
import hex.ModelBuilder;
import hex.pipeline.DataTransformerTest.AddRandomColumnTransformer;
import hex.pipeline.DataTransformerTest.FrameTrackerAsTransformer;
import hex.pipeline.PipelineModel.PipelineOutput;
import hex.pipeline.PipelineModel.PipelineParameters;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.test.dummy.DummyModel;
import water.test.dummy.DummyModelParameters;
import water.util.FrameUtils;

import static org.junit.Assert.*;
import static water.TestUtil.ar;
import static water.TestUtil.ard;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class PipelineTest {
  
  @Test
  public void test_simple_classification_pipeline() {
    try {
      Scope.enter();
      PipelineParameters pparams = new PipelineParameters();
      FrameTrackerAsTransformer tracker = new FrameTrackerAsTransformer();
      pparams._transformers = new DataTransformer[] {
             new AddRandomColumnTransformer("foo").id("add_foo"),
             new AddRandomColumnTransformer("bar").id("add_bar"), 
             tracker.id("tracker")
      };
      DummyModelParameters eparams = new DummyModelParameters();
      eparams._makeModel = true;
      pparams._estimator = eparams;
      final Frame fr = Scope.track(new TestFrameBuilder()
              .withColNames("one", "two", "target")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ard(1, 2, 3))
              .withDataForCol(1, ard(3, 2, 1))
              .withDataForCol(2, ar("yes", "no", "yes"))
              .build());
      
      pparams._train = fr._key;
      pparams._response_column = "target";

      Pipeline pipeline = ModelBuilder.make(pparams);
      PipelineModel pmodel = Scope.track_generic(pipeline.trainModel().get());
      assertNotNull(pmodel);
      PipelineOutput output = pmodel._output;
      assertNotNull(output);
      assertNotNull(output._estimator);
      Model emodel = output._estimator.get();
      assertNotNull(emodel);
      assertTrue(emodel instanceof DummyModel);
//      assertArrayEquals(emodel._input_parms._train.get().names(),  new String[] {"one", "two", "target", "foo", "bar"});
      assertArrayEquals(emodel._output._names, new String[] {"one", "two", "foo", "bar", "target"});
      
      assertEquals(1, tracker.transformations.size());
      
      Frame predictions = Scope.track(pmodel.score(fr));
      assertNotNull(predictions);
      TestUtil.printOutFrameAsTable(predictions);
    } finally {
      Scope.exit();
    }
  }
  
  @Test 
  public void test_simple_classification_pipeline_with_cv() {
    try {
      int nfolds = 3;
      Scope.enter();
      PipelineParameters pparams = new PipelineParameters();
      pparams._nfolds = nfolds;
      FrameTrackerAsTransformer tracker = new FrameTrackerAsTransformer();
      pparams._transformers = new DataTransformer[] {
              new AddRandomColumnTransformer("foo").id("add_foo"),
              new AddRandomColumnTransformer("bar").id("add_bar"),
              tracker.id("track"), 
      };
      DummyModelParameters eparams = new DummyModelParameters();
      eparams._makeModel = true;
      pparams._estimator = eparams;
      final Frame fr = Scope.track(new TestFrameBuilder()
              .withColNames("one", "two", "target")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ard(1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 9, 8, 7, 6, 5, 4, 3, 2, 1))
              .withDataForCol(1, ard(3, 2, 1, 6, 5, 4, 9, 8, 7, 2, 1, 0, 5, 4, 3, 8, 7, 6, 1))
              .withDataForCol(2, ar("y", "n", "y", "y", "y", "y", "n", "n", "n", "n", "y", "y", "n", "n", "y", "y", "y", "n", "n"))
              .build());

      pparams._train = fr._key;
      pparams._response_column = "target";

      Pipeline pipeline = ModelBuilder.make(pparams);
      PipelineModel pmodel = Scope.track_generic(pipeline.trainModel().get());
      assertNotNull(pmodel);
      PipelineOutput output = pmodel._output;
      assertNotNull(output);
      assertNotNull(output._estimator);
      Model emodel = output._estimator.get();
      assertNotNull(emodel);
      assertTrue(emodel instanceof DummyModel);
//      assertArrayEquals(emodel._input_parms._train.get().names(),  new String[] {"one", "two", "target", "foo", "bar"});
      assertArrayEquals(emodel._output._names, new String[] {"one", "two", "foo", "bar", "target"});
      
      assertEquals(2*nfolds+1, tracker.transformations.size()); // nfolds * 2 [train+valid] + 1 [final model, train only]
      
      Frame predictions = Scope.track(pmodel.score(fr));
      assertNotNull(predictions);
      TestUtil.printOutFrameAsTable(predictions);
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void test_simple_regression_pipeline() {
    try {
      Scope.enter();
      PipelineParameters pparams = new PipelineParameters();
      FrameTrackerAsTransformer tracker = new FrameTrackerAsTransformer();
      pparams._transformers = new DataTransformer[] {
              new AddRandomColumnTransformer("foo").id("add_foo"),
              new AddRandomColumnTransformer("bar").id("add_bar"),
              tracker.id("tracker")
      };
      DummyModelParameters eparams = new DummyModelParameters();
      eparams._makeModel = true;
      pparams._estimator = eparams;
      final Frame fr = Scope.track(new TestFrameBuilder()
              .withColNames("one", "two", "target")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(1, 2, 3))
              .withDataForCol(1, ard(3, 2, 1))
              .withDataForCol(2, ard(1.0, 1.5, 2.5))
              .build());

      pparams._train = fr._key;
      pparams._response_column = "target";

      Pipeline pipeline = ModelBuilder.make(pparams);
      PipelineModel pmodel = Scope.track_generic(pipeline.trainModel().get());
      assertNotNull(pmodel);
      PipelineOutput output = pmodel._output;
      assertNotNull(output);
      assertNotNull(output._estimator);
      Model emodel = output._estimator.get();
      assertNotNull(emodel);
      assertTrue(emodel instanceof DummyModel);
      assertArrayEquals(emodel._output._names, new String[] {"one", "two", "foo", "bar", "target"});

      assertEquals(1, tracker.transformations.size());

      Frame predictions = Scope.track(pmodel.score(fr));
      assertNotNull(predictions);
      TestUtil.printOutFrameAsTable(predictions);
    } finally {
      Scope.exit();
    }
  }
  
  @Test 
  public void test_simple_regression_pipeline_with_cv() {
    //CV scoring logic is different with regression 
    try {
      int nfolds = 3;
      Scope.enter();
      PipelineParameters pparams = new PipelineParameters();
      pparams._nfolds = nfolds;
      FrameTrackerAsTransformer tracker = new FrameTrackerAsTransformer();
      pparams._transformers = new DataTransformer[] {
              new AddRandomColumnTransformer("foo").id("add_foo"),
              new AddRandomColumnTransformer("bar").id("add_bar"),
              tracker.id("track"),
      };
      DummyModelParameters eparams = new DummyModelParameters();
      eparams._makeModel = true;
      pparams._estimator = eparams;
      final Frame fr = Scope.track(new TestFrameBuilder()
              .withColNames("one", "two", "target")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 9, 8, 7, 6, 5, 4, 3, 2, 1))
              .withDataForCol(1, ard(3, 2, 1, 6, 5, 4, 9, 8, 7, 2, 1, 0, 5, 4, 3, 8, 7, 6, 1))
              .withDataForCol(2, ard(1.0, 2.0, 1.2, 1.4, 1.6, 1.8, 2.0, 2.5, 2.3, 2.7, 1.3, 1.7, 2.1, 2.9, 1.3, 1.5, 1.7, 2.2, 2.8))
              .build());

      pparams._train = fr._key;
      pparams._response_column = "target";

      Pipeline pipeline = ModelBuilder.make(pparams);
      PipelineModel pmodel = Scope.track_generic(pipeline.trainModel().get());
      assertNotNull(pmodel);
      PipelineOutput output = pmodel._output;
      assertNotNull(output);
      assertNotNull(output._estimator);
      Model emodel = output._estimator.get();
      assertNotNull(emodel);
      assertTrue(emodel instanceof DummyModel);
      assertArrayEquals(emodel._output._names, new String[] {"one", "two", "foo", "bar", "target"});

      assertEquals(2*nfolds+1, tracker.transformations.size()); // nfolds * 2 [train+valid] + 1 [final model, train only]

      Frame predictions = Scope.track(pmodel.score(fr));
      assertNotNull(predictions);
      TestUtil.printOutFrameAsTable(predictions);
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void test_categorical_features_are_not_modified_before_transformations() {
    try {
      Scope.enter();
      PipelineParameters pparams = new PipelineParameters();
      FrameTrackerAsTransformer tracker = new FrameTrackerAsTransformer();
      pparams._transformers = new DataTransformer[]{
              new AddRandomColumnTransformer("foo").id("add_foo"),
              new AddRandomColumnTransformer("bar").id("add_bar"),
              tracker.id("tracker")
      };
      DummyModelParameters eparams = new DummyModelParameters();
      eparams._makeModel = true;
      pparams._estimator = eparams;
      final Frame fr = Scope.track(new TestFrameBuilder()
              .withColNames("one", "two", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "c"))
              .withDataForCol(1, ar("c", "b", "a"))
              .withDataForCol(2, ar("yes", "no", "yes"))
              .build());

      pparams._train = fr._key;
      pparams._response_column = "target";

      Pipeline pipeline = ModelBuilder.make(pparams);
      PipelineModel pmodel = Scope.track_generic(pipeline.trainModel().get());
      assertNotNull(pmodel);
      PipelineOutput output = pmodel._output;
      assertNotNull(output);
      assertNotNull(output._estimator);
      Model emodel = output._estimator.get();
      assertNotNull(emodel);
      assertTrue(emodel instanceof DummyModel);
      assertArrayEquals(new String[][] {{"a", "b", "c"}, {"a", "b", "c"}, {"no", "yes"}}, pmodel._output._domains);
      assertArrayEquals(new String[][] {{"a", "b", "c"}, {"a", "b", "c"}, null, null, {"no", "yes"}}, emodel._output._domains);
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void test_categorical_features_are_not_modified_before_transformations_with_cv() {
    //CV uses a different initialization/training flow
    try {
      int nfolds = 3;
      Scope.enter();
      PipelineParameters pparams = new PipelineParameters();
      pparams._nfolds = nfolds;
      FrameTrackerAsTransformer tracker = new FrameTrackerAsTransformer();
      pparams._transformers = new DataTransformer[]{
              new AddRandomColumnTransformer("foo").id("add_foo"),
              new AddRandomColumnTransformer("bar").id("add_bar"),
              tracker.id("tracker")
      };
      DummyModelParameters eparams = new DummyModelParameters();
      eparams._makeModel = true;
      pparams._estimator = eparams;
      final Frame fr = Scope.track(new TestFrameBuilder()
              .withColNames("one", "two", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "c", "a", "b", "c", "a", "b", "c", "d", "c", "b", "a", "c", "b", "a", "c", "b", "a"))
              .withDataForCol(1, ar("c", "b", "a", "c", "b", "a", "c", "b", "a", "c", "b", "d", "b", "a", "c", "b", "a", "c", "b"))
              .withDataForCol(1, ard(3, 2, 1, 6, 5, 4, 9, 8, 7, 2, 1, 0, 5, 4, 3, 8, 7, 6, 1))
              .withDataForCol(2, ar("y", "n", "y", "y", "y", "y", "n", "n", "n", "n", "y", "y", "n", "n", "y", "y", "y", "n", "n"))
              .build());

      pparams._train = fr._key;
      pparams._response_column = "target";

      Pipeline pipeline = ModelBuilder.make(pparams);
      PipelineModel pmodel = Scope.track_generic(pipeline.trainModel().get());
      assertNotNull(pmodel);
      PipelineOutput output = pmodel._output;
      assertNotNull(output);
      assertNotNull(output._estimator);
      Model emodel = output._estimator.get();
      assertNotNull(emodel);
      assertTrue(emodel instanceof DummyModel);
      assertArrayEquals(new String[][] {{"a", "b", "c", "d"}, {"a", "b", "c", "d"}, {"n", "y"}}, pmodel._output._domains);
      assertArrayEquals(new String[][] {{"a", "b", "c", "d"}, {"a", "b", "c", "d"}, null, null, {"n", "y"}}, emodel._output._domains);
    } finally {
      Scope.exit();
    }
  }
}
