package hex.tree;

import java.util.Arrays;
import java.util.Random;

import water.*;
import water.fvec.C0DChunk;
import water.fvec.Chunk;

/**
 * Computing oob scores over all trees and rows
 * and reconstructing <code>ntree_id, oobt</code> fields in given frame.
 *
 * <p>It prepares voter per tree and also marks
 * rows which were consider out-of-bag.</p>
 */
/* package */ public class ReconstructTreeState extends DTreeScorer<ReconstructTreeState> {

  /* @IN */ final protected double _rate;
  /* @IN */ final protected boolean _OOBEnabled;

  public ReconstructTreeState(int ncols, int nclass, SharedTree st, double rate, Key[][] treeKeys, boolean oob) {
    super(ncols,nclass,st,treeKeys);
    _rate = rate;
    _OOBEnabled = oob;
  }

  @Override public void map(Chunk[] chks) {
    double[] data = new double[_ncols];
    double [] preds = new double[_nclass+1];
    int ntrees = _trees.length;
    Chunk weight = _st.hasWeightCol() ? _st.chk_weight(chks) : new C0DChunk(1, chks[0]._len);
    Chunk oobt = _st.chk_oobt(chks);
    Chunk resp = _st.chk_resp(chks);
    for( int tidx=0; tidx<ntrees; tidx++) { // tree
      // OOB RNG for this tree
      Random rng = rngForTree(_trees[tidx], oobt.cidx());
      for (int row = 0; row< oobt._len; row++) {
        double w = weight.atd(row);
        if (w==0) continue;
        double y = resp.atd(row);
        if (Double.isNaN(y)) continue;

        boolean rowIsOOB = _OOBEnabled && rng.nextFloat() >= _rate;
        if( !_OOBEnabled || rowIsOOB) {
          // Make a prediction
          for (int i=0;i<_ncols;i++) data[i] = chks[i].atd(row);
          Arrays.fill(preds, 0);
          score0(data, preds, _trees[tidx]);
          if (_nclass==1) preds[1]=preds[0]; // Only for regression, keep consistency
          // Write tree predictions
          for (int c=0;c<_nclass;c++) { // over all class
            double prediction = preds[1+c];
            if (preds[1+c] != 0) {
              Chunk ctree = _st.chk_tree(chks, c);
              double wcount = oobt.atd(row);
              if (_OOBEnabled && _nclass >= 2)
                ctree.set(row, (float) (ctree.atd(row)*wcount + prediction)/(wcount+w)); //store avg prediction
              else
                ctree.set(row, (float) (ctree.atd(row) + prediction));
            }
          }
          // Mark oob row and store number of trees voting for this row
          if (rowIsOOB)
            oobt.set(row, oobt.atd(row)+w);
        }
      }
    }
    _st = null;
  }

  private Random rngForTree(CompressedTree[] ts, int cidx) {
    return ts[0].rngForChunk(cidx); // k-class set of trees shares the same random number
  }
}
