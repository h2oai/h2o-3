package hex.tree;

import java.util.Arrays;
import java.util.Random;

import water.*;
import water.fvec.Chunk;
import water.fvec.C0DChunk;
import water.fvec.ChunkAry;

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

  @Override public void map(ChunkAry chks) {
    double[] data = new double[_ncols];
    double [] preds = new double[_nclass+1];
    int ntrees = _trees.length;
    Chunk weight = _st.hasWeightCol() ? _st.chk_weight(chks) : C0DChunk.makeConstChunk(1);
    int oobt = _st.idx_oobt();
    Chunk resp = _st.chk_resp(chks);
    for( int tidx=0; tidx<ntrees; tidx++) { // tree
      // OOB RNG for this tree
      Random rng = rngForTree(_trees[tidx], chks.cidx());
      for (int row = 0; row< chks._len; row++) {
        double w = weight.atd(row);
        if (w==0) continue;
        double y = resp.atd(row);
        if (Double.isNaN(y)) continue;

        boolean rowIsOOB = _OOBEnabled && rng.nextFloat() >= _rate;
        if( !_OOBEnabled || rowIsOOB) {
          // Make a prediction
          for (int i=0;i<_ncols;i++) data[i] = chks.atd(row,i);
          Arrays.fill(preds, 0);
          score0(data, preds, _trees[tidx]);
          if (_nclass==1) preds[1]=preds[0]; // Only for regression, keep consistency
          // Write tree predictions
          for (int c=0;c<_nclass;c++) { // over all class
            double prediction = preds[1+c];
            if (preds[1+c] != 0) {
              int ctree = _st.idx_tree(c);
              double wcount = chks.atd(row,oobt);
              if (_OOBEnabled && _nclass >= 2)
                chks.set(row, ctree, (float) (chks.atd(row,ctree)*wcount + prediction)/(wcount+w)); //store avg prediction
              else
                chks.set(row, ctree, (float) (chks.atd(row, ctree) + prediction));
            }
          }
          // Mark oob row and store number of trees voting for this row
          if (rowIsOOB)
            chks.set(row, oobt, chks.atd(row,oobt)+w);
        }
      }
    }
    _st = null;
  }

  private Random rngForTree(CompressedTree[] ts, int cidx) {
    return ts[0].rngForChunk(cidx); // k-class set of trees shares the same random number
  }
}
