package ai.h2o.automl.targetencoding;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.util.FrameUtils;

import java.io.File;
import java.util.Map;
import java.util.UUID;

// Multi-class target encoding is not fully supported yet
public class TargetEncodingMultiClassTargetTest extends TestUtil {


  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  private Frame fr = null;

  @Ignore
  @Test
  public void targetEncoderKFoldHoldoutApplyingWithMulticlassTargetColumnTest() {
    String teColumnName = "ColA";
    String targetColumnName = "ColB";
    String foldColumnName = "fold_column";
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames(teColumnName, targetColumnName, foldColumnName)
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "b", "b", "a"))
            .withDataForCol(1, ar(1, 2, 3, 4, 5))
            .withDataForCol(2, ar(1, 2, 2, 3, 2))
            .build();

    String[] teColumns = {teColumnName};
    TargetEncoder tec = new TargetEncoder(teColumns);

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, targetColumnName, foldColumnName);

    Frame resultWithEncoding = tec.applyTargetEncoding(fr, targetColumnName, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.KFold, foldColumnName, false, 0, false, 1234, true);

    Vec expected = dvec(5.0, 1.0, 4.0, 4.0, 2.5);
    assertVecEquals(expected, resultWithEncoding.vec("ColA_te"), 1e-5);

    expected.remove();
    encodingMapCleanUp(targetEncodingMap);
    resultWithEncoding.delete();
  }


  //Test that we can do sum and count on multi-class categorical column due to numerical representation under the hood.
  // Although we can sum and count on target column, that is not what we need for computing encodings for multiclass case. We need one-against-other approach.
  @Ignore
  @Test
  public void groupThenAggregateWithoutFoldsForMultiClassTargetTest() {
    String tmpName = null;
    Frame parsedFrame = null;
    String targetColumnName = "ColA";
    try {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames(targetColumnName, "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("NO", "YES", "MAYBE"))
              .withDataForCol(1, ar("a", "a", "b"))
              .build();

      tmpName = UUID.randomUUID().toString();
      Frame.export(fr, tmpName, fr._key.toString(), true, 1);

      try {
        Thread.sleep(1000); // TODO why we need to wait? otherwise `parsedFrame` is empty.
      } catch(InterruptedException ex) {

      }
      parsedFrame = parse_test_file(Key.make("parsed"), tmpName, true);

      printOutColumnsMeta(parsedFrame);
      asFactor(parsedFrame, targetColumnName);

      String[] teColumns = {"ColB"};
      TargetEncoder tec = new TargetEncoder(teColumns);

      Frame res = tec.groupThenAggregateForNumeratorAndDenominator(parsedFrame, teColumns[0], null, 0);

      Vec expectedSumColumn = vec(0, 1, 0);
      Vec expectedCountColumn = vec(1, 1, 1);

      assertVecEquals(expectedSumColumn, res.vec(1), 1e-5);
      assertVecEquals(expectedCountColumn, res.vec(2), 1e-5);

      expectedSumColumn.remove();
      expectedCountColumn.remove();
      res.delete();

    } finally {
      new File(tmpName).delete();
      fr.delete();
      parsedFrame.delete();
    }
  }

  @After
  public void afterEach() {
    if (fr != null) fr.delete();
  }

  private void printOutColumnsMeta(Frame fr) {
    for (String header : fr.toTwoDimTable().getColHeaders()) {
      String type = fr.vec(header).get_type_str();
      int cardinality = fr.vec(header).cardinality();
      System.out.println(header + " - " + type + String.format("; Cardinality = %d", cardinality));

    }
  }
  private void encodingMapCleanUp(Map<String, Frame> encodingMap) {
    for (Map.Entry<String, Frame> map : encodingMap.entrySet()) {
      map.getValue().delete();
    }
  }
}
