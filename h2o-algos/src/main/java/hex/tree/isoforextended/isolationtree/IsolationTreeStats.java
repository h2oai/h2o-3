package hex.tree.isoforextended.isolationtree;

public class IsolationTreeStats {
    public int _minDepth = 0;
    public int _maxDepth = 0;
    public float _meanDepth;

    public int _minLeaves = 0;
    public int _maxLeaves = 0;
    public float _meanLeaves;

    public int _minIsolated = -1;
    public int _maxIsolated = -1;
    public float _meanIsolated;

    public int _minNotIsolated = -1;
    public int _maxNotIsolated = -1;
    public float _meanNotIsolated;

    public int _minZeroSplits = -1;
    public int _maxZeroSplits = -1;
    public float _meanZeroSplits;

    public int _numTrees = 0;

    private long _sumDepth = 0;
    private long _sumLeaves = 0;
    private long _sumIsolated = 0;
    private long _sumNotIsolated = 0;
    private long _sumZeroSplits = 0;

    public void updateBy(IsolationTree tree) {
        if (tree == null) return;

        if (_minDepth == 0 || _minDepth > tree.getDepth()) _minDepth = tree.getDepth();
        if (_maxDepth == 0 || _maxDepth < tree.getDepth()) _maxDepth = tree.getDepth();

        if (_minLeaves == 0 || _minLeaves > tree.getLeaves()) _minLeaves = tree.getLeaves();
        if (_maxLeaves == 0 || _maxLeaves < tree.getLeaves()) _maxLeaves = tree.getLeaves();

        if (_minIsolated == -1 || _minIsolated > tree.getIsolatedPoints()) _minIsolated = tree.getIsolatedPoints();
        if (_maxIsolated == -1 || _maxIsolated < tree.getIsolatedPoints()) _maxIsolated = tree.getIsolatedPoints();

        if (_minNotIsolated == -1 || _minNotIsolated > tree.getNotIsolatedPoints()) _minNotIsolated = tree.getNotIsolatedPoints();
        if (_maxNotIsolated == -1 || _maxNotIsolated < tree.getNotIsolatedPoints()) _maxNotIsolated = tree.getNotIsolatedPoints();

        if (_minZeroSplits == -1 || _minZeroSplits > tree.getZeroSplits()) _minZeroSplits = tree.getZeroSplits();
        if (_maxZeroSplits == -1 || _maxZeroSplits < tree.getZeroSplits()) _maxZeroSplits = tree.getZeroSplits();

        _sumDepth += tree.getDepth();
        _sumLeaves += tree.getLeaves();
        _sumIsolated += tree.getIsolatedPoints();
        _sumNotIsolated += tree.getNotIsolatedPoints();
        _sumZeroSplits += tree.getZeroSplits();
        _numTrees++;
        updateMeans();
    }

    private void updateMeans() {
        _meanDepth = ((float) _sumDepth / _numTrees);
        _meanLeaves = ((float) _sumLeaves / _numTrees);
        _meanIsolated = ((float) _sumIsolated / _numTrees);
        _meanNotIsolated = ((float) _sumNotIsolated / _numTrees);
        _meanZeroSplits = ((float) _sumZeroSplits / _numTrees);
    }
}
