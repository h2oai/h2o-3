package ai.h2o.automl.targetencoder.strategy;

import ai.h2o.automl.AutoMLBuildSpec;
import ai.h2o.automl.targetencoder.TargetEncodingHyperparamsEvaluator;
import ai.h2o.automl.targetencoder.TargetEncodingParams;
import ai.h2o.targetencoding.TargetEncoderModel;
import hex.ModelBuilder;
import water.fvec.Frame;
import water.util.TwoDimTable;

import java.util.*;

/**
 *  Random grid search for searching optimal hyperparameters for target encoding
 */
public class GridSearchTEParamsSelectionStrategy extends GridBasedTEParamsSelectionStrategy {

  private Frame _leaderboardData;
  private String _responseColumn;
  private boolean _theBiggerTheBetter;

  private PriorityQueue<Evaluated<TargetEncodingParams>> _evaluatedQueue;
  private TargetEncodingHyperparamsEvaluator _evaluator = null;

  public GridSearchTEParamsSelectionStrategy(Frame leaderboard,
                                             String responseColumn,
                                             Map<String, Double> columnNameToIdxMap,
                                             boolean theBiggerTheBetter,
                                             AutoMLBuildSpec.AutoMLTEControl teBuildSpec) {

    this(leaderboard, responseColumn, columnNameToIdxMap, theBiggerTheBetter, teBuildSpec, new TargetEncodingHyperparamsEvaluator());
  }

  public GridSearchTEParamsSelectionStrategy(Frame leaderboard,
                                             String responseColumn,
                                             Map<String, Double> columnNameToIdxMap,
                                             boolean theBiggerTheBetter,
                                             AutoMLBuildSpec.AutoMLTEControl teBuildSpec,
                                             TargetEncodingHyperparamsEvaluator evaluator) {
    _seed = teBuildSpec.seed;

    _evaluator = evaluator;

    _leaderboardData = leaderboard;
    _responseColumn = responseColumn;
    _columnNameToIdxMap = columnNameToIdxMap;
    _searchOverColumns = teBuildSpec.search_over_columns;

    _ratioOfHyperSpaceToExplore = teBuildSpec.ratio_of_hyperspace_to_explore;
    _earlyStoppingRatio = teBuildSpec.early_stopping_ratio;
    _theBiggerTheBetter = theBiggerTheBetter;

  }

  @Override
  public void setTESearchSpace(ModelValidationMode modelValidationMode) {
    super.setTESearchSpace(modelValidationMode);
    int expectedNumberOfEntries = (int)( Math.max(1, _randomGridEntrySelector.spaceSize() * _ratioOfHyperSpaceToExplore));
    _evaluatedQueue = new PriorityQueue<>(expectedNumberOfEntries, new EvaluatedComparator(_theBiggerTheBetter));
  }

  @Override
  public TargetEncodingParams getBestParams(ModelBuilder modelBuilder) {
    return getBestParamsWithEvaluation(modelBuilder).getItem();
  }

  public Evaluated<TargetEncodingParams> getBestParamsWithEvaluation(ModelBuilder modelBuilder) {
    assert _modelValidationMode != null : "`setTESearchSpace()` method should has been called to setup appropriate grid search.";

    EarlyStopper earlyStopper = new EarlyStopper(_earlyStoppingRatio, _ratioOfHyperSpaceToExplore, _randomGridEntrySelector.spaceSize(), -1, _theBiggerTheBetter);

    //TODO remove exporter related logic before merging
    //HPSearchPerformanceExporter exporter = new HPSearchPerformanceExporter();

    //TODO Consider adding stratified sampling here
    try {
      while (earlyStopper.proceed()) {

        GridEntry selected = _randomGridEntrySelector.getNext(); // Maybe we don't need to have a GridEntry

        TargetEncodingParams param = new TargetEncodingParams(selected.getItem());

        if(true) throw new IllegalStateException("Convert params");
        TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = new TargetEncoderModel.TargetEncoderParameters();
        ModelBuilder clonedModelBuilder = ModelBuilder.make(targetEncoderParameters);

        clonedModelBuilder.init(false); // in _evaluator we assume that init() has been already called

        double evaluationResult = _evaluator.evaluate(param, clonedModelBuilder, _modelValidationMode, _leaderboardData, _seed);

        earlyStopper.update(evaluationResult);

        _evaluatedQueue.add(new Evaluated<>(param, evaluationResult, earlyStopper.getTotalAttemptsCount()));
        //exporter.update(0, evaluationResult);
      }
    } catch (RandomGridEntrySelector.GridSearchCompleted ex) {
      // just proceed by returning best gridEntry found so far
    }

    //exporter.exportToCSV("scores_random_" + modelBuilder._parms.fullName());

    Evaluated<TargetEncodingParams> targetEncodingParamsEvaluated = _evaluatedQueue.peek();

    return targetEncodingParamsEvaluated;
  }

  public String getResponseColumn() {
    return _responseColumn;
  }

  public boolean isTheBiggerTheBetter() {
    return _theBiggerTheBetter;
  }

  public PriorityQueue<Evaluated<TargetEncodingParams>> getEvaluatedQueue() {
    return _evaluatedQueue;
  }


  public static void printOutFrameAsTable(Frame fr) {
    printOutFrameAsTable(fr, false, fr.numRows());
  }

  public static void printOutFrameAsTable(Frame fr, boolean rollups, long limit) {
    assert limit <= Integer.MAX_VALUE;
    TwoDimTable twoDimTable = fr.toTwoDimTable(0, (int) limit, rollups);
    System.out.println(twoDimTable.toString(2, true));
  }
}