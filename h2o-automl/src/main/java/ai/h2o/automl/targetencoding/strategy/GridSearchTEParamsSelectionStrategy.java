package ai.h2o.automl.targetencoding.strategy;

import ai.h2o.automl.targetencoding.TargetEncoder;
import ai.h2o.automl.targetencoding.TargetEncodingParams;
import hex.ModelBuilder;
import water.Iced;
import water.fvec.Frame;
import water.util.TwoDimTable;

import java.util.*;

/**
 * For now it should be named RandomGridSearchTEParamsStrategy
 */
public class GridSearchTEParamsSelectionStrategy extends TEParamsSelectionStrategy {

  private Frame _inputData;
  private String _responseColumn;
  private String[] _columnsToEncode; // we might want to search for subset as well
  private boolean _theBiggerTheBetter;
  private long _seed;

  private RandomSelector randomSelector;
  private PriorityQueue<Evaluated<TargetEncodingParams>> evaluatedQueue;
  private GridSearchTEEvaluator evaluator = new GridSearchTEEvaluator();
  
  private int _numberOfIterations; // or should be a strategy that will be in charge of stopping.

  public GridSearchTEParamsSelectionStrategy(Frame data, int numberOfIterations, String responseColumn, String[] columnsToEncode, boolean theBiggerTheBetter, long seed) {
    _seed = seed;
    
    _inputData = data;
    _numberOfIterations = numberOfIterations;
    _responseColumn = responseColumn;
    _columnsToEncode = columnsToEncode;
    _theBiggerTheBetter = theBiggerTheBetter;
    
    //Hardcoded. Get it as parameter to generalize ?
    HashMap<String, Object[]> _grid = new HashMap<>();
    _grid.put("_withBlending", new Boolean[]{true, false});
    _grid.put("_noise_level", new Double[]{0.0, 0.1, 0.01});
    _grid.put("_inflection_point", new Integer[]{1, 2, 3, 5, 10, 50, 100});
    _grid.put("_smoothing", new Double[]{5.0, 10.0, 20.0});
    _grid.put("_holdoutType", new Byte[]{TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut,TargetEncoder.DataLeakageHandlingStrategy.KFold}); // only LeaveOneOut and KFOLD as we don't want to split holdout from training frame of the whole automl process

    randomSelector = new RandomSelector(_grid, _seed);
    
    evaluatedQueue = new PriorityQueue<>(numberOfIterations, new EvaluatedComparator(theBiggerTheBetter));
  }
  
  @Override
  public TargetEncodingParams getBestParams(ModelBuilder modelBuilder) {
    return getBestParamsWithEvaluation(modelBuilder).getItem();
  }

  public Evaluated<TargetEncodingParams> getBestParamsWithEvaluation(ModelBuilder modelBuilder) {
    //TODO First we need to do stratified sampling

    for(int attempt = 0 ; attempt < _numberOfIterations; attempt++) {

      GridEntry selected = randomSelector.getNext(); // Maybe we don't need to have a GridEntry

      TargetEncodingParams param = new TargetEncodingParams(selected.getItem());

      ModelBuilder clonedModelBuilder = ModelBuilder.clone(modelBuilder);
      clonedModelBuilder.init(false); // in evaluator we assume that init() has been already called

      double evaluationResult = evaluator.evaluate(param, clonedModelBuilder, getColumnsToEncode(), _seed);
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

  public String[] getColumnsToEncode() {
    return _columnsToEncode;
  }

  public boolean isTheBiggerTheBetter() {
    return _theBiggerTheBetter;
  }


  public static class Evaluated<P> extends Iced<Evaluated<P>> {
    public P getItem() {
      return _item;
    }

    public double getScore() {
      return _score;
    }

    transient P _item; 
    private double _score;

    public Evaluated(P item, double score) {
      _item = item;
      _score = score;
    }
  }

  public static class EvaluatedComparator extends Iced<EvaluatedComparator> implements Comparator<Evaluated>{
    private boolean _theBiggerTheBetter;
    
    public EvaluatedComparator(boolean theBiggerTheBetter) {
      _theBiggerTheBetter = theBiggerTheBetter;
    }

    @Override
    public int compare(Evaluated o1, Evaluated o2) {
      int inverseTerm = _theBiggerTheBetter ? -1 : 1;
      return inverseTerm * Double.compare(o1.getScore(), o2.getScore());
    }
  }

  public static class RandomSelector extends Iced<RandomSelector> {

    HashMap<String, Object[]> _grid;
    String[] _dimensionNames;
    private int _spaceSize;
    transient private Set<Integer> _visitedPermutationHashes = new LinkedHashSet<>(); // what are the cases of retrieving automl from DKV?
    Random _randomGen;

    public RandomSelector(HashMap<String, Object[]> grid, long seed) {
      _grid = grid;
      _randomGen = new Random(seed);
      _dimensionNames = _grid.keySet().toArray(new String[0]);
      _spaceSize = calculateSpaceSize(_grid);
    }
    
    public RandomSelector(HashMap<String, Object[]> grid) {
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

    //This approach is not very efficient as over time we will start to hit cache more often and selecting unseen combination will become harder.
    private int[] nextIndices() {
      int[] chosenIndices =  new int[_dimensionNames.length];

      int hashOfIndices = 0;
      do {
        for (int i = 0; i < _dimensionNames.length; i++) {
          String name = _dimensionNames[i];
//          System.out.println("Dimension:" + i +":" + name);
          int dimensionLength = _grid.get(name).length;
          int chosenIndex = _randomGen.nextInt(dimensionLength);
          chosenIndices[i] = chosenIndex;
        }
        hashOfIndices = hashIntArray(chosenIndices);
      } while (_visitedPermutationHashes.contains(hashOfIndices) && _visitedPermutationHashes.size() != _spaceSize /*&& skipIndices(chosenIndices)*/);
      _visitedPermutationHashes.add(hashOfIndices);

      if(_visitedPermutationHashes.size() == _spaceSize) {
        System.out.println("Whole search space has been discovered. Continue selecting randomly.");
      }
      return chosenIndices;
    }
    
    // overwrite it with custom combinations
    protected boolean skipIndices(int[] possibleIndices) {
      if (possibleIndices[0] == 0 ) {
        _visitedPermutationHashes.add(hashIntArray(possibleIndices));
      }
      return true;
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
        hashMe[i] = ar[i];
//        hashMe[i] = ar[i] * _grid.get(_dimensionNames[i]).length;
      return Arrays.deepHashCode(hashMe);
    }
  }

  public static class GridEntry extends Iced<GridEntry>{
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
