package ai.h2o.targetencoding;

import ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class TargetEncodingNoneStrategyTest extends TestUtil {

  @Test
  public void test_fold_column_is_ignored_with_None_strategy() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("categorical", "numerical", "target", "foldc")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ard(1,    1,   4,   7,   4))
              .withDataForCol(2, ar("N", "Y", "Y", "Y", "Y"))
              .withDataForCol(3, ar(1,     2,   2,   3,   2))
              .build();

      TargetEncoderModel.TargetEncoderParameters teParams = new TargetEncoderModel.TargetEncoderParameters();
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.None;
      teParams._response_column = "target";
      teParams._fold_column = "foldc";
      teParams._train = fr._key;
      teParams._seed = 42;
      teParams._noise = 0;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);
      
      Frame encoded = teModel.transformTraining(fr);
      Scope.track(encoded);

      Vec catEncoded = encoded.vec("categorical_te");
      printOutFrameAsTable(encoded);
      Vec expected = dvec(0.5, 1, 1, 1, 0.5);
      assertVecEquals(expected, catEncoded, 1e-5);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_default_noise_with_None_strategy() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("categorical", "numerical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ard( 1,   1,   4,   7,   4))
              .withDataForCol(2, ar("N", "Y", "Y", "Y", "Y"))
              .build();

      TargetEncoderModel.TargetEncoderParameters teParams = new TargetEncoderModel.TargetEncoderParameters();
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.None;
      teParams._response_column = "target";
      teParams._train = fr._key;
      teParams._seed = 42;
      teParams._noise = -1; // default noise

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame encoded = teModel.transformTraining(fr);
      Scope.track(encoded);

      printOutFrameAsTable(encoded);
      Vec catEncoded = encoded.vec("categorical_te");
      Vec expected = dvec(0.5, 1, 1, 1, 0.5);
      assertVecEquals(expected, catEncoded, 1e-2);
      try {
        assertVecEquals(expected, catEncoded, 1e-5);
        fail("no noise detected");
      } catch (AssertionError ae) {
        assertFalse(ae.getMessage().contains("no noise detected"));
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void endToEndTest() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("trainingFrame")
              .withColNames("categorical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "c", "d", "e", "b", "b"))
              .withDataForCol(1, ar("N", "Y", "Y", "Y", "Y", "N", "N"))
              .build();

      Frame te_holdout = new TestFrameBuilder()
              .withName("holdoutFrame")
              .withColNames("categorical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ar("N", "Y", "Y", "Y", "Y"))
              .build();

      TargetEncoderModel.TargetEncoderParameters teParams = new TargetEncoderModel.TargetEncoderParameters();
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.None;
      teParams._response_column = "target";
      teParams._train = te_holdout._key;
      teParams._seed = 42;
      teParams._noise = 0;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame encoded = teModel.transform(fr);
      Scope.track(encoded);

      printOutFrameAsTable(encoded);

      Vec catEncoded = encoded.vec("categorical_te");
      Vec expected = dvec(0.5, 1, 0.8, 0.8, 0.8, 1, 1);
      assertVecEquals(expected, catEncoded, 1e-5);
    } finally {
      Scope.exit();
    }
  }


  @Test
  public void test_None_strategy_on_multiple_categorical_columns() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("cat1", "cat2", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ar("d", "e", "d", "e", "e"))
              .withDataForCol(2, ar("N", "Y", "Y", "Y", "Y"))
              .build();

      TargetEncoderModel.TargetEncoderParameters teParams = new TargetEncoderModel.TargetEncoderParameters();
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.None;
      teParams._response_column = "target";
      teParams._train = fr._key;
      teParams._seed = 42;
      teParams._noise = 0;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame encoded = teModel.transformTraining(fr);
      Scope.track(encoded);

      Vec expectedCat1Enc = dvec(0.5, 1, 1, 1, 0.5);
      printOutFrameAsTable(encoded);
      assertVecEquals(expectedCat1Enc, encoded.vec("cat1_te"), 1e-5);

      Vec expectedCat2Enc = dvec(0.5, 1, 0.5, 1, 1);
      assertVecEquals(expectedCat2Enc, encoded.vec("cat2_te"), 1e-5);
    } finally {
      Scope.exit();
    }
  }
  
  
  @Test
  public void test_None_strategy_produces_the_same_result_on_transform_and_transformTraining() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("trainFrame")
              .withColNames("categorical", "numerical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ard( 1,   1,   4,   7,   4))
              .withDataForCol(2, ar("N", "N", "Y", "Y", "Y"))
              .build();

      TargetEncoderModel.TargetEncoderParameters teParams = new TargetEncoderModel.TargetEncoderParameters();
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.None;
      teParams._response_column = "target";
      teParams._train = fr._key;
      teParams._seed = 42;
      teParams._noise = 0;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame encodedAsTrain = teModel.transformTraining(fr);
      Scope.track(encodedAsTrain);

      Frame encodedAsNew = teModel.transform(fr);
      Scope.track(encodedAsNew);

      assert compareFrames(encodedAsTrain, encodedAsNew, 1e-5);
    } finally {
      Scope.exit();
    }
  }
  
  
  @Test
  public void test_None_strategy_produces_the_same_result_on_transform_and_score() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("trainFrame")
              .withColNames("categorical", "numerical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ard( 1,   1,   4,   7,   4))
              .withDataForCol(2, ar("N", "Y", "Y", "Y", "N"))
              .build();

      TargetEncoderModel.TargetEncoderParameters teParams = new TargetEncoderModel.TargetEncoderParameters();
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.None;
      teParams._response_column = "target";
      teParams._train = fr._key;
      teParams._seed = 42;
      teParams._noise = 0;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame encoded = teModel.transform(fr);
      Scope.track(encoded);

      Frame predictions = teModel.score(fr);
      Scope.track(predictions);
      
      assert compareFrames(encoded, predictions, 1e-5);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_encoder_trained_with_None_strategy_can_be_used_to_transform_a_frame_without_target() {
    try {
      Scope.enter();
      Frame train = new TestFrameBuilder()
              .withName("trainFrame")
              .withColNames("cat1", "cat2", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ar("d", "e", "d", "e", "e"))
              .withDataForCol(2, ar("N", "Y", "Y", "Y", "Y"))
              .build();

      Frame test = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("cat1", "cat2")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("c", "b", "a"))
              .withDataForCol(1, ar("d", "e", "f"))
              .build();
      
      TargetEncoderModel.TargetEncoderParameters teParams = new TargetEncoderModel.TargetEncoderParameters();
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.None;
      teParams._response_column = "target";
      teParams._train = train._key;
      teParams._seed = 42;
      teParams._noise = 0;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      double priorMean1 = TargetEncoderHelper.computePriorMean(teModel._output._target_encoding_map.get("cat1"));
      double priorMean2 = TargetEncoderHelper.computePriorMean(teModel._output._target_encoding_map.get("cat2"));
      assertEquals(0.8, priorMean1, 1e-6);
      assertEquals(0.8, priorMean2, 1e-6);

      Frame encoded = teModel.transform(test);
      Scope.track(encoded);

      Frame predictions = teModel.score(test);
      Scope.track(predictions);
      
      Vec expectCat1Enc = dvec(0.8, 1., 0.5);
      assertVecEquals(expectCat1Enc, encoded.vec("cat1_te"), 1e-5);
      assertVecEquals(expectCat1Enc, predictions.vec("cat1_te"), 1e-5);

      Vec expectCat2Enc = dvec(0.5, 1., 0.8);
      assertVecEquals(expectCat2Enc, encoded.vec("cat2_te"), 1e-5);
      assertVecEquals(expectCat2Enc, predictions.vec("cat2_te"), 1e-5);
      
      assert compareFrames(encoded, predictions, 1e-5);
    } finally {
      Scope.exit();
    }
  }


}
