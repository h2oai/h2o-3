package ai.h2o.targetencoding;

import ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy;
import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import org.apache.commons.lang.ArrayUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.*;
import water.rapids.Merge;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ai.h2o.targetencoding.TargetEncoderHelper.addKFoldColumn;
import static ai.h2o.targetencoding.TargetEncoderHelper.nameToIndex;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class TargetEncoderModelTest extends TestUtil{

  @Test
  public void testTargetEncoderModel_columnsToEncode() {
    try {
      Scope.enter();
      Frame train = parse_test_file("./smalldata/testng/airlines_train.csv");
      Scope.track(train);
      Frame test = parse_test_file("./smalldata/testng/airlines_test.csv");
      Scope.track(test);

      TargetEncoderParameters paramsImplicit = new TargetEncoderParameters();
      paramsImplicit._response_column = "IsDepDelayed";
      paramsImplicit._ignored_columns = ignoredColumns(train, "Origin", paramsImplicit._response_column);
      paramsImplicit._train = train._key;
      paramsImplicit._seed = 0XFEED;


      TargetEncoder teImplicit = new TargetEncoder(paramsImplicit);
      final TargetEncoderModel teModelImplicit = teImplicit.trainModel().get();
      Scope.track_generic(teModelImplicit);
      assertNotNull(teModelImplicit);
      final Frame encodedImplicit = teModelImplicit.score(test);
      Scope.track(encodedImplicit);

      assertNotNull(encodedImplicit);
      assertEquals(train.numCols() + 1, encodedImplicit.numCols());
      final int encodedColIdx = ArrayUtils.indexOf(encodedImplicit.names(), "Origin_te");
      assertNotEquals(-1, encodedColIdx);
      assertTrue(encodedImplicit.vec(encodedColIdx).isNumeric());


      TargetEncoderParameters paramsExplicit = (TargetEncoderParameters)paramsImplicit.clone();
      paramsExplicit._ignored_columns = null;
      paramsExplicit._columns_to_encode = new String[][] {
              new String[]{"Origin"},
      };
      TargetEncoder teExplicit = new TargetEncoder(paramsExplicit);
      final TargetEncoderModel teModelExplicit = teExplicit.trainModel().get();
      Scope.track_generic(teModelExplicit);
      assertNotNull(teModelExplicit);
      final Frame encodedExplicit = teModelExplicit.score(test);
      Scope.track(encodedExplicit);

      assertNotNull(encodedExplicit);
      assertEquals(train.numCols() + 1, encodedExplicit.numCols());
      final int encodedColIdx2 = ArrayUtils.indexOf(encodedExplicit.names(), "Origin_te");
      assertNotEquals(-1, encodedColIdx2);
      assertTrue(encodedExplicit.vec(encodedColIdx2).isNumeric());
      
      assert Arrays.stream(encodedImplicit.names()).collect(Collectors.toSet())
              .equals(Arrays.stream(encodedExplicit.names()).collect(Collectors.toSet()));
      // due to use of ignored_columns, the implicit mode may produce predictions with columns in a different order.
      int[] impNewOrder = new int[encodedImplicit.numCols()];
      Map<String, Integer> impNameIdx = nameToIndex(encodedImplicit);
      int offset = 0;
      for (String name : encodedExplicit.names()) {
        impNewOrder[offset++] = impNameIdx.get(name);
      }
      encodedImplicit.reOrder(impNewOrder);
      assertFrameEquals(encodedExplicit, encodedImplicit, 1e-6);
      
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void testTargetEncoderModel_nonDefault_blendingParameters() {
    try {
      Scope.enter();
      Frame train = parseTestFile("./smalldata/testng/airlines_train.csv");
      Scope.track(train);
      Frame test = parseTestFile("./smalldata/testng/airlines_test.csv");
      Scope.track(test);

      TargetEncoderParameters params = new TargetEncoderParameters();
      params._data_leakage_handling = DataLeakageHandlingStrategy.None;
      params._inflection_point = 0.3;
      params._smoothing = 0.7;
      params._blending = true;
      params._response_column = "IsDepDelayed";
      params._ignored_columns = ignoredColumns(train, "Origin", params._response_column);
      params._train = train._key;
      params._seed = 0XFEED;
      

      TargetEncoder te = new TargetEncoder(params);
      final TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);
      assertNotNull(teModel);
      final Frame encoded = teModel.score(test);
      Scope.track(encoded);
      
      assertNotNull(encoded);
      assertEquals(train.numCols() + 1, encoded.numCols());
      final int encodedColIdx = ArrayUtils.indexOf(encoded.names(), "Origin_te");
      assertNotEquals(-1, encodedColIdx);
      assertTrue(encoded.vec(encodedColIdx).isNumeric());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testTargetEncoderModel_defaultBlendingParameters() {
    try {
      Scope.enter();
      Frame train = parseTestFile("./smalldata/testng/airlines_train.csv");
      Scope.track(train);
      Frame test = parseTestFile("./smalldata/testng/airlines_test.csv");
      Scope.track(test);

      TargetEncoderParameters params = new TargetEncoderParameters();
      params._data_leakage_handling = DataLeakageHandlingStrategy.None;
      params._blending = true;
      params._response_column = "IsDepDelayed";
      params._ignored_columns = ignoredColumns(train, "Origin", params._response_column);
      params._train = train._key;
      params._seed = 0XFEED;


      TargetEncoder te = new TargetEncoder(params);
      final TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);
      assertNotNull(teModel);
      final Frame encoded = teModel.score(test);
      Scope.track(encoded);

      assertNotNull(encoded);
      assertEquals(train.numCols() + (train.numCols() - params._ignored_columns.length - 1), encoded.numCols());
      final int encodedColIdx = ArrayUtils.indexOf(encoded.names(), "Origin_te");
      assertNotEquals(-1, encodedColIdx);
      assertTrue(encoded.vec(encodedColIdx).isNumeric());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_blending_formula_is_applied_correctly() throws Exception {
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames("categorical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "a"))
              .withDataForCol(1, ar("yes", "no", "yes"))
              .withChunkLayout(1, 1, 1)
              .build();

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._response_column = "target";
      teParams._train = fr._key;
      teParams._noise = 0;
      teParams._seed = 42;
      teParams._blending = true;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame encoded = Scope.track(teModel.score(fr));
      Vec catEnc = encoded.vec("categorical_te");

      double globalMean = 2.0 / 3;
      double k = 10.0;
      int f = 20;
      assertEquals(k, teModel._parms._inflection_point, 1e-6);
      assertEquals(f, teModel._parms._smoothing, 1e-6);

      double lambda1 = 1.0 / (1.0 + (Math.exp((k - 2) / f)));
      double te1 = (1.0 - lambda1) * globalMean + (lambda1 * 2 / 2);

      double lambda2 = 1.0 / (1 + Math.exp((k - 1) / f));
      double te2 = (1.0 - lambda2) * globalMean + (lambda2 * 0 / 1);

      double lambda3 = 1.0 / (1.0 + (Math.exp((k - 2) / f)));
      double te3 = (1.0 - lambda3) * globalMean + (lambda3 * 2 / 2);

      assertEquals(te1, catEnc.at(0), 1e-5);
      assertEquals(te2, catEnc.at(1), 1e-5);
      assertEquals(te3, catEnc.at(2), 1e-5);

    } finally {
      Scope.exit();
    }
  }


  @Test
  public void testTargetEncoderModel_ignoreNonCategoricalCols() {
    try {
      Scope.enter();
      Frame train = parseTestFile("./smalldata/testng/airlines_train.csv");
      Scope.track(train);

      TargetEncoderParameters params = new TargetEncoderParameters();
      params._data_leakage_handling = DataLeakageHandlingStrategy.None;
      params._response_column = "IsDepDelayed";
      params._ignored_columns = null;
      params._train = train._key;
      params._seed = 0XFEED;
      
      TargetEncoder te = new TargetEncoder(params);
      final TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);
      
      // Check categorical colums for not being removed
      assertArrayEquals(
              new String[]{"fYear", "fMonth", "fDayofMonth", "fDayOfWeek", "UniqueCarrier", "Origin", "Dest", "Distance", "IsDepDelayed"},
              teModel._output._names
      );
      assertArrayEquals(
              new String[]{"fYear", "fMonth", "fDayofMonth", "fDayOfWeek", "UniqueCarrier", "Origin", "Dest"}, 
              Arrays.stream(teModel._output._input_to_output_columns).flatMap(io -> Stream.of(io.from())).toArray(String[]::new)
      );
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void test_model_returns_frame_as_is_if_no_categorical_column_to_encode() {
    try {
      Scope.enter();
      final Frame train = new TestFrameBuilder()
              .withColNames("num1", "num2", "target", "foldc")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar(    1,    2,     3))
              .withDataForCol(1, ar(    5,    4,     3))
              .withDataForCol(2, ar("yes", "no", "yes"))
              .withDataForCol(3, ar(    0,    0,     1))
              .build();

      final Frame test = new TestFrameBuilder()
              .withColNames("num2", "num1")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ar(5, 4, 3))
              .withDataForCol(1, ar(3, 2, 1))
              .build();

      TargetEncoderParameters params = new TargetEncoderParameters();
      params._data_leakage_handling = DataLeakageHandlingStrategy.KFold;
      params._response_column = "target";
      params._fold_column = "foldc";
      params._train = train._key;
      params._seed = 1;

      TargetEncoder te = new TargetEncoder(params);
      final TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);
      assertNotNull(teModel._output);
      assertTrue(teModel._output._target_encoding_map.isEmpty());
      assertEquals(0, teModel._output._input_to_encoding_column.length);
      
      Frame transformed = Scope.track(teModel.transform(test));
      assertNotNull(transformed);
      assertSame(test, transformed);
      
      Frame scored = Scope.track(teModel.score(test));
      assertNotSame(test, scored);
      assertFrameEquals(test, scored, 0);
      
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_transformed_frame_columns_order_with_frame_similar_to_train() {
    try {
      Scope.enter();
      final Frame train = new TestFrameBuilder()
              .withColNames("cat2", "num2", "target", "num1", "cat1", "foldc")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar(  "a",  "b",   "a"))
              .withDataForCol(1, ar(    1,    2,     3))
              .withDataForCol(2, ar("yes", "no", "yes"))
              .withDataForCol(3, ar(    5,    4,     3))
              .withDataForCol(4, ar(  "A",  "B",   "C"))
              .withDataForCol(5, ar(    0,    0,     1))
              .build();

      final Frame test = new TestFrameBuilder()
              .withColNames("cat2", "num2", "target", "num1", "cat1")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ar(  "a",  "b",   "a"))
              .withDataForCol(1, ar(    1,    2,     3))
              .withDataForCol(2, ar("yes", "no", "yes"))
              .withDataForCol(3, ar(    5,    4,     3))
              .withDataForCol(4, ar(  "A",  "B",   "C"))
              .build();

      TargetEncoderParameters params = new TargetEncoderParameters();
      params._data_leakage_handling = DataLeakageHandlingStrategy.None;
      params._response_column = "target";
      params._ignored_columns = null;
      params._train = train._key;
      params._fold_column = "foldc";
      params._seed = 0XFEED;

      TargetEncoder te = new TargetEncoder(params);
      final TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame trans = Scope.track(teModel.transform(test));
      assertArrayEquals(new String[]{"num2", "num1", "cat2_te", "cat1_te", "cat2", "cat1", "target"}, trans.names());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_transformed_frame_columns_order_with_frame_missing_columns_from_train() {
    try {
      Scope.enter();
      final Frame train = new TestFrameBuilder()
              .withColNames("cat2", "num2", "target", "num1", "cat1", "foldc")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar(  "a",  "b",   "a"))
              .withDataForCol(1, ar(    1,    2,     3))
              .withDataForCol(2, ar("yes", "no", "yes"))
              .withDataForCol(3, ar(    5,    4,     3))
              .withDataForCol(4, ar(  "A",  "B",   "C"))
              .withDataForCol(5, ar(    0,    0,     1))
              .build();

      final Frame test = new TestFrameBuilder()
              .withColNames("target", "num1", "cat1")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ar("yes", "no", "yes"))
              .withDataForCol(1, ar(    5,    4,     3))
              .withDataForCol(2, ar(  "A",  "B",   "C"))
              .build();
      
      TargetEncoderParameters params = new TargetEncoderParameters();
      params._data_leakage_handling = DataLeakageHandlingStrategy.None;
      params._response_column = "target";
      params._ignored_columns = null;
      params._train = train._key;
      params._fold_column = "foldc";
      params._seed = 0XFEED;

      TargetEncoder te = new TargetEncoder(params);
      final TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame trans = Scope.track(teModel.transform(test));
      assertArrayEquals(new String[]{"num1", "cat1_te", "cat1", "target"}, trans.names());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_transformed_frame_columns_order_with_shuffled_frame_having_new_and_missing_columns_from_train() {
    try {
      Scope.enter();
      final Frame train = new TestFrameBuilder()
              .withColNames("cat2", "num2", "target", "num1", "cat1", "foldc")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar(  "a",  "b",   "a"))
              .withDataForCol(1, ar(    1,    2,     3))
              .withDataForCol(2, ar("yes", "no", "yes"))
              .withDataForCol(3, ar(    5,    4,     3))
              .withDataForCol(4, ar(  "A",  "B",   "C"))
              .withDataForCol(5, ar(    0,    0,     1))
              .build();

      final Frame test = new TestFrameBuilder()
              .withColNames("target", "cat3", "num3", "cat2", "num2")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("yes", "no", "yes"))
              .withDataForCol(1, ar(  "A",  "B",   "C"))
              .withDataForCol(2, ar(    5,    4,     3))
              .withDataForCol(3, ar(  "a",  "b",   "a"))
              .withDataForCol(4, ar(    1,    2,     3))
              .build();

      TargetEncoderParameters params = new TargetEncoderParameters();
      params._data_leakage_handling = DataLeakageHandlingStrategy.None;
      params._response_column = "target";
      params._ignored_columns = null;
      params._train = train._key;
      params._fold_column = "foldc";
      params._seed = 0XFEED;

      TargetEncoder te = new TargetEncoder(params);
      final TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame trans = Scope.track(teModel.transform(test));
      assertArrayEquals(new String[]{"num2", "cat2_te", "cat2", "cat3", "num3", "target"}, trans.names());
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void test_transformed_frame_columns_order_with_columns_grouping() {
    try {
      Scope.enter();
      final Frame train = new TestFrameBuilder()
              .withColNames("cat2", "num2", "target", "num1", "cat1", "foldc")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar(  "a",  "b",   "a"))
              .withDataForCol(1, ar(    1,    2,     3))
              .withDataForCol(2, ar("yes", "no", "yes"))
              .withDataForCol(3, ar(    5,    4,     3))
              .withDataForCol(4, ar(  "A",  "B",   "C"))
              .withDataForCol(5, ar(    0,    0,     1))
              .build();

      final Frame test = new TestFrameBuilder()
              .withColNames("cat1", "num3", "target", "num1", "cat2")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ar(  "a",  "b",   "a"))
              .withDataForCol(1, ar(    1,    2,     3))
              .withDataForCol(2, ar("yes", "no", "yes"))
              .withDataForCol(3, ar(    5,    4,     3))
              .withDataForCol(4, ar(  "A",  "B",   "C"))
              .build();

      TargetEncoderParameters params = new TargetEncoderParameters();
      params._data_leakage_handling = DataLeakageHandlingStrategy.None;
      params._response_column = "target";
      params._columns_to_encode = new String[][] {
              {"cat1", "cat2"}, {"cat2"}
      };
      params._train = train._key;
      params._fold_column = "foldc";
      params._seed = 0XFEED;

      TargetEncoder te = new TargetEncoder(params);
      final TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame trans = Scope.track(teModel.transform(test));
      assertArrayEquals(new String[]{"num1", "cat1:cat2_te", "cat2_te", "cat2", "cat1", "num3", "target"}, trans.names());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_TE_can_be_applied_to_frames_without_target() {
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames("categorical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "b", "b"))
              .withDataForCol(1, ar("N", "Y", "Y", "N"))
              .build();

      final Frame frNoTarget = new TestFrameBuilder()
              .withColNames("categorical")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "b", "b"))
              .build();

      TargetEncoderParameters params = new TargetEncoderParameters();
      params._train = fr._key;
      params._response_column = "target";
      params._noise = 0;
      params._seed = 0XFEED;


      TargetEncoder te = new TargetEncoder(params);
      final TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);
      
      Frame encoded = teModel.score(fr);
      Scope.track(encoded);
      
      Frame encodedNoTarget = teModel.score(frNoTarget);
      Scope.track(encodedNoTarget);
      
      assertVecEquals(encoded.vec("categorical_te"), encodedNoTarget.vec("categorical_te"), 1e-5);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void empty_strings_and_NAs_should_be_treated_as_new_categories() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("categorical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "", "", null)) // null and "" are different categories even though they look the same in printout
              .withDataForCol(1, ar("N", "Y", "Y", "N", "Y"))
              .withChunkLayout(3, 2)
              .build();

      TargetEncoderParameters params = new TargetEncoderParameters();
      params._train = fr._key;
      params._response_column = "target";
      params._noise = 0;
      params._seed = 0XFEED;


      TargetEncoder te = new TargetEncoder(params);
      final TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame encoded = teModel.score(fr);
      Scope.track(encoded);

      assertEquals(4, encoded.vec("categorical").cardinality());
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void test_columns_order_has_no_effect_on_training() {
    try {
      Scope.enter();
      final Frame fr = parseTestFile("./smalldata/gbm_test/titanic.csv");
      Scope.track(fr);
      RowIndexTask.addRowIndex(fr);

      String foldColumn = "fold_column";
      int nfolds = 5;
      addKFoldColumn(fr, foldColumn, nfolds, 1234L);
      String responseColumnName = "survived";
      asFactor(fr, responseColumnName);
      String[] teColumns = new String[]{ "home.dest", "embarked" };

      TargetEncoderParameters teParameters = new TargetEncoderParameters();
      teParameters._response_column = responseColumnName;
      teParameters._data_leakage_handling = DataLeakageHandlingStrategy.KFold;
      teParameters._fold_column = foldColumn;
      teParameters._seed = 42;
      teParameters._ignored_columns = ignoredColumns(fr,
              water.util.ArrayUtils.append(new String[]{ "home.dest", "embarked" }, teParameters._response_column, teParameters._fold_column));

      TargetEncoderParameters teParams1 = (TargetEncoderParameters) teParameters.clone();
      teParams1.setTrain(fr._key);
      TargetEncoder te1 = new TargetEncoder(teParams1);
      TargetEncoderModel teModel1 = te1.trainModel().get();
      Scope.track_generic(teModel1);
      Frame encoded1 = Scope.track(teModel1.transformTraining(fr));
      Frame sortedEncoded1 = Scope.track(Merge.sort(encoded1, encoded1.find(RowIndexTask.ROW_INDEX_COL)));

      TargetEncoderParameters teParams2 = (TargetEncoderParameters) teParameters.clone();
      int[] teColIdx = Stream.of(teColumns).mapToInt(fr::find).toArray();
      Frame modFr = new Frame(fr);
      modFr.swap(teColIdx[0], teColIdx[1]);
      DKV.put(modFr);
      Scope.track(modFr);
      teParams2.setTrain(modFr._key);
      TargetEncoder te2 = new TargetEncoder(teParams2);
      TargetEncoderModel teModel2 = te2.trainModel().get();
      Scope.track_generic(teModel2);
      Frame encoded2 = Scope.track(teModel2.transformTraining(modFr));
      Frame sortedEncoded2 = Scope.track(Merge.sort(encoded2, encoded2.find(RowIndexTask.ROW_INDEX_COL)));

      assertThat(sortedEncoded1.names(), not(equalTo(sortedEncoded2.names())));
      assertEquals(new HashSet<>(Arrays.asList(sortedEncoded1.names())), new HashSet<>(Arrays.asList(sortedEncoded2.names())));
      Frame sortedReorderedEncoded2 = new Frame(sortedEncoded1.names(), sortedEncoded2.vecs(sortedEncoded1.names()));

      assertEncodingMapsEqual(teModel1._output._target_encoding_map, teModel2._output._target_encoding_map);

      assertBitIdentical(sortedEncoded1, sortedReorderedEncoded2);
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void test_columns_order_has_no_effect_on_transform() {
    try {
      Scope.enter();
      final Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      Scope.track(fr);
      RowIndexTask.addRowIndex(fr);

      String foldColumn = "fold_column";
      int nfolds = 5;
      addKFoldColumn(fr, foldColumn, nfolds, 1234L);
      String responseColumnName = "survived";
      asFactor(fr, responseColumnName);
      String[] teColumns = new String[]{ "home.dest", "embarked" };

      TargetEncoderParameters teParameters = new TargetEncoderParameters();
      teParameters._train = fr._key;
      teParameters._response_column = responseColumnName;
      teParameters._data_leakage_handling = DataLeakageHandlingStrategy.KFold;
      teParameters._fold_column = foldColumn;
      teParameters._seed = 42;
      teParameters._ignored_columns = ignoredColumns(fr,
              water.util.ArrayUtils.append(new String[]{ "home.dest", "embarked" }, teParameters._response_column, teParameters._fold_column));

      TargetEncoder te = new TargetEncoder(teParameters);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);
      Frame encodedOri = Scope.track(teModel.transform(fr));

      int[] teColIdx = Stream.of(teColumns).mapToInt(fr::find).toArray();
      Frame modFr = new Frame(fr);
      modFr.swap(teColIdx[0], teColIdx[1]);
      Frame encodedSwap = Scope.track(teModel.transform(modFr));

      assertArrayEquals(encodedOri.names(), encodedSwap.names());
      assertBitIdentical(encodedOri, encodedSwap);

      modFr = new Frame(fr);
      modFr.add("dummy", modFr.anyVec().makeZero(new String[] {"dum", "dumm"}));
      Frame encodedPlusOne= Scope.track(teModel.transform(modFr));
      assertNotNull(encodedPlusOne.vec("dummy"));
      encodedPlusOne.remove("dummy");
      assertBitIdentical(encodedOri, encodedPlusOne);

      modFr = new Frame(fr);
      modFr.remove("sex");
      Frame encodedMinusOne= Scope.track(teModel.transform(modFr));
      assertNull(encodedMinusOne.vec("sex"));
      Frame oriMinusOne = new Frame(encodedOri);
      oriMinusOne.remove("sex");
      assertBitIdentical(oriMinusOne, encodedMinusOne);
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void test_original_features_are_kept_by_default() {
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames("categorical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "b", "b"))
              .withDataForCol(1, ar("N", "Y", "Y", "N"))
              .build();

      TargetEncoderParameters params = new TargetEncoderParameters();
      params._train = fr._key;
      params._response_column = "target";
      params._noise = 0;
      params._seed = 0XFEED;


      TargetEncoder te = new TargetEncoder(params);
      final TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame transformed = Scope.track(teModel.transformTraining(fr));
      assertTrue(ArrayUtils.contains(transformed.names(), "categorical"));

      transformed = Scope.track(teModel.transform(fr));
      assertTrue(ArrayUtils.contains(transformed.names(), "categorical"));

      transformed = Scope.track(teModel.score(fr));
      assertTrue(ArrayUtils.contains(transformed.names(), "categorical"));

      assertTrue(ArrayUtils.contains(fr.names(), "categorical"));
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void test_original_features_are_all_removed_when_keep_original_categorical_columns_is_disabled() {
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames("categorical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "b", "b"))
              .withDataForCol(1, ar("N", "Y", "Y", "N"))
              .build();

      TargetEncoderParameters params = new TargetEncoderParameters();
      params._train = fr._key;
      params._response_column = "target";
      params._noise = 0;
      params._seed = 0XFEED;
      params._keep_original_categorical_columns = false;


      TargetEncoder te = new TargetEncoder(params);
      final TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame transformed = Scope.track(teModel.transformTraining(fr));
      assertFalse(ArrayUtils.contains(transformed.names(), "categorical"));

      transformed = Scope.track(teModel.transform(fr));
      assertFalse(ArrayUtils.contains(transformed.names(), "categorical"));

      transformed = Scope.track(teModel.score(fr));
      assertFalse(ArrayUtils.contains(transformed.names(), "categorical"));

      assertTrue(ArrayUtils.contains(fr.names(), "categorical"));
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void test_interactions_are_encoded_as_a_single_categorical_column() {
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames("cat1", "cat2", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "b", "b"))
              .withDataForCol(1, ar("x", "x", "x", "y"))
              .withDataForCol(2, ar("N", "Y", "N", "Y"))
              .build();

      TargetEncoderParameters params = new TargetEncoderParameters();
      params._train = fr._key;
      params._response_column = "target";
      params._columns_to_encode = new String[][] {
              new String[] {"cat1", "cat2"}
      };
      params._noise = 0;
      params._seed = 0XFEED;
      params._keep_original_categorical_columns = true;
      params._keep_interaction_columns = true;

      TargetEncoder te = new TargetEncoder(params);
      final TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);

      Frame transformed = Scope.track(teModel.transform(fr));
      assertTrue(ArrayUtils.contains(transformed.names(), "cat1"));
      assertTrue(ArrayUtils.contains(transformed.names(), "cat2"));
      assertTrue(ArrayUtils.contains(transformed.names(), "cat1:cat2")); // only because _keep_interaction_columns=true for the test
      assertTrue(ArrayUtils.contains(transformed.names(), "cat1:cat2_te"));
      
      Vec interaction = transformed.vec("cat1:cat2");
      assertEquals(3, interaction.domain().length);
      assertArrayEquals(new String[] {"0", "1", "4"}, interaction.domain()); //[a, x] -> 0, [b, x] -> 1, [NA, x], [a, y], [b, y] -> 4, [NA, y]
      assertVecEquals(vec(0, 1, 1, 2), interaction, 0);
      
      Vec interaction_te = transformed.vec("cat1:cat2_te");
      assertVecEquals(dvec(0., .5, .5, 1.), interaction_te, 0);
    } finally {
      Scope.exit();
    }
  }
  
  
  private static class RowIndexTask extends MRTask<RowIndexTask> {
    static String ROW_INDEX_COL = "__row_index";

    @Override
    public void map(Chunk c, NewChunk nc) {
      long start = c.start();
      for (int i = 0; i < c._len; i++) {
        nc.addNum(start + i);
      }
    }

    private static void addRowIndex(Frame f) {
      Vec indexVec = new RowIndexTask().doAll(Vec.T_NUM, f.anyVec())
              .outputFrame().anyVec();
      f.insertVec(0, ROW_INDEX_COL, indexVec);
    }
  }

  private void assertEncodingMapsEqual(Map<String, Frame> encodingMapFromTargetEncoder, Map<String, Frame> targetEncodingMapFromBuilder) {
    for (Map.Entry<String, Frame> entry : targetEncodingMapFromBuilder.entrySet()) {
      String teColumn = entry.getKey();
      Frame correspondingEncodingFrameFromTargetEncoder = encodingMapFromTargetEncoder.get(teColumn);
      assertBitIdentical(entry.getValue(), correspondingEncodingFrameFromTargetEncoder);
    }
  }

}
