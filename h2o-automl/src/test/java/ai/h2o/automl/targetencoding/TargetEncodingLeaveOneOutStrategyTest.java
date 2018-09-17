package ai.h2o.automl.targetencoding;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.util.TwoDimTable;
import ai.h2o.automl.TestUtil;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class TargetEncodingLeaveOneOutStrategyTest extends TestUtil {


  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  private Frame fr = null;


  // In case of LOO holdout we subtract target value of the current row from aggregated values per group.
  // This is where we can end up with 0 in denominator column.
  @Test
  public void calculateAndAppendBlendedTEEncodingDivisionByZeroTest() {

    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", "numerator", "denominator")
            .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_NUM, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "a"))
            .withDataForCol(1, ar("yes", "no", "yes"))
            .withDataForCol(2, ar(2, 0, 2))
            .withDataForCol(3, ar(2, 0, 2))  // For b row we set denominator to 0
            .build();
    TargetEncoder tec = new TargetEncoder();
    int[] teColumns = {0};
    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 1);

    Frame result = tec.calculateAndAppendBlendedTEEncoding(fr, targetEncodingMap.get("ColA"), "ColB", "targetEncoded");

    double globalMean = 2.0 / 3;
    assertEquals(globalMean, result.vec(4).at(1), 1e-5);
    assertFalse(result.vec(2).isNA(1));

    encodingMapCleanUp(targetEncodingMap);
    result.delete();
  }

  @Test
  public void targetEncoderLOOHoldoutDivisionByZeroTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", "ColC")
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", "c", "d", "b", "a"))
            .withDataForCol(1, ard(1, 1, 4, 7, 5, 4))
            .withDataForCol(2, ar("2", "6", "6", "2", "6", "6"))
            .build();

    TargetEncoder tec = new TargetEncoder();
    int[] teColumns = {0};

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2);

    Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, false, 0.0, 1234, true);

    // For level `c` and `d` we got only one row... so after leave one out subtraction we get `0` for denominator. We need to use different formula(value) for the result.
    assertEquals(0.66666, resultWithEncoding.vec("ColA_te").at(4), 1e-5);
    assertEquals(0.66666, resultWithEncoding.vec("ColA_te").at(5), 1e-5);

    encodingMapCleanUp(targetEncodingMap);
    resultWithEncoding.delete();
  }

  @Test
  public void naValuesWithLOOStrategyTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", null, null, null))
            .withDataForCol(1, ar("2", "6", "6", "2", "6"))
            .build();

    TargetEncoder tec = new TargetEncoder();
    int[] teColumns = {0};

    printOutColumnsMeta(fr);

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 1);

    Frame resultWithEncodings = tec.applyTargetEncoding(fr, teColumns, 1, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, false, 0.0, 1234, true);

    printOutFrameAsTable(resultWithEncodings);
    Vec expected = vec(0.5, 1, 0.5, 0.6, 0.6);
    assertVecEquals(expected, resultWithEncodings.vec("ColA_te"), 1e-5);

    expected.remove();
    encodingMapCleanUp(targetEncodingMap);
    resultWithEncodings.delete();
  }

  @Test
  public void emptyStringsAndNAsAreTreatedAsDifferentCategoriesTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", "", "", null)) // null and "" are different categories even though they look the same in printout
            .withDataForCol(1, ar("2", "6", "6", "2", "6"))
            .build();

    TargetEncoder tec = new TargetEncoder();
    int[] teColumns = {0};

    printOutColumnsMeta(fr);

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 1);

    Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 1, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, false, 0.0, 1234, true);

    printOutFrameAsTable(resultWithEncoding);

    encodingMapCleanUp(targetEncodingMap);
    resultWithEncoding.delete();
  }

  // Test that NA and empty strings create same encoding. Imputed average is slightly different for some reason
  @Test
  public void comparisonBetweenNAsAndNonEmptyStringForLOOStrategyTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", null, null, null))
            .withDataForCol(1, ar("2", "6", "6", "2", "6"))
            .build();

    Frame fr2 = new TestFrameBuilder()
            .withName("testFrame2")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", "na", "na", "na"))
            .withDataForCol(1, ar("2", "6", "6", "2", "6"))
            .build();

    TargetEncoder tec = new TargetEncoder();
    int[] teColumns = {0};

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 1);

    Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 1, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, false, 0.0, 1234, true);

    Map<String, Frame> targetEncodingMap2 = tec.prepareEncodingMap(fr2, teColumns, 1);

    Frame resultWithEncoding2 = tec.applyTargetEncoding(fr2, teColumns, 1, targetEncodingMap2, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, false, 0.0, 1234, true);

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
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", "", "", ""))
            .withDataForCol(1, ar("2", "6", "2", "2", "6"))
            .build();

    Frame fr2 = new TestFrameBuilder()
            .withName("testFrame2")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", "na", "na", "na"))
            .withDataForCol(1, ar("2", "6", "2", "2", "6"))
            .build();

    BlendingParams params = new BlendingParams(20, 10);
    TargetEncoder tec = new TargetEncoder(params);
    int[] teColumns = {0};

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 1);

    Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 1, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, true, 0.0, 1234, true);

    Map<String, Frame> targetEncodingMap2 = tec.prepareEncodingMap(fr2, teColumns, 1);

    Frame resultWithEncoding2 = tec.applyTargetEncoding(fr2, teColumns, 1, targetEncodingMap2, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, true, 0.0, 1234, true);

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

    TargetEncoder tec = new TargetEncoder();

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
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", "ColC")
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", "b", "b", "a"))
            .withDataForCol(1, ard(1, 1, 4, 7, 4))
            .withDataForCol(2, ar("2", "6", "6", "6", "6"))
            .build();

    TargetEncoder tec = new TargetEncoder();
    int[] teColumns = {0};

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2);

    Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, false, 0, 1234, true);

    Vec expected = vec(1, 0, 1, 1, 1);
    assertVecEquals(expected, resultWithEncoding.vec(3), 1e-5);

    expected.remove();
    encodingMapCleanUp(targetEncodingMap);
    resultWithEncoding.delete();
  }

  @Test // Test if presence of the fold column breaks logic
  public void targetEncoderLOOHoldoutApplyingWithFoldColumnTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", "ColC", "fold_column")
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "b", "b", "a"))
            .withDataForCol(1, ard(1, 1, 4, 7, 4))
            .withDataForCol(2, ar("2", "6", "6", "6", "6"))
            .withDataForCol(3, ar(1, 2, 2, 3, 2))
            .build();

    TargetEncoder tec = new TargetEncoder();
    int[] teColumns = {0};

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

    Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, 3, false, 0, 1234, true);

    Vec expected = vec(1, 0, 1, 1, 1);
    assertVecEquals(expected, resultWithEncoding.vec(4), 1e-5);

    expected.remove();
    encodingMapCleanUp(targetEncodingMap);
    resultWithEncoding.delete();
  }

  @Test
  public void targetEncoderLOOApplyWithNoiseTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", "ColC", "fold_column")
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "b", "b", "a"))
            .withDataForCol(1, ard(1, 1, 4, 7, 4))
            .withDataForCol(2, ar("2", "6", "6", "6", "6"))
            .withDataForCol(3, ar(1, 2, 2, 3, 2))
            .build();

    TargetEncoder tec = new TargetEncoder();
    int[] teColumns = {0};

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

    //If we do not pass noise_level as parameter then it will be calculated according to the type of target column. For categorical target column it defaults to 1e-2
    Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, 3, false, 1234, true);

    Vec expected = vec(1, 0, 1, 1, 1);
    double expectedDifferenceDueToNoise = 1e-2;
    assertVecEquals(expected, resultWithEncoding.vec(4), expectedDifferenceDueToNoise); // TODO is it ok that encoding contains negative values?

    expected.remove();
    encodingMapCleanUp(targetEncodingMap);
    resultWithEncoding.delete();
  }


  // ------------------------ Multiple columns for target encoding -------------------------------------------------//

  @Test
  public void LOOHoldoutMultipleTEColumnsWithFoldColumnTest() {
    TestFrameBuilder frameBuilder = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", "ColC", "fold_column")
            .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "b", "b", "a"))
            .withDataForCol(1, ar("d", "e", "d", "e", "e"))
            .withDataForCol(2, ar("2", "6", "6", "6", "6"))
            .withDataForCol(3, ar(1, 2, 2, 3, 2));

    fr = frameBuilder.withName("testFrame").build();

    TargetEncoder tec = new TargetEncoder();
    int[] teColumns = {0, 1};

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

    Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, 3, false, 0, 1234, true);
    Frame sortedBy1 = resultWithEncoding.sort(new int[]{1});
    Vec encodingForColumnA_Multiple = sortedBy1.vec(4);
    Frame sortedBy0 = resultWithEncoding.sort(new int[]{0});
    Vec encodingForColumnB_Multiple = sortedBy0.vec(5);

    // Let's check it with Single TE version of the algorithm. So we rely here on a correctness of the single-column encoding.
    //  For the first encoded column
    Frame frA = frameBuilder.withName("testFrameA").build();

    int[] indexForColumnA = Arrays.copyOfRange(teColumns, 0, 1);
    Map<String, Frame> targetEncodingMapForColumn1 = tec.prepareEncodingMap(frA, indexForColumnA, 2, 3);
    Frame resultWithEncodingForColumn1 = tec.applyTargetEncoding(frA, indexForColumnA, 2, targetEncodingMapForColumn1, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, 3, false, 0, 1234, true);
    Frame sortedSingleColumn1ByColA = resultWithEncodingForColumn1.sort(new int[]{0});
    Vec encodingForColumnA_Single = sortedSingleColumn1ByColA.vec(4);

    assertVecEquals(encodingForColumnA_Single, encodingForColumnA_Multiple, 1e-5);

    // For the second encoded column
    Frame frB = frameBuilder.withName("testFrameB").build();

    int[] indexForColumnB = Arrays.copyOfRange(teColumns, 1, 2);
    Map<String, Frame> targetEncodingMapForColumn2 = tec.prepareEncodingMap(frB, indexForColumnB, 2, 3);
    Frame resultWithEncodingForColumn2 = tec.applyTargetEncoding(frB, indexForColumnB, 2, targetEncodingMapForColumn2, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, 3, false, 0, 1234, true);
    Frame sortedSingleColumn2ByColA = resultWithEncodingForColumn2.sort(new int[]{0});
    Vec encodingForColumnB_Single = sortedSingleColumn2ByColA.vec(4);

    assertVecEquals(encodingForColumnB_Single, encodingForColumnB_Multiple, 1e-5);

    sortedBy0.delete();
    sortedBy1.delete();
    sortedSingleColumn1ByColA.delete();
    sortedSingleColumn2ByColA.delete();
    encodingMapCleanUp(targetEncodingMap);
    encodingMapCleanUp(targetEncodingMapForColumn1);
    encodingMapCleanUp(targetEncodingMapForColumn2);
    frA.delete();
    frB.delete();
    resultWithEncoding.delete();
    resultWithEncodingForColumn1.delete();
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

  private void printOutFrameAsTable(Frame fr) {

    TwoDimTable twoDimTable = fr.toTwoDimTable();
    System.out.println(twoDimTable.toString(2, false));
  }


  private void printOutColumnsMeta(Frame fr) {
    for (String header : fr.toTwoDimTable().getColHeaders()) {
      String type = fr.vec(header).get_type_str();
      int cardinality = fr.vec(header).cardinality();
      System.out.println(header + " - " + type + String.format("; Cardinality = %d", cardinality));

    }
  }
}
