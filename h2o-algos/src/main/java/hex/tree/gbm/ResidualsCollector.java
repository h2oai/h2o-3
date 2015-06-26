package hex.tree.gbm;

import java.util.Arrays;

import hex.tree.DTreeScorer;
import water.Key;
import water.fvec.Chunk;

public class ResidualsCollector extends DTreeScorer<ResidualsCollector> {

  public ResidualsCollector(int ncols, int nclass, int skip, Key[][] treeKeys) {
    super(ncols, nclass, skip, treeKeys);
  }

  @Override public void map(Chunk[] chks) {
    double[] data = new double[_ncols];
    double [] preds = new double[_nclass+1];
    int ntrees = ntrees();
    Chunk cys   = chk_resp(chks);
    for( int tidx=0; tidx<ntrees; tidx++) { // tree
      for (int row=0; row<cys._len; row++) {
        // Make a prediction
        for (int i=0;i<_ncols;i++) data[i] = chks[i].atd(row);
        Arrays.fill(preds, 0);
        score0(data, preds, _trees[tidx]);
        if (_nclass==1) preds[1]=preds[0]; // regression shortcut
        // Write tree predictions
        for (int c=0;c<_nclass;c++) { // over all class
          if (preds[1+c] != 0) {
            Chunk ctree = chk_tree(chks, c);
            ctree.set(row, (float)(ctree.atd(row) + preds[1+c]));
          }
        }
      }
    }
  }
}
