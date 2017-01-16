package hex.tree;

import hex.genmodel.utils.DistributionFamily;
import water.H2O.H2OCountedCompleter;
import water.MRTask;
import water.fvec.C0DChunk;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.ArrayUtils;

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
  final int   _nbins_cats;// Categorical columns: Number of bins in each histogram
  final DTree _tree; // Read-only, shared (except at the histograms in the Nodes)
  final int   _leaf; // Number of active leaves (per tree)
  // Histograms for every tree, split & active column
  DHistogram _hcs[/*tree-relative node-id*/][/*column*/];
  final DistributionFamily _family;
  final int _weightIdx;
  final int _workIdx;
  final int _nidIdx;

  public ScoreBuildHistogram(H2OCountedCompleter cc, int k, int ncols, int nbins, int nbins_cats, DTree tree, int leaf, DHistogram hcs[][], DistributionFamily family, int weightIdx, int workIdx, int nidIdx) {
    super(cc);
    _k    = k;
    _ncols= ncols;
    _nbins= nbins;
    _nbins_cats= nbins_cats;
    _tree = tree;
    _leaf = leaf;
    _hcs  = hcs;
    _family = family;
    _weightIdx = weightIdx;
    _workIdx = workIdx;
    _nidIdx = nidIdx;
  }

  public ScoreBuildHistogram dfork2(byte[] types, Frame fr, boolean run_local) {
    return dfork(types,fr,run_local);
  }

  /** Marker for already decided row. */
  static public final int DECIDED_ROW = -1;
  /** Marker for sampled out rows */
  static public final int OUT_OF_BAG = -2;
  /** Marker for rows without a response */
  static public final int MISSING_RESPONSE = -1;
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


  @Override
  public void map(Chunk[] chks) {
    final Chunk wrks = chks[_workIdx];
    final Chunk nids = chks[_nidIdx];
    final Chunk weight = _weightIdx>=0 ? chks[_weightIdx] : new C0DChunk(1, chks[0].len());

    // Pass 1: Score a prior partially-built tree model, and make new Node
    // assignments to every row.  This involves pulling out the current
    // assigned DecidedNode, "scoring" the row against that Node's decision
    // criteria, and assigning the row to a new child UndecidedNode (and
    // giving it an improved prediction).
    int nnids[] = new int[nids._len];
    if( _leaf > 0)            // Prior pass exists?
      score_decide(chks,nids,nnids);
    else                      // Just flag all the NA rows
      for( int row=0; row<nids._len; row++ ) {
        if( weight.atd(row) == 0) continue;
        if( isDecidedRow((int)nids.atd(row)) )
          nnids[row] = DECIDED_ROW;
      }

    // Pass 2: accumulate all rows, cols into histograms
//    if (_subset)
//      accum_subset(chks,wrks,weight,nnids); //for debugging - simple code
//    else
      accum_all   (chks,wrks,weight,nnids); //generally faster
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

  // Pass 1: Score a prior partially-built tree model, and make new Node
  // assignments to every row.  This involves pulling out the current
  // assigned DecidedNode, "scoring" the row against that Node's decision
  // criteria, and assigning the row to a new child UndecidedNode (and
  // giving it an improved prediction).
  protected void score_decide(Chunk chks[], Chunk nids, int nnids[]) {
    for( int row=0; row<nids._len; row++ ) { // Over all rows
      int nid = (int)nids.at8(row);          // Get Node to decide from
      if( isDecidedRow(nid)) {               // already done
        nnids[row] = nid-_leaf;              // will be negative, flagging a completed row
        continue;
      }
      // Score row against current decisions & assign new split
      boolean oob = isOOBRow(nid);
      if( oob ) nid = oob2Nid(nid); // sampled away - we track the position in the tree
      DTree.DecidedNode dn = _tree.decided(nid);
      if( dn == null || dn._split == null ) { // Might have a leftover non-split
        if( DTree.isRootNode(dn) ) { nnids[row] = nid-_leaf; continue; }
        nid = dn._pid;             // Use the parent split decision then
        int xnid = oob ? nid2Oob(nid) : nid;
        nids.set(row, xnid);
        nnids[row] = xnid-_leaf;
        dn = _tree.decided(nid); // Parent steers us
      }
      assert !isDecidedRow(nid);
      nid = dn.getChildNodeID(chks,row); // Move down the tree 1 level
      if( !isDecidedRow(nid) ) {
        if( oob ) nid = nid2Oob(nid); // Re-apply OOB encoding
        nids.set(row, nid);
      }
      nnids[row] = nid-_leaf;
    }
  }

// For debugging - simple code
  // All rows, some cols, accumulate histograms
  private void accum_subset(Chunk chks[], Chunk wrks, Chunk weight, int nnids[]) {
    for( int row=0; row<nnids.length; row++ ) { // Over all rows
      int nid = nnids[row];                     // Get Node to decide from
      if( nid >= 0 ) {        // row already predicts perfectly or OOB
        double w = weight.atd(row);
        if (w == 0) continue;
        double resp = wrks.atd(row);
        assert !Double.isNaN(wrks.atd(row)); // Already marked as sampled-away
        DHistogram nhs[] = _hcs[nid];
        int sCols[] = _tree.undecided(nid+_leaf)._scoreCols; // Columns to score (null, or a list of selected cols)
        if (sCols == null) {
          for(int col=0; col<nhs.length; ++col ) { //all columns
            if (nhs[col]!=null)
              nhs[col].incr(chks[col].atd(row), resp, w); // Histogram row/col
          }
        } else {
          for( int col : sCols )
            nhs[col].incr(chks[col].atd(row), resp, w); // Histogram row/col
        }
      }
    }
  }

  /**
   * All rows, all cols, accumulate histograms.  This is the hot hot inner
   * loop of GBM, so we do some non-standard optimizations.  The rows in this
   * chunk are spread out amongst a modest set of NodeIDs/splits.  Normally
   * we would visit the rows in row-order, but this visits the NIDs in random
   * order.  The hot-part of this code updates the histograms racily (via
   * atomic updates) - once-per-row.  This optimized version updates the
   * histograms once-per-NID, but requires pre-sorting the rows by NID.
   *
   * @param chks predictors, actual response (ignored)
   * @param wrks predicted response
   * @param weight observation weights
   * @param nnids node ids
   */
  private void accum_all(Chunk chks[], Chunk wrks, Chunk weight, int nnids[]) {
    // Sort the rows by NID, so we visit all the same NIDs in a row
    // Find the count of unique NIDs in this chunk
    int nh[] = new int[_hcs.length+1];
    for( int i : nnids )
      if( i >= 0 )
        nh[i+1]++;
    // Rollup the histogram of rows-per-NID in this chunk
    for( int i=0; i<_hcs.length; i++ ) nh[i+1] += nh[i];
    // Splat the rows into NID-groups
    int rows[] = new int[nnids.length];
    for( int row=0; row<nnids.length; row++ )
      if( nnids[row] >= 0 )
        rows[nh[nnids[row]]++] = row;
    // rows[] has Chunk-local ROW-numbers now, in-order, grouped by NID.
    // nh[] lists the start of each new NID, and is indexed by NID+1.
    final DHistogram hcs[][] = _hcs;
    if( hcs.length==0 ) return; // Unlikely fast cutout
    // Local temp arrays, no atomic updates.
    LocalHisto lh = new LocalHisto(Math.max(_nbins,_nbins_cats));
    final int cols = _ncols;
    final int hcslen = hcs.length;
    // these arrays will be re-used for all cols and nodes
    double[] ws = new double[chks[0]._len];
    double[] cs = new double[chks[0]._len];
    double[] ys = new double[chks[0]._len];
    weight.getDoubles(ws,0,ws.length);
    wrks.getDoubles(ys,0,ys.length);
    for (int c = 0; c < cols; c++) {
      boolean extracted = false;
      for (int n = 0; n < hcslen; n++) {
        int sCols[] = _tree.undecided(n + _leaf)._scoreCols; // Columns to score (null, or a list of selected cols)
        if (sCols == null || ArrayUtils.find(sCols,c) >= 0) {
          if (!extracted) {
            chks[c].getDoubles(cs, 0, cs.length);
            extracted = true;
          }
          DHistogram h = hcs[n][c];
          if( h==null ) continue; // Ignore untracked columns in this split
          lh.resizeIfNeeded(h._nbin);
          h.updateSharedHistosAndReset(lh, ws, cs, ys, rows, nh[n], n == 0 ? 0 : nh[n - 1]);
        }
      }
    }
  }


  /**
   * Helper class to store the thread-local histograms
   * Can now change the internal memory layout without affecting the calling code
   */
  static class LocalHisto {
    public void wAdd(int b, double val) { bins[b]+=val; }
    public void wYAdd(int b, double val) { sums[b]+=val; }
    public void wYYAdd(int b, double val) { ssqs[b]+=val; }
    public void wClear(int b) { bins[b]=0; }
    public void wYClear(int b) { sums[b]=0; }
    public void wYYClear(int b) { ssqs[b]=0; }
    public double w(int b) { return bins[b]; }
    public double wY(int b) { return sums[b]; }
    public double wYY(int b) { return ssqs[b]; }

    private double bins[];
    private double sums[];
    private double ssqs[];

    LocalHisto(int len) {
      bins = new double[len];
      sums = new double[len];
      ssqs = new double[len];
    }
    void resizeIfNeeded(int len) {
      if( len > bins.length) {
        bins = new double[len];
        sums = new double[len];
        ssqs = new double[len];
      }
    }
  }

}
