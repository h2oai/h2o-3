package hex.tree;

import hex.Model;
import hex.ModelBuilder;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.isotonic.IsotonicRegression;
import hex.isotonic.IsotonicRegressionModel;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBMModel;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;

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
  public SharedTreeModel.SharedTreeParameters _parms_proto;

  private SharedTreeModel.SharedTreeParameters _parms;

  @Before
  public void cloneParms() {
    _parms = (SharedTreeModel.SharedTreeParameters) _parms_proto.clone();
  }
  
  @Test
  public void testDebuggingParams() {
    // first make sure the tests is aware of all declared fields 
    Field[] fields = Weaver.getWovenFields(SharedTree.SharedTreeDebugParams.class);
    List<String> fieldNames = Stream.of(fields).map(Field::getName).collect(Collectors.toList());
    assertEquals(Arrays.asList(
            "_reproducible_histos", "_keep_orig_histo_precision", "_histo_monitor_class"
    ), fieldNames);
    // next verify the fields have the expected default value
    SharedTree<?, ?, ?> st = ModelBuilder.make(_parms);
    SharedTree.SharedTreeDebugParams dp = st.getDebugParams();
    assertFalse(dp._reproducible_histos);
    assertFalse(dp._keep_orig_histo_precision);
    assertNull(dp._histo_monitor_class);
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
    _parms._col_sample_rate_per_tree = 0.5; // this will trigger a code path that actually evaluates the initial histograms
    checkNAPredictor(new TestFrameBuilder()
            .withColNames("F1", "F2", "Response")
            .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
            .withDataForCol(0, ard(Double.NaN, 0, Double.NaN, 0, Double.NaN, 0)) 
            .withDataForCol(1, ard(Double.NaN, 0, Double.NaN, 0, Double.NaN, 0)) // copy of the first one
            .withDataForCol(2, ar("A", "B", "A", "B", "A", "B"))
    );
  }

  private void checkNAPredictor(TestFrameBuilder fb) {
    Scope.enter();
    try {
      Frame frame = fb.build();

      _parms._train = frame._key;
      _parms._valid = frame._key; // we don't do sampling in DRF, metrics will be NA 
      _parms._response_column = "Response";
      _parms._ntrees = 1;
      _parms._ignore_const_cols = true; // default but to make sure and illustrate the point
      _parms._min_rows = 1;
      _parms._seed = 42;

      SharedTreeModel model = (SharedTreeModel) ModelBuilder.make(_parms).trainModel().get();
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

  /**
   * PUBDEV-8276
   */
  @Test
  public void testWeightColumnIsMissing(){
    SharedTreeModel model = null;
    Frame frame = new TestFrameBuilder()
            .withColNames("F1", "Response")
            .withVecTypes(Vec.T_NUM, Vec.T_CAT)
            .withDataForCol(0, ard(Double.NaN, 0, Double.NaN, 0, Double.NaN, 0))
            .withDataForCol(1, ar("A", "B", "A", "B", "A", "B")).build();
    try {
      _parms._train = frame._key;
      _parms._response_column = "Response";
      _parms._ntrees = 1;
      _parms._seed = 42;
      _parms._weights_column = "foo";
      model = (SharedTreeModel) ModelBuilder.make(_parms).trainModel().get();
      assert true : "The model training should fail.";
    } catch(H2OModelBuilderIllegalArgumentException ex)  {
      assert ex.getMessage().contains("ERRR on field: _weights_column"): 
              "The error message should contains info about missing _weights_column.";
    } finally {
      if (frame != null) frame.remove();
      if (model != null) model.remove();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public <T extends Keyed<T>> void testActualCalibrationMethodIsRecorded(){
    try {
      Scope.enter();
      Frame frame = new TestFrameBuilder()
              .withColNames("F1", "Response")
              .withVecTypes(Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ard(Double.NaN, 0, Double.NaN, 0, Double.NaN, 0))
              .withDataForCol(1, ar("A", "B", "A", "B", "A", "B")).build();
      _parms._train = frame._key;
      _parms._response_column = "Response";
      _parms._ntrees = 1;
      _parms._seed = 42;
      _parms._min_rows = 1;
      _parms._calibration_method = CalibrationHelper.CalibrationMethod.AUTO;
      T model = (T) ModelBuilder.make(_parms).trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);
      assertEquals(
              CalibrationHelper.CalibrationMethod.PlattScaling, 
              ((SharedTreeModel.SharedTreeParameters) ((Model<?, ?, ?>) model )._parms)._calibration_method
      );
    } finally {
      Scope.exit();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public <T extends Keyed<T>> void testIsotonicRegressionCanBeUsedForCalibration(){
    try {
      Scope.enter();
      Frame frame = new TestFrameBuilder()
              .withColNames("F1", "Response")
              .withVecTypes(Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ard(Double.NaN, 0, Double.NaN, 0, Double.NaN, 0))
              .withDataForCol(1, ar("A", "B", "A", "B", "A", "B")).build();
      _parms._train = frame._key;
      _parms._response_column = "Response";
      _parms._ntrees = 1;
      _parms._seed = 42;
      _parms._min_rows = 1;
      _parms._calibrate_model = true;
      _parms._calibration_frame = frame._key;
      _parms._calibration_method = CalibrationHelper.CalibrationMethod.IsotonicRegression;
      T model = (T) ModelBuilder.make(_parms).trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);
      Frame scored = ((Model<?, ?, ?>) model).score(frame);
      Scope.track(scored);
      assertArrayEquals(new String[]{"predict", "A", "B", "cal_A", "cal_B"}, scored.names());
      assertTrue(((Model<?, ?, ?>) model).testJavaScoring(frame, scored, 1e-8));
    } finally {
      Scope.exit();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public <T extends Keyed<T>> void testTrainedModelCanBeCalibratedManually() {
    try {
      Scope.enter();
      Frame train = TestFrameCatalog.prostateCleaned();
      _parms._train = train._key;
      _parms._response_column = "CAPSULE";
      _parms._seed = 42;

      SharedTreeModel.SharedTreeParameters parmsCal = (SharedTreeModel.SharedTreeParameters) _parms.clone();
      parmsCal._calibrate_model = true;
      parmsCal._calibration_method = CalibrationHelper.CalibrationMethod.IsotonicRegression;
      parmsCal._calibration_frame = train._key;
      
      T model = (T) ModelBuilder.make(_parms).trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);

      Frame scored = ((Model<?, ?, ?>) model).score(train);
      scored.add("actual", train.vec("CAPSULE"));
      DKV.put(scored);
      Scope.track(scored);

      IsotonicRegressionModel.IsotonicRegressionParameters irParms = new IsotonicRegressionModel.IsotonicRegressionParameters();
      irParms._train = scored._key;
      irParms._out_of_bounds = IsotonicRegressionModel.OutOfBoundsHandling.Clip;
      irParms._ignored_columns = new String[]{"predict", "p0"};
      irParms._response_column = "actual";
      IsotonicRegressionModel calibrationModel = new IsotonicRegression(irParms).trainModel().get();
      assertNotNull(calibrationModel);
      Scope.track_generic(calibrationModel);

      Rapids.exec(String.format("(set.calibration.model %s %s)", model._key, calibrationModel._key));
      model = DKV.getGet(model._key); // get the updated instance from DKV, we might have a stale one

      Frame scoredWithCalib = ((Model<?, ?, ?>) model).score(train);
      Scope.track(scoredWithCalib);

      assertArrayEquals(new String[]{"predict", "p0", "p1", "cal_p0", "cal_p1"}, scoredWithCalib.names());

      T modelCal = (T) ModelBuilder.make(parmsCal).trainModel().get();
      assertNotNull(modelCal);
      Scope.track_generic(modelCal);

      Frame scoredCal = ((Model<?, ?, ?>) model).score(train);
      Scope.track(scoredCal);

      assertFrameEquals(scoredCal, scoredWithCalib, 0);
    } finally {
      Scope.exit();
    }
  }

}
