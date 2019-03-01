package ai.h2o.automl.targetencoding;

import hex.Model;
import hex.ModelBuilder;
import hex.splitframe.ShuffleSplitFrame;
import hex.tree.gbm.GBMModel;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.*;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.concat;
import static org.junit.Assert.*;

public class TargetEncodingLeaveOneOutStrategyTest extends TestUtil {


  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  private Frame fr = null;

  // In case of LOO holdout we subtract target value of the current row from aggregated values per group.
  // This is where we can end up with 0 in denominator column.
  @Ignore // TODO see PUBDEV-5941 regarding chunk layout
  @Test
  public void calculateAndAppendBlendedTEEncodingDivisionByZeroTest() {
    String tmpName = null;
    Frame reimportedFrame = null;

    String teColumnName = "ColA";
    String targetColumnName = "ColB";
    Map<String, Frame> targetEncodingMap = null;
    Frame result = null;
    try {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames(teColumnName, targetColumnName, "numerator", "denominator")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "a"))
              .withDataForCol(1, ar("yes", "no", "yes"))
              .withDataForCol(2, ar(2, 0, 2))
              .withDataForCol(3, ar(2, 0, 2))  // For b row we set denominator to 0
              .withChunkLayout(1, 2) // TODO see PUBDEV-5941 regarding chunk layout
              .build();

      tmpName = UUID.randomUUID().toString();
      Job export = Frame.export(fr, tmpName, fr._key.toString(), true, 1);
      export.get();

      reimportedFrame = parse_test_file(Key.make("parsed"), tmpName, true);
      printOutFrameAsTable(reimportedFrame);

      String[] teColumns = {teColumnName};
      TargetEncoder tec = new TargetEncoder(teColumns);
      targetEncodingMap = tec.prepareEncodingMap(reimportedFrame, targetColumnName, null);

      result = tec.calculateAndAppendBlendedTEEncoding(reimportedFrame, targetEncodingMap.get(teColumnName), targetColumnName, "targetEncoded");

      double globalMean = 2.0 / 3;

      printOutFrameAsTable(result);
      assertEquals(globalMean, result.vec(4).at(1), 1e-5);
      assertFalse(result.vec(2).isNA(1));

    } finally {
      encodingMapCleanUp(targetEncodingMap);
      result.delete();
      reimportedFrame.delete();
      new File(tmpName).delete();
    }

  }

  @Test // TODO see PUBDEV-5941 regarding chunk layout
  public void deletionDependsOnTheChunkLayoutTest() {

    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA")
            .withVecTypes(Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", "a"))
            .withChunkLayout(1,2)  // fails if we set one single chunk `.withChunkLayout(3)`
            .build();

    Vec zeroVec = Vec.makeZero(fr.numRows());
    String nameOfAddedColumn = "someName";

    fr.add(nameOfAddedColumn, zeroVec);

    zeroVec.remove();

    fr.vec(nameOfAddedColumn).at(1);
  }

  @Test
  public void targetEncoderLOOHoldoutDivisionByZeroTest() {
    String teColumnName = "ColA";
    String targetColumnName = "ColC";
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames(teColumnName, "ColB", targetColumnName)
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", "c", "d", "b", "a"))
            .withDataForCol(1, ard(1, 1, 4, 7, 5, 4))
            .withDataForCol(2, ar("2", "6", "6", "2", "6", "6"))
            .build();

    String[] teColumns = {teColumnName};
    TargetEncoder tec = new TargetEncoder(teColumns);

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, targetColumnName, null);

    Frame resultWithEncoding = tec.applyTargetEncoding(fr, targetColumnName, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, false,0.0, false, 1234);

    printOutFrameAsTable(resultWithEncoding);
    // For level `c` and `d` we got only one row... so after leave one out subtraction we get `0` for denominator. We need to use different formula(value) for the result.
    assertEquals(0.66666, resultWithEncoding.vec("ColA_te").at(2), 1e-5);
    assertEquals(0.66666, resultWithEncoding.vec("ColA_te").at(3), 1e-5);

    encodingMapCleanUp(targetEncodingMap);
    resultWithEncoding.delete();
  }

  @Test
  public void naValuesWithLOOStrategyTest() {
    String teColumnName = "ColA";
    String targetColumnName = "ColB";
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames(teColumnName, targetColumnName)
            .withVecTypes(Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", null, null, null))
            .withDataForCol(1, ar("2", "6", "6", "2", "6"))
            .build();

    String[] teColumns = {teColumnName};
    TargetEncoder tec = new TargetEncoder(teColumns);

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, targetColumnName, null);

    Frame resultWithEncodings = tec.applyTargetEncoding(fr, targetColumnName, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, false,0.0, true, 1234);

    Vec expected = dvec(0.6, 0.6, 0.5, 1, 0.5);
    printOutFrameAsTable(resultWithEncodings);
    assertVecEquals(expected, resultWithEncodings.vec("ColA_te"), 1e-5);

    expected.remove();
    encodingMapCleanUp(targetEncodingMap);
    resultWithEncodings.delete();
  }

  @Test
  public void emptyStringsAndNAsAreTreatedAsDifferentCategoriesTest() {
    String teColumnName = "ColA";
    String targetColumnName = "ColB";
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames(teColumnName, targetColumnName)
            .withVecTypes(Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", "", "", null)) // null and "" are different categories even though they look the same in printout
            .withDataForCol(1, ar("2", "6", "6", "2", "6"))
            .build();

    String[] teColumns = {teColumnName};
    TargetEncoder tec = new TargetEncoder(teColumns);

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, targetColumnName, null);

    Frame resultWithEncoding = tec.applyTargetEncoding(fr, targetColumnName, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, false, 0.0, false, 1234);

    printOutFrameAsTable(resultWithEncoding);

    encodingMapCleanUp(targetEncodingMap);
    resultWithEncoding.delete();
  }

  // Test that NA and empty strings create same encoding. Imputed average is slightly different for some reason
  @Test
  public void comparisonBetweenNAsAndNonEmptyStringForLOOStrategyTest() {
    String teColumnName = "ColA";
    String targetColumnName = "ColB";
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames(teColumnName, targetColumnName)
            .withVecTypes(Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", null, null, null))
            .withDataForCol(1, ar("2", "6", "6", "2", "6"))
            .build();

    Frame fr2 = new TestFrameBuilder()
            .withName("testFrame2")
            .withColNames(teColumnName, targetColumnName)
            .withVecTypes(Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", "na", "na", "na"))
            .withDataForCol(1, ar("2", "6", "6", "2", "6"))
            .build();

    String[] teColumns = {teColumnName};
    TargetEncoder tec = new TargetEncoder(teColumns);

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, targetColumnName, null);

    Frame resultWithEncoding = tec.applyTargetEncoding(fr, targetColumnName, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, false, 0.0, true, 1234);

    Map<String, Frame> targetEncodingMap2 = tec.prepareEncodingMap(fr2, targetColumnName, null);

    Frame resultWithEncoding2 = tec.applyTargetEncoding(fr2, targetColumnName, targetEncodingMap2, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, false,0.0, true, 1234);

    Frame sortedResult = resultWithEncoding.sort(new int[]{2}, new int[]{2});
    Frame sortedResult2 = resultWithEncoding2.sort(new int[]{2}, new int[]{2});

    assertVecEquals(sortedResult.vec("ColA_te"), sortedResult2.vec("ColA_te"), 1e-5);

    encodingMapCleanUp(targetEncodingMap);
    encodingMapCleanUp(targetEncodingMap2);
    fr2.delete();
    sortedResult.delete();
    sortedResult2.delete();
    resultWithEncoding.delete();
    resultWithEncoding2.delete();
  }

  // Test that empty strings create same encodings as nonempty strings
  @Test
  public void comparisonBetweenEmptyStringAndNonEmptyStringForLOOStrategyTest() {
    String targetColumnName = "ColB";
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", targetColumnName)
            .withVecTypes(Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", "", "", ""))
            .withDataForCol(1, ar("2", "6", "2", "2", "6"))
            .build();

    Frame fr2 = new TestFrameBuilder()
            .withName("testFrame2")
            .withColNames("ColA", targetColumnName)
            .withVecTypes(Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", "na", "na", "na"))
            .withDataForCol(1, ar("2", "6", "2", "2", "6"))
            .build();

    BlendingParams params = new BlendingParams(20, 10);
    String[] teColumns = {"ColA"};
    TargetEncoder tec = new TargetEncoder(teColumns, params);

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, targetColumnName, null);

    Frame resultWithEncoding = tec.applyTargetEncoding(fr, targetColumnName, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, true,0.0, false, 1234);

    Map<String, Frame> targetEncodingMap2 = tec.prepareEncodingMap(fr2, targetColumnName, null);

    Frame resultWithEncoding2 = tec.applyTargetEncoding(fr2, targetColumnName, targetEncodingMap2, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, true,0.0, false,1234);

    printOutFrameAsTable(resultWithEncoding);
    printOutFrameAsTable(resultWithEncoding2);

    assertVecEquals(resultWithEncoding.vec("ColA_te"), resultWithEncoding2.vec("ColA_te"), 1e-5);

    encodingMapCleanUp(targetEncodingMap);
    encodingMapCleanUp(targetEncodingMap2);
    fr2.delete();
    resultWithEncoding.delete();
    resultWithEncoding2.delete();
  }

  @Test
  public void targetEncoderLOOHoldoutSubtractCurrentRowTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "numerator", "denominator", "target")
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", "b", "b", "a", "b"))
            .withDataForCol(1, ard(1, 1, 4, 7, 4, 2))
            .withDataForCol(2, ard(1, 1, 4, 7, 4, 6))
            .withDataForCol(3, ar("2", "6", "6", "6", "6", null))
            .build();

    String[] teColumns = {""};
    TargetEncoder tec = new TargetEncoder(teColumns);

    Frame res = tec.subtractTargetValueForLOO(fr, "target");

    // We check here that for  `target column = NA` we do not subtract anything and for other cases we subtract current row's target value
    Vec vecNotSubtracted = vec(1, 0, 3, 6, 3, 2);
    assertVecEquals(vecNotSubtracted, res.vec(1), 1e-5);
    Vec vecSubtracted = vec(0, 0, 3, 6, 3, 6);
    assertVecEquals(vecSubtracted, res.vec(2), 1e-5);

    vecNotSubtracted.remove();
    vecSubtracted.remove();
    res.delete();
  }

  @Test
  public void targetEncoderLOOHoldoutApplyingTest() {
    String targetColumn = "ColC";

    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", targetColumn)
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", "b", "b", "a"))
            .withDataForCol(1, ard(1, 1, 4, 7, 4))
            .withDataForCol(2, ar("2", "6", "6", "6", "6"))
            .build();

    String[] teColumns = {"ColA"};
    TargetEncoder tec = new TargetEncoder(teColumns);

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, targetColumn, null);

    printOutFrameAsTable(targetEncodingMap.get("ColA"));
    Frame resultWithEncoding = tec.applyTargetEncoding(fr, targetColumn, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, false, 0, false, 1234);

    Vec expected = vec(1, 1, 1, 1, 0);
    printOutFrameAsTable(resultWithEncoding, false, resultWithEncoding.numRows());
    assertVecEquals(expected, resultWithEncoding.vec(3), 1e-5);

    expected.remove();
    encodingMapCleanUp(targetEncodingMap);
    resultWithEncoding.delete();
  }

  @Test // Test if presence of the fold column breaks logic
  public void targetEncoderLOOHoldoutApplyingWithFoldColumnTest() {
    String targetColumn = "ColC";
    String foldColumnName = "fold_column";
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", targetColumn, foldColumnName)
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "b", "b", "a"))
            .withDataForCol(1, ard(1, 1, 4, 7, 4))
            .withDataForCol(2, ar("2", "6", "6", "6", "6"))
            .withDataForCol(3, ar(1, 2, 2, 3, 2))
            .build();

    String[] teColumns = {"ColA"};
    TargetEncoder tec = new TargetEncoder(teColumns);

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, targetColumn, foldColumnName);

    Frame resultWithEncoding = tec.applyTargetEncoding(fr, targetColumn, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, foldColumnName, false, 0, false, 1234);

    Vec expected = vec(1, 1, 1, 1, 0);
    assertVecEquals(expected, resultWithEncoding.vec(4), 1e-5);

    expected.remove();
    encodingMapCleanUp(targetEncodingMap);
    resultWithEncoding.delete();
  }

  @Test
  public void targetEncoderLOOApplyWithNoiseTest() {
    String targetColumn = "ColC";
    String foldColumnName = "fold_column";
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", targetColumn, foldColumnName)
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "b", "b", "a"))
            .withDataForCol(1, ard(1, 1, 4, 7, 4))
            .withDataForCol(2, ar("2", "6", "6", "6", "6"))
            .withDataForCol(3, ar(1, 2, 2, 3, 2))
            .build();

    String[] teColumns = {"ColA"};
    TargetEncoder tec = new TargetEncoder(teColumns);

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, targetColumn, foldColumnName);

    //If we do not pass noise_level as parameter then it will be calculated according to the type of target column. For categorical target column it defaults to 1e-2
    Frame resultWithEncoding = tec.applyTargetEncoding(fr, targetColumn, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, foldColumnName, false,true,1234);

    Vec expected = vec(1, 1, 1, 1, 0);
    double expectedDifferenceDueToNoise = 1e-2;
    assertVecEquals(expected, resultWithEncoding.vec(4), expectedDifferenceDueToNoise); // TODO is it ok that encoding contains negative values?

    expected.remove();
    encodingMapCleanUp(targetEncodingMap);
    resultWithEncoding.delete();
  }

  /**
   * Here as evaluator we are using GBM model with cross validation on training frame. AUC is expected to be consistent.
   */
  @Test
  public void targetEncoderLOOWithoutBlendingAndZeroNoiseTestd222() {
    String responseColumn = "IsDepDelayed";

    Frame fr = parse_test_file("./smalldata/airlines/AirlinesTrain.csv.zip");
    String[] teColumns = {"Origin", "Dest"};
    BlendingParams params = new BlendingParams(5, 1);

    TargetEncoder tec = new TargetEncoder(teColumns, params);
    printOutFrameAsTable(fr);
  }

  @Test
  public void targetEncoderLOOWithoutBlendingAndZeroNoise_WithAirlinesTest() {
    double lastResult = 0.0;
    int numberOfFailures = 0;
    for (int attemptNumber = 0; attemptNumber < 100; attemptNumber++) {


      String responseColumn = "IsDepDelayed";

      Frame fr = parse_test_file("./smalldata/airlines/AirlinesTrain.csv.zip");
//      long nr = fr.numRows();
//
//      Frame titanicFr = parse_test_file("./smalldata/gbm_test/titanic.csv").subframe(new String[]{"home.dest"});
//      fr.add(titanicFr);

      printOutColumnsMetadata(fr);
      printOutFrameAsTable(fr);

      Key[] keys = new Key[]{Key.<Frame>make("train_LOO"), Key.<Frame>make("test_LOO")};
      Frame[] splits = ShuffleSplitFrame.shuffleSplitFrame(fr, keys, new double[]{0.8, 0.2}, 42);
      Frame trainSplit = splits[0];
      Frame testSplit = splits[1];

      String[] columnNamesToEncode = {"Origin", "Dest"};
      TargetEncoder tec = new TargetEncoder(columnNamesToEncode);

      Map<String, Frame> encodingMap = tec.prepareEncodingMap(fr, responseColumn, null);

      long seedForNoise = 2345;
      byte leaveOneOutHoldout = TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut;
      Frame trainEncoded = tec.applyTargetEncoding(trainSplit, responseColumn, encodingMap, leaveOneOutHoldout, false, 0.0, true, seedForNoise);
      Frame testEncoded = tec.applyTargetEncoding(testSplit, responseColumn, encodingMap, leaveOneOutHoldout, false, 0.0, true, seedForNoise);

      GBMModel gbmModel = TargetEncodingTestFixtures.trainGBM(trainEncoded, responseColumn, columnNamesToEncode);

      gbmModel.score(testEncoded);

      hex.ModelMetricsBinomial mmb = hex.ModelMetricsBinomial.getFromDKV(gbmModel, testEncoded);

      if(lastResult == 0.0) {
        lastResult = mmb.auc();
        Frame.export(trainSplit, "auc_inconsistency_original_etalon.csv", trainSplit._key.toString(), true, 1).get();
        Frame.export(trainEncoded, "auc_inconsistency_etalon.csv", trainEncoded._key.toString(), true, 1).get();
        Frame.export(testEncoded, "auc_inconsistency_test_etalon.csv", testEncoded._key.toString(), true, 1).get();
        Frame.export(encodingMap.get("Dest"), "auc_inconsistency_encoding_etalon.csv", encodingMap.get("Dest")._key.toString(), true, 1).get();
      }
      else {
        try {
          assertEquals("Failed attempt number " + attemptNumber, lastResult, mmb.auc(), 1e-5);
        } catch (AssertionError ex) {
          Frame.export(trainSplit, "auc_inconsistency_original_badboy" + numberOfFailures +".csv", trainSplit._key.toString(), true, 1).get();
          Frame.export(trainEncoded, "auc_inconsistency_badboy" + numberOfFailures +".csv", trainEncoded._key.toString(), true, 1).get();
          Frame.export(testEncoded, "auc_inconsistency_test_badboy" + numberOfFailures + ".csv", testEncoded._key.toString(), true, 1).get();
          Frame.export(encodingMap.get("Dest"), "auc_inconsistency_encoding_badboy" + numberOfFailures + ".csv", encodingMap.get("Dest")._key.toString(), true, 1).get();
          numberOfFailures++;
          throw ex;
        }
      }
      fr.delete();
      encodingMapCleanUp(encodingMap);
    }
  }

  // Check after known issue PUBDEV-6319 is fixed
  /**
  * Here as evaluator we are using GBM model with cross validation on training frame. AUC is expected to be consistent.
  */
  @Test
  public void targetEncoderLOOWithoutBlendingAndZeroNoiseTest() {
    double lastResult = 0.0;
    int numberOfFailures = 0;
    for (int attemptNumber = 0; attemptNumber < 100; attemptNumber++) {
   

      String responseColumn = "survived";
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      asFactor(fr, responseColumn);

      printOutColumnsMetadata(fr);
      printOutFrameAsTable(fr, true, 20);
      long count = fr.vec("home.dest").naCnt();

      Key[] keys = new Key[]{Key.<Frame>make("train_LOO"), Key.<Frame>make("test_LOO")};
      Frame[] splits = ShuffleSplitFrame.shuffleSplitFrame(fr, keys, new double[]{0.8, 0.2}, 42);
      Frame trainSplit = splits[0];
      Frame testSplit = splits[1];

//      String[] columnNamesToEncode = {"cabin"};
      String[] columnNamesToEncode = {"cabin", "home.dest"};
      TargetEncoder tec = new TargetEncoder(columnNamesToEncode);

      Map<String, Frame> encodingMap = tec.prepareEncodingMap(fr, responseColumn, null);

      long seedForNoise = 2345;
      byte leaveOneOutHoldout = TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut;
      Frame trainEncoded = tec.applyTargetEncoding(trainSplit, responseColumn, encodingMap, leaveOneOutHoldout, false, 0.0, true, seedForNoise);
      Frame testEncoded = tec.applyTargetEncoding(testSplit, responseColumn, encodingMap, leaveOneOutHoldout, false, 0.0, true, seedForNoise);

      GBMModel gbmModel = TargetEncodingTestFixtures.trainGBM(trainEncoded, responseColumn, columnNamesToEncode);

      gbmModel.score(testEncoded);

      hex.ModelMetricsBinomial mmb = hex.ModelMetricsBinomial.getFromDKV(gbmModel, testEncoded);

      String encodingMapToExport = "home.dest";
      if(lastResult == 0.0) {
        lastResult = mmb.auc();
        Frame.export(trainSplit, "auc_inconsistency_original_etalon.csv", trainSplit._key.toString(), true, 1).get();
        Frame.export(trainEncoded, "auc_inconsistency_etalon.csv", trainEncoded._key.toString(), true, 1).get();
        Frame.export(testEncoded, "auc_inconsistency_test_etalon.csv", testEncoded._key.toString(), true, 1).get();
        Frame.export(encodingMap.get(encodingMapToExport), "auc_inconsistency_encoding_etalon.csv", encodingMap.get(encodingMapToExport)._key.toString(), true, 1).get();
      }
      else {
        try {
          assertEquals("Failed attempt number " + attemptNumber, lastResult, mmb.auc(), 1e-5);
        } catch (AssertionError ex) {
          Frame.export(trainSplit, "auc_inconsistency_original_badboy" + numberOfFailures +".csv", trainSplit._key.toString(), true, 1).get();
          Frame.export(trainEncoded, "auc_inconsistency_badboy" + numberOfFailures +".csv", trainEncoded._key.toString(), true, 1).get();
          Frame.export(testEncoded, "auc_inconsistency_test_badboy" + numberOfFailures + ".csv", testEncoded._key.toString(), true, 1).get();
          Frame.export(encodingMap.get(encodingMapToExport), "auc_inconsistency_encoding_badboy" + numberOfFailures + ".csv", encodingMap.get(encodingMapToExport)._key.toString(), true, 1).get();
//          throw ex;
          numberOfFailures++;
        }
      }
      fr.delete();
      encodingMapCleanUp(encodingMap);
    }
  }

  // known issue PUBDEV-6319
  @Test
  public void usingSameEncodingMapWillNotCauseInconsistencyInApplyingPhaseTest() {
    Frame etalonTrainFrame = null;
    int numberOfFailures = 0;

    String responseColumn = "survived";
    Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
    fr.remove(new String[]{"name", "ticket", "boat", "body", "pclass", "sex", "age", "sibsp", "parch", "fare", "cabin", "embarked"});
    asFactor(fr, responseColumn);

    Key[] keys = new Key[]{Key.<Frame>make("train_LOO"), Key.<Frame>make("test_LOO")};
    Frame[] splits = ShuffleSplitFrame.shuffleSplitFrame(fr, keys, new double[]{0.8, 0.2}, 42);
    Frame trainSplit = splits[0];

    String[] columnNamesToEncode = {/*"cabin", */"home.dest"};
    BlendingParams params  = new BlendingParams(3, 10);
    TargetEncoder tec = new TargetEncoder(columnNamesToEncode, params);

    String teColumnName = "home.dest";
    
    Map<String, Frame> encodingMap = tec.prepareEncodingMap(fr, responseColumn, null);
    
    for (int attemptNumber = 0; attemptNumber < 500; attemptNumber++) {

      // Making copies for current attempt
      Frame trainCopy = trainSplit.deepCopy(Key.make().toString());
      DKV.put(trainCopy);
      
      Frame encodingMapHomeDest = encodingMap.get(teColumnName).deepCopy(Key.make().toString());
      DKV.put(encodingMapHomeDest);
      Map<String, Frame> encodingMapCopy = new HashMap<>();
      encodingMapCopy.put(teColumnName, encodingMapHomeDest);

      // Applying encoding map with definitely the same encoding map
      long seedForNoise = 2345;
      byte leaveOneOutHoldout = TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut;
      Frame trainEncoded = tec.applyTargetEncoding(trainCopy, responseColumn, encodingMapCopy, leaveOneOutHoldout, false, 0.0, true, seedForNoise);

      
      if(etalonTrainFrame == null) {
        etalonTrainFrame = trainEncoded;
        Frame.export(etalonTrainFrame, "applied_encoding_etalon.csv", etalonTrainFrame._key.toString(), true, 1).get();
        Frame.export(encodingMapCopy.get(teColumnName), "encoding_map_etalon.csv", encodingMapCopy.get(teColumnName)._key.toString(), true, 1).get();
      }
      else {
        try {
          assertTrue("Failed attempt number " + attemptNumber, isBitIdentical(etalonTrainFrame, trainEncoded));
        } catch (AssertionError ex) {
          Frame.export(trainEncoded, "applied_encoding_badboy" + numberOfFailures +".csv", trainEncoded._key.toString(), true, 1).get();
          Frame.export(encodingMapCopy.get(teColumnName), "encoding_map_etalon_badboy" + numberOfFailures + ".csv", encodingMapCopy.get(teColumnName)._key.toString(), true, 1).get();
          numberOfFailures++;
          throw ex;
        }
//        trainEncoded.delete();
      }
      encodingMapCleanUp(encodingMapCopy);
    }
    fr.delete();
    encodingMapCleanUp(encodingMap);
  }
  
  //This test shows that we will get inconsistent encodings from `prepareEncodingMap` method due to the known issue PUBDEV-6319
  @Test
  public void applyingTheSameTEParamsResultsInTheSameEncodedFrameTest() throws InterruptedException{
    Frame etalonTrainFrame = null;
    int numberOfFailures = 0;
    for (int attemptNumber = 0; attemptNumber < 200; attemptNumber++) {
      
      String responseColumn = "survived";
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      fr.remove(new String[]{"name", "ticket", "boat", "body", "pclass", "sex", "age", "sibsp", "parch", "fare", "cabin", "embarked"});
      asFactor(fr, responseColumn);

      Key[] keys = new Key[]{Key.<Frame>make("train_LOO"), Key.<Frame>make("test_LOO")};
      Frame[] splits = ShuffleSplitFrame.shuffleSplitFrame(fr, keys, new double[]{0.8, 0.2}, 42);
      Frame trainSplit = splits[0];

      String[] columnNamesToEncode = {/*"cabin", */"home.dest"};
      BlendingParams params  = new BlendingParams(3, 10);
      TargetEncoder tec = new TargetEncoder(columnNamesToEncode, params);

      Map<String, Frame> encodingMap = tec.prepareEncodingMap(fr, responseColumn, null);

      long seedForNoise = 2345;
      byte leaveOneOutHoldout = TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut;
      Frame trainEncoded = tec.applyTargetEncoding(trainSplit, responseColumn, encodingMap, leaveOneOutHoldout, false, 0.0, true, seedForNoise);

      String encodingMapToExport = "home.dest";
      if(etalonTrainFrame == null) {
        etalonTrainFrame = trainEncoded;
        Frame.export(etalonTrainFrame, "applied_encoding_etalon.csv", etalonTrainFrame._key.toString(), true, 1).get();
        Frame.export(encodingMap.get(encodingMapToExport), "encoding_map_etalon.csv", encodingMap.get(encodingMapToExport)._key.toString(), true, 1).get();
      }
      else {
        try {
          assertTrue("Failed attempt number " + attemptNumber, isBitIdentical(etalonTrainFrame, trainEncoded));
        } catch (AssertionError ex) {
          Frame.export(trainEncoded, "applied_encoding_badboy" + numberOfFailures +".csv", trainEncoded._key.toString(), true, 1).get();
          Frame.export(encodingMap.get(encodingMapToExport), "encoding_map_etalon_badboy" + numberOfFailures + ".csv", encodingMap.get(encodingMapToExport)._key.toString(), true, 1).get();
          numberOfFailures++;
          throw ex;
        }
        trainEncoded.delete();
      }
      fr.delete();
      splits[0].delete();
      splits[1].delete();
      encodingMapCleanUp(encodingMap);
    }
  }

  /**
   * Not only LOO causes inconsistency. KFOLD also.
   */
  @Test
  public void targetEncoderLOOWithBlendingAndZeroNoiseCheckedOnModelBuilderTest() {
    double lastResult = 0.0;
    for (int attemptNumber = 0; attemptNumber < 100; attemptNumber++) {


      String responseColumn = "survived";
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      asFactor(fr, responseColumn);
      
      String foldColumnForTE = "fold_column_te";
      int nfolds = 5;
      addKFoldColumn(fr, foldColumnForTE, nfolds, 1234);

      Key[] keys = new Key[]{Key.<Frame>make("train_LOO"), Key.<Frame>make("test_LOO")};
      Frame[] splits = ShuffleSplitFrame.shuffleSplitFrame(fr, keys, new double[]{0.8, 0.2}, 42);
      Frame trainSplit = splits[0];
      Frame testSplit = splits[1];

      String[] columnNamesToEncode = {"cabin", "home.dest"};
      TargetEncoder tec = new TargetEncoder(columnNamesToEncode);

      Map<String, Frame> encodingMap = tec.prepareEncodingMap(fr, responseColumn, foldColumnForTE);

      long seedForNoise = 2345;
      long builderSeed = 3456;
      byte holdoutType = TargetEncoder.DataLeakageHandlingStrategy.KFold;
      
      Frame trainEncoded = tec.applyTargetEncoding(trainSplit, responseColumn, encodingMap, holdoutType, foldColumnForTE, true, 0.01, true, seedForNoise);
      Frame testEncoded = tec.applyTargetEncoding(testSplit, responseColumn, encodingMap, holdoutType, foldColumnForTE, true, 0.0, true, seedForNoise);

      ModelBuilder modelBuilder = TargetEncodingTestFixtures.modelBuilderWithValidFrameFixture(trainEncoded, responseColumn, builderSeed);
      modelBuilder.init(false);
      
      modelBuilder._parms._ignored_columns = concat(columnNamesToEncode, new String[] {foldColumnForTE});

      Keyed model = modelBuilder.trainModel().get();
      Model retrievedModel = DKV.getGet(model._key);
      retrievedModel.score(testEncoded);
      hex.ModelMetricsBinomial mmb = hex.ModelMetricsBinomial.getFromDKV(retrievedModel, testEncoded);

      if(lastResult == 0.0) lastResult = mmb.auc();
      else {
        assertEquals("Failed attempt number " + attemptNumber, lastResult, mmb.auc(), 1e-5);
      }
      fr.delete();
      encodingMapCleanUp(encodingMap);
    }
  }
  
  /**
   * Here as evaluator we are using GBM model with validation on modelBuilder
   */
  @Test
  public void targetEncoderLOOWithoutBlendingAndZeroNoiseCheckedOnModelBuilderTest() {
    double lastResult = 0.0;
    for (int attemptNumber = 0; attemptNumber < 100; attemptNumber++) {


      String responseColumn = "survived";
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      asFactor(fr, responseColumn);

      Key[] keys = new Key[]{Key.<Frame>make("train_LOO"), Key.<Frame>make("test_LOO")};
      Frame[] splits = ShuffleSplitFrame.shuffleSplitFrame(fr, keys, new double[]{0.8, 0.2}, 42);
      Frame trainSplit = splits[0];
      Frame testSplit = splits[1];

      String[] columnNamesToEncode = {"cabin", "home.dest"};
      TargetEncoder tec = new TargetEncoder(columnNamesToEncode);

      Map<String, Frame> encodingMap = tec.prepareEncodingMap(fr, responseColumn, null);

      long seedForNoise = 2345;
      long builderSeed = 3456;
      byte leaveOneOutHoldout = TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut;
      Frame trainEncoded = tec.applyTargetEncoding(trainSplit, responseColumn, encodingMap, leaveOneOutHoldout, false, 0.0, true, seedForNoise);
      Frame testEncoded = tec.applyTargetEncoding(testSplit, responseColumn, encodingMap, leaveOneOutHoldout, false, 0.0, true, seedForNoise);

      ModelBuilder modelBuilder = TargetEncodingTestFixtures.modelBuilderWithValidFrameFixture(trainEncoded, responseColumn, builderSeed);
      modelBuilder.init(false);
      modelBuilder._parms._ignored_columns = columnNamesToEncode;

      Keyed model = modelBuilder.trainModel().get();
      Model retrievedModel = DKV.getGet(model._key);
      retrievedModel.score(testEncoded);
      hex.ModelMetricsBinomial mmb = hex.ModelMetricsBinomial.getFromDKV(retrievedModel, testEncoded);
      
      if(lastResult == 0.0) lastResult = mmb.auc();
      else {
        assertEquals("Failed attempt number " + attemptNumber, lastResult, mmb.auc(), 1e-5);
      }
      fr.delete();
      encodingMapCleanUp(encodingMap);
    }
  }
  
  // It is consistent
  @Test
  public void withoutTEConsistencyTest() {
    double lastResult = 0.0;
    for (int attemptNumber = 0; attemptNumber < 300; attemptNumber++) {

      String responseColumn = "survived";
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      asFactor(fr, responseColumn);


      Key[] keys = new Key[]{Key.<Frame>make("train_LOO"), Key.<Frame>make("test_LOO")};
      Frame[] splits = ShuffleSplitFrame.shuffleSplitFrame(fr, keys, new double[]{0.8, 0.2}, 42);
      Frame trainSplit = splits[0];
      Frame testSplit = splits[1];

      long builderSeed = 3456;

      // TODO how come we are able to train model without validation frame if we don't use CV?
      ModelBuilder modelBuilder = TargetEncodingTestFixtures.modelBuilderWithValidFrameFixture(trainSplit, responseColumn, builderSeed);
      modelBuilder.init(false);

      Keyed model = modelBuilder.trainModel().get();
      Model retrievedModel = DKV.getGet(model._key);
      retrievedModel.score(testSplit);
      hex.ModelMetricsBinomial mmb = hex.ModelMetricsBinomial.getFromDKV(retrievedModel, testSplit);

      if(lastResult == 0.0) lastResult = mmb.auc();
      else {
        assertEquals("Failed attempt number " + attemptNumber, lastResult, mmb.auc(), 1e-5);
      }
      fr.delete();
    }
  }

  /**
   * Here as evaluator we are using GBM model with validation on validation_frame. 
   */
  @Test
  public void targetEncoderLOOWithoutBlendingAndZeroNoiseCheckedOnValidationFrameTest() {
  }

  // ------------------------ Multiple columns for target encoding -------------------------------------------------//

  @Test
  public void LOOHoldoutMultipleTEColumnsWithFoldColumnTest() {
    String targetColumnName = "ColC";
    String foldColumnName = "fold_column";
    TestFrameBuilder frameBuilder = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", targetColumnName, foldColumnName)
            .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "b", "b", "a"))
            .withDataForCol(1, ar("d", "e", "d", "e", "e"))
            .withDataForCol(2, ar("2", "6", "6", "6", "6"))
            .withDataForCol(3, ar(1, 2, 2, 3, 2));

    fr = frameBuilder.withName("testFrame").build();

    String[] teColumns = {"ColA", "ColB"};
    TargetEncoder tec = new TargetEncoder(teColumns);

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, targetColumnName, foldColumnName);

    Frame resultWithEncoding = tec.applyTargetEncoding(fr, targetColumnName, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, foldColumnName,  false,0.0, false, 1234);
    Vec encodingForColumnA_Multiple = resultWithEncoding.vec(4);
    Vec encodingForColumnB_Multiple = resultWithEncoding.vec(5);

    // Let's check it with Single TE version of the algorithm. So we rely here on a correctness of the single-column encoding.
    //  For the first encoded column
    Frame frA = frameBuilder.withName("testFrameA").build();

    String[] indexForColumnA = {"ColA"};
    TargetEncoder tecA = new TargetEncoder(indexForColumnA);
    Map<String, Frame> targetEncodingMapForColumn1 = tecA.prepareEncodingMap(frA, targetColumnName, foldColumnName);
    Frame resultWithEncodingForColumn1 = tecA.applyTargetEncoding(frA, targetColumnName, targetEncodingMapForColumn1, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, foldColumnName, false, 0, false, 1234);
    Vec encodingForColumnA_Single = resultWithEncodingForColumn1.vec(4);

    assertVecEquals(encodingForColumnA_Single, encodingForColumnA_Multiple, 1e-5);

    // For the second encoded column
    Frame frB = frameBuilder.withName("testFrameB").build();

    String[] indexForColumnB = {"ColB"};
    TargetEncoder tecB = new TargetEncoder(indexForColumnB);
    Map<String, Frame> targetEncodingMapForColumn2 = tecB.prepareEncodingMap(frB, targetColumnName, foldColumnName);
    Frame resultWithEncodingForColumn2 = tecB.applyTargetEncoding(frB, targetColumnName, targetEncodingMapForColumn2, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, foldColumnName, false, 0, false, 1234);
    Vec encodingForColumnB_Single = resultWithEncodingForColumn2.vec(4);

    assertVecEquals(encodingForColumnB_Single, encodingForColumnB_Multiple, 1e-5);

    resultWithEncoding.delete();
    resultWithEncodingForColumn1.delete();
    encodingMapCleanUp(targetEncodingMap);
    encodingMapCleanUp(targetEncodingMapForColumn1);
    encodingMapCleanUp(targetEncodingMapForColumn2);
    frA.delete();
    frB.delete();
    resultWithEncodingForColumn2.delete();
  }

  @After
  public void afterEach() {
    if (fr != null) fr.delete();
  }

  private void encodingMapCleanUp(Map<String, Frame> encodingMap) {
    for (Map.Entry<String, Frame> map : encodingMap.entrySet()) {
      map.getValue().delete();
    }
  }
}
