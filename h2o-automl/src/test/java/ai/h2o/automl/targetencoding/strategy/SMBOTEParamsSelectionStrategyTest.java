package ai.h2o.automl.targetencoding.strategy;

import ai.h2o.automl.AutoMLBenchmarkingHelper;
import ai.h2o.automl.targetencoding.TargetEncoder;
import ai.h2o.automl.targetencoding.TargetEncodingParams;
import ai.h2o.automl.targetencoding.TargetEncodingTestFixtures;
import hex.ModelBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;

import java.util.HashMap;

import static org.junit.Assert.*;

public class SMBOTEParamsSelectionStrategyTest  extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  // TODO duplication with GridSearchVsSMBOBenchmark
  private GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> findBestTargetEncodingParams(Frame fr, String responseColumnName, int numberOfIterations) {

    asFactor(fr, responseColumnName);

    Frame splits[] = AutoMLBenchmarkingHelper.getRandomSplitsFromDataframe(fr, new double[]{0.7, 0.15,0.15}, 2345L);
    Frame train = splits[0];
    Frame valid = splits[1];
    Frame leaderboard = splits[2];
    
    TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(fr, fr.vec("survived"), 4);
    String[] columnsToEncode = strategy.getColumnsToEncode();

    long seedForFoldColumn = 2345L;

    GridSearchTEParamsSelectionStrategy gridSearchTEParamsSelectionStrategy =
            new GridSearchTEParamsSelectionStrategy(leaderboard, numberOfIterations, responseColumnName, columnsToEncode, true, seedForFoldColumn);

    gridSearchTEParamsSelectionStrategy.setTESearchSpace(ModelValidationMode.VALIDATION_FRAME);
    ModelBuilder mb = TargetEncodingTestFixtures.modelBuilderGBMWithValidFrameFixture(train, valid, responseColumnName, seedForFoldColumn);
    mb.init(false);
    return gridSearchTEParamsSelectionStrategy.getBestParamsWithEvaluation(mb);
  }

  @Test
  public void hyperspaceMapToFrame() {

    HashMap<String, Object> entry1 = new HashMap<>();
    entry1.put("param2", 1.0);
    entry1.put("param3", 2.0);
    HashMap<String, Object> entry2 = new HashMap<>();
    entry2.put("param2", 2.0);
    entry2.put("param3", 5.5);

    GridSearchTEParamsSelectionStrategy.GridEntry[] gridEntries = {new GridSearchTEParamsSelectionStrategy.GridEntry(entry1, 0), new GridSearchTEParamsSelectionStrategy.GridEntry(entry2, 0)};
    Frame spaceAsFrame = SMBOTEParamsSelectionStrategy.hyperspaceMapToFrame(gridEntries);

    printOutFrameAsTable(spaceAsFrame);
    spaceAsFrame.delete();

  }

  @Test
  public void singleRowFrameToMap() {
    HashMap<String, Object> entry1 = new HashMap<>();
    entry1.put("param2", 1.0);
    entry1.put("param3", 2.2);

    GridSearchTEParamsSelectionStrategy.GridEntry[] gridEntries = {new GridSearchTEParamsSelectionStrategy.GridEntry(entry1, 0)};
    Frame spaceAsFrame = SMBOTEParamsSelectionStrategy.hyperspaceMapToFrame(gridEntries);

    printOutFrameAsTable(spaceAsFrame);
    HashMap<String, Object> map = SMBOTEParamsSelectionStrategy.singleRowFrameToMap(spaceAsFrame);
    assertEquals(1.0,  map.get("param2"));
    assertEquals(2.2,  map.get("param3"));
    spaceAsFrame.delete();
  }

  @Test
  public void getBestParamsWithSMBO() {
    Frame fr = null;
    try {
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

      GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> bestParamsEv = findBestTargetEncodingParams(fr, "survived", 252);
      TargetEncodingParams bestParams = bestParamsEv.getItem();

      assertEquals(1, bestParams.getBlendingParams().getK(), 1e-5);
      assertEquals(5, bestParams.getBlendingParams().getF(), 1e-5);
      assertEquals(0.01, bestParams.getNoiseLevel(), 1e-5);
      assertEquals(true, bestParams.isWithBlendedAvg());
      assertEquals(TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, bestParams.getHoldoutType());

    } finally {
      fr.delete();
    }

  }

}
