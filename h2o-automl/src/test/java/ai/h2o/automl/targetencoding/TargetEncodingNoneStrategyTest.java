package ai.h2o.automl.targetencoding;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.util.TwoDimTable;

import java.util.Map;

public class TargetEncodingNoneStrategyTest extends TestUtil {


  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  private Frame fr = null;

  @Test
  public void targetEncoderNoneHoldoutApplyingTest() {
    String teColumnName = "ColA";
    String targetColumnName = "ColC";
    String foldColumn = "fold_column";
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames(teColumnName, "ColB", targetColumnName, foldColumn)
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "b", "b", "a"))
            .withDataForCol(1, ard(1, 1, 4, 7, 4))
            .withDataForCol(2, ar("2", "6", "6", "6", "6"))
            .withDataForCol(3, ar(1, 2, 2, 3, 2))
            .build();

    String[] teColumns = {teColumnName};
    TargetEncoder tec = new TargetEncoder(teColumns);

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, targetColumnName, foldColumn);

    Frame resultWithEncoding = tec.applyTargetEncoding(fr, targetColumnName, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumn, false, 0, false, 1234, true);

    Vec vec = resultWithEncoding.vec(4);
    Vec expected = dvec(0.5, 0.5, 1, 1, 1);
    assertVecEquals(expected, vec, 1e-5);

    expected.remove();
    encodingMapCleanUp(targetEncodingMap);
    resultWithEncoding.delete();
  }

  @Test
  public void holdoutTypeNoneApplyWithNoiseTest() {
    String teColumnName = "ColA";
    String targetColumnName = "ColC";
    String foldColumn = "fold_column";
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames(teColumnName, "ColB", targetColumnName, foldColumn)
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "b", "b", "a"))
            .withDataForCol(1, ard(1, 1, 4, 7, 4))
            .withDataForCol(2, ar("2", "6", "6", "6", "6"))
            .withDataForCol(3, ar(1, 2, 2, 3, 2))
            .build();

    String[] teColumns = {teColumnName};
    TargetEncoder tec = new TargetEncoder(teColumns);

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, targetColumnName, foldColumn);

    printOutFrameAsTable(targetEncodingMap.get(teColumnName));
    //If we do not pass noise_level as parameter then it will be calculated according to the type of target column. For categorical target column it defaults to 1e-2
    Frame resultWithEncoding = tec.applyTargetEncoding(fr, targetColumnName, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumn, false, false,1234, true);

    printOutFrameAsTable(resultWithEncoding);
    double expectedDifferenceDueToNoise = 1e-2;
    Vec vec = resultWithEncoding.vec(4);

    Vec expected = dvec(0.5, 0.5, 1, 1, 1);
    assertVecEquals(expected, vec, expectedDifferenceDueToNoise);

    expected.remove();
    encodingMapCleanUp(targetEncodingMap);
    resultWithEncoding.delete();
  }

  @Test
  public void endToEndTest() {
    String teColumnName = "ColA";
    String targetColumnName = "ColC";
    Frame training = new TestFrameBuilder()
            .withName("trainingFrame")
            .withColNames(teColumnName, targetColumnName)
            .withVecTypes(Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", "c", "d", "e", "b", "b"))
            .withDataForCol(1, ar("2", "6", "6", "6", "6", "2", "2"))
            .build();

    Frame holdout = new TestFrameBuilder()
            .withName("holdoutFrame")
            .withColNames(teColumnName, targetColumnName)
            .withVecTypes(Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", "b", "b", "a"))
            .withDataForCol(1, ar("2", "6", "6", "6", "6"))
            .build();

    String[] teColumns = {teColumnName};
    TargetEncoder tec = new TargetEncoder(teColumns);

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(holdout, targetColumnName, null);

    Frame resultWithEncoding = tec.applyTargetEncoding(training, targetColumnName, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, false, 0, false, 1234, true);

    printOutFrameAsTable(resultWithEncoding);

    Vec vec = resultWithEncoding.vec(2);
    Vec expected = dvec(0.8, 0.8, 0.8, 0.5, 1, 1, 1);
    assertVecEquals(expected, vec, 1e-5);

    training.delete();
    holdout.delete();
    expected.remove();
    encodingMapCleanUp(targetEncodingMap);
    resultWithEncoding.delete();
  }


  // ------------------------ Multiple columns for target encoding -------------------------------------------------//

  @Test
  public void NoneHoldoutMultipleTEColumnsWithFoldColumnTest() {
    String targetColumnName = "ColC";
    String foldColumn = "fold_column";
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", targetColumnName, foldColumn)
            .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "b", "b", "a"))
            .withDataForCol(1, ar("d", "e", "d", "e", "e"))
            .withDataForCol(2, ar("2", "6", "6", "6", "6"))
            .withDataForCol(3, ar(1, 2, 2, 3, 2))
            .build();

    String[] teColumns = {"ColA", "ColB"};
    TargetEncoder tec = new TargetEncoder(teColumns);

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, targetColumnName, foldColumn);

    Frame resultWithEncoding = tec.applyTargetEncoding(fr, targetColumnName, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumn, false, 0, false,1234, true);

    Vec expected = dvec(0.5, 1, 0.5, 1, 1);
    assertVecEquals(expected, resultWithEncoding.vec(4), 1e-5);

    Vec expected2 = dvec(0.5, 0.5, 1, 1, 1);
    assertVecEquals(expected2, resultWithEncoding.vec(5), 1e-5);

    expected.remove();
    expected2.remove();
    encodingMapCleanUp(targetEncodingMap);
    resultWithEncoding.delete();
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

  private void printOutFrameAsTable(Frame fr, boolean full, boolean rollups) {

    TwoDimTable twoDimTable = fr.toTwoDimTable(0, 10000, rollups);
    System.out.println(twoDimTable.toString(2, full));
  }

}
