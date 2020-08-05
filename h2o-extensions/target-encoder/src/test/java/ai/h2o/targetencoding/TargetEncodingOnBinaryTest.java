package ai.h2o.targetencoding;

import ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy;
import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.*;

import static ai.h2o.targetencoding.TargetEncoderHelper.DENOMINATOR_COL;
import static ai.h2o.targetencoding.TargetEncoderHelper.NUMERATOR_COL;

public class TargetEncodingOnBinaryTest extends TestUtil {
  
  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void test_encodings_with_binary_target() {
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames("categorical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "a", "a", "a", "b", "b"))
              .withDataForCol(1, ar("NO", "YES", "YES", "YES", "NO", "YES"))
              .build();

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train = fr._key;
      teParams._response_column = "target";
      teParams._noise = 0;
      
      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);
      Frame encodings = teModel._output._target_encoding_map.get("categorical");
        
      Vec expectedCatColumn = vec(ar("a", "b"), 0, 1);
      Vec expectedNumColumn = vec( 3, 1);  // for binomial, numerator should correspond to the sum of positive occurences (here "YES" ) for each category
      Vec expectedDenColumn = vec(4, 2);   // for binomial, denominator should correspond to the sum of all occurences for each category

      assertVecEquals(expectedCatColumn, encodings.vec("categorical"), 0);
      assertVecEquals(expectedNumColumn, encodings.vec(NUMERATOR_COL), 0);
      assertVecEquals(expectedDenColumn, encodings.vec(DENOMINATOR_COL), 0);
      
      Frame predictions = teModel.score(fr);
      Scope.track(predictions);
      Vec encoded = predictions.vec("categorical_te");
      Assert.assertNotNull(encoded);
      Vec expectedEncodedCol = dvec(.75, .75, .75, .75, .5, .5);
      assertVecEquals(expectedEncodedCol, encoded, 1e-6);
      
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void test_encodings_with_binary_target_using_KFold_strategy() {
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames("categorical", "target", "foldc")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "a", "a", "a", "b", "b"))
              .withDataForCol(1, ar("NO", "YES", "YES", "YES", "NO", "YES"))
              .withDataForCol(2, ar(0, 1, 0, 1, 0, 1))
              .build();

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train = fr._key;
      teParams._response_column = "target";
      teParams._fold_column = "foldc";
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.KFold;
      teParams._noise = 0;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);
      Frame encodings = teModel._output._target_encoding_map.get("categorical");

      Vec expectedCatColumn = vec(ar("a", "b"), 0, 1, 0, 1); // for each fold value, we should have an entry per category
      Vec expectedNumColumn = vec( // for binomial, numerator should correspond to the sum of positive occurences (here "YES" ) for each category
              2, 1,     // out of fold 0 encodings
              1, 0      // out of fold 1 encodings
      ); 
      Vec expectedDenColumn = vec(   // for binomial, denominator should correspond to the sum of all occurences for each category
              2, 1,     // out of fold 0 encodings
              2, 1      // out of fold 1 encodings
      );
      Vec expectedFoldColumn = vec(0, 0, 1, 1);

      assertCatVecEquals(expectedCatColumn, encodings.vec("categorical"));
      assertVecEquals(expectedNumColumn, encodings.vec(NUMERATOR_COL), 0);
      assertVecEquals(expectedDenColumn, encodings.vec(DENOMINATOR_COL), 0);
      assertVecEquals(expectedFoldColumn, encodings.vec("foldc"), 0);
      
      Frame predictions = teModel.score(fr);
      Scope.track(predictions);
      Vec encoded = predictions.vec("categorical_te");
      Assert.assertNotNull(encoded);
      Vec expectedEncodedCol = dvec(1., .5, 1., .5, 1., .0);
      assertVecEquals(expectedEncodedCol, encoded, 1e-6);
    } finally {
      Scope.exit();
    }
  }
  

}
