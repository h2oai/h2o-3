package hex.tree;

import water.*;
import water.fvec.Chunk;

public abstract class DTreeScorer<T extends DTreeScorer<T>> extends MRTask<T> {
  protected final int _ncols;
  protected final int _nclass;
  protected final int _skip;
  protected final Key[][] _treeKeys;
  protected transient CompressedTree[][] _trees;

  public DTreeScorer(int ncols, int nclass, int skip, Key[][] treeKeys) {
    _ncols = ncols;
    _nclass = nclass;
    _treeKeys = treeKeys;
    _skip = skip;
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

  public final Chunk chk_oobt(Chunk chks[]) { return chks[_ncols+1+_nclass+_nclass+_nclass+_skip]; }
  public final Chunk chk_tree(Chunk chks[], int c) { return chks[_ncols+1+c+_skip]; }
  public final Chunk chk_resp( Chunk chks[] ) { return chks[_ncols]; }

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
