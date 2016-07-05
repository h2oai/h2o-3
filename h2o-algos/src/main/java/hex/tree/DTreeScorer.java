package hex.tree;

import water.*;

public abstract class DTreeScorer<T extends DTreeScorer<T>> extends MRTask<T> {
  protected final int _ncols;
  protected final int _nclass;
  protected final int _skip;
  protected final Key[][] _treeKeys;
  protected transient CompressedTree[][] _trees;
  protected SharedTree _st;

  public DTreeScorer(int ncols, int nclass, SharedTree st, Key[][] treeKeys) {
    _ncols = ncols;
    _nclass = nclass;
    _treeKeys = treeKeys;
    _st = st;
    _skip = _st.numSpecialCols();
  }
  protected int ntrees() { return _trees.length; }

  @Override protected final void setupLocal() {
    int ntrees = _treeKeys.length;
    _trees = new CompressedTree[ntrees][];
    for (int t=0; t<ntrees; t++) {
      Key[] treek = _treeKeys[t];
      _trees[t] = new CompressedTree[treek.length];
      // FIXME remove get by introducing fetch class for all trees
      for (int i=0; i<treek.length; i++)
        if (treek[i]!=null)
          _trees[t][i] = DKV.get(treek[i]).get();
    }
  }

  protected void score0(double data[], double preds[], CompressedTree[] ts) { scoreTree(data, preds, ts); }

  /** Score given tree on the row of data.
   *  @param data row of data
   *  @param preds array to hold resulting prediction
   *  @param ts a tree representation (single regression tree, or multi tree)  */
  public static void scoreTree(double data[], double preds[], CompressedTree[] ts) {
    for( int c=0; c<ts.length; c++ )
      if( ts[c] != null )
        preds[ts.length==1?0:c+1] += ts[c].score(data);
  }
}
