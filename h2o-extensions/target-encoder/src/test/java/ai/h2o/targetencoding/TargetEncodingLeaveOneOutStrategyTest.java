package ai.h2o.targetencoding;

import ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy;
import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static ai.h2o.targetencoding.TargetEncoderHelper.*;
import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class TargetEncodingLeaveOneOutStrategyTest extends TestUtil {

  @Test
  public void test_category_is_encoded_with_priorMean_if_denominator_becomes_zero_due_to_LOO_strategy() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("categorical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar(  "a",  "b",   "a"))
              .withDataForCol(1, ar("yes", "no", "yes"))
              .build();

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train = fr._key;
      teParams._response_column = "target";
      teParams._noise = 0;
      teParams._seed = 123;
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.LeaveOneOut;
      
      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);
      
      Frame encodings = teModel._output._target_encoding_map.get("categorical");
      assertVecEquals(vec(2, 0), encodings.vec(NUMERATOR_COL), 0);
      assertVecEquals(vec(2, 1), encodings.vec(DENOMINATOR_COL), 0);
      // encodings has denominator=1 for 'b'
      // so when applying TE with LOO strategy on 'b', it will substract 1, giving a 0 denominator.
      // TargetEncoderModel should handle this and encode 'b' as priorMean value in this case.
      double priorMean = computePriorMean(encodings);
      assertEquals(2./3, priorMean, 1e-6);
      
      Frame encoded = teModel.transformTraining(fr);
      Scope.track(encoded);
      Vec catEnc = encoded.vec("categorical_te");
      assertEquals(priorMean, catEnc.at(1), 1e-5);
    } finally {
        Scope.exit();
    }
  }

  @Test // TODO see PUBDEV-5941 regarding chunk layout
  public void deletionDependsOnTheChunkLayoutTest() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "a"))
              .withChunkLayout(1, 2)
//              .withChunkLayout(3)  // fails if we set one single chunk `.withChunkLayout(3)`
              .build();

      Vec zeroVec = Vec.makeZero(fr.numRows());
      String nameOfAddedColumn = "someName";

      fr.add(nameOfAddedColumn, zeroVec);
      zeroVec.remove();
      fr.vec(nameOfAddedColumn).at(1);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_category_is_encoded_with_priorMean_if_denominator_becomes_zero_due_to_LOO_strategy_2() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("categorical", "num", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "c", "d", "b", "a"))
              .withDataForCol(1, ard( 1,   1,   4,   7,   5,   4))
              .withDataForCol(2, ar("N", "Y", "Y", "N", "Y", "Y"))
              .build();

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train = fr._key;
      teParams._response_column = "target";
      teParams._noise = 0;
      teParams._seed = 123;
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.LeaveOneOut;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame encoded = teModel.transformTraining(fr);
      Scope.track(encoded);
      Vec catEnc = encoded.vec("categorical_te");

      Frame encodings = teModel._output._target_encoding_map.get("categorical");
      double priorMean = computePriorMean(encodings);
      assertEquals(2./3, priorMean, 1e-6);
      
      // For level `c` and `d` we got only one row... so after leave one out subtraction we get `0` for denominator.
      assertEquals(priorMean, catEnc.at(2), 1e-5);
      assertEquals(priorMean, catEnc.at(3), 1e-5);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_NA_values_are_encoded_as_a_separate_category() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("categorical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", null, null, null))
              .withDataForCol(1, ar("N", "Y",  "Y",  "N",  "Y"))
              .withChunkLayout(3, 2)
              .build();

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train = fr._key;
      teParams._response_column = "target";
      teParams._noise = 0;
      teParams._seed = 123;
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.LeaveOneOut;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame encodings = teModel._output._target_encoding_map.get("categorical");
      double priorMean = computePriorMean(encodings);
      assertEquals(.6, priorMean, 1e-6);

      Frame encoded = teModel.transformTraining(fr);
      Scope.track(encoded);
//      Vec catEnc = encoded.vec("categorical_te");
//      
//      Vec expected = dvec(0.6, 0.6, 0.5, 1, 0.5);
//      assertVecEquals(expected, catEnc, 1e-5);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_empty_string_and_NA_values_are_both_encoded_as_a_separate_category() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("categorical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b",  "",  "", null)) // null and "" are different categories even though they look the same in printout
              .withDataForCol(1, ar("N", "Y", "Y", "N",  "Y"))
              .build();

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train = fr._key;
      teParams._response_column = "target";
      teParams._noise = 0;
      teParams._seed = 123;
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.LeaveOneOut;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame encodings = teModel._output._target_encoding_map.get("categorical");
      double priorMean = computePriorMean(encodings);
      assertEquals(.6, priorMean, 1e-6);

      Frame encoded = teModel.transformTraining(fr);
      Scope.track(encoded);
      Vec catEnc = encoded.vec("categorical_te");

      Vec expected = dvec(0.6, 0.6, 0, 1, 0.6);
      assertVecEquals(expected, catEnc, 1e-5);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_NA_are_encoded_like_another_category() {
    try {
      Scope.enter();
      Frame fr1 = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("categorical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", null, null, null))
              .withDataForCol(1, ar("N", "Y",  "Y",  "N",  "Y"))
              .build();

      Frame fr2 = new TestFrameBuilder()
              .withName("testFrame2")
              .withColNames("categorical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "na", "na", "na"))
              .withDataForCol(1, ar("N", "Y",  "Y",  "N",  "Y"))
              .build();

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train = fr1._key;
      teParams._response_column = "target";
      teParams._noise = 0;
      teParams._seed = 123;
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.LeaveOneOut;

      TargetEncoder te1 = new TargetEncoder(teParams);
      TargetEncoderModel teModel1 = te1.trainModel().get();
      Scope.track_generic(teModel1);

      Frame encodings = teModel1._output._target_encoding_map.get("categorical");
      double priorMean = computePriorMean(encodings);
      assertEquals(.6, priorMean, 1e-6);

      Frame encoded1 = teModel1.transformTraining(fr1);
      Scope.track(encoded1);
      Vec catEnc1 = encoded1.vec("categorical_te");

      // now the same but training fr2, replacing missing values with a new category
      teParams._train = fr2._key;
      TargetEncoder te2 = new TargetEncoder(teParams);
      TargetEncoderModel teModel2 = te2.trainModel().get();
      Scope.track_generic(teModel2);

      Frame encoded2 = teModel2.transformTraining(fr2);
      Scope.track(encoded2);
      Vec catEnc2 = encoded2.vec("categorical_te");

      Vec expected = dvec(0.6, 0.6, 0.5, 1, 0.5);
      assertVecEquals(expected, catEnc1, 1e-5);
      assertVecEquals(expected, catEnc2, 1e-5);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void yet_another_LOO_test_with_different_values() {
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

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train = fr._key;
      teParams._response_column = "target";
      teParams._noise = 0;
      teParams._seed = 123;
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.LeaveOneOut;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame encoded = teModel.transformTraining(fr);
      Scope.track(encoded);
      Vec catEnc = encoded.vec("categorical_te");

      Vec expected = vec(1, 1, 1, 1, 0);
      assertVecEquals(expected, catEnc, 1e-5);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_that_fold_column_is_ignored_by_LOO_strategy() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("categorical", "numerical", "target", "foldc")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ard(1,    1,   4,   7,   4))
              .withDataForCol(2, ar("N", "Y", "Y", "Y", "Y"))
              .withDataForCol(3, ar(  1,   2,   2,   3,   2))
              .build();

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train = fr._key;
      teParams._response_column = "target";
      teParams._fold_column = "foldc";
      teParams._noise = 0;
      teParams._seed = 123;
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.LeaveOneOut;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame encoded = teModel.transformTraining(fr);
      Scope.track(encoded);
      Vec catEnc = encoded.vec("categorical_te");

      Vec expected = vec(1, 1, 1, 1, 0);
      assertVecEquals(expected, catEnc, 1e-5);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_noise_can_be_applied_with_LOO_strategy() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("categorical", "numerical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ard(1,    1,   4,   7,   4))
              .withDataForCol(2, ar("N", "Y", "Y", "Y", "Y"))
              .build();

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train = fr._key;
      teParams._response_column = "target";
      teParams._noise = -1; // use default noise level computed from target range
      teParams._seed = 123;
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.LeaveOneOut;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame encoded = teModel.transformTraining(fr);
      Scope.track(encoded);
      Vec catEnc = encoded.vec("categorical_te");

      Vec expected = vec(1, 1, 1, 1, 0);
      assertVecEquals(expected, catEnc, 1e-2);
      try {
        assertVecEquals(expected, catEnc, 1e-5);
        fail("no noise detected");
      } catch (AssertionError ae) {
        assertFalse(ae.getMessage().contains("no noise detected"));
      }
    } finally {
      Scope.exit();
    }
  }


  @Test
  public void test_LOO_strategy_can_be_applied_on_multiple_columns_at_once() {
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

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train = fr._key;
      teParams._response_column = "target";
      teParams._noise = 0;
      teParams._seed = 123;
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.LeaveOneOut;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);
        
      Frame encoded = teModel.transformTraining(fr);
      Scope.track(encoded);
      Vec cat1Enc = encoded.vec("cat1_te");
      Vec cat2Enc = encoded.vec("cat2_te");
      assertNotNull(cat1Enc);
      assertNotNull(cat2Enc);
        
      // Let's check it with Single TE version of the algorithm. So we rely here on a correctness of the single-column encoding.
      //  For the first encoded column
      teParams._ignored_columns = ar("cat2");
      TargetEncoder te_cat1only = new TargetEncoder(teParams);
      TargetEncoderModel teModelCat1only = te_cat1only.trainModel().get();
      Scope.track_generic(teModelCat1only);
        
      Frame encodedCat1only = teModelCat1only.transformTraining(fr);
      Scope.track(encodedCat1only);
      Vec cat1EncCat1only = encodedCat1only.vec("cat1_te");
      Vec cat2EncCat1only = encodedCat1only.vec("cat2_te");
      assertNotNull(cat1EncCat1only);
      assertNull(cat2EncCat1only);
      assertVecEquals(cat1Enc, cat1EncCat1only, 1e-6);
        
      // For the second encoded column
      teParams._ignored_columns = ar("cat1");
      TargetEncoder te_cat2only = new TargetEncoder(teParams);
      TargetEncoderModel teModelCat2only = te_cat2only.trainModel().get();
      Scope.track_generic(teModelCat2only);

      Frame encodedCat2only = teModelCat2only.transformTraining(fr);
      Scope.track(encodedCat2only);
      Vec cat1EncCat2only = encodedCat2only.vec("cat1_te");
      Vec cat2EncCat2only = encodedCat2only.vec("cat2_te");
      assertNull(cat1EncCat2only);
      assertNotNull(cat2EncCat2only);
      assertVecEquals(cat2Enc, cat2EncCat2only, 1e-6);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_LOO_strategy_does_not_produce_the_same_result_on_transform_and_transformTraining() {
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
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.LeaveOneOut;
      teParams._response_column = "target";
      teParams._train = fr._key;
      teParams._seed = 42;
      teParams._noise = 0;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame encodedAsTrain = teModel.transformTraining(fr);
      Scope.track(encodedAsTrain);
      assertVecEquals(dvec(1, 1, 0.5, 0.5, 0), encodedAsTrain.vec("categorical_te"), 1e-5);

      Frame encodedAsNew = teModel.transform(fr);
      Scope.track(encodedAsNew);
      assertVecEquals(dvec(0.5, 0.667, 0.667, 0.667, 0.5), encodedAsNew.vec("categorical_te"), 1e-3);

      try {
        compareFrames(encodedAsTrain, encodedAsNew, 1e-5);
        fail("should have thrown");
      } catch (AssertionError ae) {
        assertFalse(ae.getMessage().contains("should have thrown"));
      }
    } finally {
      Scope.exit();
    }
  }


  @Test
  public void test_LOO_strategy_does_produce_the_same_result_on_transform_and_score() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("trainFrame")
              .withColNames("categorical", "numerical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ard( 1,   1,   4,   7,   4))
              .withDataForCol(2, ar("N", "Y", "Y", "Y", "Y"))
              .build();

      TargetEncoderModel.TargetEncoderParameters teParams = new TargetEncoderModel.TargetEncoderParameters();
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.LeaveOneOut;
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

      compareFrames(encoded, predictions, 1e-5);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_encoder_trained_with_LOO_strategy_can_be_used_to_transform_a_frame_without_target() {
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
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.LeaveOneOut;
      teParams._response_column = "target";
      teParams._train = train._key;
      teParams._seed = 42;
      teParams._noise = 0;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);
      
      double priorMean1 = computePriorMean(teModel._output._target_encoding_map.get("cat1"));
      double priorMean2 = computePriorMean(teModel._output._target_encoding_map.get("cat2"));
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
