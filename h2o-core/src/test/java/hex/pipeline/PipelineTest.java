package hex.pipeline;

import hex.Model;
import hex.ModelBuilder;
import hex.pipeline.DataTransformerTest.*;
import hex.pipeline.DataTransformerTest.FrameTrackerAsTransformer.Transformation;
import hex.pipeline.DataTransformerTest.FrameTrackerAsTransformer.Transformations;
import hex.pipeline.PipelineModel.PipelineOutput;
import hex.pipeline.PipelineModel.PipelineParameters;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.junit.rules.DKVIsolation;
import water.junit.rules.ScopeTracker;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.test.dummy.DummyModel;
import water.test.dummy.DummyModelParameters;
import water.util.ArrayUtils;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.Assert.*;
import static water.TestUtil.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class PipelineTest {

  @Rule
  public ScopeTracker scope = new ScopeTracker();

  @Rule
  public DKVIsolation isolation = new DKVIsolation();

  private void checkFrameState(Frame fr) {
    assertNotNull(fr.getKey());
    assertNotNull(DKV.get(fr.getKey()));
    assertFrameEquals(fr, DKV.getGet(fr.getKey()), 1e-10);
    for (int i=0; i<fr.keys().length; i++) {
      Key<Vec> k = fr.keys()[i];
      assertNotNull(k);
      assertNotNull(k.get());
      assertVecEquals(fr.vec(i), k.get(), 1e-10);
    }
  }
  
  @Test
  public void test_simple_transformation_pipeline() {
    PipelineParameters pparams = new PipelineParameters();
    FrameTrackerAsTransformer tracker = new FrameTrackerAsTransformer();
    pparams.setTransformers(
            new MultiplyNumericColumnTransformer("two", 5).name("mult_5"),
            new AddRandomColumnTransformer("foo").name("add_foo"),
            new AddRandomColumnTransformer("bar").name("add_bar"),
            tracker.name("tracker")
    );
    final Frame fr = Scope.track(new TestFrameBuilder()
            .withColNames("one", "two", "target")
            .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
            .withDataForCol(0, ard(1, 2, 3))
            .withDataForCol(1, ard(3, 2, 1))
            .withDataForCol(2, ar("yes", "no", "yes"))
            .build());
    
    Vec notMult = fr.vec(1).makeCopy();
    Vec mult = fr.vec(1).makeZero(); mult.set(0, 3*5); mult.set(1, 2*5); mult.set(2, 1*5); // a 5-mult vec of column "two" hand-made for test assertions.

    pparams._train = fr._key;

    Pipeline pipeline = ModelBuilder.make(pparams);
    PipelineModel pmodel = Scope.track_generic(pipeline.trainModel().get());
    
    assertNotNull(pmodel);
    PipelineOutput output = pmodel._output;
    assertNotNull(output);
    assertNull(output._estimator);
    assertNotNull(output._transformers);
    assertEquals(4, output._transformers.length);
    assertEquals(0, tracker.getTransformations().size());
    checkFrameState(fr);
    assertVecEquals(notMult, fr.vec(1), 0);

    Frame scored = Scope.track(pmodel.score(fr));
    assertNotNull(scored);
    TestUtil.printOutFrameAsTable(scored);
    assertEquals(1, tracker.getTransformations().size());
    assertArrayEquals(new String[] {"one", "two", "target", "foo", "bar"}, scored.names());
    checkFrameState(fr);
    checkFrameState(scored);
    assertVecEquals(notMult, fr.vec(1), 0);
    assertVecEquals(mult, scored.vec(1), 0);

    Frame rescored = Scope.track(pmodel.score(fr));
    TestUtil.printOutFrameAsTable(rescored);
    assertEquals(2, tracker.getTransformations().size());
    assertNotSame(scored, rescored);
    assertFrameEquals(scored, rescored, 1.6);
    checkFrameState(fr);
    checkFrameState(rescored);
    assertVecEquals(notMult, fr.vec(1), 0);
    assertVecEquals(mult, rescored.vec(1), 0);

    Frame transformed = Scope.track(pmodel.transform(fr));
    TestUtil.printOutFrameAsTable(transformed);
    assertEquals(3, tracker.getTransformations().size());
    assertNotSame(scored, transformed);
    assertFrameEquals(scored, transformed, 1.6);
    checkFrameState(fr);
    checkFrameState(transformed);
    assertVecEquals(notMult, fr.vec(1), 0);
    assertVecEquals(mult, transformed.vec(1), 0);
  }

  @Test
  public void test_simple_classification_pipeline() {
    PipelineParameters pparams = new PipelineParameters();
    FrameTrackerAsTransformer tracker = new FrameTrackerAsTransformer();
    pparams.setTransformers(
            new AddRandomColumnTransformer("foo").name("add_foo"),
            new AddRandomColumnTransformer("bar").name("add_bar"), 
            tracker.name("tracker")
    );
    DummyModelParameters eparams = new DummyModelParameters();
    eparams._makeModel = true;
    pparams._estimatorParams = eparams;
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
    assertArrayEquals(new String[] {"one", "two", "foo", "bar", "target"}, emodel._output._names);
      
    assertEquals(1, tracker.getTransformations().size());
    checkFrameState(fr);
      
    Frame predictions = Scope.track(pmodel.score(fr));
    assertEquals(2, tracker.getTransformations().size());
    assertNotNull(predictions);
    TestUtil.printOutFrameAsTable(predictions);
    checkFrameState(fr);
    checkFrameState(predictions);
    
    Frame transformed = Scope.track(pmodel.transform(fr));
    assertEquals(3, tracker.getTransformations().size());
    assertNotNull(transformed);
    TestUtil.printOutFrameAsTable(transformed);
    assertArrayEquals(
            Arrays.stream(emodel._output._names).sorted().toArray(), //model reorders input columns to obtain this output
            Arrays.stream(transformed.names()).sorted().toArray()
    );
    checkFrameState(fr);
    checkFrameState(transformed);
  }
  
  @Test 
  public void test_simple_classification_pipeline_with_sensitive_cv() {
    int nfolds = 3;
    PipelineParameters pparams = new PipelineParameters();
    pparams._nfolds = nfolds;
    FrameTrackerAsTransformer tracker = new FrameTrackerAsTransformer();
    pparams.setTransformers(
            new AddRandomColumnTransformer("foo").name("add_foo"),
            new AddRandomColumnTransformer("bar").name("add_bar"),
            new AddDummyCVColumnTransformer("cv_fold", Vec.T_CAT).name("add_cv_fold"),
            tracker.name("track")
    );
    DummyModelParameters eparams = new DummyModelParameters();
    eparams._makeModel = true;
    eparams._keep_cross_validation_models = true;
      
    pparams._estimatorParams = eparams;
    final Frame fr = Scope.track(new TestFrameBuilder()
            .withName("train")
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
    assertArrayEquals(new String[] {"one", "two", "foo", "bar", "target"}, emodel._output._names);

    Transformations transformations = tracker.getTransformations();
    System.out.println(transformations);
    assertEquals(2*nfolds+1, tracker.getTransformations().size()); // nfolds * 2 [train+valid] + 1 [final model, train only]
    assertNotEquals(fr.getKey().toString(), transformations.transformations[0].frameId); // training frame for final model transformed first
    assertTrue(transformations.transformations[0].frameId.startsWith(fr.getKey().toString()+"@@Training_trf_by_add_bar")); 
    assertEquals(DataTransformer.FrameType.Training, transformations.transformations[0].type);
    assertFalse(transformations.transformations[0].is_cv);
    assertEquals(nfolds*2, Stream.of(transformations.transformations).filter(t -> t.is_cv).count());
    assertEquals(nfolds, Stream.of(transformations.transformations).filter(t -> t.is_cv && t.type == DataTransformer.FrameType.Training).count());
    assertEquals(nfolds, Stream.of(transformations.transformations).filter(t -> t.is_cv && t.type == DataTransformer.FrameType.Validation).count());
    assertEquals(nfolds, emodel._output._cross_validation_models.length);
    for (int i=0; i<nfolds; i++) {
      DummyModel cvModel = (DummyModel) emodel._output._cross_validation_models[i].get();
      assertNotNull(cvModel);
      assertArrayEquals("CV training frame not transformed", 
              new String[] {"one", "two", "foo", "bar", "cv_fold", ModelBuilder.CV_WEIGHTS_COLUMN, "target"}, 
              cvModel._output._names);
      assertArrayEquals("CV training frame not transformed using CV context", 
              IntStream.rangeClosed(0, i+1).mapToObj(String::valueOf).toArray(String[]::new), 
              cvModel._output._domains[4]);
    }
    checkFrameState(fr);
      
    Frame predictions = Scope.track(pmodel.score(fr));
    assertNotNull(predictions);
    TestUtil.printOutFrameAsTable(predictions);
    checkFrameState(fr);
    checkFrameState(predictions);
  }

  @Test
  public void test_simple_classification_pipeline_with_nonsensitive_cv() {
    // testing optimization when none of the transformers is sensitive to cross-validation, 
    // ie requires specific transformation to be applied for each CV-frame.
    // in which case we expect the train+valid frames to be transformed only once.
    int nfolds = 3;
    PipelineParameters pparams = new PipelineParameters();
    pparams._nfolds = nfolds;
    FrameTrackerAsTransformer tracker = new FrameTrackerAsTransformer();
    pparams.setTransformers(
            new AddRandomColumnTransformer("foo").name("add_foo"),
            new AddRandomColumnTransformer("bar").name("add_bar"),
            tracker.name("track")
    );
    DummyModelParameters eparams = new DummyModelParameters();
    eparams._makeModel = true;
    pparams._estimatorParams = eparams;
    final Frame fr = Scope.track(new TestFrameBuilder()
            .withName("train")
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
    assertArrayEquals(new String[] {"one", "two", "foo", "bar", "target"}, emodel._output._names);

    Transformations transformations = tracker.getTransformations();
    System.out.println(transformations);
    assertEquals(1, tracker.getTransformations().size()); // only one transformation, once and for all, as no transformer is CV-sensitive
    assertNotEquals(fr.getKey().toString(), transformations.transformations[0].frameId); 
    assertTrue(transformations.transformations[0].frameId.startsWith(fr.getKey().toString()+"@@Training_trf_by_add_bar")); 
    assertEquals(DataTransformer.FrameType.Training, transformations.transformations[0].type);
    assertFalse(transformations.transformations[0].is_cv);
    checkFrameState(fr);

    Frame predictions = Scope.track(pmodel.score(fr));
    assertNotNull(predictions);
    TestUtil.printOutFrameAsTable(predictions);
    checkFrameState(fr);
    checkFrameState(predictions);
  }
  
  @Test
  public void test_simple_regression_pipeline() {
    PipelineParameters pparams = new PipelineParameters();
    FrameTrackerAsTransformer tracker = new FrameTrackerAsTransformer();
    pparams.setTransformers(
            new AddRandomColumnTransformer("foo").name("add_foo"),
            new AddRandomColumnTransformer("bar").name("add_bar"),
            tracker.name("tracker")
    );
    DummyModelParameters eparams = new DummyModelParameters();
    eparams._makeModel = true;
    pparams._estimatorParams = eparams;
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
    assertArrayEquals(new String[] {"one", "two", "foo", "bar", "target"}, emodel._output._names);

    assertEquals(1, tracker.getTransformations().size());
    checkFrameState(fr);

    Frame predictions = Scope.track(pmodel.score(fr));
    assertNotNull(predictions);
    TestUtil.printOutFrameAsTable(predictions);
    assertEquals(2, tracker.getTransformations().size());
    checkFrameState(fr);
    checkFrameState(predictions);
    
    Frame transformed = Scope.track(pmodel.transform(fr));
    assertNotNull(transformed);
    TestUtil.printOutFrameAsTable(transformed);
    assertEquals(3, tracker.getTransformations().size());
    assertArrayEquals(
            Arrays.stream(emodel._output._names).sorted().toArray(), //model reorders input columns to obtain this output
            Arrays.stream(transformed.names()).sorted().toArray()
    );
    checkFrameState(fr);
    checkFrameState(transformed);
  }
  
  @Test 
  public void test_simple_regression_pipeline_with_sensitive_cv() {
    //CV scoring logic is different with regression 
    int nfolds = 3;
    PipelineParameters pparams = new PipelineParameters();
    pparams._nfolds = nfolds;
    FrameTrackerAsTransformer tracker = new FrameTrackerAsTransformer();
    pparams.setTransformers(
            new AddRandomColumnTransformer("foo").name("add_foo"),
            new AddRandomColumnTransformer("bar").name("add_bar"),
            new AddDummyCVColumnTransformer("cv_fold").name("add_cv_fold"),
            tracker.name("track")
    );
    DummyModelParameters eparams = new DummyModelParameters();
    eparams._makeModel = true;
    pparams._estimatorParams = eparams;
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
    assertArrayEquals(new String[] {"one", "two", "foo", "bar", "target"}, emodel._output._names);

    Transformations transformations = tracker.getTransformations();
    System.out.println(transformations);
    assertEquals(2*nfolds+1, transformations.size()); // nfolds * 2 [train+valid] + 1 [final model, train only]
    checkFrameState(fr);

    Frame predictions = Scope.track(pmodel.score(fr));
    assertNotNull(predictions);
    TestUtil.printOutFrameAsTable(predictions);
    checkFrameState(fr);
    checkFrameState(predictions);
  }
  
  @Test
  public void test_categorical_features_are_not_modified_before_transformations() {
    PipelineParameters pparams = new PipelineParameters();
    FrameTrackerAsTransformer tracker = new FrameTrackerAsTransformer();
    pparams.setTransformers(
            new AddRandomColumnTransformer("foo").name("add_foo"),
            new AddRandomColumnTransformer("bar").name("add_bar"),
            tracker.name("tracker")
    );
    DummyModelParameters eparams = new DummyModelParameters();
    eparams._makeModel = true;
    pparams._estimatorParams = eparams;
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
    checkFrameState(fr);
  }
  
  @Test
  public void test_categorical_features_are_not_modified_before_transformations_with_cv() {
    //CV uses a different initialization/training flow
    int nfolds = 3;
    PipelineParameters pparams = new PipelineParameters();
    pparams._nfolds = nfolds;
    FrameCheckerAsTransformer checker = new FrameCheckerAsTransformer(fr -> {
//      TestUtil.printOutFrameAsTable(fr);
      String[][] unencodedDomains = {{"a", "b", "c", "d"}, {"a", "b", "c", "d"}, {"n", "y"}};
      if (ArrayUtils.contains(fr.names(), ModelBuilder.CV_WEIGHTS_COLUMN)) {
        assertEquals(ModelBuilder.CV_WEIGHTS_COLUMN, fr.name(fr.names().length-1));
        // weights col is numerical
        assertArrayEquals(ArrayUtils.append(unencodedDomains, new String[][] { null }), fr.domains());
      } else {
        assertArrayEquals(unencodedDomains, fr.domains());
      }
    });
    FrameTrackerAsTransformer tracker = new FrameTrackerAsTransformer();
    pparams.setTransformers(
            checker.name("check_frame_not_encoded"),
            new AddRandomColumnTransformer("foo").name("add_foo"),
            new AddRandomColumnTransformer("bar").name("add_bar"),
            new AddDummyCVColumnTransformer("cv_fold").name("add_cv_fold"),
            tracker.name("tracker")
    );
    DummyModelParameters eparams = new DummyModelParameters();
    eparams._makeModel = true;
    eparams._keep_cross_validation_models = true;
    eparams._categorical_encoding = Model.Parameters.CategoricalEncodingScheme.LabelEncoder;
      
    pparams._estimatorParams = eparams;
    final Frame fr = Scope.track(new TestFrameBuilder()
            .withColNames("one", "two", "target")
            .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", "c", "a", "b", "c", "a", "b", "c", "d", "c", "b", "a", "c", "b", "a", "c", "b", "a"))
            .withDataForCol(1, ar("c", "b", "a", "c", "b", "a", "c", "b", "a", "c", "b", "d", "b", "a", "c", "b", "a", "c", "b"))
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
    assertArrayEquals(new String[][] {{"a", "b", "c", "d"}, {"a", "b", "c", "d"}, null/*foo*/, null/*bar*/, {"n", "y"}}, emodel._output._origDomains);
    assertArrayEquals(new String[][] {null/*encoded one*/, null/*encoded two*/, null/*foo*/, null/*bar*/, {"n", "y"}}, emodel._output._domains);
      
    assertEquals(nfolds, emodel._output._cross_validation_models.length);
    for (Key<DummyModel> km : emodel._output._cross_validation_models) {
      DummyModel cvModel = km.get();
      assertNotNull(cvModel);
      assertArrayEquals(new String[][] {{"a", "b", "c", "d"}, {"a", "b", "c", "d"}, null/*foo*/, null/*bar*/, null/*cv_fold*/, null/*cv_weights*/, {"n", "y"}}, cvModel._output._origDomains);
      assertArrayEquals(new String[][] {null/*encoded one*/, null/*encoded two*/, null/*foo*/, null/*bar*/ , null/*cv_fold*/, null/*cv_weights*/, {"n", "y"}}, cvModel._output._domains);
    }
    checkFrameState(fr);
  }
}
