package hex.gbm;

import water.Iced;

public class TreeStats extends Iced {
  public int   _minDepth = Integer.MAX_VALUE;
  public int   _maxDepth = Integer.MIN_VALUE;
  public float _meanDepth;
  public int   _minLeaves = Integer.MAX_VALUE;
  public int   _maxLeaves = Integer.MIN_VALUE;
  public float _meanLeaves;

  transient long _sumDepth  = 0;
  transient long _sumLeaves = 0;
  transient int  _numTrees = 0;
  public boolean isValid() { return _minDepth <= _maxDepth; }
  public void updateBy(DTree[] ktrees) {
    if( ktrees==null ) return;
    for( int i=0; i<ktrees.length; i++ ) {
      DTree tree = ktrees[i];
      if( tree == null ) continue;
      if( _minDepth  > tree._depth ) _minDepth  = tree._depth;
      if( _maxDepth  < tree._depth ) _maxDepth  = tree._depth;
      if( _minLeaves > tree._leaves) _minLeaves = tree._leaves;
      if( _maxLeaves < tree._leaves) _maxLeaves = tree._leaves;
      _sumDepth  += tree._depth;
      _sumLeaves += tree._leaves;
      _numTrees++;
      _meanDepth  = ((float)_sumDepth  / _numTrees);
      _meanLeaves = ((float)_sumLeaves / _numTrees);
    }
  }

  public void setNumTrees(int i) { _numTrees = i; }
}
