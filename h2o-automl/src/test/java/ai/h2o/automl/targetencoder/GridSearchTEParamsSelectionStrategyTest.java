package ai.h2o.automl.targetencoder;

import ai.h2o.automl.AutoMLBuildSpec;
import ai.h2o.automl.targetencoder.strategy.GridSearchTEParamsSelectionStrategy;
import ai.h2o.automl.targetencoder.strategy.ModelValidationMode;
import ai.h2o.automl.targetencoder.strategy.TEParamsSelectionStrategy;
import ai.h2o.targetencoding.TargetEncoderModel;
import ai.h2o.targetencoding.strategy.TEApplicationStrategy;
import ai.h2o.targetencoding.strategy.ThresholdTEApplicationStrategy;
import hex.ModelBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

import static org.junit.Assert.assertEquals;

public class GridSearchTEParamsSelectionStrategyTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void strategyDoesNotLeakKeysWithValidationFrameMode() {
    Scope.enter();
    try {
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      Scope.track(fr);
      double ratioOfHPToExplore = 0.05;
      GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncoderModel.TargetEncoderParameters> bestParamsEv = findBestTargetEncodingParams(fr , ModelValidationMode.VALIDATION_FRAME, "survived", ratioOfHPToExplore);
      bestParamsEv.getItem();
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void strategyDoesNotLeakKeysWithCVMode() {
    Scope.enter();
    try {
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      Scope.track(fr);
      double ratioOfHPToExplore = 0.01;
      GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncoderModel.TargetEncoderParameters> bestParamsEv = findBestTargetEncodingParams(fr , ModelValidationMode.CV, "survived", ratioOfHPToExplore);
      bestParamsEv.getItem();
    } finally {
      Scope.exit();
    }
  }

  private GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncoderModel.TargetEncoderParameters> findBestTargetEncodingParams(Frame fr, ModelValidationMode modelValidationMode, String responseColumnName, double ratioOfHPToExplore) {

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
            new GridSearchTEParamsSelectionStrategy(leaderboard, responseColumnName, columnsToEncode, _columnNameToIdxMap, true, autoMLTEControl);

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
    TEParamsSelectionStrategy.Evaluated<TargetEncoderModel.TargetEncoderParameters> bestParamsWithEvaluation =
            gridSearchTEParamsSelectionStrategy.getBestParamsWithEvaluation(mb);

    if(train != null) train.delete();
    if(valid != null) valid.delete();
    if(leaderboard != null) leaderboard.delete();

    return bestParamsWithEvaluation;
  }

  @Test
  public void priorityQueueOrderingWithEvaluatedTest() {
    boolean theBiggerTheBetter = true;
    Comparator comparator = new GridSearchTEParamsSelectionStrategy.EvaluatedComparator(theBiggerTheBetter);
    PriorityQueue<GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncoderModel.TargetEncoderParameters>> evaluatedQueue = new PriorityQueue<>(200, comparator);


    TargetEncoderModel.TargetEncoderParameters params1 = TargetEncodingTestFixtures.randomTEParams();
    TargetEncoderModel.TargetEncoderParameters params2 = TargetEncodingTestFixtures.randomTEParams();
    TargetEncoderModel.TargetEncoderParameters params3 = TargetEncodingTestFixtures.randomTEParams();
    evaluatedQueue.add(new GridSearchTEParamsSelectionStrategy.Evaluated<>(params1, 0.9984, 0));
    evaluatedQueue.add(new GridSearchTEParamsSelectionStrategy.Evaluated<>(params2, 0.9996, 1));
    evaluatedQueue.add(new GridSearchTEParamsSelectionStrategy.Evaluated<>(params3, 0.9784, 2));

    assertEquals(0.9996, evaluatedQueue.poll().getScore(), 1e-5);
    assertEquals(0.9984, evaluatedQueue.poll().getScore(), 1e-5);
    assertEquals(0.9784, evaluatedQueue.poll().getScore(), 1e-5);

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
    doNothing().when(modelBuilderMock).findBestTEParams(anyBoolean());

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

    GridSearchTEParamsSelectionStrategy.EarlyStopper earlyStopper = new GridSearchTEParamsSelectionStrategy.EarlyStopper(_earlyStoppingRatio, _ratioOfHyperSpaceToExplore, _numberOfIterations, -1, true);
    int counter = 0;
    while(earlyStopper.proceed()) {
      earlyStopper.update(0.42);
      counter++;
    }

    assertEquals(11, counter); // 1 for changing  -1 to 0.42 and then 10 fruitless attempts
  }

  @Test
  public void early_stopping_is_not_happening_when_we_improve_within_early_stopping_ratio_test() {
    double earlyStoppingRatio = 0.1;
    double ratioOfHyperSpaceToExplore = 0.2;
    int numberOfIterations = 100;

    GridSearchTEParamsSelectionStrategy.EarlyStopper earlyStopper = new GridSearchTEParamsSelectionStrategy.EarlyStopper(earlyStoppingRatio, ratioOfHyperSpaceToExplore, numberOfIterations, -1, true);
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