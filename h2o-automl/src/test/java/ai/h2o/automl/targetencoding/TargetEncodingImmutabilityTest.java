package ai.h2o.automl.targetencoding;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.fvec.*;
import java.util.Map;

import static org.junit.Assert.*;

public class TargetEncodingImmutabilityTest extends TestUtil {


  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  private Frame fr = null;


  @Test
  public void deepCopyTest() {
    fr = new TestFrameBuilder()
            .withName("trainingFrame")
            .withColNames("colA")
            .withVecTypes(Vec.T_STR)
            .withDataForCol(0, ar("a", "b", "c"))
            .build();

    Frame trainCopy = fr.deepCopy(Key.make().toString());
    DKV.put(trainCopy);

    assertTrue(isBitIdentical(fr, trainCopy));

    trainCopy.vec(0).set(0, "d");

    assertFalse(isBitIdentical(fr, trainCopy));
    trainCopy.delete();
  }

  @Test
  public void ensureImmutabilityOfTheOriginalDatasetDuringApplicationOfTE() {
    String teColumnName = "ColA";
    String targetColumnName = "ColC";
    String foldColumn = "fold_column";
    Frame training = new TestFrameBuilder()
            .withName("trainingFrame")
            .withColNames(teColumnName, targetColumnName, foldColumn)
            .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "c", "d", "e", "b", "b"))
            .withDataForCol(1, ar("2", "6", "6", "6", "6", "2", "2"))
            .withDataForCol(2, ar(1, 2, 2, 3, 1, 2, 1))
            .build();

    String[] teColumns = {teColumnName};
    TargetEncoder tec = new TargetEncoder(teColumns);

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(training, targetColumnName, foldColumn);

    Frame trainCopy = training.deepCopy(Key.make().toString());
    DKV.put(trainCopy);

    Frame resultWithEncoding = tec.applyTargetEncoding(training, targetColumnName, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.KFold, foldColumn, false, 0, false, 1234, true);

    assertTrue(isBitIdentical(training, trainCopy));

    training.delete();
    trainCopy.delete();
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
}
