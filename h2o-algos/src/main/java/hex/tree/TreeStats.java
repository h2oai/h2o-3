package hex.tree;

import water.Iced;

public class TreeStats extends Iced {
  public int _min_depth = 0;
  public int _max_depth = 0;
  public float _mean_depth;
  public int _min_leaves = 0; 
  public int _max_leaves = 0;
  public float _mean_leaves;
  public long _byte_size;
  public int _num_trees = 0;

  transient long _sum_depth = 0;
  transient long _sum_leaves = 0;
  public boolean isValid() { return _min_depth <= _max_depth; }
  public void updateBy(DTree tree) {
    if( tree == null ) return;
    if( _min_depth == 0 || _min_depth > tree._depth ) _min_depth = tree._depth;
    if( _max_depth == 0 || _max_depth < tree._depth ) _max_depth = tree._depth;
    if( _min_leaves == 0 || _min_leaves > tree._leaves) _min_leaves = tree._leaves;
    if( _max_leaves == 0 || _max_leaves < tree._leaves) _max_leaves = tree._leaves;
    _sum_depth += tree._depth;
    _sum_leaves += tree._leaves;
    _num_trees++;
    updateMeans();
  }

  public void setNumTrees(int i) { _num_trees = i; }

  @Override
  public String toString() {
    return "TreeStats{" +
           "_min_depth=" + _min_depth +
           ", _max_depth=" + _max_depth +
           ", _mean_depth=" + _mean_depth +
           '}';
  }
  
  private void updateMeans() {
    _mean_depth = ((float) _sum_depth / _num_trees);
    _mean_leaves = ((float) _sum_leaves / _num_trees);
  }
  
  public void mergeWith(TreeStats otherTreeStats) {
    if (otherTreeStats._min_depth < this._min_depth) this._min_depth = otherTreeStats._min_depth;
    if (otherTreeStats._max_depth > this._max_depth) this._max_depth = otherTreeStats._max_depth;
    if (otherTreeStats._min_leaves < this._min_leaves) this._min_leaves = otherTreeStats._min_leaves;
    if (otherTreeStats._max_leaves > this._max_leaves) this._max_leaves = otherTreeStats._max_leaves;
    this._byte_size += otherTreeStats._byte_size;
    this._num_trees += otherTreeStats._num_trees;
    this._sum_depth += otherTreeStats._sum_depth;
    this._sum_leaves += otherTreeStats._sum_leaves;
    updateMeans();
  }
}
