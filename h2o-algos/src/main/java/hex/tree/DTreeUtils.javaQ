package hex.gbm;

import hex.gbm.DTree.TreeModel.CompressedTree;

/** Toolkit class providing various useful methods for tree models */
public class DTreeUtils {

  /**
   * Score given tree on the row of data.
   *
   * @param data row of data
   * @param preds array to hold resulting prediction
   * @param ts a tree representation (single regression tree, or multi tree)
   */
  public static void scoreTree(double data[], float preds[], CompressedTree[] ts) {
    for( int c=0; c<ts.length; c++ )
      if( ts[c] != null )
        preds[ts.length==1?0:c+1] += ts[c].score(data);
  }
}
