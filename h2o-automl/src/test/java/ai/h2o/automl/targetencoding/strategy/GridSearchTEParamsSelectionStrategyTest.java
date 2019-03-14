package ai.h2o.automl.targetencoding.strategy;

import ai.h2o.automl.targetencoding.TargetEncoder;
import ai.h2o.automl.targetencoding.TargetEncodingParams;
import ai.h2o.automl.targetencoding.TargetEncodingTestFixtures;
import hex.ModelBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import water.AutoBuffer;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.StratificationAssistant;

import java.util.Comparator;
import java.util.HashMap;
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
      GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> bestParamsEv = findBestTargetEncodingParams(fr ,"survived", numberOfIterations);
      bestParamsEv.getItem();
      
    } finally {
      fr.delete();
    }
    
  }

  private GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> findBestTargetEncodingParams(Frame fr, String responseColumnName, int numberOfIterations) {

    asFactor(fr, responseColumnName);

    TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(fr, fr.vec("survived"), 4);
    String[] columnsToEncode = strategy.getColumnsToEncode();

    long seedForFoldColumn = 2345L;

    GridSearchTEParamsSelectionStrategy gridSearchTEParamsSelectionStrategy =
            new GridSearchTEParamsSelectionStrategy(fr, numberOfIterations, responseColumnName, columnsToEncode, true, seedForFoldColumn);

    gridSearchTEParamsSelectionStrategy.setTESearchSpace(ModelValidationMode.VALIDATION_FRAME);
    ModelBuilder mb = TargetEncodingTestFixtures.modelBuilderWithValidFrameFixture(fr, responseColumnName, seedForFoldColumn);
    mb.init(false);
    return gridSearchTEParamsSelectionStrategy.getBestParamsWithEvaluation(mb);
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
      GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> bestParamsFromGLM = findBestTargetEncodingParams(fr, "survived", numberOfSearchIterations);
      
      fr2 = parse_test_file("./smalldata/gbm_test/titanic.csv");
      GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> bestParamsFromGBM = findBestTargetEncodingParams(fr2, "survived", numberOfSearchIterations);

      fr3 = parse_test_file("./smalldata/gbm_test/titanic.csv");
      long seedForFoldColumn = 2345L;
      final String foldColumnForTE = "custom_fold";
      int nfolds = 5;
      addKFoldColumn(fr3, foldColumnForTE, nfolds, seedForFoldColumn);

      String responseColumnName = "survived";

      asFactor(fr3, responseColumnName);

      Vec survivedVec = fr3.vec("survived");
      TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(fr3, survivedVec, 4);

      TargetEncodingHyperparamsEvaluator evaluator = new TargetEncodingHyperparamsEvaluator();

//      double gbmWithBestGLMParams = evaluator.evaluate(bestParamsFromGLM._item, new Algo[]{Algo.GBM}, fr3, responseColumnName, foldColumnForTE, strategy.getColumnsToEncode());
//      double gbmWithBestGBMParams = evaluator.evaluate(bestParamsFromGBM._item, new Algo[]{Algo.GBM}, fr3, responseColumnName, foldColumnForTE, strategy.getColumnsToEncode());
//
//      assertTrue(gbmWithBestGBMParams > gbmWithBestGLMParams);
//      assertTrue(bestParamsFromGBM.getScore() == gbmWithBestGBMParams);
//      
//      double glmWithBestGLMParams = evaluator.evaluate(bestParamsFromGLM._item, new Algo[]{Algo.GLM}, fr3, responseColumnName, foldColumnForTE, strategy.getColumnsToEncode());
//      double glmWithBestGBMParams = evaluator.evaluate(bestParamsFromGBM._item, new Algo[]{Algo.GLM}, fr3, responseColumnName, foldColumnForTE, strategy.getColumnsToEncode());
//
//      assertTrue(glmWithBestGLMParams > glmWithBestGBMParams);
//      assertTrue(bestParamsFromGLM.getScore() == glmWithBestGLMParams);
      
    } finally {
      Scope.exit();
      fr.delete();
      fr2.delete();
      fr3.delete();
    }
  }
  
  @Test
  public void priorityQueueOrderingWithEvaluatedTest() {
    boolean theBiggerTheBetter = true;
    Comparator comparator = new GridSearchTEParamsSelectionStrategy.EvaluatedComparator(theBiggerTheBetter);
    PriorityQueue<GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams>> evaluatedQueue = new PriorityQueue<>(200, comparator);
    
    
    TargetEncodingParams params1 = TargetEncodingTestFixtures.randomTEParams();
    TargetEncodingParams params2 = TargetEncodingTestFixtures.randomTEParams();
    TargetEncodingParams params3 = TargetEncodingTestFixtures.randomTEParams();
    evaluatedQueue.add(new GridSearchTEParamsSelectionStrategy.Evaluated<>(params1, 0.9984, 0));
    evaluatedQueue.add(new GridSearchTEParamsSelectionStrategy.Evaluated<>(params2, 0.9996, 1));
    evaluatedQueue.add(new GridSearchTEParamsSelectionStrategy.Evaluated<>(params3, 0.9784, 2));
    
    assertEquals(0.9996, evaluatedQueue.poll().getScore(), 1e-5);
    assertEquals(0.9984, evaluatedQueue.poll().getScore(), 1e-5);
    assertEquals(0.9784, evaluatedQueue.poll().getScore(), 1e-5);

  }

  @Test
  public void randomSelectorTest() {
    HashMap<String, Object[]> searchParams = new HashMap<>();
    searchParams.put("_withBlending", new Boolean[]{true, false});
    searchParams.put("_noise_level", new Double[]{0.0, 0.1, 0.01});
    searchParams.put("_inflection_point", new Integer[]{1, 2, 3, 5, 10, 50, 100});
    searchParams.put("_smoothing", new Double[]{5.0, 10.0, 20.0});
    searchParams.put("_holdoutType", new Byte[]{TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, TargetEncoder.DataLeakageHandlingStrategy.KFold});

    GridSearchTEParamsSelectionStrategy.RandomSelector randomSelector = new GridSearchTEParamsSelectionStrategy.RandomSelector(searchParams);
    int sizeOfSpace = 252;
    try {
      for (int i = 0; i < sizeOfSpace; i++) {
        randomSelector.getNext();
      }
    } catch (GridSearchTEParamsSelectionStrategy.RandomSelector.GridSearchCompleted ex) {
      
    }
    assertEquals(sizeOfSpace, randomSelector.getVisitedPermutationHashes().size());

    try {
      //Check that cache with permutations will not increase in size after extra `getNext` call when grid has been already discovered. But we still will get some random item.
      randomSelector.getNext();
    } catch (GridSearchTEParamsSelectionStrategy.RandomSelector.GridSearchCompleted ex) {

    }
    assertEquals(sizeOfSpace, randomSelector.getVisitedPermutationHashes().size());
  }

  /**
   *  We assume that 1) sampling might change selection of best target encoding's hyper parameters( hard to test in a consistent way) 
   *                  2) Even with RGS over TE params with sampled dataset we do better than without TE
   *                  3) Sampling might hurt performance of the GRS over TE params
   */
  @Test 
  public void samplingAssumptionTest() {
    int numberOfSearchIterations = 252;
    String responseColumnName = "survived";
    Frame frFull = null;
    Frame frameThatWillBeSampledByHalf = null;
    Frame sampledByHalf = null;
    Frame frBaseLine = null;
    try {
      frFull = parse_test_file("./smalldata/gbm_test/titanic.csv");
      GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> bestParamsFromGLM_FULL = findBestTargetEncodingParams(frFull, "survived", numberOfSearchIterations);

      frameThatWillBeSampledByHalf = parse_test_file("./smalldata/gbm_test/titanic.csv");
      sampledByHalf = StratificationAssistant.sample(frameThatWillBeSampledByHalf, responseColumnName, 0.5, 1234L);
      GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> bestParamsFromGLM_HALF = findBestTargetEncodingParams(sampledByHalf, "survived", numberOfSearchIterations);

      double scoreFull = bestParamsFromGLM_FULL.getScore();
      double scoreHalf = bestParamsFromGLM_HALF.getScore();
      
      // expecting that best K and F parameters will shift to a smaller and bigger values correspondingly after sampling given the same DataLeakageHandlingStrategy 
      assertTrue(bestParamsFromGLM_FULL.getItem().getHoldoutType() != bestParamsFromGLM_HALF.getItem().getHoldoutType());
      
      frBaseLine = parse_test_file("./smalldata/gbm_test/titanic.csv");
      String[] columnsOtExclude = new String[]{};
      double scoreBaseLine = TargetEncodingHyperparamsEvaluator.evaluateWithGLM(frBaseLine, responseColumnName, columnsOtExclude);
      
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
  public void randomSelectorSerialization() {
    HashMap<String, Object[]> searchParams = new HashMap<>();
    searchParams.put("_withBlending", new Boolean[]{true, false});
    
    GridSearchTEParamsSelectionStrategy.RandomSelector randomSelector = new GridSearchTEParamsSelectionStrategy.RandomSelector(searchParams);
    AutoBuffer ab = new AutoBuffer();
    String json = new String(randomSelector.writeJSON(ab).buf());

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
      GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> bestParamsFromGLM_FULL = findBestTargetEncodingParams(frFull, "survived", numberOfSearchIterations);
      long timeWithoutSampling = System.currentTimeMillis() - start1;

      long start2 = System.currentTimeMillis();
      frameThatWillBeSampledByHalf = parse_test_file("./smalldata/gbm_test/titanic.csv");
      sampledByHalf = StratificationAssistant.sample(frameThatWillBeSampledByHalf, responseColumnName, 0.5, 1234L);
      GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> bestParamsFromGLM_HALF = findBestTargetEncodingParams(sampledByHalf, "survived", numberOfSearchIterations);
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
  
}
