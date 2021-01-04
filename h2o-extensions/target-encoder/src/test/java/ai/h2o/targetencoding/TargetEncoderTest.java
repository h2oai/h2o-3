package ai.h2o.targetencoding;

import java.util.*;

import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import water.*;
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
      Frame fr = parseTestFile("./smalldata/gbm_test/titanic.csv");
      Scope.track(fr);
      
      String target = "survived";
      asFactor(fr, target);
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
  public void test_missing_values_handling() {
    try {
      Scope.enter();
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("home.dest", "embarked", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b"))
              .withDataForCol(1, ar("s", null))
              .withDataForCol(2, ar("yes", "no"))
              .build();

      TargetEncoderParameters teParameters = new TargetEncoderParameters();
      teParameters._response_column = "target";
      teParameters.setTrain(fr._key);

      TargetEncoder builder = new TargetEncoder(teParameters);
      TargetEncoderModel targetEncoderModel = builder.trainModel().get();
      Scope.track_generic(targetEncoderModel);

      Map<String, Boolean> hasNAsMap = targetEncoderModel._output._te_column_to_hasNAs;
      assertFalse(hasNAsMap.get("home.dest"));
      assertTrue(hasNAsMap.get("embarked"));
    } finally {
      Scope.exit();
    }
  }
  

}
