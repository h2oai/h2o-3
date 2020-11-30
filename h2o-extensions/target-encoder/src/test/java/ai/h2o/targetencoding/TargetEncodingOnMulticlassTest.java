package ai.h2o.targetencoding;

import ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy;
import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import org.junit.Assert;
import org.junit.BeforeClass;
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
import static org.junit.Assert.assertEquals;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class TargetEncodingOnMulticlassTest extends TestUtil {
  
  @Test
  public void test_encodings_with_multiclass_target() {
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames("categorical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a",    "a",   "a",     "a",  "b",   "b"))
              .withDataForCol(1, ar("NO", "YES", "YES", "MAYBE", "NO", "YES"))
              .build();

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train = fr._key;
      teParams._response_column = "target";
      teParams._noise = 0;
      
      TargetEncoder te = new TargetEncoder(teParams);
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);
      Frame encodings = teModel._output._target_encoding_map.get("categorical");
//      printOutFrameAsTable(encodings);
        
      Vec expectedCatColumn = vec(ar("a", "b"),  // 4 entries per category (one for each target class+NA)
              0, 0, 0, 0, 
              1, 1, 1, 1);
      Vec expectedTargetClassColumn = vec(ar("MAYBE", "NO", "YES", "missing(NA)"), // 2 entries per target class, one for each category (from the categorical column)
              0, 1, 2, 3,
              0, 1, 2, 3); 
      Vec expectedNumColumn = vec( // for multinomial, numerator should correspond to the sum of matching target occurrences for each category
              1, 1, 2, 0, 
              0, 1, 1, 0);  
      Vec expectedDenColumn = vec( // for multinomial, denominator should correspond to the sum of all occurrences for each category
              4, 4, 4, 4, 
              2, 2, 2, 2); 

      assertVecEquals(expectedCatColumn, encodings.vec("categorical"), 0);
      assertVecEquals(expectedTargetClassColumn, encodings.vec(TARGETCLASS_COL), 0);
      assertVecEquals(expectedNumColumn, encodings.vec(NUMERATOR_COL), 0);
      assertVecEquals(expectedDenColumn, encodings.vec(DENOMINATOR_COL), 0);
      
      Frame trainEncoded = teModel.transformTraining(fr);
      Scope.track(trainEncoded);
      Assert.assertEquals(4, trainEncoded.numCols()); // 2 original cols + 2 (=3-1) enc columns for the categorical col (NA target currently ignored)
      
      Vec trainNOEncCol = trainEncoded.vec("categorical_NO_te");
      Assert.assertNotNull(trainNOEncCol);
      Vec expectedNOEncCol = dvec(.25, .25, .25, .25, .5, .5);
      assertVecEquals(expectedNOEncCol, trainNOEncCol, 1e-6);
      
      Vec trainYESEncCol = trainEncoded.vec("categorical_YES_te");
      Assert.assertNotNull(trainYESEncCol);
      Vec expectedYESEncCol = dvec(.5, .5, .5, .5, .5, .5);
      assertVecEquals(expectedYESEncCol, trainYESEncCol, 1e-6);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_encodings_with_multiclass_target_using_None_strategy() {
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames("categorical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar( "a",   "a",    null,   "b",   "a",    null,  "b",   "a", null,     "a"))
              .withDataForCol(1, ar("NO", "YES", "MAYBE", "YES", "YES", "MAYBE", "NO", "YES", "NO", "MAYBE"))
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

      Vec expectedCatColumn = vec(ar("a", "b", "categorical_NA"),  // 4 entries per category (one for each target class+NA)
          //  M  N  Y na
              0, 0, 0, 0,  //a
              1, 1, 1, 1,  //b
              2, 2, 2, 2); //NA
      Vec expectedTargetClassColumn = vec(ar("MAYBE", "NO", "YES", "missing(NA)"), // 2 entries per target class, one for each category (from the categorical column)
              0, 1, 2, 3,
              0, 1, 2, 3,
              0, 1, 2, 3);
      Vec expectedNumColumn = vec( // for multinomial, numerator should correspond to the sum of matching target occurrences for each category
              1, 1, 3, 0,
              0, 1, 1, 0,
              2, 1, 0, 0);
      Vec expectedDenColumn = vec( // for multinomial, denominator should correspond to the sum of all occurrences for each category
              5, 5, 5, 5,
              2, 2, 2, 2,
              3, 3, 3, 3);

      assertCatVecEquals(expectedCatColumn, encodings.vec("categorical"));
      assertVecEquals(expectedTargetClassColumn, encodings.vec(TARGETCLASS_COL), 0);
      assertVecEquals(expectedNumColumn, encodings.vec(NUMERATOR_COL), 0);
      assertVecEquals(expectedDenColumn, encodings.vec(DENOMINATOR_COL), 0);

      Frame trainEncoded = teModel.transformTraining(fr);
      Scope.track(trainEncoded);
      Assert.assertEquals(4, trainEncoded.numCols()); // 2 original cols + 2 (=3-1) enc columns for the categorical col (NA target currently ignored)

      Vec trainNOEncCol = trainEncoded.vec("categorical_NO_te");
      Assert.assertNotNull(trainNOEncCol);
      double priorMeanNO = computePriorMean(encodings, 1);
      assertEquals(0.3, priorMeanNO, 1e-3); // == 3/10
      Vec expectedNOEncCol = dvec(.2, .2, .333, .5, .2, .333, .5, .2, .333, .2); // == (1/5, 1/5, 1/3, 1/2, 1/5, 1/3, 1/2, 1/5, 1/3, 1/5)
      assertVecEquals(expectedNOEncCol, trainNOEncCol, 1e-3);

      Vec trainYESEncCol = trainEncoded.vec("categorical_YES_te");
      Assert.assertNotNull(trainYESEncCol);
      double priorMeanYES = computePriorMean(encodings, 2);
      assertEquals(0.4, priorMeanYES, 1e-3); // == 4/10
      Vec expecteYESEncCol = dvec(.6, .6, 0, .5, .6, 0, .5, .6, 0, .6); // == (3/5, 3/5, 0/3, 1/2, 3/5, 0/3, 1/2, 3/5, 0/3, 3/5)
      assertVecEquals(expecteYESEncCol, trainYESEncCol, 1e-3);
      
      final Frame test = new TestFrameBuilder()
              .withColNames("categorical")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar("c", "b", "a", null))
              .build();
      Frame testEncoded = teModel.transform(test);
      Scope.track(testEncoded);

      Vec testNOEncCol = testEncoded.vec("categorical_NO_te");
      Assert.assertNotNull(testNOEncCol);
      Vec expectedTestNOEnc = dvec(0.333, .5, .2, 0.333); // == (1/3, 1/2, 1/5, 1/3), unseen "c' currently like null (would rather use prior...)
      assertVecEquals(expectedTestNOEnc, testNOEncCol, 1e-3);

      Vec testYESEncCol = testEncoded.vec("categorical_YES_te");
      Assert.assertNotNull(testYESEncCol);
      Vec expectedTestYESEnc = dvec(0, .5, .6, 0); // == (0/3, 1/2, 3/5, 0/3), unseen "c' currently like null (would rather use prior...)
      assertVecEquals(expectedTestYESEnc, testYESEncCol, 1e-3);

      Frame testPredictions = teModel.score(test);
      Scope.track(testPredictions);
      assertVecEquals(expectedTestNOEnc, testPredictions.vec("categorical_NO_te"), 1e-3);
      assertVecEquals(expectedTestYESEnc, testPredictions.vec("categorical_YES_te"), 1e-3);

//       with None strategy, transformTraining behaves the same as default transform
      Frame testEncodedAsTrain = teModel.transformTraining(test);
      Scope.track(testEncodedAsTrain);
      assertVecEquals(expectedTestNOEnc, testEncodedAsTrain.vec("categorical_NO_te"), 1e-3);
      assertVecEquals(expectedTestYESEnc, testEncodedAsTrain.vec("categorical_YES_te"), 1e-3);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_encodings_with_multiclass_target_using_LOO_strategy() {
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames("categorical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar( "a",   "a",    null,   "b",   "a",    null,  "b",   "a", null,   "c",     "a",   "a"))
              .withDataForCol(1, ar("NO", "YES", "MAYBE", "YES", "YES", "MAYBE", "NO", "YES", "NO", "YES", "MAYBE", "YES"))
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

      Vec expectedCatColumn = vec(ar("a", "b", "c", "categorical_NA"),  // 4 entries per category (one for each target class+NA)
           // M  N  Y na   
              0, 0, 0, 0,  //a
              1, 1, 1, 1,  //b
              2, 2, 2, 2,  //c
              3, 3, 3, 3); //NA
      Vec expectedTargetClassColumn = vec(ar("MAYBE", "NO", "YES", "missing(NA)"), // 2 entries per target class, one for each category (from the categorical column)
              0, 1, 2, 3,
              0, 1, 2, 3,
              0, 1, 2, 3,
              0, 1, 2, 3);
      Vec expectedNumColumn = vec( // for multinomial, numerator should correspond to the sum of matching target occurrences for each category
              1, 1, 4, 0,
              0, 1, 1, 0,
              0, 0, 1, 0,
              2, 1, 0, 0);
      Vec expectedDenColumn = vec( // for multinomial, denominator should correspond to the sum of all occurrences for each category
              6, 6, 6, 6,
              2, 2, 2, 2,
              1, 1, 1, 1,
              3, 3, 3, 3);

      assertCatVecEquals(expectedCatColumn, encodings.vec("categorical"));
      assertVecEquals(expectedTargetClassColumn, encodings.vec(TARGETCLASS_COL), 0);
      assertVecEquals(expectedNumColumn, encodings.vec(NUMERATOR_COL), 0);
      assertVecEquals(expectedDenColumn, encodings.vec(DENOMINATOR_COL), 0);

      Frame trainEncoded = teModel.transformTraining(fr);
      Scope.track(trainEncoded);
      Assert.assertEquals(4, trainEncoded.numCols()); // 2 original cols + 2 (=3-1) enc columns for the categorical col (NA target currently ignored)
      printOutFrameAsTable(trainEncoded);
      
      Vec trainNOEncCol = trainEncoded.vec("categorical_NO_te");
      Assert.assertNotNull(trainNOEncCol);
      double priorMeanNO = computePriorMean(encodings, 1);
      assertEquals(0.25, priorMeanNO, 1e-3); // == 3/12
      Vec expectedNOEncCol = dvec(0, .2, .5, 1, .2, .5, 0, .2, 0, .25, .2, .2); // == (0/5, 1/5, 1/2, 1/1, 1/5, 1/2, 0/1, 1/5, 0/2, 0/0=prior, 1/5, 1/5)
      assertVecEquals(expectedNOEncCol, trainNOEncCol, 1e-3);

      Vec trainYESEncCol = trainEncoded.vec("categorical_YES_te");
      Assert.assertNotNull(trainYESEncCol);
      double priorMeanYES = computePriorMean(encodings, 2);
      assertEquals(0.5, priorMeanYES, 1e-3); // == 6/12
      Vec expecteYESEncCol = dvec(.8, .6, 0, 0, .6, 0, 1, .6, 0, .5, .8, .6); // == (4/5, 3/5, 0/2, 0/1, 3/5, 0/2, 1/1, 3/5, 0/2, 0/0=prior, 4/5, 3/5)
      assertVecEquals(expecteYESEncCol, trainYESEncCol, 1e-3);
      
      
      final Frame test = new TestFrameBuilder()
              .withColNames("categorical", "target")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar(  "d",   "c",   "b",   "a",  null))
              .withDataForCol(1, ar("YES",  "NO", "YES", "YES", "YES"))
              .build();
      Frame testEncoded = teModel.transform(test); // LOO should not be applied this time (target ignored).
      Scope.track(testEncoded);
      Vec testEncodedCol = testEncoded.vec("categorical_YES_te");
      Assert.assertNotNull(testEncodedCol);
      Vec expectedTestEnc = dvec(0, 1, .5, .667, 0); // == (0/3, 1/1, 1/2, 4/6, 0/3), unseen "d' encoded like a NA this time (None strategy)
      assertVecEquals(expectedTestEnc, testEncodedCol, 1.e-3);

      Frame testPredictions = teModel.score(test);
      Scope.track(testPredictions);
      assertVecEquals(expectedTestEnc, testPredictions.vec("categorical_YES_te"), 1e-3);
      
      // with LOO strategy, transformTraining applies to test as if it were a training frame, requiring and taking target into account
      Frame testEncodedAsTrain = teModel.transformTraining(test);
      Scope.track(testEncodedAsTrain);
      printOutFrameAsTable(testEncodedAsTrain);
      Vec testEncodedAsTrainCol = testEncodedAsTrain.vec("categorical_YES_te");
      Assert.assertNotNull(testEncodedAsTrainCol);
      Vec expectedTestEncAsTrain = dvec(.5, .5, 0, .6, -.5); // == (prior, 1/0=prior, 0/1, 3/5, -1/2), unseen "d' encodedCol using prior (inconsistent with None strategy)
      assertVecEquals(expectedTestEncAsTrain, testEncodedAsTrainCol, 1e-3);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_encodings_with_multiclass_target_using_KFold_strategy() {
    try {
      Scope.enter();
      final Frame fr = new TestFrameBuilder()
              .withColNames("categorical", "target", "foldc")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar( "a",   "a",    null,   "b",   "a",    null,  "b",   "a", null,   "c",     "a",   "a"))
              .withDataForCol(1, ar("NO", "YES", "MAYBE", "YES", "YES", "MAYBE", "NO", "YES", "NO", "YES", "MAYBE", "YES"))
              .withDataForCol(2, ar(   0,     1,       0,     1,     0,       1,    0,     1,    0,     1,        0,    1))
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
      
      Vec expectedCatColumn = vec(ar("a", "b", "c", "categorical_NA"),  // 4 entries per category (one for each target class+NA)
           // M  N  Y na   
              0, 0, 0, 0,  //a oof 0
              1, 1, 1, 1,  //b oof 0
              2, 2, 2, 2,  //c oof 0
              3, 3, 3, 3,  //NA oof 0
              0, 0, 0, 0,  //a oof 1
              1, 1, 1, 1,  //b oof 1
              3, 3, 3, 3   //NA oof 1
      ); 
      Vec expectedTargetClassColumn = vec(ar("MAYBE", "NO", "YES", "missing(NA)"), // 2 entries per target class, one for each category (from the categorical column)
              0, 1, 2, 3,
              0, 1, 2, 3,
              0, 1, 2, 3,
              0, 1, 2, 3,
              0, 1, 2, 3,
              0, 1, 2, 3,
              0, 1, 2, 3);
      Vec expectedNumColumn = vec( // for multinomial, numerator should correspond to the sum of matching target occurrences for each category
             0, 0, 3, 0,
              0, 0, 1, 0,
              0, 0, 1, 0,
              1, 0, 0, 0,
              1, 1, 1, 0,
              0, 1, 0, 0,
              1, 1, 0, 0);
      Vec expectedDenColumn = vec( // for multinomial, denominator should correspond to the sum of all occurrences for each category
              3, 3, 3, 3,
              1, 1, 1, 1,
              1, 1, 1, 1,
              1, 1, 1, 1,
              3, 3, 3, 3,
              1, 1, 1, 1,
              2, 2, 2, 2);
      Vec expectedFoldColumn = vec(
              0, 0, 0, 0, 
              0, 0, 0, 0,
              0, 0, 0, 0,
              0, 0, 0, 0,
              1, 1, 1, 1, 
              1, 1, 1, 1,
              1, 1, 1, 1);

      assertCatVecEquals(expectedCatColumn, encodings.vec("categorical"));
      assertVecEquals(expectedTargetClassColumn, encodings.vec(TARGETCLASS_COL), 0);
      assertVecEquals(expectedNumColumn, encodings.vec(NUMERATOR_COL), 0);
      assertVecEquals(expectedDenColumn, encodings.vec(DENOMINATOR_COL), 0);
      assertVecEquals(expectedFoldColumn, encodings.vec("foldc"), 0);

      Frame trainEncoded = teModel.transformTraining(fr);
      Scope.track(trainEncoded);
      Assert.assertEquals(5, trainEncoded.numCols()); // 3 original cols + 2 (=3-1) enc columns for the categorical col (NA target currently ignored)
      printOutFrameAsTable(trainEncoded);
      
      Vec trainNOEncCol = trainEncoded.vec("categorical_NO_te");
      Assert.assertNotNull(trainNOEncCol);
      double priorMeanNO = computePriorMean(encodings, 1);
      assertEquals(0.25, priorMeanNO, 1e-3); // == 3/12
      Vec expectedNOEncCol = dvec(0, .333, 0, 1, 0, .5, 0, .333, 0, .25, 0, .333); // == (0/3, 1/3, 0/1, 1/1, 0/3, 1/2, 0/1, 1/3, 0/1, unseen_in_f1=prior, 0/3, 1/3)
      assertVecEquals(expectedNOEncCol, trainNOEncCol, 1e-3);

      Vec trainYESEncCol = trainEncoded.vec("categorical_YES_te");
      Assert.assertNotNull(trainYESEncCol);
      double priorMeanYES = computePriorMean(encodings, 2);
      assertEquals(0.5, priorMeanYES, 1e-3); // == 6/12
      Vec expecteYESEncCol = dvec(1, .333, 0, 0, 1, 0, 1, .333, 0, .5, 1, .333); // == (3/3, 1/3, 0/1, 0/1, 3/3, 0/2, 1/1, 1/3, 0/1, unseen_in_f1=prior, 3/3, 1/3)
      assertVecEquals(expecteYESEncCol, trainYESEncCol, 1e-3);
      
      final Frame test = new TestFrameBuilder()
              .withColNames("categorical", "foldc")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("d", "c", "b", "a", null))
              .withDataForCol(1, ar(  0,   0,   0,   0,    0))
              .build();
      Frame testEncoded = teModel.transform(test); //KFold should not be applied (fold column ignored, all folds being merged/summed)
      Scope.track(testEncoded);
      Vec testEncodedCol = testEncoded.vec("categorical_YES_te");
      Assert.assertNotNull(testEncodedCol);
      Vec expectedTestEnc = dvec(0, 1, .5, .667, 0); // == (0/3, 1/1, 1/2, 4/6, 0/3), unseen "d' encoded like a NA this time (None strategy)
      assertVecEquals(expectedTestEnc, testEncodedCol, 1.e-3);

      Frame testPredictions = teModel.score(test);
      Scope.track(testPredictions);
      assertVecEquals(expectedTestEnc, testPredictions.vec("categorical_YES_te"), 1e-3);

      // with Kfold strategy, transformTraining applies to test as if it were a training frame, requiring and taking fold column into account
      Frame testEncodedAsTrain = teModel.transformTraining(test);
      Scope.track(testEncodedAsTrain);
      Vec testEncodedAsTrainCol = testEncodedAsTrain.vec("categorical_YES_te");
      Assert.assertNotNull(testEncodedAsTrainCol);
      Vec expectedTestEncAsTrain = dvec(.5, 1, 1, 1, 0); // == (prior, 1/1, 1/1, 3/3, 0/1), unseen "d' encoded using prior (inconsistent with None strategy)
      assertVecEquals(expectedTestEncAsTrain, testEncodedAsTrainCol, 1e-3);
    } finally {
      Scope.exit();
    }
  }

}
