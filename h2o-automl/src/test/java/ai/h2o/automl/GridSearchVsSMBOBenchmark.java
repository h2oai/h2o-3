package ai.h2o.automl;

import ai.h2o.automl.targetencoding.TargetEncodingParams;
import ai.h2o.automl.targetencoding.TargetEncodingTestFixtures;
import ai.h2o.automl.targetencoding.strategy.*;
import hex.ModelBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import water.fvec.Frame;

import java.util.PriorityQueue;
import java.util.Random;

import static ai.h2o.automl.AutoMLBenchmarkingHelper.getPreparedTitanicFrame;
import static ai.h2o.automl.targetencoding.strategy.TEParamsSelectionStrategy.Evaluated;

public class GridSearchVsSMBOBenchmark extends water.TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }


  private GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> [] findBestTargetEncodingParams(Frame fr, String responseColumnName, double ratioOfHyperSpaceToExplore, long seed) {

    asFactor(fr, responseColumnName);

    Frame splits[] = AutoMLBenchmarkingHelper.getRandomSplitsFromDataframe(fr, new double[]{0.7, 0.15,0.15}, 2345L);
    Frame train = splits[0];
    Frame valid = splits[1];
    Frame leaderboard = splits[2];

    TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(fr, fr.vec("survived"), 4);
    String[] columnsToEncode = strategy.getColumnsToEncode();

    GridSearchTEParamsSelectionStrategy gridSearchTEParamsSelectionStrategy =
            new GridSearchTEParamsSelectionStrategy(leaderboard, ratioOfHyperSpaceToExplore, responseColumnName, columnsToEncode, true, seed);

    gridSearchTEParamsSelectionStrategy.setTESearchSpace(ModelValidationMode.VALIDATION_FRAME);
    ModelBuilder mb = TargetEncodingTestFixtures.modelBuilderGBMWithValidFrameFixture(train, valid, responseColumnName, seed);
    mb.init(false);
    PriorityQueue<Evaluated<TargetEncodingParams>> evaluatedQueue = gridSearchTEParamsSelectionStrategy.getEvaluatedQueue();
    Evaluated<TargetEncodingParams> bestParamsWithEvaluation = gridSearchTEParamsSelectionStrategy.getBestParamsWithEvaluation(mb);

    Evaluated<TargetEncodingParams> [] leaders = new Evaluated[topListSize];
    for (int i = 0; i < topListSize; i++) {
      leaders[i] = gridSearchTEParamsSelectionStrategy.getEvaluatedQueue().poll();
    }
    assert bestParamsWithEvaluation.getScore() == leaders[0].getScore();
    train.delete();
    valid.delete();
    leaderboard.delete();
    return leaders;
  }

  private SMBOTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> findBestTargetEncodingParamsWithSMBO(Frame fr, String responseColumnName, double earlyStoppingRatio, long seed) {

    asFactor(fr, responseColumnName);

    Frame splits[] = AutoMLBenchmarkingHelper.getRandomSplitsFromDataframe(fr, new double[]{0.7, 0.15,0.15}, 2345L);
    Frame train = splits[0];
    Frame valid = splits[1];
    Frame leaderboard = splits[2];
    
    TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(fr, fr.vec("survived"), 4);
    String[] columnsToEncode = strategy.getColumnsToEncode();

    double ratioOfHyperspaceToExplore = 0.4;
    SMBOTEParamsSelectionStrategy gridSearchTEParamsSelectionStrategy =
            new SMBOTEParamsSelectionStrategy(leaderboard, earlyStoppingRatio, ratioOfHyperspaceToExplore, responseColumnName, columnsToEncode, true, seed);

    gridSearchTEParamsSelectionStrategy.setTESearchSpace(ModelValidationMode.VALIDATION_FRAME);

    ModelBuilder mb = TargetEncodingTestFixtures.modelBuilderGBMWithValidFrameFixture(train, valid, responseColumnName, seed);
    mb.init(false);
    Evaluated<TargetEncodingParams> bestParamsWithEvaluation = gridSearchTEParamsSelectionStrategy.getBestParamsWithEvaluation(mb);
    train.delete();
    valid.delete();
    leaderboard.delete();
    return bestParamsWithEvaluation;
  }

  int topListSize = 10;
  
  // Here we run RGS through whole hyperspace (or part of it) to get real OF evaluations for every grid entry so that we can understand how good our SMBO search is.
  @Test
  public void benchmark() {
    double ratioOfHyperSpaceToExplore = 0.4; // total size = 189;  
    String responseColumnName = "survived";
    Frame frForRGS = null;
    Frame frameForSMBO = null;
    Random generator = new Random();
    
    int[] successCountPerRank = new int[topListSize];
    int successCountTotal = 0;
    int clearWinCount = 0;
    
    double accBestScoreRGS = 0;
    double accBestScoreSMBO = 0;
    
    int averageIndexWhenBestParamsWasFoundSMBO = 0;
    double averageTimeRGS = 0;
    double averageTimeSMBO = 0;

    int numberOfRuns = 1;
    for (int seedAttempt = 0; seedAttempt < numberOfRuns; seedAttempt++) {
      long seed = generator.nextLong();
      try {
        long start2 = System.currentTimeMillis();
        frameForSMBO = getPreparedTitanicFrame("survived");
        
        Evaluated<TargetEncodingParams> bestParamsFromSMBO = findBestTargetEncodingParamsWithSMBO(frameForSMBO, responseColumnName, 0.15, seed);
        long timeWithSMBO = System.currentTimeMillis() - start2;

        long start1 = System.currentTimeMillis();
        frForRGS = getPreparedTitanicFrame("survived");
        Evaluated<TargetEncodingParams> [] bestParamsFromRGS = findBestTargetEncodingParams(frForRGS, responseColumnName, ratioOfHyperSpaceToExplore, seed);
        long timeWithRGS = System.currentTimeMillis() - start1;
        
        
        System.out.println("Time with GRS: " + timeWithRGS);
        System.out.println("Time with SMBO: " + timeWithSMBO);

        averageTimeRGS += timeWithRGS;
        averageTimeSMBO += timeWithSMBO;

        System.out.println("bestParamsFromSMBO.getScore(): " + bestParamsFromSMBO.getScore());
        System.out.println("bestParamsFromRGS.getScore(): " + bestParamsFromRGS[0].getScore());

        accBestScoreSMBO += bestParamsFromSMBO.getScore();
        accBestScoreRGS += bestParamsFromRGS[0].getScore();
        
        // If SMBO prediction is in top `topListSize` then it is considered as a success
        for (int rankIdx = 0; rankIdx < topListSize; rankIdx++) {
          
          if(bestParamsFromSMBO.getScore() > bestParamsFromRGS[0].getScore()) {
            clearWinCount++;
          }
          if(bestParamsFromSMBO.getScore() >= bestParamsFromRGS[rankIdx].getScore()) {
            successCountPerRank[rankIdx]++;
            successCountTotal++;
            break;
          }
        }

        System.out.println("Index of best found params for " + seedAttempt + "st attempt : " + bestParamsFromSMBO.getIndex() );

        averageIndexWhenBestParamsWasFoundSMBO += bestParamsFromSMBO.getIndex();
      } catch (Exception ex) {
        throw ex;
      } finally {
        if(frForRGS!= null) frForRGS.delete();
        if(frameForSMBO!= null) frameForSMBO.delete();
      }
    }
    System.out.println("\n\nNumber of times SBMO beat RGS is " + successCountTotal + " out of total " + numberOfRuns + " runs. Probability: " + (double)successCountTotal / numberOfRuns);
    for (int rankIdx = 0; rankIdx < topListSize; rankIdx++) {
      System.out.println("Number of times SBMO found value with rank=" + rankIdx + " is " + successCountPerRank[rankIdx]);
    }
    System.out.println("Average index of best found(locally) params: " + (double) averageIndexWhenBestParamsWasFoundSMBO / numberOfRuns );
    System.out.println("Total number of pure wins by SMBO: " + clearWinCount );
    
    System.out.println("Average time RGS: " + averageTimeRGS / numberOfRuns );
    System.out.println("Average time SMBO: " + averageTimeSMBO / numberOfRuns );
    
    System.out.println("Average performance RGS: " + accBestScoreRGS / numberOfRuns );
    System.out.println("Average performance SMBO: " + accBestScoreSMBO / numberOfRuns );
  }

}
