package ai.h2o.targetencoding;

import java.util.*;

import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.*;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.*;
import static water.TestUtil.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class TargetEncoderTest {
  
  @Rule
  public ExpectedException thrown = ExpectedException.none();
  
  @Test
  public void test_all_non_ignored_categorical_predictors_are_encoded_by_default() {
    try {
      Scope.enter();
      Frame fr = loadTitanic();
      Scope.track(fr);
      
      String target = "survived";
      String[] ignoredColumns = new String[] { "name", "ticket", "boat" };
      Set<String> expectedTEColumns = new HashSet<>(Arrays.asList("sex", "cabin", "embarked", "home.dest"));

      TargetEncoderParameters teParameters = new TargetEncoderParameters();
      teParameters._response_column = target;
      teParameters._ignored_columns = ignoredColumns;
      teParameters.setTrain(fr._key);

      TargetEncoder builder = new TargetEncoder(teParameters);
      TargetEncoderModel targetEncoderModel = builder.trainModel().get();
      Scope.track_generic(targetEncoderModel);
      assertEquals(expectedTEColumns, targetEncoderModel._output._target_encoding_map.keySet());
    } finally {
      Scope.exit();
    }
  }


  @Test
  public void test_columns_to_encode_cannot_be_ignored() {
    try {
      Scope.enter();
      Frame fr = loadTitanic();
      Scope.track(fr);

      String target = "survived";
      String[] ignoredColumns = new String[] { "name", "ticket", "boat" };

      TargetEncoderParameters teParameters = new TargetEncoderParameters();
      teParameters._response_column = target;
      teParameters._ignored_columns = ignoredColumns;
      teParameters._columns_to_encode = new String[][] {
              new String[] {"sex"},
              new String[] {"cabin", "embarked"},
              new String[] {"cabin", "embarked", "boat"}
      };
      teParameters.setTrain(fr._key);

      TargetEncoder builder = new TargetEncoder(teParameters);
      try { // validation occurs when starting training (expensive init)
        builder.trainModel().get();
        fail("should have thrown validation error");
      } catch(H2OModelBuilderIllegalArgumentException e) {
        assertTrue(e.getMessage().contains("Column `boat` from interaction [cabin, embarked, boat] is not categorical or is missing from the training frame"));
      }
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void test_columns_to_encode_should_all_be_categorical() {
    try {
      Scope.enter();
      Frame fr = loadTitanic();
      Scope.track(fr);

      String target = "survived";
      String[] ignoredColumns = new String[] { "name", "ticket", "boat" };

      TargetEncoderParameters teParameters = new TargetEncoderParameters();
      teParameters._response_column = target;
      teParameters._ignored_columns = ignoredColumns;
      teParameters._columns_to_encode = new String[][] {
              new String[] {"sex"},
              new String[] {"cabin", "embarked"},
              new String[] {"cabin", "embarked", "age"}
      };
      teParameters.setTrain(fr._key);

      TargetEncoder builder = new TargetEncoder(teParameters);
      try { // validation occurs when starting training (expensive init)
        builder.trainModel().get();
        fail("should have thrown validation error");
      } catch(H2OModelBuilderIllegalArgumentException e) {
        assertTrue(e.getMessage().contains("Column `age` from interaction [cabin, embarked, age] must first be converted into categorical to be used by target encoder"));
      }
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void test_interactions_cannot_contain_duplicate_columns() {
    try {
      Scope.enter();
      Frame fr = loadTitanic();
      Scope.track(fr);

      String target = "survived";
      String[] ignoredColumns = new String[] { "name", "ticket", "boat" };

      TargetEncoderParameters teParameters = new TargetEncoderParameters();
      teParameters._response_column = target;
      teParameters._ignored_columns = ignoredColumns;
      teParameters._columns_to_encode = new String[][] {
              new String[] {"sex"},
              new String[] {"cabin", "embarked"},
              new String[] {"cabin", "embarked", "cabin"}
      };
      teParameters.setTrain(fr._key);

      TargetEncoder builder = new TargetEncoder(teParameters);
      try { // validation occurs when starting training (expensive init)
        builder.trainModel().get();
        fail("should have thrown validation error");
      } catch(H2OModelBuilderIllegalArgumentException e) {
        assertTrue(e.getMessage().contains("Columns interaction [cabin, embarked, cabin] contains duplicate columns"));
      }
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void test_output_contains_interactions_to_encoded_columns_mapping() {
    try {
      Scope.enter();
      Frame fr = loadTitanic();
      Scope.track(fr);

      String target = "survived";
      TargetEncoderParameters teParameters = new TargetEncoderParameters();
      teParameters._response_column = target;
      teParameters._columns_to_encode = new String[][] {
              new String[] {"sex"},
              new String[] {"cabin", "embarked"},
              new String[] {"cabin", "embarked", "boat"}
      };
      teParameters.setTrain(fr._key);

      TargetEncoder builder = new TargetEncoder(teParameters);
      TargetEncoderModel model = builder.trainModel().get();
      Scope.track_generic(model);
      
      ColumnsToSingleMapping[] inputToEncodingColumn = model._output._input_to_encoding_column;
      Map<String, Frame> encodingMap = model._output._target_encoding_map;
      assertEquals(teParameters._columns_to_encode.length, inputToEncodingColumn.length);
      for (int i=0; i<teParameters._columns_to_encode.length; i++) {
        assertArrayEquals(teParameters._columns_to_encode[i], inputToEncodingColumn[i].from());
        assertTrue(encodingMap.containsKey(inputToEncodingColumn[i].toSingle()));
      }
      assertEquals("sex", inputToEncodingColumn[0].toSingle());
      assertEquals("cabin:embarked", inputToEncodingColumn[1].toSingle());
      assertEquals("cabin:embarked:boat", inputToEncodingColumn[2].toSingle());
      
      assertArrayEquals(fr.vec("sex").domain(),  inputToEncodingColumn[0].toDomain());
      assertTrue(inputToEncodingColumn[1].toDomain().length > fr.vec("cabin").domain().length);
      assertTrue(inputToEncodingColumn[1].toDomain().length > fr.vec("embarked").domain().length);
      assertTrue(inputToEncodingColumn[1].toDomain().length < fr.vec("cabin").domain().length * fr.vec("embarked").domain().length);
      assertTrue(inputToEncodingColumn[2].toDomain().length > fr.vec("cabin").domain().length);
      assertTrue(inputToEncodingColumn[2].toDomain().length > fr.vec("embarked").domain().length);
      assertTrue(inputToEncodingColumn[2].toDomain().length > fr.vec("boat").domain().length);
      assertTrue(inputToEncodingColumn[2].toDomain().length < fr.vec("cabin").domain().length * fr.vec("embarked").domain().length * fr.vec("boat").domain().length);
    } finally {
      Scope.exit();
    }
    
  }
  
  @Test
  public void test_output_contains_input_columns_to_output_columns_mapping() {
    try {
      Scope.enter();
      Frame fr = loadTitanic();
      Scope.track(fr);

      String target = "survived";
      TargetEncoderParameters teParameters = new TargetEncoderParameters();
      teParameters._response_column = target;
      teParameters._columns_to_encode = new String[][] {
              new String[] {"sex"},
              new String[] {"cabin", "embarked"},
              new String[] {"cabin", "embarked", "boat"}
      };
      teParameters.setTrain(fr._key);

      TargetEncoder builder = new TargetEncoder(teParameters);
      TargetEncoderModel model = builder.trainModel().get();
      Scope.track_generic(model);
      
      ColumnsMapping[] inputToOutputMapping = model._output._input_to_output_columns;
      assertEquals(teParameters._columns_to_encode.length, inputToOutputMapping.length);
      for (int i=0; i<teParameters._columns_to_encode.length; i++) {
        assertArrayEquals(teParameters._columns_to_encode[i], inputToOutputMapping[i].from());
      }
      assertArrayEquals(new  String[] {"sex_te"}, inputToOutputMapping[0].to());
      assertArrayEquals(new String[] {"cabin:embarked_te"}, inputToOutputMapping[1].to());
      assertArrayEquals(new String[] {"cabin:embarked:boat_te"}, inputToOutputMapping[2].to());
      
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_output_contains_input_columns_to_output_columns_mapping_with_multiclass_task() {
    try {
      Scope.enter();
      Frame fr = loadTitanic();
      Scope.track(fr);

      String target = "embarked";
      assertArrayEquals(new String[] {"C", "Q", "S"}, fr.vec(target).domain());
      TargetEncoderParameters teParameters = new TargetEncoderParameters();
      teParameters._response_column = target;
      teParameters._columns_to_encode = new String[][] {
              new String[] {"sex"},
              new String[] {"cabin", "boat"},
              new String[] {"sex", "cabin", "boat"}
      };
      teParameters.setTrain(fr._key);

      TargetEncoder builder = new TargetEncoder(teParameters);
      TargetEncoderModel model = builder.trainModel().get();
      Scope.track_generic(model);

      ColumnsMapping[] inputToOutputMapping = model._output._input_to_output_columns;
      assertEquals(teParameters._columns_to_encode.length, inputToOutputMapping.length);
      for (int i=0; i<teParameters._columns_to_encode.length; i++) {
        assertArrayEquals(teParameters._columns_to_encode[i], inputToOutputMapping[i].from());
      }
      assertArrayEquals(new String[] {"sex_Q_te", "sex_S_te"}, inputToOutputMapping[0].to());
      assertArrayEquals(new String[] {"cabin:boat_Q_te", "cabin:boat_S_te"}, inputToOutputMapping[1].to());
      assertArrayEquals(new String[] {"sex:cabin:boat_Q_te", "sex:cabin:boat_S_te"}, inputToOutputMapping[2].to());

    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void test_missing_values_handling() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("cat1", "cat2", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar(  "a",  "b"))
              .withDataForCol(1, ar(  "s", null))
              .withDataForCol(2, ar("yes", "no"))
              .build();

      TargetEncoderParameters teParameters = new TargetEncoderParameters();
      teParameters._response_column = "target";
      teParameters.setTrain(fr._key);

      TargetEncoder builder = new TargetEncoder(teParameters);
      TargetEncoderModel targetEncoderModel = builder.trainModel().get();
      Scope.track_generic(targetEncoderModel);

      Map<String, Boolean> hasNAsMap = targetEncoderModel._output._te_column_to_hasNAs;
      assertFalse(hasNAsMap.get("cat1"));
      assertTrue(hasNAsMap.get("cat2"));
    } finally {
      Scope.exit();
    }
  }


  private Frame loadTitanic() {
    return parseTestFile(
            "./smalldata/gbm_test/titanic.csv",
            "NA",
            1,
            new byte[] {Vec.T_NUM, Vec.T_CAT, Vec.T_STR, Vec.T_CAT, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM, Vec.T_CAT},
            //             pclass,  survived,      name,       sex,       age,     sibsp,     parch,    ticket,      fare,     cabin,  embarked,      boat,      body, home.dest
            null,
            new int[] {2}
    );
  }

}
