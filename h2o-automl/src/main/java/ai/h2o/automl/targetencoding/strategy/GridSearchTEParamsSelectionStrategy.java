package ai.h2o.automl.targetencoding.strategy;

import ai.h2o.automl.targetencoding.TargetEncoder;
import ai.h2o.automl.targetencoding.TargetEncodingParams;
import hex.ModelBuilder;
import water.Iced;
import water.fvec.Frame;
import water.util.Log;
import water.util.TwoDimTable;

import java.util.*;

/**
 * For now it should be named RandomGridSearchTEParamsStrategy
 */
public class GridSearchTEParamsSelectionStrategy extends TEParamsSelectionStrategy {

  private Frame _leaderboardData;
  private String _responseColumn;
  private String[] _columnsToEncode; // we might want to search for subset as well
  private boolean _theBiggerTheBetter;
  private long _seed;

  private RandomSelector randomSelector;
  private PriorityQueue<Evaluated<TargetEncodingParams>> _evaluatedQueue;
  private GridSearchTEEvaluator _evaluator = new GridSearchTEEvaluator();
//  private GridSearchTEStratifiedEvaluator _evaluator = new GridSearchTEStratifiedEvaluator();
  
  private TESearchSpace _teSearchSpace;
  
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
  
  public void setTESearchSpace(TESearchSpace teSearchSpace) {
    HashMap<String, Object[]> _grid = new HashMap<>();
    
    // Also when we chose holdoutType=None we don't need to search for noise
    switch (teSearchSpace) {
      case CV_EARLY_STOPPING: // TODO move up common parameter' ranges
        _grid.put("_withBlending", new Double[]{1.0/*, false*/}); // NOTE: we can postpone implementation of hierarchical hyperparameter spaces... as in most cases blending is helpful.
        //_grid.put("_noise_level", new Double[]{0.0, 0.1, 0.01}); when we chose holdoutType=None we don't need to search for noise
        _grid.put("_inflection_point", new Double[]{1.0, 2.0, 3.0, 5.0, 10.0, 50.0, 100.0});
        _grid.put("_smoothing", new Double[]{5.0, 10.0, 20.0});
        _grid.put("_holdoutType", new Double[]{ 2.0});
        break;
      case VALIDATION_FRAME_EARLY_STOPPING:
        _grid.put("_withBlending", new Double[]{1.0, 0.0}); // NOTE: we can postpone implementation of hierarchical hyperparameter spaces... as in most cases blending is helpful.
        _grid.put("_noise_level", new Double[]{0.0, 0.1, 0.01});
        _grid.put("_inflection_point", new Double[]{1.0, 2.0, 3.0, 5.0, 10.0, 50.0, 100.0});
        _grid.put("_smoothing", new Double[]{5.0, 10.0, 20.0});
        _grid.put("_holdoutType", new Double[]{0.0, 1.0, 2.0}); // see TargetEncoder.DataLeakageHandlingStrategy
        break;
    }
    
    _teSearchSpace = teSearchSpace;

    randomSelector = new RandomSelector(_grid, _seed);
  }
  
  @Override
  public TargetEncodingParams getBestParams(ModelBuilder modelBuilder) {
    return getBestParamsWithEvaluation(modelBuilder).getItem();
  }

  public Evaluated<TargetEncodingParams> getBestParamsWithEvaluation(ModelBuilder modelBuilder) {
    assert _teSearchSpace != null : "`setTESearchSpace()` method should has been called to setup appropriate grid search.";
    
    //TODO First we need to do stratified sampling
    try {
      for (int attempt = 0; attempt < _numberOfIterations; attempt++) {

        GridEntry selected = randomSelector.getNext(); // Maybe we don't need to have a GridEntry

        TargetEncodingParams param = new TargetEncodingParams(selected.getItem());

        ModelBuilder clonedModelBuilder = ModelBuilder.clone(modelBuilder);
        clonedModelBuilder.init(false); // in _evaluator we assume that init() has been already called

        double evaluationResult = _evaluator.evaluate(param, clonedModelBuilder, _leaderboardData, getColumnsToEncode(), _seed);
        _evaluatedQueue.add(new Evaluated<>(param, evaluationResult));
      }
    } catch (RandomSelector.GridSearchCompleted ex) {
      // just proceed by returning best gridEntry found so far
    }

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



  public static void printOutFrameAsTable(Frame fr) {
    printOutFrameAsTable(fr, false, fr.numRows());
  }

  public static void printOutFrameAsTable(Frame fr, boolean rollups, long limit) {
    assert limit <= Integer.MAX_VALUE;
    TwoDimTable twoDimTable = fr.toTwoDimTable(0, (int) limit, rollups);
    System.out.println(twoDimTable.toString(2, true));
  }
}
