package ai.h2o.automl.targetencoding.strategy;

import ai.h2o.automl.Algo;
import ai.h2o.automl.targetencoding.TargetEncoder;
import ai.h2o.automl.targetencoding.TargetEncodingParams;
import water.fvec.Frame;
import water.util.TwoDimTable;

import java.util.*;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;

/**
 * For now it should be named RandomGridSearchTEParamsStrategy
 */
public class GridSearchTEParamsSelectionStrategy extends TEParamsSelectionStrategy {

  private Algo[] _evaluationAlgos;
  private Frame _inputData;
  private String _responseColumn;
  private String[] _columnsToEncode; // we might want to search for subset as well
  private boolean _theBiggerTheBetter;
  private long _seed;
  Map<String, Object[]> _grid = new HashMap<>();
  private RandomSelector randomSelector;
  private PriorityQueue<Evaluated<TargetEncodingParams>> evaluatedQueue;
  GridSearchTEEvaluator evaluator = new GridSearchTEEvaluator();
  
  int _numberOfIterations; // or should be a strategy that will be in charge of stopping.
  final String foldColumnForTE = "custom_fold";
  

  public GridSearchTEParamsSelectionStrategy(Frame data, Algo[] evaluationAlgos, int numberOfIterations, String responseColumn, String[] columnsToEncode, boolean theBiggerTheBetter, long seed) {
    _evaluationAlgos = evaluationAlgos;
    _seed = seed;
    
    int nfolds = 5; // we might want to search for this parameter as well
    addKFoldColumn(data, foldColumnForTE, nfolds, _seed);
    _inputData = data;
    _numberOfIterations = numberOfIterations;
    _responseColumn = responseColumn;
    _columnsToEncode = columnsToEncode;
    _theBiggerTheBetter = theBiggerTheBetter;
    
    //Get it as parameter ?
    _grid.put("_withBlending", new Boolean[]{true, false});
    _grid.put("_noise_level", new Double[]{0.0, 0.1, 0.01});
    _grid.put("_inflection_point", new Integer[]{1, 2, 3, 5, 10, 50, 100});
    _grid.put("_smoothing", new Double[]{5.0, 10.0, 20.0});
    _grid.put("_holdoutType", new Byte[]{TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, TargetEncoder.DataLeakageHandlingStrategy.KFold}); // only LeaveOneOut and KFOLD as we don't want to split holdout from training frame of the whole automl process

    randomSelector = new RandomSelector(_grid, _seed);
    
    evaluatedQueue = new PriorityQueue<>(numberOfIterations, new EvaluatedComparator(theBiggerTheBetter)); //TODO fix numberOfIterations
  }
  
  @Override
  public TargetEncodingParams getBestParams() {
    return getBestParamsWithEvaluation().getItem();
  }

  public Evaluated<TargetEncodingParams> getBestParamsWithEvaluation() {
    // First we need to do stratified sampling

    for(int attempt = 0 ; attempt < _numberOfIterations; attempt++) {

      GridEntry selected = randomSelector.getNext(); // Maybe we don't need to have a GridEntry

      TargetEncodingParams param = new TargetEncodingParams(selected.getItem());

      double evaluationResult = evaluator.evaluate(param, getEvaluationAlgos(), getInputData(), getResponseColumn(), foldColumnForTE, getColumnsToEncode());
      evaluatedQueue.add(new Evaluated<>(param, evaluationResult));
    }

    Evaluated<TargetEncodingParams> targetEncodingParamsEvaluated = evaluatedQueue.peek();

    return targetEncodingParamsEvaluated;
  }

  public String getResponseColumn() {
    return _responseColumn;
  }
  
  public Frame getInputData() {
    return _inputData;
  }

  public Algo[] getEvaluationAlgos() {
    return _evaluationAlgos;
  }

  public String[] getColumnsToEncode() {
    return _columnsToEncode;
  }

  public boolean isTheBiggerTheBetter() {
    return _theBiggerTheBetter;
  }


  public static class Evaluated<P> {
    public P getItem() {
      return _item;
    }

    public double getScore() {
      return _score;
    }

    P _item;
    private double _score;

    public Evaluated(P item, double score) {
      _item = item;
      _score = score;
    }
  }

  public static class EvaluatedComparator implements Comparator<Evaluated>{
    private boolean _theBiggerTheBetter;
    
    public EvaluatedComparator(boolean theBiggerTheBetter) {
      _theBiggerTheBetter = theBiggerTheBetter;
    }

    @Override
    public int compare(Evaluated o1, Evaluated o2) {
      int inverseTerm = _theBiggerTheBetter ? -1 : 1;
      return inverseTerm * Double.compare(o1.getScore(), o2.getScore()); // TODO the bigger the better or the other way around
    }
  }

  public static class RandomSelector {

    Map<String, Object[]> _grid = new HashMap<>();
    String[] _dimensionNames;
    private int _spaceSize;
    private Set<Integer> _visitedPermutationHashes = new LinkedHashSet<>();
    Random randomGen;

    public RandomSelector(Map<String, Object[]> grid, long seed) {
      _grid = grid;
      randomGen = new Random(seed);
      _dimensionNames = _grid.keySet().toArray(new String[0]);
      _spaceSize = calculateSpaceSize(_grid);
    }
    
    public RandomSelector(Map<String, Object[]> grid) {
      this(grid, -1);
    }
    
    GridEntry getNext() {
      Map<String, Object> _next = new HashMap<>();
      int[] indices = nextIndices();
      for (int i = 0; i < indices.length; i++) {
        _next.put(_dimensionNames[i], _grid.get(_dimensionNames[i])[indices[i]]);
      }
      return new GridEntry(_next, hashIntArray(indices));
    }

    private int[] nextIndices() {
      int[] chosenIndices =  new int[_dimensionNames.length];

      int hashOfIndices = 0;
      do {
        for (int i = 0; i < _dimensionNames.length; i++) {
          String name = _dimensionNames[i];
          int dimensionLength = _grid.get(name).length;
          chosenIndices[i] = randomGen.nextInt(dimensionLength);
        }
        hashOfIndices = hashIntArray(chosenIndices);
      } while (_visitedPermutationHashes.contains(hashOfIndices) && _visitedPermutationHashes.size() != _spaceSize);
      _visitedPermutationHashes.add(hashOfIndices);

      if(_visitedPermutationHashes.size() == _spaceSize) System.out.println("Whole search space has been discovered. Continue selecting randomly.");
      return chosenIndices;
    }

    private int calculateSpaceSize(Map<String, Object[]> grid) {
      String[] dimensionNames = grid.keySet().toArray(new String[0]);
      int spaceSize = 1;
      for (int i = 0; i < dimensionNames.length; i++) {
        String name = dimensionNames[i];
        int dimensionLength = grid.get(name).length;
        spaceSize *= dimensionLength;
      }
      return spaceSize;
    }

    public Set<Integer> getVisitedPermutationHashes() {
      return _visitedPermutationHashes;
    }

    private int hashIntArray(int[] ar) {
      Integer[] hashMe = new Integer[ar.length];
      for (int i = 0; i < ar.length; i++)
        hashMe[i] = ar[i] * _grid.get(_dimensionNames[i]).length;
      return Arrays.deepHashCode(hashMe);
    }
  }

  public static class GridEntry {
    Map<String, Object> _item;
    int _hash;

    public Map<String, Object> getItem() {
      return _item;
    }

    public int getHash() {
      return _hash;
    }
    
    public GridEntry(Map<String, Object> item, int hash) {
      _item = item;
      _hash = hash;
      
    }
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
