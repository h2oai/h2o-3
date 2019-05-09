package ai.h2o.automl.targetencoding;

import hex.splitframe.ShuffleSplitFrame;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.*;
import water.fvec.Frame;
import water.fvec.RebalanceDataSet;

import java.util.Map;
import java.util.Random;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;

@Ignore("Ignoring benchmarkштп tests")
public class TargetEncodingSpeedBenchmark extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  private Frame rebalance(Frame fr, Key targetKey, int nChunks) {
    RebalanceDataSet rb = new RebalanceDataSet(fr, targetKey, nChunks);
    H2O.submitTask(rb);
    rb.join();
    return DKV.get(targetKey).get();
  }

  private Frame[] getSplitsFromAirlinesDataset(String pathToFile, long splitSeed) {

    Frame fr = parse_test_file(pathToFile);

    Frame rebalanced = rebalance(fr, Key.make("rebalanced"), 2);

    double[] ratios = ard(0.7, 0.3);
    Key<Frame>[] keys = aro(Key.<Frame>make(), Key.<Frame>make());
    Frame[] splits = null;
    splits = ShuffleSplitFrame.shuffleSplitFrame(rebalanced, keys, ratios, splitSeed);
    fr.delete();
    rebalanced.delete();
    return splits;
  }

  @Test
  public void airlinesTest() {

    Map<String, Frame> encodingMap = null;

    double sumTETime = 0;
    int numberOfRuns = 100;
    int numberOfWarmUpRuns = 2;

    for (int i = 0; i < numberOfRuns; i++) {
      Scope.enter();
      long seed = new Random().nextLong();
      try {
        Frame[] airlinesTrain = getSplitsFromAirlinesDataset("smalldata/airlines/AirlinesTrain.csv.zip", seed);
        Frame airlinesTrainWithTEH = airlinesTrain[0];
        Frame airlinesValid = airlinesTrain[1];
        Frame airlinesTestFrame = parse_test_file(Key.make("airlines_test"), "smalldata/airlines/AirlinesTest.csv.zip");
        Scope.track(airlinesTrainWithTEH, airlinesValid, airlinesTestFrame);

        long startTimeEncoding = System.currentTimeMillis();

        String foldColumnName = "fold";
        addKFoldColumn(airlinesTrainWithTEH, foldColumnName, 5, seed);

        BlendingParams params = new BlendingParams(5, 1);

        String[] teColumns = {"Origin", "Dest"};
        TargetEncoder tec = new TargetEncoder(teColumns, params);
        String targetColumnName = "IsDepDelayed";

        boolean withBlendedAvg = true;
        boolean withImputationForNAsInOriginalColumns = true;

        encodingMap = tec.prepareEncodingMap(airlinesTrainWithTEH, targetColumnName, foldColumnName, true);

        Frame trainEncoded;

        trainEncoded = tec.applyTargetEncoding(airlinesTrainWithTEH, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.KFold, foldColumnName, withBlendedAvg, withImputationForNAsInOriginalColumns, seed);

        Frame validEncoded = tec.applyTargetEncoding(airlinesValid, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnName, withBlendedAvg, 0, withImputationForNAsInOriginalColumns, seed);

        Frame testEncoded = tec.applyTargetEncoding(airlinesTestFrame, targetColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnName, withBlendedAvg, 0, withImputationForNAsInOriginalColumns, seed);

        Scope.track(trainEncoded, validEncoded, testEncoded);
        
        long finishTimeEncoding = System.currentTimeMillis();
        long timeSpentOnTE = finishTimeEncoding - startTimeEncoding;
        System.out.println("Calculation of encodings took: " + timeSpentOnTE);
        
        if (i >= numberOfWarmUpRuns) sumTETime += timeSpentOnTE;
        
      } catch (Exception ex) {
        Assert.fail(ex.getMessage());
      } finally {
        if (encodingMap != null) encodingMapCleanUp(encodingMap);
        Scope.exit();
      }
    }

    System.out.println("Average TE time: " + sumTETime / (numberOfRuns - numberOfWarmUpRuns));
  }

  private void encodingMapCleanUp(Map<String, Frame> encodingMap) {
    for (Map.Entry<String, Frame> map : encodingMap.entrySet()) {
      map.getValue().delete();
    }
  }

}
