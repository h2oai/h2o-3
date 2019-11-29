package ai.h2o.automl.targetencoder;

import ai.h2o.automl.AutoMLBuildSpec;
import ai.h2o.automl.targetencoder.strategy.GridBasedTEParamsSelectionStrategy;
import ai.h2o.automl.targetencoder.strategy.GridSearchTEParamsSelectionStrategy;
import ai.h2o.automl.targetencoder.strategy.ModelValidationMode;
import ai.h2o.automl.targetencoder.strategy.TEParamsSelectionStrategy;
import ai.h2o.targetencoding.TargetEncoder;
import ai.h2o.targetencoding.strategy.TEApplicationStrategy;
import ai.h2o.targetencoding.strategy.ThresholdTEApplicationStrategy;
import hex.ModelBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;

import java.util.*;

import static org.junit.Assert.assertEquals;

public class GridSearchTEParamsSelectionStrategyTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void strategyDoesNotLeakKeysWithValidationFrameMode() {
    Frame fr = null;
    try {
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      double ratioOfHPToExplore = 0.05;
      GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> bestParamsEv = findBestTargetEncodingParams(fr , ModelValidationMode.VALIDATION_FRAME, "survived", ratioOfHPToExplore);
      bestParamsEv.getItem();
    } finally {
      fr.delete();
    }
  }

  @Test
  public void strategyDoesNotLeakKeysWithCVMode() {
    Frame fr = null;
    try {
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      double ratioOfHPToExplore = 0.01;
      GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> bestParamsEv = findBestTargetEncodingParams(fr , ModelValidationMode.CV, "survived", ratioOfHPToExplore);
      bestParamsEv.getItem();
    } finally {
      fr.delete();
    }
  }

  private GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> findBestTargetEncodingParams(Frame fr, ModelValidationMode modelValidationMode, String responseColumnName, double ratioOfHPToExplore) {

    asFactor(fr, responseColumnName);
    Frame train = null;
    Frame valid = null;
    Frame leaderboard = null;
    switch (modelValidationMode) {
      case CV:
        train = fr;
        break;
      case VALIDATION_FRAME:
        Frame splits[] = AutoMLBenchmarkHelper.getRandomSplitsFromDataframe(fr, new double[]{0.7, 0.15,0.15}, 2345L);
        train = splits[0];
        valid = splits[1];
        leaderboard = splits[2];

    }

    TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(fr,4, new String[]{"survived"});
    String[] columnsToEncode = strategy.getColumnsToEncode();

    long seedForFoldColumn = 2345L;

    AutoMLBuildSpec.AutoMLTEControl autoMLTEControl = new AutoMLBuildSpec.AutoMLTEControl();
    autoMLTEControl.ratio_of_hyperspace_to_explore = 0.1;
    autoMLTEControl.search_over_columns = true;
    autoMLTEControl.early_stopping_ratio = 1.0;
    autoMLTEControl.seed = seedForFoldColumn;

    Map<String, Double> _columnNameToIdxMap = new HashMap<>();
    for (String column : columnsToEncode) {
      _columnNameToIdxMap.put(column, (double) fr.find(column));
    }
    GridSearchTEParamsSelectionStrategy gridSearchTEParamsSelectionStrategy =
            new GridSearchTEParamsSelectionStrategy(leaderboard, responseColumnName, _columnNameToIdxMap, true, autoMLTEControl);

    gridSearchTEParamsSelectionStrategy.setTESearchSpace(modelValidationMode);
    ModelBuilder mb = null;
    switch (modelValidationMode) {
      case CV:
        mb = TargetEncodingTestFixtures.modelBuilderGBMWithCVFixture(train, responseColumnName, seedForFoldColumn);
        break;
      case VALIDATION_FRAME:
        mb = TargetEncodingTestFixtures.modelBuilderGBMWithValidFrameFixture(train, valid, responseColumnName, seedForFoldColumn);
    }
    mb.init(false);
    TEParamsSelectionStrategy.Evaluated<TargetEncodingParams> bestParamsWithEvaluation = gridSearchTEParamsSelectionStrategy.getBestParamsWithEvaluation(mb);

    if(train != null) train.delete();
    if(valid != null) valid.delete();
    if(leaderboard != null) leaderboard.delete();

    return bestParamsWithEvaluation;
  }

  @Test
  public void priorityQueueOrderingWithEvaluatedTest() {
    boolean theBiggerTheBetter = true;
    Comparator comparator = new GridSearchTEParamsSelectionStrategy.EvaluatedComparator(theBiggerTheBetter);
    PriorityQueue<GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams>> evaluatedQueue = new PriorityQueue<>(200, comparator);


    TargetEncodingParams params1 = TargetEncodingTestFixtures.randomTEParams(null);
    TargetEncodingParams params2 = TargetEncodingTestFixtures.randomTEParams(null);
    TargetEncodingParams params3 = TargetEncodingTestFixtures.randomTEParams(null);
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
    searchParams.put("_holdoutType", new Byte[]{TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut.getVal(), TargetEncoder.DataLeakageHandlingStrategy.KFold.getVal()});

    TEParamsSelectionStrategy.RandomGridEntrySelector randomGridEntrySelector = new TEParamsSelectionStrategy.RandomGridEntrySelector(searchParams);
    int sizeOfSpace = 252;
    try {
      for (int i = 0; i < sizeOfSpace; i++) {
        randomGridEntrySelector.getNext();
      }
    } catch (TEParamsSelectionStrategy.RandomGridEntrySelector.GridSearchCompleted ex) {

    }

    assertEquals(sizeOfSpace, randomGridEntrySelector.getVisitedPermutationHashes().size());

    try {
      //Check that cache with permutations will not increase in size after extra `getNext` call when grid has been already discovered.
      randomGridEntrySelector.getNext();
    } catch (TEParamsSelectionStrategy.RandomGridEntrySelector.GridSearchCompleted ex) {

    }
    assertEquals(sizeOfSpace, randomGridEntrySelector.getVisitedPermutationHashes().size());
  }

  // TODO Mockito
  /*@Test
  public void earlyStoppingRatioTest() {

    Map<String, Double> columnNameToIdxMap = new HashMap<>();
    AutoMLBuildSpec.AutoMLTEControl teSpec = new AutoMLBuildSpec.AutoMLTEControl();
    teSpec.search_over_columns = false;
    teSpec.early_stopping_ratio = 0.1;
    teSpec.ratio_of_hyperspace_to_explore = 0.3;

    TargetEncodingHyperparamsEvaluator evaluatorMock = mock(TargetEncodingHyperparamsEvaluator.class);

    when(evaluatorMock.evaluate(any(TargetEncodingParams.class), any(ModelBuilder.class), any(ModelValidationMode.class), any(Frame.class), anyLong())).thenReturn(0.42);

    // Let's check only CV case. Logic for stopping is using same code for both cases.
    GridSearchTEParamsSelectionStrategy selectionStrategy =
            new GridSearchTEParamsSelectionStrategy(null, "survived", columnNameToIdxMap, true, teSpec, evaluatorMock);
    selectionStrategy.setTESearchSpace(ModelValidationMode.CV);

    ModelBuilder modelBuilderMock = mock(ModelBuilder.class);
    when(modelBuilderMock.makeCopy()).thenReturn(modelBuilderMock);
    doNothing().when(modelBuilderMock).init(anyBoolean());

    selectionStrategy.getBestParams(modelBuilderMock);


    int actuallyEvaluatedCount = selectionStrategy.getEvaluatedQueue().size();
    double expectedToEvaluate = teSpec.early_stopping_ratio * selectionStrategy.getRandomGridEntrySelector().spaceSize();
    assertEquals((int) expectedToEvaluate + 1, actuallyEvaluatedCount, 1e-5);
  }*/

  @Test
  public void earlyStopperTest() {
    double _earlyStoppingRatio = 0.1;
    double _ratioOfHyperSpaceToExplore = 0.2;
    int _numberOfIterations = 100;

    GridBasedTEParamsSelectionStrategy.EarlyStopper earlyStopper = new GridBasedTEParamsSelectionStrategy.EarlyStopper(_earlyStoppingRatio, _ratioOfHyperSpaceToExplore, _numberOfIterations, -1, true);
    int counter = 0;
    while(earlyStopper.proceed()) {
      earlyStopper.update(0.42);
      counter++;
    }

    assertEquals(11, counter); // 1 for changing  -1 to 0.42 and then 10 fruitless attempts
  }

  @Test
  public void early_stopping_does_not_happening_when_we_improve_within_early_stopping_ratio_test() {
    double earlyStoppingRatio = 0.1;
    double ratioOfHyperSpaceToExplore = 0.2;
    int numberOfIterations = 100;

    GridBasedTEParamsSelectionStrategy.EarlyStopper earlyStopper = new GridBasedTEParamsSelectionStrategy.EarlyStopper(earlyStoppingRatio, ratioOfHyperSpaceToExplore, numberOfIterations, -1, true);
    int counter = 0;
    double score = 0.42;

    while(earlyStopper.proceed()) {
      if (counter % 10 == 9) {
        score += 0.1;
        earlyStopper.update(score);
      } else
        earlyStopper.update(score);

      counter++;
    }

    assertEquals(numberOfIterations * ratioOfHyperSpaceToExplore, counter, 1e-5);
  }

}