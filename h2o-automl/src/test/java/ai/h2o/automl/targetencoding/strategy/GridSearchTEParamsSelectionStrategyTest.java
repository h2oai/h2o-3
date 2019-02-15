package ai.h2o.automl.targetencoding.strategy;

import ai.h2o.automl.Algo;
import ai.h2o.automl.targetencoding.TargetEncoder;
import ai.h2o.automl.targetencoding.TargetEncodingParams;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.StratifiedSampler;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static org.junit.Assert.*;

public class GridSearchTEParamsSelectionStrategyTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }
  
  @Test
  public void strategyDoesNotLeakKeys() {
    Frame fr = null;
    try {
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      int numberOfIterations = 1;
      GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> bestParamsEv = findBestTargetEncodingParams(fr ,Algo.GBM, numberOfIterations);
      bestParamsEv.getItem();
      
    } finally {
      fr.delete();
    }
    
  }
  
  @Test
  public void getBestParamsBasedOnGBMRepresentative() {
    Frame fr = null;
    try {
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> bestParamsEv = findBestTargetEncodingParams(fr, Algo.GBM, 252);

      TargetEncodingParams bestParams = bestParamsEv.getItem();
      assertEquals(50, bestParams.getBlendingParams().getK(), 1e-5);
      assertEquals(5, bestParams.getBlendingParams().getF(), 1e-5);
      assertEquals(0.0, bestParams.getNoiseLevel(), 1e-5);
      assertEquals(true, bestParams.isWithBlendedAvg());
      assertEquals(TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, bestParams.getHoldoutType());
      
    } finally {
      fr.delete();
    }
    
  }
  
  @Test
  public void getBestParamsBasedOnGLMRepresentative() {
    Frame fr = null;
    try {
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      
      GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> bestParamsEv = findBestTargetEncodingParams(fr, Algo.GLM, 252);
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

  private GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> findBestTargetEncodingParams(Frame fr, Algo glm, int i) {

    String responseColumnName = "survived";

    asFactor(fr, responseColumnName);

    TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(fr, fr.vec("survived"), 4);
    String[] columnsToEncode = strategy.getColumnsToEncode();
    Algo[] evaluationAlgos = new Algo[]{glm};

    long seedForFoldColumn = 2345L;

    GridSearchTEParamsSelectionStrategy gridSearchTEParamsSelectionStrategy =
            new GridSearchTEParamsSelectionStrategy(fr, evaluationAlgos, i, responseColumnName, columnsToEncode, true, seedForFoldColumn);

    return gridSearchTEParamsSelectionStrategy.getBestParamsWithEvaluation();
  }

  @Test
  public void perModelTEParametersAreBetterTest() {
    
    int numberOfSearchIterations = 20;
    Frame fr = null;
    Frame fr2 = null;
    Frame fr3 = null;
    Scope.enter();
    try {
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> bestParamsFromGLM = findBestTargetEncodingParams(fr, Algo.GLM, numberOfSearchIterations);
      
      fr2 = parse_test_file("./smalldata/gbm_test/titanic.csv");
      GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> bestParamsFromGBM = findBestTargetEncodingParams(fr2, Algo.GBM, numberOfSearchIterations);

      fr3 = parse_test_file("./smalldata/gbm_test/titanic.csv");
      long seedForFoldColumn = 2345L;
      final String foldColumnForTE = "custom_fold";
      int nfolds = 5;
      addKFoldColumn(fr3, foldColumnForTE, nfolds, seedForFoldColumn);

      String responseColumnName = "survived";

      asFactor(fr3, responseColumnName);

      Vec survivedVec = fr3.vec("survived");
      TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(fr3, survivedVec, 4);

      GridSearchTEEvaluator evaluator = new GridSearchTEEvaluator();

      double gbmWithBestGLMParams = evaluator.evaluate(bestParamsFromGLM._item, new Algo[]{Algo.GBM}, fr3, responseColumnName, foldColumnForTE, strategy.getColumnsToEncode());
      double gbmWithBestGBMParams = evaluator.evaluate(bestParamsFromGBM._item, new Algo[]{Algo.GBM}, fr3, responseColumnName, foldColumnForTE, strategy.getColumnsToEncode());

      assertTrue(gbmWithBestGBMParams > gbmWithBestGLMParams);
      assertTrue(bestParamsFromGBM.getScore() == gbmWithBestGBMParams);
      
      double glmWithBestGLMParams = evaluator.evaluate(bestParamsFromGLM._item, new Algo[]{Algo.GLM}, fr3, responseColumnName, foldColumnForTE, strategy.getColumnsToEncode());
      double glmWithBestGBMParams = evaluator.evaluate(bestParamsFromGBM._item, new Algo[]{Algo.GLM}, fr3, responseColumnName, foldColumnForTE, strategy.getColumnsToEncode());

      assertTrue(glmWithBestGLMParams > glmWithBestGBMParams);
      assertTrue(bestParamsFromGLM.getScore() == glmWithBestGLMParams);
      
    } finally {
      Scope.exit();
      fr.delete();
      fr2.delete();
      fr3.delete();
    }
  }

  @Test
  public void priorityQueueTheBiggerTheBetterTest() {
    boolean theBiggerTheBetter = true;
    
    Comparator comparator = new GridSearchTEParamsSelectionStrategy.EvaluatedComparator(theBiggerTheBetter);
    PriorityQueue<GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams>> evaluatedQueue = new PriorityQueue<>(5, comparator);
    TargetEncodingParams params1 = new TargetEncodingParams((byte)2);
    TargetEncodingParams params2 = new TargetEncodingParams((byte)3);
    TargetEncodingParams params3 = new TargetEncodingParams((byte)1);
    evaluatedQueue.add(new GridSearchTEParamsSelectionStrategy.Evaluated<>(params1, 5));
    evaluatedQueue.add(new GridSearchTEParamsSelectionStrategy.Evaluated<>(params2, 10));
    evaluatedQueue.add(new GridSearchTEParamsSelectionStrategy.Evaluated<>(params3, 2));
    
    assertEquals(10, evaluatedQueue.peek().getScore(), 1e-5);

  }

  @Test
  public void randomSelectorTest() {
    Map<String, Object[]> searchParams = new HashMap<>();
    searchParams.put("_withBlending", new Boolean[]{true, false});
    searchParams.put("_noise_level", new Double[]{0.0, 0.1, 0.01});
    searchParams.put("_inflection_point", new Integer[]{1, 2, 3, 5, 10, 50, 100});
    searchParams.put("_smoothing", new Double[]{5.0, 10.0, 20.0});
    searchParams.put("_holdoutType", new Byte[]{TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, TargetEncoder.DataLeakageHandlingStrategy.KFold});

    GridSearchTEParamsSelectionStrategy.RandomSelector randomSelector = new GridSearchTEParamsSelectionStrategy.RandomSelector(searchParams);
    int sizeOfSpace = 252;
    for (int i = 0; i < sizeOfSpace; i++) {
      randomSelector.getNext();
    }
    assertEquals(sizeOfSpace, randomSelector.getVisitedPermutationHashes().size());
    
    //Check that cache with permutations will not increase in size after extra `getNext` call when grid has been already discovered. But we still will get some random item.
    randomSelector.getNext();
    assertEquals(sizeOfSpace, randomSelector.getVisitedPermutationHashes().size());
  }
  
  @Test // We assume that sampling might change best target encoding's hyper parameters. 
  public void samplingAssumptionTest() {
    int numberOfSearchIterations = 252;
    String responseColumnName = "survived";
    Frame frFull = null;
    Frame frameThatWillBeSampledByHalf = null;
    Frame sampledByHalf = null;
    Frame frBaseLine = null;
    try {
      frFull = parse_test_file("./smalldata/gbm_test/titanic.csv");
      GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> bestParamsFromGLM_FULL = findBestTargetEncodingParams(frFull, Algo.GLM, numberOfSearchIterations);

      frameThatWillBeSampledByHalf = parse_test_file("./smalldata/gbm_test/titanic.csv");
      sampledByHalf = StratifiedSampler.sample(frameThatWillBeSampledByHalf, responseColumnName, 0.5, 1234L);
      GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> bestParamsFromGLM_HALF = findBestTargetEncodingParams(sampledByHalf, Algo.GLM, numberOfSearchIterations);

      double scoreFull = bestParamsFromGLM_FULL.getScore();
      double scoreHalf = bestParamsFromGLM_HALF.getScore();
      
      // expecting that best K and F parameters will shift to a smaller and bigger values correspondingly after sampling given the same DataLeakageHandlingStrategy 
      assertTrue(bestParamsFromGLM_FULL.getItem().getHoldoutType() != bestParamsFromGLM_HALF.getItem().getHoldoutType());
      
      frBaseLine = parse_test_file("./smalldata/gbm_test/titanic.csv");
      String[] columnsOtExclude = new String[]{};
      double scoreBaseLine = GridSearchTEEvaluator.evaluateWithGLM(frBaseLine, responseColumnName, columnsOtExclude);
      
      assertTrue(scoreBaseLine < scoreHalf);
      
      assertTrue(scoreFull > scoreHalf);
      
    } finally {
      frFull.delete();
      frameThatWillBeSampledByHalf.delete();
      sampledByHalf.delete();
      frBaseLine.delete();
    }
  }
  
  
  @Test 
  public void speedupBySamplingAssumptionTest() {
    int numberOfSearchIterations = 252;
    String responseColumnName = "survived";
    Frame frFull = null;
    Frame frameThatWillBeSampledByHalf = null;
    Frame sampledByHalf = null;
    try {
      long start1 = System.currentTimeMillis();
      frFull = parse_test_file("./smalldata/gbm_test/titanic.csv");
      GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> bestParamsFromGLM_FULL = findBestTargetEncodingParams(frFull, Algo.GLM, numberOfSearchIterations);
      long timeWithoutSampling = System.currentTimeMillis() - start1;

      long start2 = System.currentTimeMillis();
      frameThatWillBeSampledByHalf = parse_test_file("./smalldata/gbm_test/titanic.csv");
      sampledByHalf = StratifiedSampler.sample(frameThatWillBeSampledByHalf, responseColumnName, 0.5, 1234L);
      GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> bestParamsFromGLM_HALF = findBestTargetEncodingParams(sampledByHalf, Algo.GLM, numberOfSearchIterations);
      long timeWithSampling = System.currentTimeMillis() - start2;
      System.out.println("Time without sampling: " + timeWithoutSampling);
      System.out.println("Time with sampling: " + timeWithSampling);

      assertTrue(timeWithoutSampling * 0.65 <= timeWithSampling + 3000 && timeWithoutSampling * 0.65 >= timeWithSampling - 3000);


    } finally {
      frFull.delete();
      frameThatWillBeSampledByHalf.delete();
      sampledByHalf.delete();
    }
  }

  @Test
  public void assumption1Test() {
    //test that shows effectiveness of learning by family of algo and not by every specific model(i.e. model with specific set of hyper parameters)
  }
  @Test
  public void assumptionTest() {
    //TODO test that for N best parameters found based on samped data and representational model we will get best performance for our models:
    // 1) models of the same family (we will verify viability of sampling
    // 2) any model ( we will check if representational model is able to find sutable parameters for other models. Find set of models that benefits(higher than baseline performance) from TE
    
  }
}
