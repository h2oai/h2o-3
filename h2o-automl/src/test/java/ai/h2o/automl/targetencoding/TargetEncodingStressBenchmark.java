package ai.h2o.automl.targetencoding;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.util.Map;
import java.util.Random;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;

public class TargetEncodingStressBenchmark extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(2);
  }

  @Test
  public void syntheticBigDataWithHighCardinalityCategoricalColumnTest() {

    Map<String, Frame> encodingMap = null;

    Scope.enter();
    long seed = new Random().nextLong();
    try {
      int size = 10000000;
      int sizePerChunk = size / 5;
      String[] arr = new String[size];
      for (int a = 0; a < size; a++) {
        arr[a] = Integer.toString(new Random().nextInt(size / 2));
      }
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "y")
              .withVecTypes(Vec.T_STR, Vec.T_NUM)
              .withDataForCol(0, arr)
              .withRandomIntDataForCol(1, size, 0, 2, seed)
              .withChunkLayout(sizePerChunk, sizePerChunk, sizePerChunk, sizePerChunk, sizePerChunk)
              .build();

      asFactor(fr, "ColA");
      asFactor(fr, "y");

      int cardinality = fr.vec("ColA").cardinality();
      System.out.println("Cardinality of the categorical column which is about to be encoded is: " + cardinality);
//      Assert.assertTrue(cardinality >= Categorical.MAX_CATEGORICAL_COUNT * 0.5);

      printOutFrameAsTable(fr, false, 10);
      String foldColumnName = "fold";
      addKFoldColumn(fr, foldColumnName, 5, seed);

      BlendingParams params = new BlendingParams(5, 1);

      String[] teColumns = {"ColA"};
      TargetEncoder tec = new TargetEncoder(teColumns, params);
      String responseColumnName = "y";

      boolean withBlendedAvg = true;
      boolean withImputationForNAsInOriginalColumns = true;

      long startTime = System.currentTimeMillis();
      encodingMap = tec.prepareEncodingMap(fr, responseColumnName, foldColumnName, true);

      Frame trainEncoded;

      trainEncoded = tec.applyTargetEncoding(fr, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.KFold, foldColumnName, withBlendedAvg, withImputationForNAsInOriginalColumns, seed);

      long totalTime = System.currentTimeMillis() - startTime;
      printOutFrameAsTable(trainEncoded, false, 100);
      Scope.track(trainEncoded);

      System.out.println("Time spent for preparing EN and applying it: " + totalTime);
    } catch (Exception ex) {
      Assert.fail(ex.getMessage());
    } finally {
      if (encodingMap != null) encodingMapCleanUp(encodingMap);
      Scope.exit();
    }
  }

  private void encodingMapCleanUp(Map<String, Frame> encodingMap) {
    for (Map.Entry<String, Frame> map : encodingMap.entrySet()) {
      map.getValue().delete();
    }
  }

}
