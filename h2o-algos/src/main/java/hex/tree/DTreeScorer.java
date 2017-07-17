package hex.tree;

import water.*;

public abstract class DTreeScorer<T extends DTreeScorer<T>> extends MRTask<T> {
  protected final int _ncols;
  protected final int _nclass;
  protected final int _skip;
  protected final CompressedForest _cforest;
  protected transient CompressedForest.LocalCompressedForest _forest;
  protected SharedTree _st;

  public DTreeScorer(int ncols, int nclass, SharedTree st, CompressedForest cforest) {
    _ncols = ncols;
    _nclass = nclass;
    _cforest = cforest;
    _st = st;
    _skip = _st.numSpecialCols();
  }

  protected int ntrees() { return _cforest.ntrees(); }

  @Override protected final void setupLocal() {
    _forest = _cforest.fetch();
  }

  protected void score0(double data[], double preds[], int tidx) { _forest.scoreTree(data, preds, tidx); }

}
