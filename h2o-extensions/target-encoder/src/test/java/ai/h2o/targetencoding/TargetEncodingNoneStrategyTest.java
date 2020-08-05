package ai.h2o.targetencoding;

import ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class TargetEncodingNoneStrategyTest extends TestUtil {


  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void test_fold_column_is_ignored_with_None_strategy() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("categorical", "numerical", "target", "foldc")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ard(1, 1, 4, 7, 4))
              .withDataForCol(2, ar("2", "6", "6", "6", "6"))
              .withDataForCol(3, ar(1, 2, 2, 3, 2))
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
      
      Frame encoded = teModel.score(fr);
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
              .withColNames("categorical", "numerical", "target", "foldc")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ard(1, 1, 4, 7, 4))
              .withDataForCol(2, ar("2", "6", "6", "6", "6"))
              .withDataForCol(3, ar(1, 2, 2, 3, 2))
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

      Frame encoded = teModel.score(fr);
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
      Frame training = new TestFrameBuilder()
              .withName("trainingFrame")
              .withColNames("categorical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "c", "d", "e", "b", "b"))
              .withDataForCol(1, ar("2", "6", "6", "6", "6", "2", "2"))
              .build();

      Frame holdout = new TestFrameBuilder()
              .withName("holdoutFrame")
              .withColNames("categorical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ar("2", "6", "6", "6", "6"))
              .build();

      TargetEncoderModel.TargetEncoderParameters teParams = new TargetEncoderModel.TargetEncoderParameters();
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.None;
      teParams._response_column = "target";
      teParams._train = holdout._key;
      teParams._seed = 42;
      teParams._noise = 0;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame encoded = teModel.score(training);
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
              .withColNames("cat1", "cat2", "target", "foldc")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ar("d", "e", "d", "e", "e"))
              .withDataForCol(2, ar("2", "6", "6", "6", "6"))
              .withDataForCol(3, ar(1, 2, 2, 3, 2))
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

      Frame encoded = teModel.score(fr);
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

}
