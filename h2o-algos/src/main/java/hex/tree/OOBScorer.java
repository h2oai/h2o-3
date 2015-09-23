package hex.tree;

import java.util.Arrays;
import java.util.Random;

import water.*;
import water.fvec.Chunk;

/**
 * Computing oob scores over all trees and rows
 * and reconstructing <code>ntree_id, oobt</code> fields in given frame.
 *
 * <p>It prepares voter per tree and also marks
 * rows which were consider out-of-bag.</p>
 */
/* package */ public class OOBScorer extends DTreeScorer<OOBScorer> {

  /* @IN */ final protected float _rate;

  public OOBScorer(int ncols, int nclass, int skip, float rate, Key[][] treeKeys) {
    super(ncols,nclass,skip,treeKeys);
    _rate = rate;
  }

  @Override public void map(Chunk[] chks) {
    double[] data = new double[_ncols];
    double [] preds = new double[_nclass+1];
    int ntrees = _trees.length;
    Chunk coobt = chk_oobt(chks);
    Chunk cys   = chk_resp(chks);
    for( int tidx=0; tidx<ntrees; tidx++) { // tree
      // OOB RNG for this tree
      Random rng = rngForTree(_trees[tidx], coobt.cidx());
      for (int row=0; row<coobt._len; row++) {
        if( rng.nextFloat() >= _rate || Double.isNaN(cys.atd(row)) ) {
          // Make a prediction
          for (int i=0;i<_ncols;i++) data[i] = chks[i].atd(row);
          Arrays.fill(preds, 0);
          score0(data, preds, _trees[tidx]);
          if (_nclass==1) preds[1]=preds[0]; // Only for regression, keep consistency
          // Write tree predictions
          for (int c=0;c<_nclass;c++) { // over all class
            double prediction = preds[1+c];
            if (preds[1+c] != 0) {
              Chunk ctree = chk_tree(chks, c);
              long count = coobt.at8(row);
              if (_nclass >= 2)
                ctree.set(row, (float) (ctree.atd(row)*count + prediction)/(count+1)); //store avg prediction
              else
                ctree.set(row, (float) (ctree.atd(row) + prediction));
            }
          }
          // Mark oob row and store number of trees voting for this row
          coobt.set(row, coobt.atd(row)+1);
        }
      }
    }
  }

  private Random rngForTree(CompressedTree[] ts, int cidx) {
    return ts[0].rngForChunk(cidx); // k-class set of trees shares the same random number
  }
}
