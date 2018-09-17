package ai.h2o.automl.targetencoding;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.util.TwoDimTable;
import ai.h2o.automl.TestUtil;

import java.util.Map;

public class TargetEncodingNoneStrategyTest extends TestUtil {


  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  private Frame fr = null;

  @Test
  public void targetEncoderNoneHoldoutApplyingTest() {
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

    Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, 3, false, 0, 1234, true);

    Vec vec = resultWithEncoding.vec(4);
    Vec expected = vec(0.5, 0.5, 1, 1, 1);
    assertVecEquals(expected, vec, 1e-5);

    expected.remove();
    encodingMapCleanUp(targetEncodingMap);
    resultWithEncoding.delete();
  }

  @Test
  public void holdoutTypeNoneApplyWithNoiseTest() {
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

    printOutFrameAsTable(targetEncodingMap.get("ColA"));
    //If we do not pass noise_level as parameter then it will be calculated according to the type of target column. For categorical target column it defaults to 1e-2
    Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, 3, false, 1234, true);

    printOutFrameAsTable(resultWithEncoding);
    double expectedDifferenceDueToNoise = 1e-2;
    Vec vec = resultWithEncoding.vec(4);

    Vec expected = vec(0.5, 0.5, 1, 1, 1);
    assertVecEquals(expected, vec, expectedDifferenceDueToNoise);

    expected.remove();
    encodingMapCleanUp(targetEncodingMap);
    resultWithEncoding.delete();
  }


  // ------------------------ Multiple columns for target encoding -------------------------------------------------//

  @Test
  public void NoneHoldoutMultipleTEColumnsWithFoldColumnTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", "ColC", "fold_column")
            .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "b", "b", "a"))
            .withDataForCol(1, ar("d", "e", "d", "e", "e"))
            .withDataForCol(2, ar("2", "6", "6", "6", "6"))
            .withDataForCol(3, ar(1, 2, 2, 3, 2))
            .build();

    TargetEncoder tec = new TargetEncoder();
    int[] teColumns = {0, 1};

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, teColumns, 2, 3);

    Frame resultWithEncoding = tec.applyTargetEncoding(fr, teColumns, 2, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, 3, false, 0, 1234, true);

    Vec expected = vec(0.5, 1, 0.5, 1, 1);
    assertVecEquals(expected, resultWithEncoding.vec(4), 1e-5);

    Vec expected2 = vec(0.5, 0.5, 1, 1, 1);
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
