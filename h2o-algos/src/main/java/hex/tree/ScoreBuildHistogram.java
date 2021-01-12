package hex.tree;

import hex.genmodel.utils.DistributionFamily;
import water.H2O.H2OCountedCompleter;
import water.MRTask;
import water.fvec.Frame;

/**  Score and Build Histogram
 *
 * <p>Fuse 2 conceptual passes into one:
 *
 * <dl>
 *
 * <dt>Pass 1:</dt><dd>Score a prior partially-built tree model, and make new Node assignments to
 * every row.  This involves pulling out the current assigned DecidedNode,
 * "scoring" the row against that Node's decision criteria, and assigning the
 * row to a new child UndecidedNode (and giving it an improved prediction).</dd>
 *
 * <dt>Pass 2:</dt><dd>Build new summary DHistograms on the new child UndecidedNodes
 * every row got assigned into.  Collect counts, mean, variance, min,
 * max per bin, per column.</dd>
 * </dl>
 *
 * <p>The result is a set of DHistogram arrays; one DHistogram array for each
 * unique 'leaf' in the tree being histogramed in parallel.  These have node
 * ID's (nids) from 'leaf' to 'tree._len'.  Each DHistogram array is for all
 * the columns in that 'leaf'.
 *
 * <p>The other result is a prediction "score" for the whole dataset, based on
 * the previous passes' DHistograms.
 */
public class ScoreBuildHistogram extends MRTask<ScoreBuildHistogram> {
  final int   _k;    // Which tree
  final int   _ncols;// Active feature columns
  final int   _nbins;// Numerical columns: Number of bins in each histogram
  final DTree _tree; // Read-only, shared (except at the histograms in the Nodes)
  final int   _leaf; // Number of active leaves (per tree)
  // Histograms for every tree, split & active column
  DHistogram _hcs[/*tree-relative node-id*/][/*column*/];
  final DistributionFamily _family;
  final int _weightIdx;
  final int _workIdx;
  final int _nidIdx;
  final int _upliftIdx;

  public ScoreBuildHistogram(H2OCountedCompleter cc, int k, int ncols, int nbins, DTree tree, int leaf, DHistogram hcs[][], DistributionFamily family, int weightIdx, int workIdx, int nidIdx, int upliftIdx) {
    super(cc);
    _k    = k;
    _ncols= ncols;
    _nbins= nbins;
    _tree = tree;
    _leaf = leaf;
    _hcs  = hcs;
    _family = family;
    _weightIdx = weightIdx;
    _workIdx = workIdx;
    _nidIdx = nidIdx;
    _upliftIdx = upliftIdx;
  }
  
  public ScoreBuildHistogram dfork2(byte[] types, Frame fr, boolean run_local) {
    return dfork(types,fr,run_local);
  }

  /** Marker for already decided row. */
  static public final int DECIDED_ROW = -1;
  /** Marker for sampled out rows */
  static public final int OUT_OF_BAG = -2;
  /** Marker for a fresh tree */
  static public final int UNDECIDED_CHILD_NODE_ID = -1; //Integer.MIN_VALUE;

  static public final int FRESH = 0;

  static public boolean isOOBRow(int nid)     { return nid <= OUT_OF_BAG; }
  static public boolean isDecidedRow(int nid) { return nid == DECIDED_ROW; }
  static public int     oob2Nid(int oobNid)   { return -oobNid + OUT_OF_BAG; }
  static public int     nid2Oob(int nid)      { return -nid + OUT_OF_BAG; }

  // Once-per-node shared init
  @Override public void setupLocal( ) {
    // Init all the internal tree fields after shipping over the wire
    _tree.init_tree();
    // Allocate local shared memory histograms
    for( int l=_leaf; l<_tree._len; l++ ) {
      DTree.UndecidedNode udn = _tree.undecided(l);
      DHistogram hs[] = _hcs[l-_leaf];
      int sCols[] = udn._scoreCols;
      if( sCols != null ) { // Sub-selecting just some columns?
        for( int col : sCols ) // For tracked cols
          hs[col].init();
      } else {                 // Else all columns
        for( int j=0; j<_ncols; j++) // For all columns
          if( hs[j] != null )        // Tracking this column?
            hs[j].init();
      }
    }
  }
  
  @Override public void reduce( ScoreBuildHistogram sbh ) {
    // Merge histograms
    if( sbh._hcs == _hcs )
      return; // Local histograms all shared; free to merge
    // Distributed histograms need a little work
    for( int i=0; i<_hcs.length; i++ ) {
      DHistogram hs1[] = _hcs[i], hs2[] = sbh._hcs[i];
      if( hs1 == null ) _hcs[i] = hs2;
      else if( hs2 != null )
        for( int j=0; j<hs1.length; j++ )
          if( hs1[j] == null ) hs1[j] = hs2[j];
          else if( hs2[j] != null )
            hs1[j].add(hs2[j]);
    }
  }
}
