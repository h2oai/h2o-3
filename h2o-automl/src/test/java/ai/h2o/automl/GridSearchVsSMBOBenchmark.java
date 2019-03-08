package ai.h2o.automl;

import ai.h2o.automl.targetencoding.TargetEncodingParams;
import ai.h2o.automl.targetencoding.TargetEncodingTestFixtures;
import ai.h2o.automl.targetencoding.strategy.*;
import hex.ModelBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.H2O;
import water.fvec.Frame;

import java.util.Random;

import static ai.h2o.automl.targetencoding.strategy.TEParamsSelectionStrategy.Evaluated;
import static org.junit.Assert.assertTrue;

public class GridSearchVsSMBOBenchmark extends water.TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  private GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> findBestTargetEncodingParams(Frame fr, String responseColumnName, int numberOfIterations, long seed) {

    asFactor(fr, responseColumnName);

    TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(fr, fr.vec("survived"), 4);
    String[] columnsToEncode = strategy.getColumnsToEncode();

    GridSearchTEParamsSelectionStrategy gridSearchTEParamsSelectionStrategy =
            new GridSearchTEParamsSelectionStrategy(fr, numberOfIterations, responseColumnName, columnsToEncode, true, seed);

    gridSearchTEParamsSelectionStrategy.setTESearchSpace(TESearchSpace.VALIDATION_FRAME_EARLY_STOPPING);
    ModelBuilder mb = TargetEncodingTestFixtures.modelBuilderWithValidFrameFixture(fr, responseColumnName, seed);
    mb.init(false);
    return gridSearchTEParamsSelectionStrategy.getBestParamsWithEvaluation(mb);
  }

  private SMBOTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> findBestTargetEncodingParamsWithSMBO(Frame fr, String responseColumnName, double earlyStoppingRatio, long seed) {

    asFactor(fr, responseColumnName);

    TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(fr, fr.vec("survived"), 4);
    String[] columnsToEncode = strategy.getColumnsToEncode();

    SMBOTEParamsSelectionStrategy gridSearchTEParamsSelectionStrategy =
            new SMBOTEParamsSelectionStrategy(fr, earlyStoppingRatio, responseColumnName, columnsToEncode, true, seed);

    gridSearchTEParamsSelectionStrategy.setTESearchSpace(TESearchSpace.VALIDATION_FRAME_EARLY_STOPPING);
    ModelBuilder mb = TargetEncodingTestFixtures.modelBuilderWithValidFrameFixture(fr, responseColumnName, seed);
    mb.init(false);
    return gridSearchTEParamsSelectionStrategy.getBestParamsWithEvaluation(mb);
  }
  
  @Test
  public void benchmark() {
    int numberOfSearchIterations = 377;
    String responseColumnName = "survived";
    Frame frForRGS = null;
    Frame frameForSMBO = null;
    Random generator = new Random();
    
    int successCount = 0;

    int numberOfRuns = 5;
    for (int seedAttempt = 0; seedAttempt < numberOfRuns; seedAttempt++) {
      long seed = generator.nextLong();
      try {
        long start1 = System.currentTimeMillis();
        frForRGS = parse_test_file("./smalldata/gbm_test/titanic.csv");
        Evaluated<TargetEncodingParams> bestParamsFromRGS = findBestTargetEncodingParams(frForRGS, responseColumnName, numberOfSearchIterations, seed);
        long timeWithRGS = System.currentTimeMillis() - start1;

        long start2 = System.currentTimeMillis();
        frameForSMBO = parse_test_file("./smalldata/gbm_test/titanic.csv");
        Evaluated<TargetEncodingParams> bestParamsFromSMBO = findBestTargetEncodingParamsWithSMBO(frameForSMBO, responseColumnName, 0.15, seed);
        long timeWithSMBO = System.currentTimeMillis() - start2;
        System.out.println("Time with GRS: " + timeWithRGS);
        System.out.println("Time with SMBO: " + timeWithSMBO);

        System.out.println("bestParamsFromSMBO.getScore(): " + bestParamsFromSMBO.getScore());
        System.out.println("bestParamsFromRGS.getScore(): " + bestParamsFromRGS.getScore());

        if(bestParamsFromSMBO.getScore() == bestParamsFromRGS.getScore()) successCount++;
//        assertTrue(bestParamsFromSMBO.getScore() == bestParamsFromRGS.getScore());
//        assertTrue(timeWithSMBO <= timeWithRGS);

      } finally {
        frForRGS.delete();
        frameForSMBO.delete();
      }
    }
    System.out.println("Number of times SBMO beat RGS is " + successCount + " out of total " + numberOfRuns + " runs. Probability: " + (double)successCount / numberOfRuns);
    H2O.STORE.clear();
  }

}
