package ai.h2o.targetencoding;

import ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy;
import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.*;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static ai.h2o.targetencoding.TargetEncoderHelper.DENOMINATOR_COL;
import static ai.h2o.targetencoding.TargetEncoderHelper.NUMERATOR_COL;
import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class TargetEncodingOnBinaryTest extends TestUtil {
  
  @Test
  public void test_encodings_with_binary_target() {
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames("categorical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a",    "a",   "a",   "a",  "b",   "b"))
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
      Vec expectedNumColumn = vec(3, 1);  // for binomial, numerator should correspond to the sum of positive occurrences (here "YES" ) for each category
      Vec expectedDenColumn = vec(4, 2);  // for binomial, denominator should correspond to the sum of all occurrences for each category

      assertVecEquals(expectedCatColumn, encodings.vec("categorical"), 0);
      assertVecEquals(expectedNumColumn, encodings.vec(NUMERATOR_COL), 0);
      assertVecEquals(expectedDenColumn, encodings.vec(DENOMINATOR_COL), 0);
      
      Frame trainEncoded = teModel.transformTraining(fr);
      Scope.track(trainEncoded);
      Assert.assertEquals(3, trainEncoded.numCols()); // 2 original cols + 1 enc column for the categorical one
        
      Vec trainEncodedCol = trainEncoded.vec("categorical_te");
      Assert.assertNotNull(trainEncodedCol);
      Vec expectedEncodedCol = dvec(.75, .75, .75, .75, .5, .5);
      assertVecEquals(expectedEncodedCol, trainEncodedCol, 1e-6);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_encodings_with_binary_target_using_None_strategy() {
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames("categorical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar( "a",   "a",  null,   "b",   "a",  null,  "b",   "a", null))
              .withDataForCol(1, ar("NO", "YES",  "NO", "YES", "YES", "YES", "NO", "YES", "NO"))
              .build();

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train = fr._key;
      teParams._response_column = "target";
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.None;
      teParams._noise = 0;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);
      Frame encodings = teModel._output._target_encoding_map.get("categorical");
      printOutFrameAsTable(encodings);
      double priorMean = TargetEncoderHelper.computePriorMean(encodings);
      assertEquals(0.556, priorMean, 1e-3); // == 5/9

      Vec expectedCatColumn = vec(ar("a", "b", "categorical_NA"), 0, 1, 2); //  we should have an entry per category
      Vec expectedNumColumn = vec(3, 1, 1); // for binomial, numerator should correspond to the sum of positive occurrences (here "YES" ) for each category
      Vec expectedDenColumn = vec(4, 2, 3); // for binomial, denominator should correspond to the sum of all occurrences for each category

      assertCatVecEquals(expectedCatColumn, encodings.vec("categorical"));
      assertVecEquals(expectedNumColumn, encodings.vec(NUMERATOR_COL), 0);
      assertVecEquals(expectedDenColumn, encodings.vec(DENOMINATOR_COL), 0);

      Frame trainEncoded = teModel.transformTraining(fr);
      Scope.track(trainEncoded);
      Vec trainEncodedCol = trainEncoded.vec("categorical_te");
      Assert.assertNotNull(trainEncodedCol);
      Vec expectedEncodedCol = dvec(0.75, 0.75, 0.333, 0.5, 0.75, 0.333, 0.5, 0.75, 0.333); // == (3/4, 3/4, 1/3, 1/2, 3/4, 1/3, 1/2, 3/4, 1/3)
      assertVecEquals(expectedEncodedCol, trainEncodedCol, 1e-3);
      
      final Frame test = new TestFrameBuilder()
              .withColNames("categorical")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar("c", "b", "a", null))
              .build();
      Frame testEncoded = teModel.transform(test);
      Scope.track(testEncoded);
      Vec testEncodedCol = testEncoded.vec("categorical_te");
      Assert.assertNotNull(testEncodedCol);
      Vec expectedTestEnc = dvec(0.333, 0.5, 0.75, 0.333); // == (1/3, 1/2, 3/4, 1/3), unseen "c' currently trainEncodedCol like null (would rather use prior...)
      assertVecEquals(expectedTestEnc, testEncodedCol, 1e-3);

      Frame testPredictions = teModel.score(test);
      Scope.track(testPredictions);
      assertVecEquals(expectedTestEnc, testPredictions.vec("categorical_te"), 1e-3);
      
      // with None strategy, transformTraining behaves the same as default transform
      Frame testEncodedAsTrain = teModel.transformTraining(test);
      Scope.track(testEncodedAsTrain);
      assertVecEquals(expectedTestEnc, testEncodedAsTrain.vec("categorical_te"), 1e-3);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_encodings_with_binary_target_using_LOO_strategy() {
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames("categorical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar( "a",   "a",  null,   "b",   "a",  null,  "b",   "a", null,   "c"))
              .withDataForCol(1, ar("NO", "YES",  "NO", "YES", "YES", "YES", "NO", "YES", "NO", "YES"))
              .build();

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train = fr._key;
      teParams._response_column = "target";
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.LeaveOneOut;
      teParams._noise = 0;

      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);
      Frame encodings = teModel._output._target_encoding_map.get("categorical");
      printOutFrameAsTable(encodings);
      double priorMean = TargetEncoderHelper.computePriorMean(encodings);
      assertEquals(0.6, priorMean, 1e-3); // == 6/10

      Vec expectedCatColumn = vec(ar("a", "b", "c", "categorical_NA"), 0, 1, 2, 3); //  we should have an entry per category
      Vec expectedNumColumn = vec(3, 1, 1, 1); // for binomial, numerator should correspond to the sum of positive occurrences (here "YES" ) for each category
      Vec expectedDenColumn = vec(4, 2, 1, 3); // for binomial, denominator should correspond to the sum of all occurrences for each category

      assertCatVecEquals(expectedCatColumn, encodings.vec("categorical"));
      assertVecEquals(expectedNumColumn, encodings.vec(NUMERATOR_COL), 0);
      assertVecEquals(expectedDenColumn, encodings.vec(DENOMINATOR_COL), 0);

      Frame trainEncoded = teModel.transformTraining(fr);
      Scope.track(trainEncoded);
      Vec encodedCol = trainEncoded.vec("categorical_te");
      Assert.assertNotNull(encodedCol);
      Vec expectedEncodedCol = dvec(1., 0.667, 0.5, 0., 0.667, 0., 1., 0.667, 0.5, 0.6); // == (3/3, 2/3, 1/2, 0/1, 2/3, 0/2, 1/1, 2/3, 1/2, 0/0=prior)
      assertVecEquals(expectedEncodedCol, encodedCol, 1e-3);
      
      final Frame test = new TestFrameBuilder()
              .withColNames("categorical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar(  "d",   "c",   "b",   "a",  null))
              .withDataForCol(1, ar("YES",  "NO", "YES", "YES", "YES"))
              .build();
      Frame testEncoded = teModel.transform(test); // LOO should not be applied this time (target ignored).
      Scope.track(testEncoded);
      Vec testEncodedCol = testEncoded.vec("categorical_te");
      Assert.assertNotNull(testEncodedCol);
      Vec expectedTestEnc = dvec(0.333, 1., 0.5, 0.75, 0.333); // == (1.3, 1/1, 1/2, 3/4, 1/3), unseen "d' encoded like a NA this time (None strategy)
      assertVecEquals(expectedTestEnc, testEncodedCol, 1.e-3);

      Frame testPredictions = teModel.score(test);
      Scope.track(testPredictions);
      assertVecEquals(expectedTestEnc, testPredictions.vec("categorical_te"), 1e-3);
      
      // with LOO strategy, transformTraining applies to test as if it were a training frame, requiring and taking target into account
      Frame testEncodedAsTrain = teModel.transformTraining(test);
      Scope.track(testEncodedAsTrain);
      Vec testEncodedAsTrainCol = testEncodedAsTrain.vec("categorical_te");
      Assert.assertNotNull(testEncodedAsTrainCol);
      Vec expectedTestEncAsTrain = dvec(0.6, 0.6, 0., 0.667, 0.); // == (prior, 1/0=prior, 0/1, 2/3, 0/2), unseen "d' encodedCol using prior (inconsistent with None strategy)
      assertVecEquals(expectedTestEncAsTrain, testEncodedAsTrainCol, 1e-3);
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
              .withDataForCol(0, ar( "a",   "a",  null,   "b",   "a",  null,  "b",   "a", null,   "c"))
              .withDataForCol(1, ar("NO", "YES",  "NO", "YES", "YES", "YES", "NO", "YES", "NO", "YES"))
              .withDataForCol(2, ar(   0,     1,     0,     1,     0,     1,    0,     1,    0,     1))
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
      printOutFrameAsTable(encodings);
      double priorMean = TargetEncoderHelper.computePriorMean(encodings);
      assertEquals(0.6, priorMean, 1e-3); // == 6/10

      Vec expectedCatColumn = vec(ar("a", "b", "c", "categorical_NA"), // for each fold value, we should have an entry per category (except for 'c', only in fold 0)
              0, 1, 2, 3, 
              0, 1, 3);
      Vec expectedNumColumn = vec( // for binomial, numerator should correspond to the sum of positive occurrences (here "YES" ) for each category
              2, 1, 1, 1,    // out of fold 0 encodings
              1, 0, 0      // out of fold 1 encodings
      ); 
      Vec expectedDenColumn = vec(   // for binomial, denominator should correspond to the sum of all occurrences for each category
              2, 1, 1, 1,    // out of fold 0 encodings
              2, 1, 2    // out of fold 1 encodings
      );
      Vec expectedFoldColumn = vec(0, 0, 0, 0, 1, 1, 1);

      assertCatVecEquals(expectedCatColumn, encodings.vec("categorical"));
      assertVecEquals(expectedNumColumn, encodings.vec(NUMERATOR_COL), 0);
      assertVecEquals(expectedDenColumn, encodings.vec(DENOMINATOR_COL), 0);
      assertVecEquals(expectedFoldColumn, encodings.vec("foldc"), 0);
      
      Frame predictions = teModel.transformTraining(fr);
      Scope.track(predictions);
      Vec encoded = predictions.vec("categorical_te");
      Assert.assertNotNull(encoded);
      Vec expectedEncodedCol = dvec(1., 0.5, 1., 0., 1., 0., 1., 0.5, 1., 0.6); // == (2/2, 1/2, 1/1, 0/1, 2/2, 0/2, 1/1, 1/2, 1/1, unseen_in_fold1=prior)
      //XXX: unseen in fold1 is encoded as global prior. Wouldn't it be better to encode it using out-of-fold1 prior (here 1/5=0.2) to avoid leakage? 
      assertVecEquals(expectedEncodedCol, encoded, 1e-3);
      
      final Frame test = new TestFrameBuilder()
              .withColNames("categorical", "foldc")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("d", "c", "b", "a", null))
              .withDataForCol(1, ar(  0,   0,   0,   0,    0))
              .build();
      Frame testEncoded = teModel.transform(test); //KFold should not be applied (fold column ignored, all folds being merged/summed)
      Scope.track(testEncoded);
      Vec testEncodedCol = testEncoded.vec("categorical_te");
      Assert.assertNotNull(testEncodedCol);
      Vec expectedTestEnc = dvec(0.333, 1., 0.5, 0.75, 0.333); // == (1.3, 1/1, 1/2, 3/4, 1/3), unseen "d' encoded like a NA this time (None strategy)
      assertVecEquals(expectedTestEnc, testEncodedCol, 1.e-3);

      Frame testPredictions = teModel.score(test);
      Scope.track(testPredictions);
      assertVecEquals(expectedTestEnc, testPredictions.vec("categorical_te"), 1e-3);

      // with Kfold strategy, transformTraining applies to test as if it were a training frame, requiring and taking fold column into account
      Frame testEncodedAsTrain = teModel.transformTraining(test);
      Scope.track(testEncodedAsTrain);
      Vec testEncodedAsTrainCol = testEncodedAsTrain.vec("categorical_te");
      Assert.assertNotNull(testEncodedAsTrainCol);
      Vec expectedTestEncAsTrain = dvec(0.6, 1., 1., 1., 1.); // == (prior, 1/1, 1/1, 2/2, 1/1), unseen "d' encoded using prior (inconsistent with None strategy)
      assertVecEquals(expectedTestEncAsTrain, testEncodedAsTrainCol, 1e-3);
    } finally {
      Scope.exit();
    }
  }

}
