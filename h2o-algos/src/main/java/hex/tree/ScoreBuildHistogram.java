package hex.tree;

import hex.Distribution;
import water.MRTask;
import water.H2O.H2OCountedCompleter;
import water.fvec.C0DChunk;
import water.fvec.Chunk;
import water.util.AtomicUtils;

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
  final DHistogram _hcs[/*tree-relative node-id*/][/*column*/];
  final boolean _subset;      // True if working a subset of cols
  final Distribution.Family _family;

  public ScoreBuildHistogram(H2OCountedCompleter cc, int k, int ncols, int nbins, int nbins_cats, DTree tree, int leaf, DHistogram hcs[][], boolean subset, Distribution.Family family) {
    super(cc);
    _k    = k;
    _ncols= ncols;
    _nbins= nbins;
    _nbins_cats= nbins_cats;
    _tree = tree;
    _leaf = leaf;
    _hcs  = hcs;
    _subset = subset;
    _modifiesInputs = true;
    _family = family;
  }

  /** Marker for already decided row. */
  static public final int DECIDED_ROW = -1;
  /** Marker for sampled out rows */
  static public final int OUT_OF_BAG = -2;

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

  @Override public void map( Chunk[] chks ) {
    final Chunk wrks = chks[_ncols+2]; //fitting target (same as response for DRF, residual for GBM)
    final Chunk nids = chks[_ncols+3];
    final Chunk weight = chks.length >= _ncols+5 ? chks[_ncols+4] : new C0DChunk(1, chks[0].len());

    // Pass 1: Score a prior partially-built tree model, and make new Node
    // assignments to every row.  This involves pulling out the current
    // assigned DecidedNode, "scoring" the row against that Node's decision
    // criteria, and assigning the row to a new child UndecidedNode (and
    // giving it an improved prediction).
    int nnids[] = new int[nids._len];
    if( _leaf > 0)            // Prior pass exists?
      score_decide(chks,nids,nnids);
    else                      // Just flag all the NA rows
      for( int row=0; row<nids._len; row++ )
        if( isDecidedRow((int)nids.atd(row)) ) nnids[row] = -1;

    // Pass 2: accumulate all rows, cols into histograms
    if( _subset ) accum_subset(chks,wrks,weight,nnids);
    else          accum_all   (chks,wrks,weight,nnids);
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
  private void score_decide(Chunk chks[], Chunk nids, int nnids[]) {
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
      if( dn._split._col == -1 ) { // Might have a leftover non-split
        if( DTree.isRootNode(dn) ) { nnids[row] = nid-_leaf; continue; }
        nid = dn._pid;             // Use the parent split decision then
        int xnid = oob ? nid2Oob(nid) : nid;
        nids.set(row, xnid);
        nnids[row] = xnid-_leaf;
        dn = _tree.decided(nid); // Parent steers us
      }

      assert !isDecidedRow(nid);
      nid = dn.ns(chks,row); // Move down the tree 1 level
      if( !isDecidedRow(nid) ) {
        if( oob ) nid = nid2Oob(nid); // Re-apply OOB encoding
        nids.set(row, nid);
      }
      nnids[row] = nid-_leaf;
    }
  }

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
              nhs[col].incr((float) chks[col].atd(row), resp, w); // Histogram row/col
          }
        } else {
          for( int col : sCols )
            nhs[col].incr((float) chks[col].atd(row), resp, w); // Histogram row/col
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
    for( int i : nnids ) if( i >= 0 ) nh[i+1]++;
    // Rollup the histogram of rows-per-NID in this chunk
    for( int i=0; i<_hcs.length; i++ ) nh[i+1] += nh[i];
    // Splat the rows into NID-groups
    int rows[] = new int[nnids.length];
    for( int row=0; row<nnids.length; row++ )
      if( nnids[row] >= 0 )
        rows[nh[nnids[row]]++] = row;
    // rows[] has Chunk-local ROW-numbers now, in-order, grouped by NID.
    // nh[] lists the start of each new NID, and is indexed by NID+1.
    accum_all2(chks,wrks,weight,nh,rows);
  }

  // For all columns, for all NIDs, for all ROWS...
  private void accum_all2(Chunk chks[], Chunk wrks, Chunk weight, int nh[], int[] rows) {
    final DHistogram hcs[][] = _hcs;
    if( hcs.length==0 ) return; // Unlikely fast cutout
    // Local temp arrays, no atomic updates.
    double bins[] = new double[Math.max(_nbins, _nbins_cats)];
    double sums[] = new double[Math.max(_nbins, _nbins_cats)];
    double ssqs[] = new double[Math.max(_nbins, _nbins_cats)];
    int binslen = bins.length;
    int cols = _ncols;
    int hcslen = hcs.length;
    // For All Columns
    for( int c=0; c<cols; c++) { // for all columns
      Chunk chk = chks[c];
      // For All NIDs
      for( int n=0; n<hcslen; n++ ) {
        final DHistogram rh = hcs[n][c];
        if( rh==null ) continue; // Ignore untracked columns in this split
        double[] rhbins = rh._bins;
        int rhbinslen = rhbins.length;
        final int lo = n==0 ? 0 : nh[n-1];
        final int hi = nh[n];
        float min = rh._min2;
        float max = rh._maxIn;
        // While most of the time we are limited to nbins, we allow more bins
        // in a few cases (top-level splits have few total bins across all
        // the (few) splits) so it's safe to bin more; also categoricals want
        // to split one bin-per-level no matter how many levels).
        if( rhbinslen >= binslen) { // Grow bins if needed
          bins = new double[rhbinslen];
          sums = new double[rhbinslen];
          ssqs = new double[rhbinslen];
        }

        // Gather all the data for this set of rows, for 1 column and 1 split/NID
        // Gather min/max, sums and sum-squares.
        for( int xrow=lo; xrow<hi; xrow++ ) {
          int row = rows[xrow];
          double w = weight.atd(row);
          if (w == 0) continue;
          float col_data = (float)chk.atd(row);
          if( col_data < min ) min = col_data;
          if( col_data > max ) max = col_data;
          int b = rh.bin(col_data); // Compute bin# via linear interpolation
          double resp = wrks.atd(row); // fitting target (residual)
          double wy = w*resp;
          bins[b] += w;                // Bump count in bin
          sums[b] += wy;
          ssqs[b] += wy*resp;
        }

        // Add all the data into the Histogram (atomically add)
        rh.setMin(min);       // Track actual lower/upper bound per-bin
        rh.setMax(max);
        int len = rhbinslen;
        for( int b=0; b<len; b++ ) { // Bump counts in bins
          if( bins[b] != 0 ) { AtomicUtils.DoubleArray.add(rhbins,b,bins[b]); bins[b]=0; }
          if( sums[b] != 0 ) { rh.incr1(b,sums[b],ssqs[b]); sums[b]=ssqs[b]=0; }
        }
      }
    }
  }
}
