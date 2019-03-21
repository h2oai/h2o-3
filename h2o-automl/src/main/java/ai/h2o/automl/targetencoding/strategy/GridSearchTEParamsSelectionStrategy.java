package ai.h2o.automl.targetencoding.strategy;

import ai.h2o.automl.targetencoding.TargetEncodingParams;
import hex.ModelBuilder;
import water.fvec.Frame;
import water.util.TwoDimTable;

import java.util.*;

/**
 * For now it should be named RandomGridSearchTEParamsStrategy
 */
public class GridSearchTEParamsSelectionStrategy extends GridBasedTEParamsSelectionStrategy {

  private Frame _leaderboardData;
  private String _responseColumn;
  private String[] _columnsToEncode; // we might want to search for subset as well
  private boolean _theBiggerTheBetter;

  private PriorityQueue<Evaluated<TargetEncodingParams>> _evaluatedQueue;
  private TargetEncodingHyperparamsEvaluator _evaluator = new TargetEncodingHyperparamsEvaluator();
//  private GridSearchTEStratifiedEvaluator _evaluator = new GridSearchTEStratifiedEvaluator();
  
  
  private int _numberOfIterations; // or should be a strategy that will be in charge of stopping.

  public GridSearchTEParamsSelectionStrategy(Frame leaderboard, int numberOfIterations, String responseColumn, String[] columnsToEncode, boolean theBiggerTheBetter, long seed) {
    _seed = seed;
    
    _leaderboardData = leaderboard;
    _numberOfIterations = numberOfIterations;
    _responseColumn = responseColumn;
    _columnsToEncode = columnsToEncode;
    _theBiggerTheBetter = theBiggerTheBetter;
    
    _evaluatedQueue = new PriorityQueue<>(numberOfIterations, new EvaluatedComparator(theBiggerTheBetter));
  }
  
  @Override
  public TargetEncodingParams getBestParams(ModelBuilder modelBuilder) {
    return getBestParamsWithEvaluation(modelBuilder).getItem();
  }

  public Evaluated<TargetEncodingParams> getBestParamsWithEvaluation(ModelBuilder modelBuilder) {
    assert _modelValidationMode != null : "`setTESearchSpace()` method should has been called to setup appropriate grid search.";
    
    SMBOTEParamsSelectionStrategy.Exporter exporter = new SMBOTEParamsSelectionStrategy.Exporter();
    //TODO Consider adding stratified sampling here
    try {
      for (int attempt = 0; attempt < _numberOfIterations; attempt++) {

        GridEntry selected = _randomSelector.getNext(); // Maybe we don't need to have a GridEntry

        TargetEncodingParams param = new TargetEncodingParams(selected.getItem());

        ModelBuilder clonedModelBuilder = ModelBuilder.clone(modelBuilder);
        clonedModelBuilder.init(false); // in _evaluator we assume that init() has been already called

        double evaluationResult = _evaluator.evaluate(param, clonedModelBuilder, _modelValidationMode, _leaderboardData, getColumnsToEncode(), _seed);
        _evaluatedQueue.add(new Evaluated<>(param, evaluationResult, attempt));
        exporter.update(0, evaluationResult);
      }
    } catch (RandomSelector.GridSearchCompleted ex) {
      // just proceed by returning best gridEntry found so far
    }

    exporter.exportToCSV("scores_random_" + modelBuilder._parms.fullName());
    
    Evaluated<TargetEncodingParams> targetEncodingParamsEvaluated = _evaluatedQueue.peek();

    return targetEncodingParamsEvaluated;
  }

  public String getResponseColumn() {
    return _responseColumn;
  }

  public String[] getColumnsToEncode() {
    return _columnsToEncode;
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
