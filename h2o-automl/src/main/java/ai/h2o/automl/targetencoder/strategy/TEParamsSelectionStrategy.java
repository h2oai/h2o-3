package ai.h2o.automl.targetencoder.strategy;

import ai.h2o.automl.targetencoder.TargetEncodingParams;
import hex.ModelBuilder;
import water.Iced;
import water.fvec.Frame;
import water.util.Log;
import water.util.TwoDimTable;

import java.util.*;

public abstract class TEParamsSelectionStrategy extends Iced {

  public abstract TargetEncodingParams getBestParams(ModelBuilder modelBuilder);


  public static class Evaluated<P> extends Iced<Evaluated<P>> {
    public P getItem() {
      return _item;
    }

    public double getScore() {
      return _score;
    }

    transient P _item;
    private double _score;

    // Zero-based index of evaluation run
    private int _index;

    public Evaluated(P item, double score, int index) {
      _item = item;
      _score = score;
      _index = index;
    }

    public int getIndex() {
      return _index;
    }
  }

  public static class EvaluatedComparator extends Iced<EvaluatedComparator> implements Comparator<Evaluated> {
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

  public static class RandomGridEntrySelector extends Iced<RandomGridEntrySelector> {

    HashMap<String, Object[]> _grid;
    String[] _dimensionNames;

    private int _spaceSize;
    transient private Set<Integer> _visitedPermutationHashes = new LinkedHashSet<>(); // what are the cases of retrieving automl from DKV?
    Random _randomGen;

    public RandomGridEntrySelector(HashMap<String, Object[]> grid, long seed) {
      _grid = grid;
      _randomGen = new Random(seed);
      _dimensionNames = _grid.keySet().toArray(new String[0]);
      _spaceSize = calculateSpaceSize(_grid);
    }

    public RandomGridEntrySelector(HashMap<String, Object[]> grid) {
      this(grid, -1);
    }

    public GridEntry getNext() throws GridSearchCompleted {
      Map<String, Object> _next = new HashMap<>();
      int[] indices = nextIndices();
      for (int i = 0; i < indices.length; i++) {
        _next.put(_dimensionNames[i], _grid.get(_dimensionNames[i])[indices[i]]);
      }
      return new GridEntry(_next, hashIntArray(indices));
    }

    //This approach is not very efficient as over time we will start to hit cache more often and selecting unseen combination will become harder.
    private int[] nextIndices() throws GridSearchCompleted {
      int[] chosenIndices =  new int[_dimensionNames.length];

      int hashOfIndices = 0;
      do {
        for (int i = 0; i < _dimensionNames.length; i++) {
          String name = _dimensionNames[i];
          int dimensionLength = _grid.get(name).length;
          int chosenIndex = _randomGen.nextInt(dimensionLength);
          chosenIndices[i] = chosenIndex;
        }
        hashOfIndices = hashIntArray(chosenIndices);
      } while (_visitedPermutationHashes.contains(hashOfIndices) && _visitedPermutationHashes.size() != _spaceSize);
      _visitedPermutationHashes.add(hashOfIndices);

      if(_visitedPermutationHashes.size() == _spaceSize + 1) { // Stop when we try to visit more that size of space
        Log.info("Whole search space has been discovered (" + _visitedPermutationHashes.size() + " grid entries). Stopping search.");
        throw new GridSearchCompleted();
      }
      return chosenIndices;
    }

    public int spaceSize() {
      return _spaceSize;
    }

    public HashMap<String, Object[]> grid() {
      return _grid;
    }

    public static class GridSearchCompleted extends Exception{ }

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
