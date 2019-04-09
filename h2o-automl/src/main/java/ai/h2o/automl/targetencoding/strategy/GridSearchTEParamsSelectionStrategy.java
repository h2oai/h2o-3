package ai.h2o.automl.targetencoding.strategy;

import ai.h2o.automl.targetencoding.TargetEncodingParams;
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
  private TargetEncodingHyperparamsEvaluator _evaluator = new TargetEncodingHyperparamsEvaluator();
  //  private GridSearchTEStratifiedEvaluator _evaluator = new GridSearchTEStratifiedEvaluator(); // add parameter to switch between stratified vs non-stratified
  
  private int _numberOfIterations;

  public GridSearchTEParamsSelectionStrategy(Frame leaderboard,
                                             double ratioOfHyperSpaceToExplore,
                                             String responseColumn,
                                             Map<String, Double> columnNameToIdxMap,
                                             boolean theBiggerTheBetter,
                                             boolean searchOverColumns,
                                             long seed) {
    _seed = seed;
    
    _leaderboardData = leaderboard;
    _responseColumn = responseColumn;
    _columnNameToIdxMap = columnNameToIdxMap;
    _searchOverColumns = searchOverColumns;
    
    _ratioOfHyperSpaceToExplore = ratioOfHyperSpaceToExplore;
    _theBiggerTheBetter = theBiggerTheBetter;

  }

  @Override
  public void setTESearchSpace(ModelValidationMode modelValidationMode) {
    super.setTESearchSpace(modelValidationMode);
    _numberOfIterations = (int)( _randomGridEntrySelector.spaceSize() * _ratioOfHyperSpaceToExplore);
    _evaluatedQueue = new PriorityQueue<>(_numberOfIterations, new EvaluatedComparator(_theBiggerTheBetter));
  }

  @Override
  public TargetEncodingParams getBestParams(ModelBuilder modelBuilder) {
    return getBestParamsWithEvaluation(modelBuilder).getItem();
  }

  public Evaluated<TargetEncodingParams> getBestParamsWithEvaluation(ModelBuilder modelBuilder) {
    assert _modelValidationMode != null : "`setTESearchSpace()` method should has been called to setup appropriate grid search.";

    HPSearchPerformanceExporter exporter = new HPSearchPerformanceExporter();
    
    //TODO Consider adding stratified sampling here
    try {
      for (int attempt = 0; attempt < _numberOfIterations; attempt++) {

        GridEntry selected = _randomGridEntrySelector.getNext(); // Maybe we don't need to have a GridEntry

        TargetEncodingParams param = new TargetEncodingParams(selected.getItem());

        ModelBuilder clonedModelBuilder = ModelBuilder.clone(modelBuilder);
        clonedModelBuilder.init(false); // in _evaluator we assume that init() has been already called

        double evaluationResult = _evaluator.evaluate(param, clonedModelBuilder, _modelValidationMode, _leaderboardData, _seed);
        _evaluatedQueue.add(new Evaluated<>(param, evaluationResult, attempt));
        exporter.update(0, evaluationResult);
      }
    } catch (RandomGridEntrySelector.GridSearchCompleted ex) {
      // just proceed by returning best gridEntry found so far
    }

//    exporter.exportToCSV("scores_random_" + modelBuilder._parms.fullName());
    
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
