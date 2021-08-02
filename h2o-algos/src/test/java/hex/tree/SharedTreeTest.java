package hex.tree;

import hex.ModelBuilder;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.Scope;
import water.TestUtil;
import water.Weaver;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.util.PojoUtils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class SharedTreeTest extends TestUtil  {

  @BeforeClass
  public static void stall() { 
    stall_till_cloudsize(1);
  }

  @Parameterized.Parameters(name = "{index}: gbm({0})")
  public static Iterable<SharedTreeModel.SharedTreeParameters> data() {
    // 1. GBM
    GBMModel.GBMParameters gbmParams = new GBMModel.GBMParameters();
    gbmParams._learn_rate = 1;
    // 2. DRF
    DRFModel.DRFParameters drfParams = new DRFModel.DRFParameters();
    drfParams._sample_rate = 1;
    return Arrays.asList(gbmParams, drfParams);
  }

  @Parameterized.Parameter
  public SharedTreeModel.SharedTreeParameters _parms;

  @Test
  public void testDebuggingParams() {
    // first make sure the tests is aware of all declared fields 
    Field[] fields = Weaver.getWovenFields(SharedTree.SharedTreeDebugParams.class);
    List<String> fieldNames = Stream.of(fields).map(Field::getName).collect(Collectors.toList());
    assertEquals(Arrays.asList(
            "_reproducible_histos", "_keep_orig_histo_precision"
    ), fieldNames);
    // next verify the fields have the expected default value
    SharedTree<?, ?, ?> st = ModelBuilder.make(_parms);
    SharedTree.SharedTreeDebugParams dp = st.getDebugParams();
    assertFalse(dp._reproducible_histos);
    assertFalse(dp._keep_orig_histo_precision);
  }

  @Test
  public void testStrictHistogramReproducibilityIsDisabledByDefault() {
    assertFalse(_parms.forceStrictlyReproducibleHistograms());
  }

  @Test
  public void testNAPredictor_cat() {
    checkNAPredictor(twoVecFrameBuilder()
            .withVecTypes(Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, ar(null, "V", null, "V", null, "V"))
    );
  }

  @Test
  public void testNAPredictor_num() {
    checkNAPredictor(twoVecFrameBuilder()
            .withVecTypes(Vec.T_NUM, Vec.T_CAT)
            .withDataForCol(0, ard(Double.NaN, 1, Double.NaN, 1, Double.NaN, 1))
    );
  }

  @Test
  public void testNAPredictor_PUBDEV7517() {
    SharedTreeModel.SharedTreeParameters parms = (SharedTreeModel.SharedTreeParameters) _parms.clone();
    parms._col_sample_rate_per_tree = 0.5; // this will trigger a code path that actually evaluates the initial histograms
    checkNAPredictor(new TestFrameBuilder()
            .withColNames("F1", "F2", "Response")
            .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
            .withDataForCol(0, ard(Double.NaN, 0, Double.NaN, 0, Double.NaN, 0)) 
            .withDataForCol(1, ard(Double.NaN, 0, Double.NaN, 0, Double.NaN, 0)) // copy of the first one
            .withDataForCol(2, ar("A", "B", "A", "B", "A", "B")),
            parms
    );
  }

  private void checkNAPredictor(TestFrameBuilder fb) {
    checkNAPredictor(fb, (SharedTreeModel.SharedTreeParameters) _parms.clone());
  }
  
  private void checkNAPredictor(TestFrameBuilder fb, SharedTreeModel.SharedTreeParameters parms) {
    Scope.enter();
    try {
      Frame frame = fb.build();

      assertNotSame(parms, _parms); // make sure we are mutating a clone
      parms._train = frame._key;
      parms._valid = frame._key; // we don't do sampling in DRF, metrics will be NA 
      parms._response_column = "Response";
      parms._ntrees = 1;
      parms._ignore_const_cols = true; // default but to make sure and illustrate the point
      parms._min_rows = 1;
      parms._seed = 42;

      SharedTreeModel model = (SharedTreeModel) ModelBuilder.make(parms).trainModel().get();
      Scope.track_generic(model);

      // We should have a perfect model
      assertEquals(0, model.classification_error(), 0);

      // Check that we predict perfectly
      Frame test = Scope.track(frame.subframe(model._output.features()));
      Frame scored = Scope.track(model.score(test));
      assertCatVecEquals(frame.vec("Response"), scored.vec("predict"));

      // Tree should split on NAs
      SharedTreeSubgraph tree0 = model.getSharedTreeSubgraph(0, 0);
      assertEquals(3, tree0.nodesArray.size()); // this implies depth 1
      assertTrue(tree0.rootNode.isNaVsRest());
    } finally {
      Scope.exit();
    }
  }

  private TestFrameBuilder twoVecFrameBuilder() {
    return new TestFrameBuilder()
          .withColNames("F", "Response")
          .withDataForCol(1, ar("A", "B", "A", "B", "A", "B"));
  }

}
