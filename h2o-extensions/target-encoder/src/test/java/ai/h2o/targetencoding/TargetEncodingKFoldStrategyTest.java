package ai.h2o.targetencoding;

import ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy;
import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;

import java.util.Map;

import static ai.h2o.targetencoding.TargetEncoderHelper.*;
import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class TargetEncodingKFoldStrategyTest extends TestUtil {
  
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void test_TE_using_KFold_leakage_strategy(){
    try {
      Scope.enter();
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      Scope.track(fr);

      String foldColumn = "fold_column";
      int nfolds = 5;
      int seed = 1234;
      addKFoldColumn(fr, foldColumn, nfolds, seed);
      String target = "survived";
      asFactor(fr, target);
      String[] teColumns = new String[]{ "home.dest", "embarked" };

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._response_column = target;
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.KFold;
      teParams._fold_column = foldColumn;
      teParams._ignored_columns = ignoredColumns(fr,
              ArrayUtils.append(teColumns, teParams._response_column, teParams._fold_column));
      teParams._seed = seed;
      teParams.setTrain(fr._key);

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Map<String, Frame> teEncodingMap = teModel._output._target_encoding_map;

      // check encodings for a column without NAs
      Vec embarked = fr.vec("embarked");
      Frame embarkedEncodings = teEncodingMap.get("embarked");
      embarkedEncodings = embarkedEncodings.sort(new int[] {3, 0}); // sort by fold first, then embarked column
      Scope.track(embarkedEncodings);
      printOutFrameAsTable(embarkedEncodings);
      assertTrue(teModel._output._te_column_to_hasNAs.get("embarked"));
      assertTrue(embarkedEncodings.find(foldColumn) > 0);
      assertArrayEquals(new String[] {"embarked", NUMERATOR_COL, DENOMINATOR_COL, foldColumn}, embarkedEncodings.names());
      assertEquals(nfolds * embarked.cardinality() + /* NA entry on 4 of the 5 kfolds */ (nfolds - 1),  //titanic has only 2 NAs for "embarked" column, with this seed, they're both in the same fold (3)
              embarkedEncodings.numRows());

      // check encodings for a column with NAs
      Vec homeDest = fr.vec("home.dest");
      Frame homeDestEncodings = teEncodingMap.get("home.dest");
      homeDestEncodings = homeDestEncodings.sort(new int[] {3, 0}); // sort by fold first, then embarked column
      Scope.track(homeDestEncodings);
      printOutFrameAsTable(homeDestEncodings);
      assertTrue(teModel._output._te_column_to_hasNAs.get("home.dest"));
      assertTrue(homeDestEncodings.find(foldColumn) > 0);
      assertArrayEquals(new String[] {"home.dest", NUMERATOR_COL, DENOMINATOR_COL, foldColumn}, homeDestEncodings.names());
      //too many home.dest distinct values and too many NAs there to know exactly how many rows we'll obtain
      assertEquals(1616, homeDestEncodings.numRows()); // expected value obtained empirically: should not change in the future (as soon as the seed doesn't change...)
      assertTrue(nfolds * (homeDest.cardinality()+1) > homeDestEncodings.numRows());

      Frame scored = teModel.score(fr);
      Scope.track(scored);
      Frame transformed = teModel.transform(fr);
      Scope.track(transformed);
      assertBitIdentical(scored, transformed);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_TE_with_KFold_strategy_training_fails_if_fold_column_is_not_set() {
    thrown.expect(H2OModelBuilderIllegalArgumentException.class);
    try {
      Scope.enter();
      Frame train = new TestFrameBuilder()
              .withName("train")
              .withColNames("categorical", "numerical", "target", "foldc")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ard( 1,   1,   4,   7,   4))
              .withDataForCol(2, ar("N", "Y", "Y", "Y", "Y"))
              .withDataForCol(3, ar(  1,   2,   2,   3,   2))
              .build();

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train = train._key;
      teParams._response_column = "target";
      teParams._seed = 123;
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.KFold;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_TE_with_KFold_strategy_training_fails_if_training_frame_is_missing_fold_column() {
    thrown.expect(H2OModelBuilderIllegalArgumentException.class);
    try {
      Scope.enter();
      Frame train = new TestFrameBuilder()
              .withName("train")
              .withColNames("categorical", "numerical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ard( 1,   1,   4,   7,   4))
              .withDataForCol(2, ar("N", "Y", "Y", "Y", "Y"))
              .build();

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train = train._key;
      teParams._response_column = "target";
      teParams._fold_column = "foldc";
      teParams._seed = 123;
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.KFold;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_TE_with_KFold_strategy_fails_if_test_frame_is_missing_fold_column() {
    thrown.expect(H2OIllegalArgumentException.class);
    try {
      Scope.enter();
      Frame train = new TestFrameBuilder()
              .withName("train")
              .withColNames("categorical", "numerical", "target", "foldc")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ard( 1,   1,   4,   7,   4))
              .withDataForCol(2, ar("N", "Y", "Y", "Y", "Y"))
              .withDataForCol(3, ar(  1,   2,   2,   3,   2))
              .build();

      Frame test = new TestFrameBuilder()
              .withName("test")
              .withColNames("categorical", "numerical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "c", "b", "a"))
              .withDataForCol(1, ard( 1,   1,   4,   7,   4))
              .withDataForCol(2, ar("N", "Y", "Y", "Y", "Y"))
              .build();
      
      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train = train._key;
      teParams._response_column = "target";
      teParams._fold_column = "foldc";
      teParams._seed = 123;
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.KFold;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);
      
      teModel.transformTraining(test);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_encodings_with_KFold_strategy() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("categorical", "numerical", "target", "foldc")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "b", "b"))
              .withDataForCol(1, ard( 1,   1,   4,   7))
              .withDataForCol(2, ar("N", "Y", "Y", "Y"))
              .withDataForCol(3, ar(  1,   2,   2,   3))
              .build();

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train = fr._key;
      teParams._response_column = "target";
      teParams._fold_column = "foldc";
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.KFold;
      teParams._seed = 42;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame colAEncodings = teModel._output._target_encoding_map.get("categorical");
      colAEncodings = colAEncodings.sort(new int[] {3, 0}); //sort encodings by fold first + categorical level
      Scope.track(colAEncodings);
      printOutFrameAsTable(colAEncodings);

      Vec vec1 = vec(3, 0, 1, 0, 2); //fold 1 has only one entry (for 'b'), others have 2 ('a', 'b').
      assertVecEquals(vec1, colAEncodings.vec("numerator"), 0);
      Vec vec2 = vec(3, 1, 1, 1, 2);
      assertVecEquals(vec2, colAEncodings.vec("denominator"), 0);

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void apply_TE_with_KFold_strategy_and_default_noise() {
      try {
        Scope.enter();
        Frame train = new TestFrameBuilder()
                .withName("train")
                .withColNames("categorical", "numerical", "target", "foldc")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "b", "b", "a"))
                .withDataForCol(1, ard( 1,   1,   4,   7,   4))
                .withDataForCol(2, ar("N", "Y", "Y", "Y", "Y"))
                .withDataForCol(3, ar(  1,   2,   2,   3,   2))
                .build();

        Frame test = new TestFrameBuilder()
                .withName("test")
                .withColNames("categorical", "numerical", "target", "foldc")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "c", "b", "a"))
                .withDataForCol(1, ard( 1,   1,   4,   7,   4))
                .withDataForCol(2, ar("N", "Y", "Y", "Y", "Y"))
                .withDataForCol(3, ar(  1,   2,   2,   3,   2))
                .build();

        TargetEncoderParameters teParams = new TargetEncoderParameters();
        teParams._train = train._key;
        teParams._response_column = "target";
        teParams._fold_column = "foldc";
        teParams._noise = -1;
        teParams._seed = 123;
        teParams._data_leakage_handling = DataLeakageHandlingStrategy.KFold;

        TargetEncoder te = new TargetEncoder(teParams);
        TargetEncoderModel teModel = te.trainModel().get();
        Scope.track_generic(teModel);

        Frame encoded = teModel.transformTraining(test);
        Scope.track(encoded);
        Vec catEnc = encoded.vec("categorical_te");
        
        printOutFrameAsTable(encoded, false, encoded.numRows());
        Frame encodings = teModel._output._target_encoding_map.get("categorical");
        double priorMean = TargetEncoderHelper.computePriorMean(encodings);
        assertEquals(.8, priorMean, 1e-6);
        // We expect that for `c` level we will get global priorMean. Maybe we should have instead priorMean on all folds but 3 -> 0.75
        Vec expected = dvec(1.0, 1.0, 0.8, 1, 0.0);
        assertVecEquals(expected, catEnc, 1e-2); // TODO is it ok that encoding contains negative values?
      } finally {
        Scope.exit();
      }
  }

  @Test
  public void apply_TE_with_KFold_strategy_and_noise() {
      try {
        Scope.enter();
        Frame fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("categorical", "numerical", "target", "foldc")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "b", "b", "a"))
                .withDataForCol(1, ard( 1,   1,   4,   7,   4))
                .withDataForCol(2, ar("N", "Y", "Y", "Y", "Y"))
                .withDataForCol(3, ar(  1,   2,   2,   3,   2))
                .build();

        TargetEncoderParameters teParams = new TargetEncoderParameters();
        teParams._train = fr._key;
        teParams._response_column = "target";
        teParams._fold_column = "foldc";
        teParams._noise = 0.02;
        teParams._seed = 123;
        teParams._data_leakage_handling = DataLeakageHandlingStrategy.KFold;

        TargetEncoder te = new TargetEncoder(teParams);
        TargetEncoderModel teModel = te.trainModel().get();
        Scope.track_generic(teModel);

        Frame encoded = teModel.transformTraining(fr);
        Scope.track(encoded);
        Vec catEnc = encoded.vec("categorical_te");

        Vec expected = vec(1, 1, 1, 1, 0);
        assertVecEquals(expected, catEnc, 2e-2);
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
  public void apply_TE_with_KFold_strategy_and_blending() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("categorical", "numerical", "target", "foldc")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "b", "b", "a", "c"))
              .withDataForCol(1, ard( 1,   1,   4,   7,   4,   9))
              .withDataForCol(2, ar("N", "Y", "Y", "Y", "Y", "N"))
              .withDataForCol(3, ar(  1,   2,   2,   3,   2,   2))
              .build();

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train = fr._key;
      teParams._response_column = "target";
      teParams._fold_column = "foldc";
      teParams._noise = 0;
      teParams._seed = 123;
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.KFold;
      teParams._blending = true;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame encoded = teModel.transformTraining(fr);
      Scope.track(encoded);
      Vec catEnc = encoded.vec("categorical_te");
      printOutFrameAsTable(encoded);
      
      double priorMean = TargetEncoderHelper.computePriorMean(teModel._output._target_encoding_map.get("categorical"));
      BlendingParams bp = teModel._parms.getBlendingParameters();

      // values below obtained empirically with default blending params (10, 20): should not change as soon as we don't change those default values. 
      assertEquals(0.666, priorMean, 1e-3); // works for (val, fold) = ('c', 2) 
      assertEquals(0.796, getBlendedValue(1, priorMean, 1, bp), 1e-3); // for (val, fold) = ('a', 1) or ('b', 2)
      assertEquals(0.800, getBlendedValue(1, priorMean, 2, bp), 1e-3); // for (val, fold) = ('b', 3)
      assertEquals(0.407, getBlendedValue(0, priorMean, 1, bp), 1e-3); // for (val, fold) = ('a', 2)
      Vec expectedEnc = dvec(0.796, 0.796, 0.796, 0.800, 0.407, 0.666);
      assertVecEquals(expectedEnc, catEnc, 1e-3);
    } finally {
      Scope.exit();
    }
  }


  @Test
  public void apply_TE_with_KFold_strategy_with_more_than_2_folds() {
      try {
        Scope.enter();
        Frame fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("categorical", "target", "foldc")
                .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "b", "c", "c", "a", "d", "d", "d", "d", "e", "e", "a", "f", "f"))
                .withDataForCol(1, ar("N", "Y", "Y", "Y", "Y", "Y", "N", "Y", "Y", "Y", "Y", "N", "N", "N", "N"))
                .withDataForCol(2, ar(  1,   2,   1,   2,   1,   3,   2,   2,   1,   3,   1,   2,   3,   3,   2))
                .build();

        TargetEncoderParameters teParams = new TargetEncoderParameters();
        teParams._train = fr._key;
        teParams._response_column = "target";
        teParams._fold_column = "foldc";
        teParams._noise = 0;
        teParams._seed = 123;
        teParams._data_leakage_handling = DataLeakageHandlingStrategy.KFold;

        TargetEncoder te = new TargetEncoder(teParams);
        TargetEncoderModel teModel = te.trainModel().get();
        Scope.track_generic(teModel);

        Frame encoded = teModel.transformTraining(fr);
        Scope.track(encoded);
        printOutFrameAsTable(encoded, false, 100);

        Vec catEnc = encoded.vec("categorical_te");
        Vec expected = dvec(0.5, 1, 1, 1, 1, 0, 1, 1, 0.66666, 0.66666, 0, 1, 0, 0, 0);
        assertVecEquals(expected, catEnc, 1e-5);
      } finally {
        Scope.exit();
      }
  }

  @Test
  public void apply_TE_with_KFold_strategy_on_test_frame() {
      try {
        Scope.enter();
        Frame train = new TestFrameBuilder()
                .withName("trainingFrame")
                .withColNames("categorical", "target", "foldc")
                .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "c", "d", "e", "b", "b"))
                .withDataForCol(1, ar("N", "Y", "Y", "Y", "Y", "N", "N"))
                .withDataForCol(2, ar(  1,   2,   2,   3,   1,   2,   1))
                .build();

        Frame test = new TestFrameBuilder()
                .withName("validFrame")
                .withColNames("categorical", "target", "foldc")
                .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "b", "b", "a"))
                .withDataForCol(1, ar("N", "Y", "Y", "Y", "Y"))
                .withDataForCol(2, ar(  1,   2,   1,   2,   1))
                .build();

        TargetEncoderParameters teParams = new TargetEncoderParameters();
        teParams._train = train._key;
        teParams._response_column = "target";
        teParams._fold_column = "foldc";
        teParams._noise = 0;
        teParams._seed = 123;
        teParams._data_leakage_handling = DataLeakageHandlingStrategy.KFold;

        TargetEncoder te = new TargetEncoder(teParams);
        TargetEncoderModel teModel = te.trainModel().get();
        Scope.track_generic(teModel);
        
        Frame encoded = teModel.transformTraining(test);
        Scope.track(encoded);
        printOutFrameAsTable(encoded, false, 100);
        
        Frame encodings = teModel._output._target_encoding_map.get("categorical");
        double priorMean = TargetEncoderHelper.computePriorMean(encodings);
        assertEquals(.571429, priorMean, 1e-6);

        Vec catEnc = encoded.vec("categorical_te");
        Vec expected = dvec(0.57143, 0.0, 0.5, 0.0, 0.57143);
        assertVecEquals(expected,catEnc , 1e-5);
      } finally {
        Scope.exit();
      }
  }

  @Test
  public void test_KFold_strategy_can_be_applied_on_multiple_columns_at_once() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("cat1", "cat2", "target", "foldc")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ar("d", "e", "d", "e", "e"))
              .withDataForCol(2, ar("N", "Y", "Y", "Y", "Y"))
              .withDataForCol(3, ar(  1,   2,   2,   3,   2))
              .build();

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train = fr._key;
      teParams._response_column = "target";
      teParams._fold_column = "foldc";
      teParams._noise = 0;
      teParams._seed = 123;
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.KFold;

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
  public void test_KFold_strategy_does_not_produce_the_same_result_on_transform_and_transformTraining() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("trainFrame")
              .withColNames("categorical", "numerical", "target", "foldc")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ard( 1,   1,   4,   7,   4))
              .withDataForCol(2, ar("N", "N", "Y", "Y", "Y"))
              .withDataForCol(3, ar(  1,   2,   2,   1,   2))
              .build();

      TargetEncoderModel.TargetEncoderParameters teParams = new TargetEncoderModel.TargetEncoderParameters();
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.KFold;
      teParams._response_column = "target";
      teParams._fold_column = "foldc";
      teParams._train = fr._key;
      teParams._seed = 42;
      teParams._noise = 0;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame encodedAsTrain = teModel.transformTraining(fr);
      Scope.track(encodedAsTrain);
      assertVecEquals(dvec(1, 1, 1, 0.5, 0), encodedAsTrain.vec("categorical_te"), 1e-5);

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
  public void test_KFold_strategy_does_produce_the_same_result_on_transform_and_score() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("trainFrame")
              .withColNames("categorical", "numerical", "target", "foldc")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ard( 1,   1,   4,   7,   4))
              .withDataForCol(2, ar("N", "Y", "Y", "Y", "Y"))
              .withDataForCol(3, ar(  1,   2,   2,   3,   2))
              .build();

      TargetEncoderModel.TargetEncoderParameters teParams = new TargetEncoderModel.TargetEncoderParameters();
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.KFold;
      teParams._response_column = "target";
      teParams._fold_column = "foldc";
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
  public void test_encoder_trained_with_KFold_strategy_can_be_used_to_transform_a_frame_without_target_nor_fold_column() {
    try {
      Scope.enter();
      Frame train = new TestFrameBuilder()
              .withName("trainFrame")
              .withColNames("cat1", "cat2", "target", "foldc")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "b", "b", "a"))
              .withDataForCol(1, ar("d", "e", "d", "e", "e"))
              .withDataForCol(2, ar("N", "Y", "Y", "Y", "Y"))
              .withDataForCol(3, ar(  1,   2,   2,   3,   2))
              .build();

      Frame test = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("cat1", "cat2")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("c", "b", "a"))
              .withDataForCol(1, ar("d", "e", "f"))
              .build();

      TargetEncoderModel.TargetEncoderParameters teParams = new TargetEncoderModel.TargetEncoderParameters();
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.KFold;
      teParams._response_column = "target";
      teParams._fold_column = "foldc";
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
