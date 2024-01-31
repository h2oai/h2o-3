package hex.tree;

import hex.Distribution;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.uplift.Divergence;
import jsr166y.RecursiveAction;
import org.apache.log4j.Logger;
import water.AutoBuffer;
import water.H2O;
import water.Iced;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.*;

import java.util.*;

import static hex.tree.SharedTreeModel.SharedTreeParameters.HistogramType;

/** A Decision Tree, laid over a Frame of Vecs, and built distributed.
 *
 *  <p>This class defines an explicit Tree structure, as a collection of {@code
 *  DTree} {@code Node}s.  The Nodes are numbered with a unique {@code _nid}.
 *  Users need to maintain their own mapping from their data to a {@code _nid},
 *  where the obvious technique is to have a Vec of {@code _nid}s (ints), one
 *  per each element of the data Vecs.
 *
 *  <p>Each {@code Node} has a {@code DHistogram}, describing summary data
 *  about the rows.  The DHistogram requires a pass over the data to be filled
 *  in, and we expect to fill in all rows for Nodes at the same depth at the
 *  same time.  i.e., a single pass over the data will fill in all leaf Nodes'
 *  DHistograms at once.
 *
 *  @author Cliff Click
 */
public class DTree extends Iced {

  private static final Logger LOG = Logger.getLogger(DTree.class);

  final String[] _names; // Column names
  final int _ncols;      // Active training columns
  final long _seed;      // RNG seed; drives sampling seeds if necessary
  private Node[] _ns;    // All the nodes in the tree.  Node 0 is the root.
  public int _len;       // Resizable array
  // Public stats about tree
  public int _leaves;
  public int _depth;
  public final int _mtrys;           // Number of columns to choose amongst in splits (at every split)
  public final int _mtrys_per_tree;  // Number of columns to choose amongst in splits (once per tree)
  public final transient Random _rand; // RNG for split decisions & sampling
  public final transient int[] _cols; // Per-tree selection of columns to consider for splits
  public transient SharedTreeModel.SharedTreeParameters _parms;


  // compute the effective number of columns to sample
  public int actual_mtries() {
    return Math.min(Math.max(1,(int)((double)_mtrys * Math.pow(_parms._col_sample_rate_change_per_level, _depth))),_ncols);
  }

  public DTree(Frame fr, int ncols, int mtrys, int mtrys_per_tree, long seed, SharedTreeModel.SharedTreeParameters parms) {
    _names = fr.names();
    _ncols = ncols;
    _parms = parms;
    _ns = new Node[1];
    _mtrys = mtrys;
    _mtrys_per_tree = mtrys_per_tree;
    _seed = seed;
    _rand = RandomUtils.getRNG(seed);
    int[] activeCols=new int[_ncols];
    for (int i=0;i<activeCols.length;++i)
      activeCols[i] = i;
    // per-tree column sample if _mtrys_per_tree < _ncols
    int len = _ncols;
    if (mtrys_per_tree < _ncols) {
      Random colSampleRNG = RandomUtils.getRNG(_seed*0xDA7A);
      for( int i=0; i<mtrys_per_tree; i++ ) {
        if( len == 0 ) break;
        int idx2 = colSampleRNG.nextInt(len);
        int col = activeCols[idx2];
        activeCols[idx2] = activeCols[--len];
        activeCols[len] = col;
      }
      activeCols = Arrays.copyOfRange(activeCols,len,activeCols.length);
    }
    _cols = activeCols;
  }

  /**
   * Copy constructor
   * @param tree
   */
  public DTree(DTree tree){
    _names = tree._names;
    _ncols = tree._ncols;
    _parms = tree._parms;
    _ns = new Node[tree._ns.length];
    for(int i=0; i<_ns.length; i++) {
      Node node = tree._ns[i];
      if(node  instanceof UndecidedNode) {
        _ns[i] = new UndecidedNode((UndecidedNode)node, this);
      } else if(node instanceof DecidedNode){
        _ns[i] = new DecidedNode((DecidedNode)node, this);
      } else if(node instanceof LeafNode) {
        _ns[i] = new LeafNode((LeafNode)node, this);
      } else {
        _ns[i] = null;
      }
    }
    _mtrys = tree._mtrys;
    _mtrys_per_tree = tree._mtrys_per_tree;
    _seed = tree._seed;
    _rand = tree._rand;
    _cols = tree._cols;
    _leaves = tree._leaves;
    _len = tree._len;
    _depth = tree._depth;
  }

  public final Node root() { return _ns[0]; }
  // One-time local init after wire transfer
  void init_tree( ) { for( int j=0; j<_len; j++ ) _ns[j]._tree = this; }

  // Return Node i
  public final Node node( int i ) { return _ns[i]; }
  public final UndecidedNode undecided( int i ) { return (UndecidedNode)node(i); }
  public final   DecidedNode   decided( int i ) { return (  DecidedNode)node(i); }

  // Get a new node index, growing innards on demand
  private synchronized int newIdx(Node n) {
    if( _len == _ns.length ) _ns = Arrays.copyOf(_ns,_len<<1);
    _ns[_len] = n;
    return _len++;
  }

  public final int len() { return _len; }

  // --------------------------------------------------------------------------
  // Abstract node flavor
  public static abstract class Node extends Iced {
    transient protected DTree _tree;    // Make transient, lest we clone the whole tree
    final public int _pid;    // Parent node id, root has no parent and uses NO_PARENT
    final protected int _nid;           // My node-ID, 0 is root
    Node( DTree tree, int pid, int nid ) {
      _tree = tree;
      _pid=pid;
      tree._ns[_nid=nid] = this;
    }
    Node( DTree tree, int pid ) {
      _tree = tree;
      _pid=pid;
      _nid = tree.newIdx(this);
    }

    Node( DTree tree, int pid, int nid, boolean copy) {
      _tree = tree;
      _pid = pid;
      if(copy) {
        _nid = nid;
      } else {
        _nid = tree.newIdx(this);
      }
    }

    // Recursively print the decision-line from tree root to this child.
    StringBuilder printLine(StringBuilder sb ) {
      if( _pid== NO_PARENT) return sb.append("[root]");
      DecidedNode parent = _tree.decided(_pid);
      parent.printLine(sb).append(" to ");
      return parent.printChild(sb,_nid);
    }
    abstract public StringBuilder toString2(StringBuilder sb, int depth);
    abstract protected AutoBuffer compress(AutoBuffer ab, AutoBuffer abAux);
    abstract protected int size();
    abstract protected int numNodes();

    public final int nid() { return _nid; }
    public final int pid() { return _pid; }
  }

  // --------------------------------------------------------------------------
  // Records a column, a bin to split at within the column, and the MSE.
  public static class Split extends Iced {
    final public int _col, _bin;// Column to split, bin where being split
    final DHistogram.NASplitDir _nasplit;
    final IcedBitSet _bs;       // For binary y and categorical x (with >= 4 levels), split into 2 non-contiguous groups
    final byte _equal;          // Split is 0: <, 2: == with group split (<= 32 levels), 3: == with group split (> 32 levels)
    final double _se;           // Squared error without a split
    final double _se0, _se1;    // Squared error of each subsplit
    final double _n0,  _n1;     // (Weighted) Rows in each final split
    final double _p0,  _p1;     // Predicted value for each split
    final double _tree_p0, _tree_p1;
    final double _p0Treat, _p0Contr, _p1Treat, _p1Contr; // uplift predictions
    final double _n0Treat, _n0Contr, _n1Treat, _n1Contr;

    public Split(int col, int bin, DHistogram.NASplitDir nasplit, IcedBitSet bs, byte equal, double se, double se0, double se1, double n0, double n1, double p0, double p1, double tree_p0, double tree_p1) {
      assert nasplit != DHistogram.NASplitDir.None;
      assert nasplit != DHistogram.NASplitDir.NAvsREST || bs == null : "Split type NAvsREST shouldn't have a bitset";
      assert equal != 1; //no longer done
      // FIXME: Disabled for testing PUBDEV-6495:
      // assert se > se0+se1 || se==Double.MAX_VALUE; // No point in splitting unless error goes down
      assert col >= 0;
      assert bin >= 0;
      _col = col;  _bin = bin; _nasplit = nasplit; _bs = bs;  _equal = equal;  _se = se;
      _n0 = n0;  _n1 = n1;  _se0 = se0;  _se1 = se1;
      _p0 = p0;  _p1 = p1;
      _tree_p0 = tree_p0; _tree_p1 = tree_p1;
      _p0Treat = _p0Contr = _p1Treat = _p1Contr = 0;
      _n0Treat = _n0Contr = _n1Treat = _n1Contr = 0;
    }

    public Split(int col, int bin, DHistogram.NASplitDir nasplit, IcedBitSet bs, byte equal, double se, double se0, double se1, double n0, double n1, double p0, double p1, double tree_p0, double tree_p1,
                 double p0Treat, double p0Contr, double p1Treat, double p1Contr, double n0Treat, double n0Contr, double n1Treat, double n1Contr) {
      assert(nasplit!= DHistogram.NASplitDir.None);
      assert(equal!=1); //no longer done
      // FIXME: Disabled for testing PUBDEV-6495:
      // assert se > se0+se1 || se==Double.MAX_VALUE; // No point in splitting unless error goes down
      assert(col>=0);
      assert(bin>=0);
      _col = col;  _bin = bin; _nasplit = nasplit; _bs = bs;  _equal = equal;  _se = se;
      _n0 = n0;  _n1 = n1;  _se0 = se0;  _se1 = se1;
      _p0 = p0;  _p1 = p1;
      _tree_p0 = tree_p0; _tree_p1 = tree_p1;
      _p0Treat = p0Treat; _p0Contr = p0Contr; _p1Treat = p1Treat; _p1Contr = p1Contr;
      _n0Treat = n0Treat; _n0Contr = n0Contr; _n1Treat = n1Treat; _n1Contr = n1Contr;
    }
    public final double pre_split_se() { return _se; }
    public final double se() { return _se0+_se1; }
    public final int   col() { return _col; }
    public final int   bin() { return _bin; }
    public final DHistogram.NASplitDir naSplitDir() { return _nasplit; }
    public final double n0() { return _n0; }
    public final double n1() { return _n1; }

    /**
     * Returns an optimal numeric split point for numerical splits,
     * -1 for bitwise splits and Float.NaN if a split should be abandoned.
     * @param hs histograms
     * @return "split at" value
     */
    float splat(DHistogram[] hs) {
      return isNumericSplit() ? splatNumeric(hs[_col]) : -1f; // Split-at value (-1 for group-wise splits)
    }

    boolean isNumericSplit() {
      return _nasplit != DHistogram.NASplitDir.NAvsREST && (_equal == 0 || _equal == 1);
    }

    // Split-at dividing point.  Don't use the step*bin+bmin, due to roundoff
    // error we can have that point be slightly higher or lower than the bin
    // min/max - which would allow values outside the stated bin-range into the
    // split sub-bins.  Always go for a value which splits the nearest two
    // elements.
    float splatNumeric(final DHistogram h) {
      assert _nasplit != DHistogram.NASplitDir.NAvsREST : "Shouldn't be called for NA split type 'NA vs REST'";
      assert _bin > 0 && _bin < h.nbins();
      assert _bs==null : "Dividing point is a bitset, not a bin#, so don't call splat() as result is meaningless";
      assert _equal != 1;
      assert _equal==0; // not here for bitset splits, just range splits
      // Find highest non-empty bin below the split
      int x=_bin-1;
      while( x >= 0 && h.bins(x)==0 ) x--;
      // Find lowest  non-empty bin above the split
      int n=_bin;
      while( n < h.nbins() && h.bins(n)==0 ) n++;
      // Lo is the high-side of the low non-empty bin, rounded to int for int columns
      // Hi is the low -side of the hi  non-empty bin, rounded to int for int columns

      // Example: Suppose there are no empty bins, and we are splitting an
      // integer column at 48.4 (more than nbins, so step != 1.0, perhaps
      // step==1.8).  The next lowest non-empty bin is from 46.6 to 48.4, and
      // we set lo=48.4.  The next highest non-empty bin is from 48.4 to 50.2
      // and we set hi=48.4.  Since this is an integer column, we round lo to
      // 48 (largest integer below the split) and hi to 49 (smallest integer
      // above the split).  Finally we average them, and split at 48.5.
      double lo = h.binAt(x+1);
      double hi = h.binAt(n  );
      if (h._isInt > 0) {
        lo = h._step==1 ? lo-1 : Math.floor(lo);
        hi = h._step==1 ? hi   : Math.ceil (hi);
      }
      final float splitAt = (float) ((lo + hi) / 2.0);
      // abandon split if rounding errors could cause observations being incorrectly
      // assigned to child nodes at scoring time
      // this will typically happen when bin lengths are very small (eg. 1e-6)
      // we will abandon a split if `lo` is not a true lower bound to a float `splitAt`
      // (and symmetrically for `hi`)
      if (h._checkFloatSplits && lo != hi && (lo > splitAt || hi < splitAt)) {
        return Float.NaN;
      }
      return splitAt;
    }


    /**
     * Prepare children histograms, one per column.
     * Typically, histograms are created with a level-dependent binning strategy.
     * For the histogram of the current split decision, the children histograms are left/right range-adjusted.
     *
     * Any histgoram can null if there is no point in splitting
     * further (such as there's fewer than min_row elements, or zero
     * error in the response column).  Return an array of DHistograms (one
     * per column), which are bounded by the split bin-limits.  If the column
     * has constant data, or was not being tracked by a prior DHistogram
     * (for being constant data from a prior split), then that column will be
     * null in the returned array.
     * @param currentHistos Histograms for all applicable columns computed for the previous split finding process
     * @param way 0 (left) or 1 (right)
     * @param splat Split point for previous split (if applicable)
     * @param parms user-given parameters (will use nbins, min_rows, etc.)
     * @return Array of histograms to be used for the next level of split finding
     */
    public DHistogram[] nextLevelHistos(DHistogram[] currentHistos, int way, double splat, SharedTreeModel.SharedTreeParameters parms, Constraints cs, BranchInteractionConstraints bcs) {
      double n = way==0 ? _n0 : _n1;
      if( n < parms._min_rows ) {
        if (LOG.isTraceEnabled()) LOG.trace("Not splitting: too few observations left: " + n);
        return null; // Too few elements
      }
      double se = way==0 ? _se0 : _se1;
      if( se <= 1e-30 ) {
        LOG.trace("Not splitting: pure node (perfect prediction).");
        return null; // No point in splitting a perfect prediction
      }

      // Build a next-gen split point from the splitting bin
      int cnt=0;                  // Count of possible splits
      DHistogram nhists[] = new DHistogram[currentHistos.length]; // A new histogram set
      boolean checkBranchInteractions = bcs != null;
      for(int j = 0; j < currentHistos.length; j++ ) { // For every column in the new split
        // Check branch interaction constraint if it is not null 
        if (checkBranchInteractions && !bcs.isAllowedIndex(j)) {
          // Column is denied by branch interaction constraints -> the histogram is set to null
          continue;
        }
        DHistogram h = currentHistos[j];            // old histogram of column
        if( h == null )
          continue;        // Column was not being tracked?
        final int adj_nbins      = Math.max(h.nbins()>>1,parms._nbins); //update number of bins dependent on level depth

        // min & max come from the original column data, since splitting on an
        // unrelated column will not change the j'th columns min/max.
        // Tighten min/max based on actual observed data for tracked columns
        double min, maxEx;
        if( h._vals == null || _equal > 1) { // Not tracked this last pass? For bitset, always keep the full range of factors
          min = h._min;         // Then no improvement over last go
          maxEx = h._maxEx;
        } else {                // Else pick up tighter observed bounds
          min = h.find_min();   // Tracked inclusive lower bound
          if( h.find_maxIn() == min )
            continue; // This column will not split again
          maxEx = h.find_maxEx(); // Exclusive max
        }
        if (_nasplit== DHistogram.NASplitDir.NAvsREST) {
          if (way==1) continue; //no histogram needed - we just split NAs away
          // otherwise leave the min/max alone, and make another histogram (but this time, there won't be any NAs)
        }

        // Tighter bounds on the column getting split: exactly each new
        // DHistogram's bound are the bins' min & max.
        if( _col==j ) {
          switch( _equal ) {
          case 0:  // Ranged split; know something about the left & right sides
            if (_nasplit != DHistogram.NASplitDir.NAvsREST) {
              if (h._vals[h._vals_dim*_bin] == 0)
                throw H2O.unimpl(); // Here I should walk up & down same as split() above.
            }
            assert _bs==null : "splat not defined for BitSet splits";
            double split = splat;
            if( h._isInt > 0 ) split = (float)Math.ceil(split);
            if (_nasplit != DHistogram.NASplitDir.NAvsREST) {
              if (way == 0) maxEx = split;
              else min = split;
            }
            break;
          case 1:               // Equality split; no change on unequals-side
            if( way == 1 )
              continue; // but know exact bounds on equals-side - and this col will not split again
            break;
          case 2:               // BitSet (small) split
          case 3:               // BitSet (big)   split
            break;
          default: throw H2O.fail();
          }
        }
        if( min >  maxEx )
          continue; // Happens for all-NA subsplits
        if( MathUtils.equalsWithinOneSmallUlp(min, maxEx) )
          continue; // This column will not split again
        if( Double.isInfinite(adj_nbins/(maxEx-min)) )
          continue;
        if( h._isInt > 0 && !(min+1 < maxEx ) )
          continue; // This column will not split again
        assert min < maxEx && adj_nbins > 1 : ""+min+"<"+maxEx+" nbins="+adj_nbins;

        // only count NAs if we have any going our way (note: NAvsREST doesn't build a histo for the NA direction)
        final boolean hasNAs = (_nasplit == DHistogram.NASplitDir.NALeft && way == 0 || 
                _nasplit == DHistogram.NASplitDir.NARight && way == 1) && h.hasNABin();

        double[] customSplitPoints = h._customSplitPoints;
        if (parms._histogram_type == HistogramType.UniformRobust && 
                j != _col && // don't apply if we were able to split on the column with the current bins
                GuidedSplitPoints.isApplicableTo(h)
        ) {
          final int nonEmptyBins = h.nonEmptyBins();
          final double density = nonEmptyBins / ((double) h.nbins());

          if (density <= GuidedSplitPoints.LOW_DENSITY_THRESHOLD) {
            customSplitPoints = GuidedSplitPoints.makeSplitPoints(h, adj_nbins, min, maxEx);
          }
        }

        nhists[j] = DHistogram.make(h._name, adj_nbins, h._isInt, min, maxEx, h._intOpt, hasNAs,
                h._seed*0xDECAF+(way+1), parms,
                h._globalSplitPointsKey, cs, h._checkFloatSplits, customSplitPoints);
        cnt++;                    // At least some chance of splitting
      }
      return cnt == 0 ? null : nhists;
    }

    public Constraints nextLevelConstraints(Constraints currentConstraints, int way, double splat, SharedTreeModel.SharedTreeParameters parms) {
      int constraint = currentConstraints.getColumnConstraint(_col);
      if (constraint == 0) {
        return currentConstraints; // didn't split on a column with constraints => no need to modify them
      }
      double mid = (_tree_p0 + _tree_p1) / 2;
      return currentConstraints.withNewConstraint(_col, way, mid);
    }

    @Override public String toString() {
      return "Splitting: col=" + _col + " type=" + ((int)_equal == 0 ? " < " : "bitset")
              + ", splitpoint=" + _bin + ", nadir=" + _nasplit.toString() + ", se0=" + _se0 + ", se1=" + _se1 + ", n0=" + _n0 + ", n1=" + _n1;
    }
  }

  // --------------------------------------------------------------------------
  // An UndecidedNode: Has a DHistogram which is filled in (in parallel
  // with other histograms) in a single pass over the data.  Does not contain
  // any split-decision.
  public static class UndecidedNode extends Node {
    public transient DHistogram[] _hs; //(up to) one histogram per column
    public transient Constraints _cs;
    public transient BranchInteractionConstraints _bics;
    public final int _scoreCols[];      // A list of columns to score; could be null for all
    public UndecidedNode( DTree tree, int pid, DHistogram[] hs, Constraints cs, BranchInteractionConstraints bics) {
      super(tree,pid);
      assert hs.length==tree._ncols;
      _hs = hs; //these histograms have no bins yet (just constructed)
      _cs = cs;
      _bics = bics;
      _scoreCols = scoreCols();
    }

    public UndecidedNode(UndecidedNode node, DTree tree){
      super(tree, node._pid, node._nid, true);
      _hs = node._hs; //these histograms have no bins yet (just constructed)
      _cs = node._cs;
      _bics = node._bics;
      _scoreCols = node._scoreCols;
    }

    // Pick a random selection of columns to compute best score.
    // Can return null for 'all columns'.
    public int[] scoreCols() {
      DTree tree = _tree;
      if (tree.actual_mtries() == _hs.length && tree._mtrys_per_tree == _hs.length)
        return null;

      // per-tree pre-selected columns
      int[] activeCols = tree._cols;
      if (LOG.isTraceEnabled()) LOG.trace("For tree with seed " + tree._seed + ", out of " + _hs.length + " cols, the following cols are activated via mtry_per_tree=" + tree._mtrys_per_tree + ": " + Arrays.toString(activeCols));

      int[] cols = new int[activeCols.length];
      int len=0;

      // collect columns that can be split (non-constant, large enough to split, etc.)
      for(int i = 0; i< activeCols.length; i++ ) {
        int idx = activeCols[i];
        assert(idx == i || tree._mtrys_per_tree < _hs.length);
        if( _hs[idx]==null ) continue; // Ignore not-tracked cols
        assert _hs[idx]._min < _hs[idx]._maxEx && _hs[idx].actNBins() > 1 : "broken histo range "+_hs[idx];
        cols[len++] = idx;        // Gather active column
      }
      if (LOG.isTraceEnabled()) LOG.trace("These columns can be split: " + Arrays.toString(Arrays.copyOfRange(cols, 0, len)));
      int choices = len;        // Number of columns I can choose from

      int mtries = tree.actual_mtries();
      if (choices > 0) { // It can happen that we have no choices, because this node cannot be split any more (all active columns are constant, for example).
        // Draw up to mtry columns at random without replacement.
        for (int i = 0; i < mtries; i++) {
          if (len == 0) break;   // Out of choices!
          int idx2 = tree._rand.nextInt(len);
          int col = cols[idx2];     // The chosen column
          cols[idx2] = cols[--len]; // Compress out of array; do not choose again
          cols[len] = col;          // Swap chosen in just after 'len'
        }
        assert len < choices;
      }
      if (LOG.isTraceEnabled()) LOG.trace("Picking these (mtry=" + mtries + ") columns to evaluate for splitting: " + Arrays.toString(Arrays.copyOfRange(cols, len, choices)));
      return Arrays.copyOfRange(cols, len, choices);
    }

    // Make the parent of this Node use UNINTIALIZED NIDs for its children to prevent the split that this
    // node otherwise induces.  Happens if we find out too-late that we have a
    // perfect prediction here, and we want to turn into a leaf.
    public void doNotSplit( ) {
      if( _pid == NO_PARENT) return; // skip root
      DecidedNode dn = _tree.decided(_pid);
      for( int i=0; i<dn._nids.length; i++ )
        if( dn._nids[i]==_nid )
          { dn._nids[i] = ScoreBuildHistogram.UNDECIDED_CHILD_NODE_ID; return; }
      throw H2O.fail();
    }

    @Override public String toString() {
      final String colPad="  ";
      final int cntW=4, mmmW=4, menW=5, varW=5;
      final int colW=cntW+1+mmmW+1+mmmW+1+menW+1+varW;
      StringBuilder sb = new StringBuilder();
      sb.append("Nid# ").append(_nid).append(", ");
      printLine(sb).append("\n");
      if( _hs == null ) return sb.append("_hs==null").toString();
      for( DHistogram hs : _hs )
        if( hs != null )
          p(sb,hs._name+String.format(", %4.1f-%4.1f",hs._min,hs._maxEx),colW).append(colPad);
      sb.append('\n');
      for( DHistogram hs : _hs ) {
        if( hs == null ) continue;
        p(sb,"cnt" ,cntW).append('/');
        p(sb,"min" ,mmmW).append('/');
        p(sb,"max" ,mmmW).append('/');
        p(sb,"mean",menW).append('/');
        p(sb,"var" ,varW).append(colPad);
      }
      sb.append('\n');

      // Max bins
      int nbins=0;
      for( DHistogram hs : _hs )
        if( hs != null && hs.nbins() > nbins ) nbins = hs.nbins();

      for( int i=0; i<nbins; i++ ) {
        for( DHistogram h : _hs ) {
          if( h == null ) continue;
          if( i < h.nbins() && h._vals != null ) {
            p(sb, h.bins(i),cntW).append('/');
            p(sb, h.binAt(i),mmmW).append('/');
            p(sb, h.binAt(i+1),mmmW).append('/');
            p(sb, h.mean(i),menW).append('/');
            p(sb, h.var (i),varW).append(colPad);
          } else {
            p(sb,"",colW).append(colPad);
          }
        }
        sb.append('\n');
      }
      sb.append("Nid# ").append(_nid);
      return sb.toString();
    }
    static private StringBuilder p(StringBuilder sb, String s, int w) {
      return sb.append(StringUtils.fixedLength(s,w));
    }
    static private StringBuilder p(StringBuilder sb, double d, int w) {
      String s = Double.isNaN(d) ? "NaN" :
        ((d==Float.MAX_VALUE || d==-Float.MAX_VALUE || d==Double.MAX_VALUE || d==-Double.MAX_VALUE) ? " -" :
         (d==0?" 0":Double.toString(d)));
      if( s.length() <= w ) return p(sb,s,w);
      s = String.format("% 4.2f",d);
      if( s.length() > w )
        s = String.format("%4.1f",d);
      if( s.length() > w )
        s = String.format("%4.0f",d);
      return p(sb,s,w);
    }

    @Override public StringBuilder toString2(StringBuilder sb, int depth) {
      for( int d=0; d<depth; d++ ) sb.append("  ");
      return sb.append("Undecided\n");
    }
    @Override protected AutoBuffer compress(AutoBuffer ab, AutoBuffer abAux) { throw H2O.fail(); }
    @Override protected int size() { throw H2O.fail(); }
    @Override protected int numNodes() { throw H2O.fail(); }
  }

  // --------------------------------------------------------------------------
  // Internal tree nodes which split into several children over a single
  // column.  Includes a split-decision: which child does this Row belong to?
  // Does not contain a histogram describing how the decision was made.
  public static class DecidedNode extends Node {
    public final Split _split;         // Split: col, equal/notequal/less/greater, nrows, MSE
    public final float _splat;         // Split At point: lower bin-edge of split
    // _equals\_nids[] \   0   1
    // ----------------+----------
    //       F         |   <   >=
    //       T         |  !=   ==
    public final int _nids[];          // Children NIDS for the split LEFT, RIGHT

    transient byte _nodeType; // Complex encoding: see the compressed struct comments
    transient int _size = 0;  // Compressed byte size of this subtree
    transient int _nnodes = 0; // Number of nodes in this subtree

    public DecidedNode(DecidedNode node, DTree tree){
      super(tree, node._pid, node._nid, true);
      _split = node._split;
      _splat = node._splat;
      _nids = node._nids;
      _nodeType = node._nodeType;
      _size = node._size;
      _nnodes = node._nnodes;
    }

    // Make a correctly flavored Undecided
    public UndecidedNode makeUndecidedNode(DHistogram hs[], Constraints cs, BranchInteractionConstraints bics) {
      return new UndecidedNode(_tree, _nid, hs, cs, bics);
    }

    // Pick the best column from the given histograms
    public Split bestCol(UndecidedNode u, DHistogram hs[], Constraints cs) {
      DTree.Split best = null;
      if( hs == null ) return null;
      final int maxCols = u._scoreCols == null /* all cols */ ? hs.length : u._scoreCols.length;
      List<FindSplits> findSplits = new ArrayList<>();
      //total work is to find the best split across sum_over_cols_to_split(nbins)
      long nbinsSum = 0;
      for( int i=0; i<maxCols; i++ ) {
        int col = u._scoreCols == null ? i : u._scoreCols[i];
        if( hs[col]==null || hs[col].actNBins() <= 1 )
          continue;
        nbinsSum += hs[col].actNBins();
      }
      // for small work loads, do a serial loop, otherwise, submit work to FJ thread pool
      final boolean isSmall = (nbinsSum <= 1024); //heuristic - 50 cols with 20 nbins, or 1 column with 1024 bins, etc.
      for( int i=0; i<maxCols; i++ ) {
        int col = u._scoreCols == null ? i : u._scoreCols[i];
        if( hs[col]==null || hs[col].actNBins() <= 1 )
          continue;
        FindSplits fs = new FindSplits(hs, cs, col, u._nid);
        findSplits.add(fs);
        if (isSmall) fs.compute();
      }
      if (!isSmall) jsr166y.ForkJoinTask.invokeAll(findSplits);
      for( FindSplits fs : findSplits) {
        DTree.Split s = fs._s;
        if( s == null ) continue;
        if (best == null || s.se() < best.se()) {
          if (hs[s._col]._checkFloatSplits) {
            // we evaluate the feasibility of the split only if it brings improvement of SE
            // same could be done when building the split (findBestSplitPoint) but the lazy
            // evaluation avoids scanning the bins unnecessarily
            float splitAt = s.splat(hs);
            if (Float.isNaN(splitAt))
              continue;
          }
          best = s;
        }
      }
      return best;
    }

    public final class FindSplits extends RecursiveAction {
      public FindSplits(DHistogram[] hs, Constraints cs, int col, UndecidedNode node) {
        this(hs, cs, col, node._nid);
      }
      private FindSplits(DHistogram[] hs, Constraints cs, int col, int nid) {
        _hs = hs; _cs = cs; _col = col; _nid = nid;
        _useUplift = _hs[_col].useUplift();
      }
      final DHistogram[] _hs;
      final Constraints _cs;
      final int _col;
      final int _nid;
      DTree.Split _s;
      final boolean _useUplift;

      @Override public void compute() {
        computeSplit();
      }
      public final DTree.Split computeSplit() {
        final double min, max;
        final int constraint;
        final boolean useBounds;
        final Distribution dist;
        if (_cs != null) {
          min = _cs._min;
          max = _cs._max;
          constraint = _cs.getColumnConstraint(_col);
          useBounds = _cs.useBounds();
          dist = _cs._dist;
        } else {
          min = Double.NaN;
          max = Double.NaN;
          constraint = 0;
          useBounds = false;
          dist = null;
        }
        if(_useUplift){
          _s = findBestSplitPointUplift(_hs[_col], _col, _tree._parms._min_rows);
        } else {
          _s = findBestSplitPoint(_hs[_col], _col, _tree._parms._min_rows, constraint, min, max, useBounds, dist);
        }
        return _s;
      }
    }

    public DecidedNode(UndecidedNode n, DHistogram hs[], Constraints cs, GlobalInteractionConstraints ics) {
      super(n._tree,n._pid,n._nid); // Replace Undecided with this DecidedNode
      _nids = new int[2];           // Split into 2 subsets
      _split = bestCol(n,hs,cs);  // Best split-point for this tree
      if( _split == null) {
        // Happens because the predictor columns cannot split the responses -
        // which might be because all predictor columns are now constant, or
        // because all responses are now constant.
        _splat = Float.NaN;
        Arrays.fill(_nids,ScoreBuildHistogram.UNDECIDED_CHILD_NODE_ID);
        return;
      }
      _splat = _split.splat(hs);
      for(int way = 0; way <2; way++ ) { // left / right
        // Prepare the next level of constraints if monotone or interaction constraints are set
        Constraints ncs = cs != null ? _split.nextLevelConstraints(cs, way, _splat, _tree._parms) : null;
        BranchInteractionConstraints nbics = n._bics != null ? n._bics.nextLevelInteractionConstraints(ics, _split._col) : null;
        // Create children histograms, not yet populated, but the ranges are set
        DHistogram nhists[] = _split.nextLevelHistos(hs, way,_splat, _tree._parms, ncs, nbics); //maintains the full range for NAvsREST
        assert nhists==null || nhists.length==_tree._ncols;
        // Assign a new (yet undecided) node to each child, and connect this (the parent) decided node and the newly made histograms to it
        _nids[way] = nhists == null ? ScoreBuildHistogram.UNDECIDED_CHILD_NODE_ID : makeUndecidedNode(nhists,ncs, nbics)._nid;
      }
    }

    public int getChildNodeID(Chunk [] chks, int row ) {
      double d = chks[_split._col].atd(row);
      int bin = -1;
      boolean isNA = Double.isNaN(d);

      if (!isNA) {
        if (_split._nasplit == DHistogram.NASplitDir.NAvsREST)
          bin = 0;
        else if (_split._equal == 0) {
          assert(!Float.isNaN(_splat));
          bin = d >= _splat ? 1 : 0;
//        else if (_split._equal == 1)
//          bin = d == _splat ? 1 : 0;
        }
        else if (_split._equal >= 2) {
          int b = (int)d;
          if (_split._bs.isInRange(b)) {
            bin = _split._bs.contains(b) ? 1 : 0; // contains goes right
          } else {
            isNA = true;
          }
        }
      }

      // NA handling
      if (isNA) {
        if (_split._nasplit== DHistogram.NASplitDir.NALeft || _split._nasplit == DHistogram.NASplitDir.Left) {
          bin = 0;
        } else if (_split._nasplit == DHistogram.NASplitDir.NARight || _split._nasplit == DHistogram.NASplitDir.Right || _split._nasplit == DHistogram.NASplitDir.NAvsREST) {
          bin = 1;
        } else if (_split._nasplit == DHistogram.NASplitDir.None) {
          bin = 1; // if no NAs in training, but NAs in testing -> go right TODO: Pick optimal direction
        } else throw H2O.unimpl();
      }
      return _nids[bin];
    }

    public double pred( int nid ) {
      return nid==0 ? _split._p0 : _split._p1;
    }

    public double predTreatment( int nid ) {
      return nid==0 ? _split._p0Treat : _split._p1Treat;
    }

    public double predControl( int nid ) {
      return nid==0 ? _split._p0Contr : _split._p1Contr;
    }

    @Override public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("DecidedNode:\n");
      sb.append("_nid: " + _nid + "\n");
      sb.append("_nids (children): " + Arrays.toString(_nids) + "\n");
      if (_split!=null)
        sb.append("_split:" + _split.toString() + "\n");
      sb.append("_splat:" + _splat + "\n");
      if( _split == null ) {
        sb.append(" col = -1\n");
      } else {
        int col = _split._col;
        if (_split._equal == 1) {
          sb.append(_tree._names[col] + " != " + _splat + "\n" +
                  _tree._names[col] + " == " + _splat + "\n");
        } else if (_split._equal == 2 || _split._equal == 3) {
          sb.append(_tree._names[col] + " not in " + _split._bs.toString() + "\n" +
                  _tree._names[col] + "  is in " + _split._bs.toString() + "\n");
        } else {
          sb.append(
                  _tree._names[col] + " < " + _splat + "\n" +
                          _splat + " >=" + _tree._names[col] + "\n");
        }
      }
      return sb.toString();
    }

    StringBuilder printChild( StringBuilder sb, int nid ) {
      int i = _nids[0]==nid ? 0 : 1;
      assert _nids[i]==nid : "No child nid "+nid+"? " +Arrays.toString(_nids);
      sb.append("[").append(_tree._names[_split._col]);
      sb.append(_split._equal != 0
                ? (i==0 ? " != " : " == ")
                : (i==0 ? " <  " : " >= "));
      sb.append((_split._equal == 2 || _split._equal == 3) ? _split._bs.toString() : _splat).append("]");
      return sb;
    }

    @Override public StringBuilder toString2(StringBuilder sb, int depth) {
      assert(_nids.length==2);
      for( int i=0; i<_nids.length; i++ ) {
        for( int d=0; d<depth; d++ ) sb.append("  ");
        sb.append(_nid).append(" ");
        if( _split._col < 0 ) sb.append("init");
        else {
          sb.append(_tree._names[_split._col]);
          if (_split._nasplit == DHistogram.NASplitDir.NAvsREST) {
            if (i==0) sb.append(" not NA");
            if (i==1) sb.append(" is NA");
          }
          else {
            if (_split._equal < 2) {
              if (_split._nasplit == DHistogram.NASplitDir.NARight || _split._nasplit == DHistogram.NASplitDir.Right || _split._nasplit == DHistogram.NASplitDir.None)
                sb.append(_split._equal != 0 ? (i == 0 ? " != " : " == ") : (i == 0 ? " <  " : " is NA or >= "));
              if (_split._nasplit == DHistogram.NASplitDir.NALeft || _split._nasplit == DHistogram.NASplitDir.Left)
                sb.append(_split._equal != 0 ? (i == 0 ? " is NA or != " : " == ") : (i == 0 ? " is NA or <  " : " >= "));
            } else {
              sb.append(i == 0 ? " not in " : "  is in ");
            }
            sb.append((_split._equal == 2 || _split._equal == 3) ? _split._bs.toString() : _splat).append("\n");
          }
        }
        if( _nids[i] >= 0 && _nids[i] < _tree._len )
          _tree.node(_nids[i]).toString2(sb,depth+1);
      }
      return sb;
    }

    // Size of this subtree; sets _nodeType also
    @Override public final int size(){
      if( _size != 0 ) return _size; // Cached size

      assert _nodeType == 0:"unexpected node type: " + _nodeType;
      if(_split._equal != 0)
        _nodeType |= _split._equal == 1 ? 4 : (_split._equal == 2 ? 8 : 12);

      // int res = 7;  // 1B node type + flags, 2B colId, 4B float split val
      // 1B node type + flags, 2B colId, 4B split val/small group or (2B offset + 4B size) + large group
      int res = _split._equal == 3 ? 9 + _split._bs.numBytes() : 7;

      // NA handling correction
      res++; //1 byte for NA split dir
      if (_split._nasplit == DHistogram.NASplitDir.NAvsREST) {
        assert _split._equal == 0;
        res -= 4; // we don't need to represent the actual split value
      }

      Node left = _tree.node(_nids[0]);
      int lsz = left.size();
      res += lsz;
      if( left instanceof LeafNode ) _nodeType |= (byte)48;
      else {
        int slen = lsz < 256 ? 0 : (lsz < 65535 ? 1 : (lsz<(1<<24) ? 2 : 3));
        _nodeType |= slen; // Set the size-skip bits
        res += (slen+1); //
      }

      Node right = _tree.node(_nids[1]);
      if( right instanceof LeafNode ) _nodeType |= (byte)(48 << 2);
      res += right.size();
      assert (_nodeType&0x33) != 51;
      assert res != 0;
      return (_size = res);
    }

    @Override
    protected int numNodes() {
      if (_nnodes > 0)
        return _nnodes;
      _nnodes = 1 + _tree.node(_nids[0]).numNodes() + _tree.node(_nids[1]).numNodes();
      return _nnodes;
    }

    // Compress this tree into the AutoBuffer
    @Override public AutoBuffer compress(AutoBuffer ab, AutoBuffer abAux) {
      int pos = ab.position();
      if( _nodeType == 0 ) size(); // Sets _nodeType & _size both
      ab.put1(_nodeType);          // Includes left-child skip-size bits
      assert _split != null;    // Not a broken root non-decision?
      assert _split._col >= 0;
      ab.put2((short)_split._col);
      ab.put1((byte)_split._nasplit.value());

      // Save split-at-value or group
      if (_split._nasplit!= DHistogram.NASplitDir.NAvsREST) {
        if (_split._equal == 0 || _split._equal == 1) ab.put4f(_splat);
        else if(_split._equal == 2) _split._bs.compress2(ab);
        else _split._bs.compress3(ab);
      }
      if (abAux != null) {
        abAux.put4(_nid);
        abAux.put4(_tree.node(_nids[0]).numNodes()); // number of nodes in the left subtree; this used to be 'parent node id'
        abAux.put4f((float)_split._n0);
        abAux.put4f((float)_split._n1);
        abAux.put4f((float)_split._p0);
        abAux.put4f((float)_split._p1);
        abAux.put4f((float)_split._se0);
        abAux.put4f((float)_split._se1);
        abAux.put4(_nids[0]);
        abAux.put4(_nids[1]);
      }

      Node left = _tree.node(_nids[0]);
      if( (_nodeType&48) == 0 ) { // Size bits are optional for left leaves !
        int sz = left.size();
        if(sz < 256)            ab.put1(       sz);
        else if (sz < 65535)    ab.put2((short)sz);
        else if (sz < (1<<24))  ab.put3(       sz);
        else                    ab.put4(       sz); // 1<<31-1
      }
      // now write the subtree in
      left.compress(ab, abAux);
      Node rite = _tree.node(_nids[1]);
      rite.compress(ab, abAux);
      assert _size == ab.position()-pos:"reported size = " + _size + " , real size = " + (ab.position()-pos);
      return ab;
    }
  }

  public final static class LeafNode extends Node {
    public float _pred;
    public LeafNode( DTree tree, int pid ) { super(tree,pid); tree._leaves++; }
    public LeafNode( DTree tree, int pid, int nid ) { super(tree,pid,nid); tree._leaves++; }
    public LeafNode( LeafNode node, DTree tree) {
      super(tree,node._pid, node._nid, true);
      _pred = node._pred;
    }
    @Override public String toString() { return "Leaf#"+_nid+" = "+_pred; }
    @Override public final StringBuilder toString2(StringBuilder sb, int depth) {
      for( int d=0; d<depth; d++ ) sb.append("  ");
      sb.append(_nid).append(" ");
      return sb.append("pred=").append(_pred).append("\n");
    }
    // Insert just the predictions: a single byte/short if we are predicting a
    // single class, or else the full distribution.
    @Override protected AutoBuffer compress(AutoBuffer ab, AutoBuffer abAux) {
      assert !Double.isNaN(_pred); return ab.put4f(_pred);
    }
    @Override protected int size() { return 4; }
    @Override protected int numNodes() { return 0; }
    public final double pred() { return _pred; }
    // returns prediction calculated while building the regression tree (extract it from Split)
    // for some distributions this can be used to calculate the leaf node predictions
    public final double getSplitPrediction() {
      DTree.DecidedNode parent = (DTree.DecidedNode) _tree.node(_pid);
      boolean isLeft = parent._nids[0] == _nid;
      return isLeft ? parent._split._tree_p0 : parent._split._tree_p1;
    }
  }
  
  final static public int NO_PARENT = -1;
  static public boolean isRootNode(Node n)   { return n._pid == NO_PARENT; }

  public transient AutoBuffer _abAux;
  // Build a compressed-tree struct
  public CompressedTree compress(int tid, int cls, String[][] domains) {
    int sz = root().size();
    if( root() instanceof LeafNode ) sz += 3; // Oops - tree-stump
    AutoBuffer ab = new AutoBuffer(sz);
    _abAux = new AutoBuffer();
    if( root() instanceof LeafNode ) // Oops - tree-stump
      ab.put1(0).put2((char)65535); // Flag it special so the decompress doesn't look for top-level decision
    root().compress(ab, _abAux);      // Compress whole tree
    assert ab.position() == sz;
    return new CompressedTree(ab.buf(), _seed,tid,cls);
  }

  static Split findBestSplitPoint(DHistogram hs, int col, double min_rows, int constraint, double min, double max, 
                                  boolean useBounds, Distribution dist) {
    if(hs._vals == null) {
      if (LOG.isTraceEnabled()) LOG.trace("can't split " + hs._name + ": histogram not filled yet.");
      return null;
    }
    final int nbins = hs.nbins();
    assert nbins >= 1;

    final boolean hasPreds = hs.hasPreds();
    final boolean hasDenom = hs.hasDenominator();
    final boolean hasNomin = hs.hasNominator();

    // Histogram arrays used for splitting, these are either the original bins
    // (for an ordered predictor), or sorted by the mean response (for an
    // unordered predictor, i.e. categorical predictor).
    double[]   vals =   hs._vals;
    final int vals_dim = hs._vals_dim; 
    int idxs[] = null;          // and a reverse index mapping

    // For categorical (unordered) predictors, sort the bins by average
    // prediction then look for an optimal split.
    if( hs._isInt == 2 && hs._step == 1 ) {
      // Sort the index by average response
      idxs = MemoryManager.malloc4(nbins+1); // Reverse index
      for( int i=0; i<nbins+1; i++ ) idxs[i] = i; //index in 0..nbins-1
      final double[] avgs = MemoryManager.malloc8d(nbins+1);
      for( int i=0; i<nbins; i++ ) avgs[i] = hs.w(i)==0 ? -Double.MAX_VALUE /* value doesn't matter - see below for sending empty buckets (unseen levels) into the NA direction */: hs.wY(i) / hs.w(i); // Average response
      avgs[nbins] = Double.MAX_VALUE;
      ArrayUtils.sort(idxs, avgs);
      // Fill with sorted data.  Makes a copy, so the original data remains in
      // its original order.
      vals = MemoryManager.malloc8d(vals_dim*nbins);

      for( int i=0; i<nbins; i++ ) {
        int id = idxs[i];
        vals[vals_dim*i+0] = hs._vals[vals_dim*id+0];
        vals[vals_dim*i+1] = hs._vals[vals_dim*id+1];
        vals[vals_dim*i+2] = hs._vals[vals_dim*id+2];
        if (hasPreds) {
          vals[vals_dim * i + 3] = hs._vals[vals_dim * id + 3];
          vals[vals_dim * i + 4] = hs._vals[vals_dim * id + 4];
          if (hasDenom)
            vals[vals_dim * i + 5] = hs._vals[vals_dim * id + 5];
          if (hasNomin)
            vals[vals_dim * i + 6] = hs._vals[vals_dim * id + 6];
        }
        if (LOG.isTraceEnabled()) LOG.trace(vals[3*i] + " obs have avg response [" + i + "]=" + avgs[id]);
      }
    }

    // Compute mean/var for cumulative bins from 0 to nbins inclusive.
    double   wlo[] = MemoryManager.malloc8d(nbins+1);
    double  wYlo[] = MemoryManager.malloc8d(nbins+1);
    double wYYlo[] = MemoryManager.malloc8d(nbins+1);
    double pr1lo[] = hasPreds ? MemoryManager.malloc8d(nbins+1) : null;
    double pr2lo[] = hasPreds ? MemoryManager.malloc8d(nbins+1) : null;
    double denlo[] = hasDenom ? MemoryManager.malloc8d(nbins+1) : wlo;
    double nomlo[] = hasNomin ? MemoryManager.malloc8d(nbins+1) : wYlo;
    for( int b=1; b<=nbins; b++ ) {
      int id = vals_dim*(b-1);
      double n0 =   wlo[b-1], n1 = vals[id+0];
      if( n0==0 && n1==0 )
        continue;
      double m0 =  wYlo[b-1], m1 = vals[id+1];
      double s0 = wYYlo[b-1], s1 = vals[id+2];
      wlo[b] = n0+n1;
      wYlo[b] = m0+m1;
      wYYlo[b] = s0+s1;
      if (hasPreds) {
        double p10 = pr1lo[b - 1], p11 = vals[id + 3];
        double p20 = pr2lo[b - 1], p21 = vals[id + 4];
        pr1lo[b] = p10 + p11;
        pr2lo[b] = p20 + p21;
        if (hasDenom) {
          double d0 = denlo[b - 1], d1 = vals[id + 5];
          denlo[b] = d0 + d1;
        }
        if (hasNomin) {
          double d0 = nomlo[b - 1], d1 = vals[id + 6];
          nomlo[b] = d0 + d1;
        }
      }
    }
    final double wNA = hs.wNA();
    double tot = wlo[nbins] + wNA; //total number of (weighted) rows
    // Is any split possible with at least min_obs?
    if( tot < 2*min_rows ) {
      if (LOG.isTraceEnabled()) LOG.trace("can't split " + hs._name + ": min_rows: total number of observations is " + tot);
      return null;
    }
    // If we see zero variance, we must have a constant response in this
    // column.  Normally this situation is cut out before we even try to split,
    // but we might have NA's in THIS column...
    double wYNA = hs.wYNA();
    double wYYNA = hs.wYYNA();
    double var = (wYYlo[nbins]+wYYNA)*tot - (wYlo[nbins]+wYNA)*(wYlo[nbins]+wYNA);
    if( ((float)var) == 0f ) {
      if (LOG.isTraceEnabled()) LOG.trace("can't split " + hs._name + ": var = 0.");
      return null;
    }

    final double denNA = hasDenom ? hs.denNA() : wNA;
    final double nomNA = hasNomin ? hs.nomNA() : wYNA;
    
    // Compute mean/var for cumulative bins from nbins to 0 inclusive.
    double   whi[] = MemoryManager.malloc8d(nbins+1);
    double  wYhi[] = MemoryManager.malloc8d(nbins+1);
    double wYYhi[] = MemoryManager.malloc8d(nbins+1);
    double pr1hi[] = hasPreds ? MemoryManager.malloc8d(nbins+1) : null;
    double pr2hi[] = hasPreds ? MemoryManager.malloc8d(nbins+1) : null;
    double denhi[] = hasDenom ? MemoryManager.malloc8d(nbins+1) : whi;
    double nomhi[] = hasNomin ? MemoryManager.malloc8d(nbins+1) : wYhi;
    for( int b=nbins-1; b>=0; b-- ) {
      double n0 =   whi[b+1], n1 = vals[vals_dim*b];
      if( n0==0 && n1==0 )
        continue;
      double m0 =  wYhi[b+1], m1 = vals[vals_dim*b+1];
      double s0 = wYYhi[b+1], s1 = vals[vals_dim*b+2];
      whi[b] = n0+n1;
      wYhi[b] = m0+m1;
      wYYhi[b] = s0+s1;
      if (hasPreds) {
        double p10 = pr1hi[b + 1], p11 = vals[vals_dim * b + 3];
        double p20 = pr2hi[b + 1], p21 = vals[vals_dim * b + 4];
        pr1hi[b] = p10 + p11;
        pr2hi[b] = p20 + p21;
        if (hasDenom) {
          double d0 = denhi[b + 1], d1 = vals[vals_dim * b + 5];
          denhi[b] = d0 + d1;
        }
        if (hasNomin) {
          double d0 = nomhi[b + 1], d1 = vals[vals_dim * b + 6];
          nomhi[b] = d0 + d1;
        }
      }
      assert MathUtils.compare(wlo[b]+ whi[b]+wNA,tot,1e-5,1e-5);
    }

    double best_seL=Double.MAX_VALUE;   // squared error for left side of the best split (so far)
    double best_seR=Double.MAX_VALUE;   // squared error for right side of the best split (so far)
    DHistogram.NASplitDir nasplit = DHistogram.NASplitDir.None;

    // squared error of all non-NAs
    double seNonNA = wYYhi[0] - wYhi[0]* wYhi[0]/ whi[0]; // Squared Error with no split
    if (seNonNA < 0) seNonNA = 0;
    double seBefore = seNonNA;

    double nLeft = 0;
    double nRight = 0;
    double predLeft = 0;
    double predRight = 0;
    double tree_p0 = 0;
    double tree_p1 = 0;

    // if there are any NAs, then try to split them from the non-NAs
    if (wNA>=min_rows) {
      double seAll = (wYYhi[0] + wYYNA) - (wYhi[0] + wYNA) * (wYhi[0] + wYNA) / (whi[0] + wNA);
      double seNA = wYYNA - wYNA * wYNA / wNA;
      if (seNA < 0) seNA = 0;
      best_seL = seNonNA;
      best_seR = seNA;
      nasplit = DHistogram.NASplitDir.NAvsREST;
      seBefore = seAll;
      nLeft = whi[0]; //all non-NAs
      predLeft = wYhi[0];
      nRight = wNA;
      predRight = wYNA;
      if(hasDenom){
        tree_p0 = nomhi[0] /denhi[0];
        tree_p1 = nomNA / denNA;
      } else {
        tree_p0 = predLeft / nLeft;
        tree_p1 = predRight / nRight;
      }
    }

    // Now roll the split-point across the bins.  There are 2 ways to do this:
    // split left/right based on being less than some value, or being equal/
    // not-equal to some value.  Equal/not-equal makes sense for categoricals
    // but both splits could work for any integral datatype.  Do the less-than
    // splits first.
    int best=0;                         // The no-split
    byte equal=0;                       // Ranged check
    for( int b=1; b<=nbins-1; b++ ) {
      if( vals[vals_dim*b] == 0 ) continue; // Ignore empty splits
      if( wlo[b]+wNA < min_rows ) continue;
      if( whi[b]+wNA < min_rows ) break; // w1 shrinks at the higher bin#s, so if it fails once it fails always
      // We're making an unbiased estimator, so that MSE==Var.
      // Then Squared Error = MSE*N = Var*N
      //                    = (wYY/N - wY^2)*N
      //                    = wYY - N*wY^2
      //                    = wYY - N*(wY/N)(wY/N)
      //                    = wYY - wY^2/N

      // no NAs
      if (wNA==0) {
        double selo = wYYlo[b] - wYlo[b] * wYlo[b] / wlo[b];
        double sehi = wYYhi[b] - wYhi[b] * wYhi[b] / whi[b];
        if (selo < 0) selo = 0;    // Roundoff error; sometimes goes negative
        if (sehi < 0) sehi = 0;    // Roundoff error; sometimes goes negative
        if ((selo + sehi < best_seL + best_seR) || // Strictly less error?
                // Or tied MSE, then pick split towards middle bins
                (selo + sehi == best_seL + best_seR &&
                        Math.abs(b - (nbins >> 1)) < Math.abs(best - (nbins >> 1)))) {
          double tmpPredLeft;
          double tmpPredRight;
          if(constraint != 0 && dist._family.equals(DistributionFamily.quantile)) { 
            int quantileBinLeft = 0;
            int quantileBinRight = 0;
            for (int bin = 1; bin <= nbins; bin++) {
              // left tree prediction quantile
              if (bin <= b) {
                double n = wlo[b];
                double quantilePosition = dist._quantileAlpha * n;
                if(quantilePosition < wlo[bin]){
                  quantileBinLeft = bin;
                  bin = b+1;
                }
                // right tree prediction quantile
              } else {
                double n = (wlo[nbins] - wlo[b]);
                double quantilePosition = dist._quantileAlpha * n;
                if (quantilePosition < wlo[bin] - wlo[b]) {
                  quantileBinRight = bin;
                  break;
                }
              }
            }
            tmpPredLeft = wYlo[quantileBinLeft];
            tmpPredRight = wYlo[quantileBinRight] - wYlo[b]; 
          } else {
            tmpPredLeft = hasDenom ? nomlo[b] / denlo[b] : wYlo[b] / wlo[b];
            tmpPredRight = hasDenom ? nomhi[b] / denhi[b] : wYhi[b] / whi[b];
          }
          if (constraint == 0 || (constraint * tmpPredLeft <= constraint * tmpPredRight)) {
            best_seL = selo;
            best_seR = sehi;
            best = b;
            nLeft = wlo[best];
            nRight = whi[best];
            predLeft = wYlo[best];
            predRight = wYhi[best];
            tree_p0 = tmpPredLeft;
            tree_p1 = tmpPredRight;
          }
        }
      } else {
        // option 1: split the numeric feature and throw NAs to the left
        {
          double selo = wYYlo[b] + wYYNA - (wYlo[b] + wYNA) * (wYlo[b] + wYNA) / (wlo[b] + wNA);
          double sehi = wYYhi[b] - wYhi[b] * wYhi[b] / whi[b];
          if (selo < 0) selo = 0;    // Roundoff error; sometimes goes negative
          if (sehi < 0) sehi = 0;    // Roundoff error; sometimes goes negative
          if ((selo + sehi < best_seL + best_seR) || // Strictly less error?
                  // Or tied SE, then pick split towards middle bins
                  (selo + sehi == best_seL + best_seR &&
                          Math.abs(b - (nbins >> 1)) < Math.abs(best - (nbins >> 1)))) {
            if((wlo[b] + wNA) >= min_rows && whi[b] >= min_rows) {
              double tmpPredLeft;
              double tmpPredRight;
              if(constraint != 0 && dist._family.equals(DistributionFamily.quantile)) {
                int quantileBinLeft = 0;
                int quantileBinRight = 0;
                for (int bin = 1; bin <= nbins; bin++) {
                  // left tree prediction quantile
                  if (bin <= b) {
                    double n = wlo[b];
                    double quantilePosition = dist._quantileAlpha * n;
                    if(quantilePosition < wlo[bin]){
                      quantileBinLeft = bin;
                      bin = b+1;
                    }
                    // right tree prediction quantile
                  } else {
                    double n = (wlo[nbins] - wlo[b]);
                    double quantilePosition = dist._quantileAlpha * n;
                    if (quantilePosition < wlo[bin] - wlo[b]) {
                      quantileBinRight = bin;
                      break;
                    }
                  }
                }
                tmpPredLeft = wYlo[quantileBinLeft] + wYNA;
                tmpPredRight = wYlo[quantileBinRight] - wYlo[b];
              } else { 
                tmpPredLeft = hasDenom ? (nomlo[b] + nomNA) / (denlo[b] + denNA) : (wYlo[b] + wYNA) / (wlo[b] + wNA);
                tmpPredRight = hasDenom ? nomhi[b] / denhi[b] : wYhi[b] / whi[b];
              }
              if (constraint == 0 || (constraint * tmpPredLeft <= constraint * tmpPredRight)) {
                best_seL = selo;
                best_seR = sehi;
                best = b;
                nLeft = wlo[best] + wNA;
                nRight = whi[best];
                predLeft = wYlo[best] + wYNA;
                predRight = wYhi[best];
                nasplit = DHistogram.NASplitDir.NALeft;
                tree_p0 = tmpPredLeft;
                tree_p1 = tmpPredRight;
              }
            }
          }
        }

        // option 2: split the numeric feature and throw NAs to the right
        {
          double selo = wYYlo[b] - wYlo[b] * wYlo[b] / wlo[b];
          double sehi = wYYhi[b]+wYYNA - (wYhi[b]+wYNA) * (wYhi[b]+wYNA) / (whi[b]+wNA);
          if (selo < 0) selo = 0;    // Roundoff error; sometimes goes negative
          if (sehi < 0) sehi = 0;    // Roundoff error; sometimes goes negative
          if ((selo + sehi < best_seL + best_seR) || // Strictly less error?
                  // Or tied SE, then pick split towards middle bins
                  (selo + sehi == best_seL + best_seR &&
                          Math.abs(b - (nbins >> 1)) < Math.abs(best - (nbins >> 1)))) {
            if( wlo[b] >= min_rows && (whi[b] + wNA) >= min_rows ) {
              double tmpPredLeft;
              double tmpPredRight;
              if(constraint != 0 && dist._family.equals(DistributionFamily.quantile)) {
                int quantileBinLeft = 0;
                int quantileBinRight = 0;
                double ratio = 1;
                double delta = 1;
                for (int bin = 1; bin <= nbins; bin++) {
                  // left tree prediction quantile
                  if (bin <= b) {
                    double n = wlo[b];
                    double quantilePosition = dist._quantileAlpha * n;
                    if(quantilePosition < wlo[bin]){
                      quantileBinLeft = bin;
                      bin = b+1;
                    }
                    // right tree prediction quantile
                  } else {
                    double n = (wlo[nbins] - wlo[b]);
                    double quantilePosition = dist._quantileAlpha * n;
                    if (quantilePosition < wlo[bin] - wlo[b]) {
                      quantileBinRight = bin;
                      break;
                    }
                  }
                }
                tmpPredLeft = wYlo[quantileBinLeft];
                tmpPredRight = wYlo[quantileBinRight] - wYlo[b] + wYNA;
              } else {
                tmpPredLeft = hasDenom ? nomlo[b] / denlo[b] : wYlo[b] / wlo[b];
                tmpPredRight = hasDenom ? (nomhi[b] + nomNA) / (denhi[b] + denNA) : (wYhi[b] + wYNA) / (whi[b] + wNA);
              }
              if (constraint == 0 || (constraint * tmpPredLeft <= constraint * tmpPredRight)) {
                best_seL = selo;
                best_seR = sehi;
                best = b;
                nLeft = wlo[best];
                nRight = whi[best] + wNA;
                predLeft = wYlo[best];
                predRight = wYhi[best] + wYNA;
                nasplit = DHistogram.NASplitDir.NARight;
                tree_p0 = tmpPredLeft;
                tree_p1 = tmpPredRight;
              }
            }
          }
        }
      }
    }

    if( best==0 && nasplit== DHistogram.NASplitDir.None) {
      if (LOG.isTraceEnabled()) LOG.trace("can't split " + hs._name + ": no optimal split found:\n" + hs);
      return null;
    }

    //if( se <= best_seL+best_se1) return null; // Ultimately roundoff error loses, and no split actually helped
    if (!(best_seL+ best_seR < seBefore * (1- hs._minSplitImprovement))) {
      if (LOG.isTraceEnabled()) LOG.trace("can't split " + hs._name + ": not enough relative improvement: " + (1-(best_seL + best_seR) / seBefore) + "\n" + hs);
      return null;
    }

    assert(Math.abs(tot - (nRight + nLeft)) < 1e-5*tot);

    if( MathUtils.equalsWithinOneSmallUlp((float)(predLeft / nLeft),(float)(predRight / nRight)) ) {
      if (LOG.isTraceEnabled()) LOG.trace("can't split " + hs._name + ": Predictions for left/right are the same.");
      return null;
    }

    if (nLeft < min_rows || nRight < min_rows) {
      if (LOG.isTraceEnabled()) LOG.trace("can't split " + hs._name + ": split would violate min_rows limit.");
      return null;
    }

    // FIXME (PUBDEV-7553): these asserts do not hold because histogram doesn't skip rows with NA response
    // assert hasNomin || nomLeft == predLeft;
    // assert hasNomin || nomRight == predRight;

    final double node_p0 = predLeft / nLeft;
    final double node_p1 = predRight / nRight;

    if (constraint != 0) {
      if (constraint * tree_p0 > constraint * tree_p1) {
        if (LOG.isTraceEnabled()) LOG.trace("can't split " + hs._name + ": split would violate monotone constraint.");
        return null;
      }
    }

    if (!Double.isNaN(min)) {
      if (tree_p0 < min) {
        if (! useBounds) {
          if (LOG.isTraceEnabled()) LOG.trace("minimum constraint violated in the left split of " + hs._name + ": node will not split");
          return null;
        }
        if (LOG.isTraceEnabled()) LOG.trace("minimum constraint violated in the left split of " + hs._name + ": left node will predict minimum bound: " + min);
        tree_p0 = min;
        if (nasplit == DHistogram.NASplitDir.NAvsREST) {
          best_seL = pr1hi[0];
        } else if (nasplit == DHistogram.NASplitDir.NALeft) {
          best_seL = pr1lo[best] + hs.seP1NA();
        } else {
          best_seL = pr1lo[best];
        }
      }
      if (tree_p1 < min) {
        if (! useBounds) {
          if (LOG.isTraceEnabled()) LOG.trace("minimum constraint violated in the right split of " + hs._name + ": node will not split");
          return null;
        }
        if (LOG.isTraceEnabled()) LOG.trace("minimum constraint violated in the right split of " + hs._name + ": right node will predict minimum bound: " + min);
        tree_p1 = min;
        if (nasplit == DHistogram.NASplitDir.NAvsREST) {
          best_seR = hs.seP1NA();
        } else if (nasplit == DHistogram.NASplitDir.NARight) {
          best_seR = pr1hi[best] + hs.seP1NA();
        } else {
          best_seR = pr1hi[best];
        }
      }
    }
    if (!Double.isNaN(max)) {
      if (tree_p0 > max) {
        if (! useBounds) {
          if (LOG.isTraceEnabled()) LOG.trace("minimum constraint violated in the left split of " + hs._name + ": node will not split");
          return null;
        }
        if (LOG.isTraceEnabled()) LOG.trace("maximum constraint violated in the left split of " + hs._name + ": left node will predict maximum bound: " + max);
        tree_p0 = max;
        if (nasplit == DHistogram.NASplitDir.NAvsREST) {
          best_seL = pr2hi[0];
        } else if (nasplit == DHistogram.NASplitDir.NALeft) {
          best_seL = pr2lo[best] + hs.seP2NA();
        } else {
          best_seL = pr2lo[best];
        }
      }
      if (tree_p1 > max) {
        if (! useBounds) {
          if (LOG.isTraceEnabled()) LOG.trace("minimum constraint violated in the right split of " + hs._name + ": node will not split");
          return null;
        }
        if (LOG.isTraceEnabled()) LOG.trace("maximum constraint violated in the right split of " + hs._name + ": right node will predict maximum bound: " + max);
        tree_p1 = max;
        if (nasplit == DHistogram.NASplitDir.NAvsREST) {
          best_seR = hs.seP2NA();
        } else if (nasplit == DHistogram.NASplitDir.NARight) {
          best_seR = pr2hi[best] + hs.seP2NA();
        } else {
          best_seR = pr2hi[best];
        }
      }
    }

    if (!(best_seL + best_seR < seBefore * (1- hs._minSplitImprovement))) {
      if (LOG.isTraceEnabled()) LOG.trace("can't split " + hs._name + ": not enough relative improvement: " + (1-(best_seL + best_seR) / seBefore) + "\n" + hs);
      return null;
    }

    if( MathUtils.equalsWithinOneSmallUlp((float) tree_p0,(float) tree_p1) ) {
      if (LOG.isTraceEnabled()) LOG.trace("can't split " + hs._name + ": Predictions for left/right are the same.");
      return null;
    }


    // For categorical (unordered) predictors, we sorted the bins by average
    // prediction then found the optimal split on sorted bins
    IcedBitSet bs = null;       // In case we need an arbitrary bitset
    if (idxs != null            // We sorted bins; need to build a bitset
            && nasplit != DHistogram.NASplitDir.NAvsREST) { // NA vs REST don't need a bitset
      final int off = (int) hs._min;
      bs = new IcedBitSet(nbins, off);
      equal = fillBitSet(hs, off, idxs, best, nbins, bs);
      if (equal < 0)
        return null;
    }

    // if still undecided (e.g., if there are no NAs in training), pick a good default direction for NAs in test time
    if (nasplit == DHistogram.NASplitDir.None) {
      nasplit = nLeft > nRight ? DHistogram.NASplitDir.Left : DHistogram.NASplitDir.Right;
    }
    
    assert constraint == 0 || constraint * tree_p0 <= constraint * tree_p1;
    assert (Double.isNaN(min) || min <= tree_p0) && (Double.isNaN(max) || tree_p0 <= max);
    assert (Double.isNaN(min) || min <= tree_p1) && (Double.isNaN(max) || tree_p1 <= max);

    Split split = new Split(col, best, nasplit, bs, equal, seBefore, best_seL, best_seR, nLeft, nRight, node_p0, node_p1, tree_p0, tree_p1);
    if (LOG.isTraceEnabled()) LOG.trace("splitting on " + hs._name + ": " + split);
    return split;
  }

  static Split findBestSplitPointUplift(DHistogram hs, int col, double min_rows) {
    if(hs._valsUplift == null) {
      if (LOG.isTraceEnabled()) LOG.trace("can't split " + hs._name + ": histogram not filled yet.");
      return null;
    }
    final int nbins = hs.nbins();
    assert nbins >= 1;

    final Divergence upliftMetric = hs._upliftMetric;

    // Histogram arrays used for splitting, these are either the original bins
    // (for an ordered predictor), or sorted by the mean response (for an
    // unordered predictor, i.e. categorical predictor).
    double[]   vals =   hs._vals;
    final int vals_dim = hs._vals_dim;
    double[] valsUplift = hs._valsUplift;
    final int valsUpliftDim = 4;
    int idxs[] = null;          // and a reverse index mapping

    // For categorical (unordered) predictors, sort the bins by average
    // prediction then look for an optimal split.
    if( hs._isInt == 2 && hs._step == 1 ) {
      // Sort the index by average response
      idxs = MemoryManager.malloc4(nbins+1); // Reverse index
      for( int i=0; i<nbins+1; i++ ) idxs[i] = i; //index in 0..nbins-1
      final double[] avgs = MemoryManager.malloc8d(nbins+1);
      for( int i=0; i<nbins; i++ ) avgs[i] = hs.w(i)==0 ? -Double.MAX_VALUE /* value doesn't matter - see below for sending empty buckets (unseen levels) into the NA direction */: hs.wY(i) / hs.w(i); // Average response
      avgs[nbins] = Double.MAX_VALUE;
      ArrayUtils.sort(idxs, avgs);
      // Fill with sorted data.  Makes a copy, so the original data remains in
      // its original order.
      vals = MemoryManager.malloc8d(vals_dim*nbins);
      valsUplift = MemoryManager.malloc8d(4*nbins);

      for( int i=0; i<nbins; i++ ) {
        int id = idxs[i];
        vals[vals_dim*i+0] = hs._vals[vals_dim*id+0];
        vals[vals_dim*i+1] = hs._vals[vals_dim*id+1];
        vals[vals_dim*i+2] = hs._vals[vals_dim*id+2];
        valsUplift[valsUpliftDim * i] = valsUplift[valsUpliftDim * id];
        valsUplift[valsUpliftDim * i + 1] = valsUplift[valsUpliftDim * id + 1];
        valsUplift[valsUpliftDim * i + 2] = valsUplift[valsUpliftDim * id + 2];
        valsUplift[valsUpliftDim * i + 3] = valsUplift[valsUpliftDim * id + 3];
        if (LOG.isTraceEnabled()) LOG.trace(vals[3*i] + " obs have avg response [" + i + "]=" + avgs[id]);
      }
    }

    // Compute mean/var for cumulative bins from 0 to nbins inclusive.
    double   wlo[] = MemoryManager.malloc8d(nbins+1);
    double  wYlo[] = MemoryManager.malloc8d(nbins+1);
    double wYYlo[] = MemoryManager.malloc8d(nbins+1);
    double[]  numloTreat = MemoryManager.malloc8d(nbins + 1);
    double[]  resploTreat = MemoryManager.malloc8d(nbins + 1);
    double[] numloContr = MemoryManager.malloc8d(nbins + 1);
    double[]  resploContr = MemoryManager.malloc8d(nbins + 1);

    for( int b = 1; b <= nbins; b++ ) {
      int id = vals_dim * (b - 1);
      double n0 = wlo[b - 1], n1 = vals[id + 0];
      if( n0==0 && n1==0 )
        continue;
      double m0 =  wYlo[b - 1], m1 = vals[id + 1];
      double s0 = wYYlo[b - 1], s1 = vals[id + 2];
      wlo[b] = n0+n1;
      wYlo[b] = m0+m1;
      wYYlo[b] = s0+s1;
      id = valsUpliftDim * (b - 1);
      double nt0 = numloTreat[b - 1], nt1 = valsUplift[id];
      numloTreat[b] = nt0 + nt1;
      double dt0 = resploTreat[b - 1], dt1 = valsUplift[id + 1];
      resploTreat[b] = dt0 + dt1;
      double nc0 = numloContr[b - 1], nc1 = valsUplift[id + 2];
      numloContr[b] = nc0 + nc1;
      double dc0 = resploContr[b - 1], dc1 = valsUplift[id + 3];
      resploContr[b] = dc0 + dc1;
    }
    final double wNA = hs.wNA();
    double tot = wlo[nbins] + wNA; //total number of (weighted) rows
    // Is any split possible with at least min_obs?
    if( tot < 2*min_rows ) {
      if (LOG.isTraceEnabled()) LOG.trace("can't split " + hs._name + ": min_rows: total number of observations is " + tot);
      return null;
    }
    // If we see zero variance, we must have a constant response in this
    // column.  Normally this situation is cut out before we even try to split,
    // but we might have NA's in THIS column...
    double wYNA = hs.wYNA();
    double wYYNA = hs.wYYNA();
    double var = (wYYlo[nbins]+wYYNA)*tot - (wYlo[nbins]+wYNA)*(wYlo[nbins]+wYNA);
    if( ((float)var) == 0f ) {
      if (LOG.isTraceEnabled()) LOG.trace("can't split " + hs._name + ": var = 0.");
      return null;
    }

    // Compute mean/var for cumulative bins from nbins to 0 inclusive.
    double   whi[] = MemoryManager.malloc8d(nbins+1);
    double  wYhi[] = MemoryManager.malloc8d(nbins+1);
    double wYYhi[] = MemoryManager.malloc8d(nbins+1);
    double[] numhiTreat = MemoryManager.malloc8d(nbins+1);
    double[] resphiTreat = MemoryManager.malloc8d(nbins+1);
    double[] numhiContr = MemoryManager.malloc8d(nbins+1);
    double[] resphiContr = MemoryManager.malloc8d(nbins+1);
    
    for( int b = nbins-1; b >= 0; b-- ) {
      int id = vals_dim * b;
      double n0 = whi[b+1], n1 = vals[id];
      if( n0==0 && n1==0 )
        continue;
      double m0 =  wYhi[b + 1], m1 = vals[id + 1];
      double s0 = wYYhi[b + 1], s1 = vals[id + 2];
      whi[b] = n0+n1;
      wYhi[b] = m0+m1;
      wYYhi[b] = s0+s1;
      id = valsUpliftDim * b;
      double nt0 = numhiTreat[b + 1], nt1 = valsUplift[id];
      numhiTreat[b] = nt0 + nt1;
      double dt0 = resphiTreat[b + 1], dt1 = valsUplift[id + 1];
      resphiTreat[b] = dt0 + dt1;
      double nc0 = numhiContr[b + 1], nc1 = valsUplift[id + 2];
      numhiContr[b] = nc0 + nc1;
      double dc0 = resphiContr[b + 1], dc1 = valsUplift[id + 3];
      resphiContr[b] = dc0 + dc1;
      assert MathUtils.compare(wlo[b]+ whi[b]+wNA,tot,1e-5,1e-5);
    }

    double best_seL=Double.MAX_VALUE;   // squared error for left side of the best split (so far)
    double best_seR=Double.MAX_VALUE;   // squared error for right side of the best split (so far)
    DHistogram.NASplitDir nasplit = DHistogram.NASplitDir.None;

    // squared error of all non-NAs
    double seNonNA = wYYhi[0] - wYhi[0]* wYhi[0]/ whi[0]; // Squared Error with no split
    if (seNonNA < 0) seNonNA = 0;
    double seBefore = seNonNA;

    double nLeft = 0;
    double nRight = 0;
    double predLeft = 0;
    double predRight = 0;
    double tree_p0 = 0;
    double tree_p1 = 0;

    double  numTreatNA = hs.numTreatmentNA();
    double  respTreatNA = hs.respTreatmentNA();
    double  numContrNA = hs.numControlNA();
    double  respContrNA = hs.respControlNA();

    double bestNLCT1 = 0;
    double bestNLCT0 = 0;
    double bestNRCT1 = 0;
    double bestNRCT0 = 0;
    double bestPrLY1CT1 = 0;
    double bestPrLY1CT0 = 0;
    double bestPrRY1CT1 = 0;
    double bestPrRY1CT0 = 0;

    double nCT1 = numhiTreat[0];
    double nCT0 = numhiContr[0];
    double nCT1Y1hi = resphiTreat[0];
    double nCT0Y1hi = resphiContr[0];
    // no response in treatment or control group -> can't split
    if(nCT1 == 0 || nCT0 == 0 || nCT1Y1hi == 0 || nCT0Y1hi == 0){
      return null;
    }
    double prY1CT1 = nCT1Y1hi/nCT1;
    double prY1CT0 = nCT0Y1hi/nCT0;
    double bestUpliftGain = upliftMetric.node(prY1CT1 , prY1CT0);
    // if there are any NAs, then try to split them from the non-NAs
    if (wNA>=min_rows) {
      double prCT1All = (nCT1 + numTreatNA + 1)/(nCT0 + numContrNA + nCT1 + numTreatNA + 2);
      double prCT0All = 1-prCT1All;
      double prY1CT1All = (resphiTreat[0] + respTreatNA) / (nCT1 + numTreatNA);
      double prY1CT0All = (resphiContr[0] + respContrNA) / (nCT0 + numContrNA);
      double prLCT1 = (nCT1 + 1)/(nCT0 + nCT1 + 2);
      double prLCT0 = 1 - prLCT1;
      double prL = prLCT1 * prCT1All + prLCT0 * prCT0All;
      double prR = 1 - prL;
      double nLCT1 = numhiTreat[0];
      double nLCT0 = numhiContr[0];
      double prLY1CT1 = (resphiTreat[0] + 1) / (numhiTreat[0] + 2);
      double prLY1CT0 = (resphiContr[0] + 1) / (numhiContr[0] + 2);
      double nRCT1 = numTreatNA;
      double nRCT0 = numContrNA;
      double prRY1CT1 = (respTreatNA + 1) / (numTreatNA + 2);
      double prRY1CT0 = (respContrNA + 1) / (numContrNA + 2);
      bestUpliftGain = upliftMetric.value(prY1CT1All, prY1CT0All, prL, prLY1CT1, prLY1CT0, prR, prRY1CT1, prRY1CT0, prCT1All, prCT0All, prLCT1, prLCT0);
      if (bestUpliftGain != Double.POSITIVE_INFINITY) {
        bestNLCT1 = nLCT1;
        bestNLCT0 = nLCT0;
        bestNRCT1 = nRCT1;
        bestNRCT0 = nRCT0;
        bestPrLY1CT1 = prLY1CT1;
        bestPrLY1CT0 = prLY1CT0;
        bestPrRY1CT1 = prRY1CT1;
        bestPrRY1CT0 = prRY1CT0;
        double seAll = (wYYhi[0] + wYYNA) - (wYhi[0] + wYNA) * (wYhi[0] + wYNA) / (whi[0] + wNA);
        double seNA = wYYNA - wYNA * wYNA / wNA;
        if (seNA < 0) seNA = 0;
        best_seL = seNonNA;
        best_seR = seNA;
        nasplit = DHistogram.NASplitDir.NAvsREST;
        seBefore = seAll;
        nLeft = whi[0]; //all non-NAs
        predLeft = wYhi[0];
        nRight = wNA;
        predRight = wYNA;
        tree_p0 = predLeft / nLeft;
        tree_p1 = predRight / nRight;
      }
    }

    // Now roll the split-point across the bins.  There are 2 ways to do this:
    // split left/right based on being less than some value, or being equal/
    // not-equal to some value.  Equal/not-equal makes sense for categoricals
    // but both splits could work for any integral datatype.  Do the less-than
    // splits first.
    int best=0;                         // The no-split
    byte equal=0;                       // Ranged check
    for( int b=1; b<=nbins-1; b++ ) {
      if( vals[vals_dim*b] == 0 ) continue; // Ignore empty splits
      if( wlo[b]+wNA < min_rows ) continue;
      if( whi[b]+wNA < min_rows ) break; // w1 shrinks at the higher bin#s, so if it fails once it fails always
      // We're making an unbiased estimator, so that MSE==Var.
      // Then Squared Error = MSE*N = Var*N
      //                    = (wYY/N - wY^2)*N
      //                    = wYY - N*wY^2
      //                    = wYY - N*(wY/N)(wY/N)
      //                    = wYY - wY^2/N

      // no NAs
      if (wNA==0) {
        double selo = wYYlo[b] - wYlo[b] * wYlo[b] / wlo[b];
        double sehi = wYYhi[b] - wYhi[b] * wYhi[b] / whi[b];
        if (selo < 0) selo = 0;    // Roundoff error; sometimes goes negative
        if (sehi < 0) sehi = 0;    // Roundoff error; sometimes goes negative
        nCT1 = numhiTreat[b];
        nCT0 = numhiContr[b];
        double prCT1 = (nCT1 + 1)/(nCT0 + nCT1 + 2);
        double prCT0 = 1-prCT1;
        double prLCT1 = (numloTreat[b] + 1)/(numloTreat[b] + numhiTreat[b] + 2);
        double prLCT0 = 1 - prLCT1;
        double prL = prLCT1 * prCT1 + prLCT0 * prCT0;
        double prR = 1 - prL;
        double nLCT1 = numloTreat[b];
        double nLCT0 = numloContr[b];
        double prLY1CT1 = (resploTreat[b] + 1) / (numloTreat[b] + 2);
        double prLY1CT0 = (resploContr[b] + 1) / (numloContr[b] + 2);
        double nRCT1 = numhiTreat[b];
        double nRCT0 = numhiContr[b];
        double prRY1CT1 = (resphiTreat[b] + 1) / (numhiTreat[b] + 2);
        double prRY1CT0 = (resphiContr[b] + 1) / (numhiContr[b] + 2);
        double upliftGain = upliftMetric.value(prY1CT1, prY1CT0, prL, prLY1CT1, prLY1CT0, prR, prRY1CT1, prRY1CT0, prCT1, prCT0, prLCT1, prLCT0);
        if (upliftGain != Double.POSITIVE_INFINITY && upliftGain > bestUpliftGain) {
          double tmpPredLeft =  wYlo[b] / wlo[b];
          double tmpPredRight =  wYhi[b] / whi[b];
          best_seL = selo;
          best_seR = sehi;
          best = b;
          nLeft = wlo[best];
          nRight = whi[best];
          predLeft = wYlo[best];
          predRight = wYhi[best];
          tree_p0 = tmpPredLeft;
          tree_p1 = tmpPredRight;
          bestUpliftGain = upliftGain;
          bestNLCT1 = nLCT1;
          bestNLCT0 = nLCT0;
          bestNRCT1 = nRCT1;
          bestNRCT0 = nRCT0;
          bestPrLY1CT1 = prLY1CT1;
          bestPrLY1CT0 = prLY1CT0;
          bestPrRY1CT1 = prRY1CT1;
          bestPrRY1CT0 = prRY1CT0;
        }
      } else {
        // option 1: split the numeric feature and throw NAs to the left
        {
          double selo = wYYlo[b] + wYYNA - (wYlo[b] + wYNA) * (wYlo[b] + wYNA) / (wlo[b] + wNA);
          double sehi = wYYhi[b] - wYhi[b] * wYhi[b] / whi[b];
          if (selo < 0) selo = 0;    // Roundoff error; sometimes goes negative
          if (sehi < 0) sehi = 0;    // Roundoff error; sometimes goes negative
          nCT1 = numhiTreat[b] + numTreatNA;
          nCT0 = numhiContr[b] + numContrNA;
          double prCT1 = (nCT1 + 1)/(nCT0 + nCT1 + 2);
          double prCT0 = 1 - prCT1;
          double prLCT1 = (numloTreat[b] + numTreatNA + 1)/(numloTreat[b] + numTreatNA + numhiTreat[b] + 2);
          double prLCT0 = 1 - prLCT1;
          double prL = prLCT1 * prCT1 + prLCT0 * prCT0;
          double prR = 1 - prL;
          double nLCT1 = numloTreat[b] + numTreatNA;
          double nLCT0 = numloContr[b] + numContrNA;
          double prLY1CT1 = (resploTreat[b] + respTreatNA + 1) / (numloTreat[b] + numTreatNA + 2);
          double prLY1CT0 = (resploContr[b] + respContrNA + 1) / (numloContr[b] + numContrNA + 2);
          double nRCT1 = numhiTreat[b];
          double nRCT0 = numhiContr[b];
          double prRY1CT1 = (resphiTreat[b] + 1) / (numhiTreat[b] + 2);
          double prRY1CT0 = (resphiContr[b] + 1) / (numhiContr[b] + 2);
          double upliftGain = upliftMetric.value(prY1CT1, prY1CT0, prL, prLY1CT1, prLY1CT0, prR, prRY1CT1, prRY1CT0, prCT1, prCT0, prLCT1, prLCT0);
          if (upliftGain != Double.POSITIVE_INFINITY && upliftGain > bestUpliftGain) {
            if((wlo[b] + wNA) >= min_rows && whi[b] >= min_rows) {
              double tmpPredLeft = (wYlo[b] + wYNA) / (wlo[b] + wNA);
              double tmpPredRight = wYhi[b] / whi[b];
              best_seL = selo;
              best_seR = sehi;
              best = b;
              nLeft = wlo[best] + wNA;
              nRight = whi[best];
              predLeft = wYlo[best] + wYNA;
              predRight = wYhi[best];
              nasplit = DHistogram.NASplitDir.NALeft;
              tree_p0 = tmpPredLeft;
              tree_p1 = tmpPredRight;
              bestUpliftGain = upliftGain;
              bestNLCT1 = nLCT1;
              bestNLCT0 = nLCT0;
              bestNRCT1 = nRCT1;
              bestNRCT0 = nRCT0;
              bestPrLY1CT1 = prLY1CT1;
              bestPrLY1CT0 = prLY1CT0;
              bestPrRY1CT1 = prRY1CT1;
              bestPrRY1CT0 = prRY1CT0;
            }
          }
        }

        // option 2: split the numeric feature and throw NAs to the right
        {
          double selo = wYYlo[b] - wYlo[b] * wYlo[b] / wlo[b];
          double sehi = wYYhi[b]+wYYNA - (wYhi[b]+wYNA) * (wYhi[b]+wYNA) / (whi[b]+wNA);
          if (selo < 0) selo = 0;    // Roundoff error; sometimes goes negative
          if (sehi < 0) sehi = 0;    // Roundoff error; sometimes goes negative
          nCT1 = numhiTreat[b] + numTreatNA;
          nCT0 = numhiContr[b] + numContrNA;
          double prCT1 = (nCT1 + 1)/(nCT0 + nCT1 + 2);
          double prCT0 = 1 - prCT1;
          double prLCT1 = (numloTreat[b] + 1)/(numloTreat[b] + numhiTreat[0] + numTreatNA + 2);
          double prLCT0 = 1 - prLCT1;
          double prL = prLCT1 * prCT1 + prLCT0 * prCT0;
          double prR = 1 - prL;
          double nLCT1 = numloTreat[b];
          double nLCT0 = numloContr[b];
          double prLY1CT1 = (resploTreat[b] + respTreatNA + 1) / (numloTreat[b] + 2);
          double prLY1CT0 = (resploContr[b] + respContrNA + 1) / (numloContr[b] + 2);
          double nRCT1 = numhiTreat[b] + numTreatNA;
          double nRCT0 = numhiContr[b] + numContrNA;
          double prRY1CT1 = (resphiTreat[b] + 1) / (numhiTreat[b] + numTreatNA + 2);
          double prRY1CT0 = (resphiContr[b] + 1) / (numhiContr[b] + numContrNA + 2);
          double upliftGain = upliftMetric.value(prY1CT1, prY1CT0, prL, prLY1CT1, prLY1CT0, prR, prRY1CT1, prRY1CT0, prCT1, prCT0, prLCT1, prLCT0);
          if (upliftGain != Double.POSITIVE_INFINITY &&  upliftGain > bestUpliftGain) {
            if( wlo[b] >= min_rows && (whi[b] + wNA) >= min_rows ) {
              double tmpPredLeft = wYlo[b] / wlo[b];
              double tmpPredRight = (wYhi[b] + wYNA) / (whi[b] + wNA);
              best_seL = selo;
              best_seR = sehi;
              best = b;
              nLeft = wlo[best];
              nRight = whi[best] + wNA;
              predLeft = wYlo[best];
              predRight = wYhi[best] + wYNA;
              nasplit = DHistogram.NASplitDir.NARight;
              tree_p0 = tmpPredLeft;
              tree_p1 = tmpPredRight;
              bestUpliftGain = upliftGain;
              bestNLCT1 = nLCT1;
              bestNLCT0 = nLCT0;
              bestNRCT1 = nRCT1;
              bestNRCT0 = nRCT0;
              bestPrLY1CT1 = prLY1CT1;
              bestPrLY1CT0 = prLY1CT0;
              bestPrRY1CT1 = prRY1CT1;
              bestPrRY1CT0 = prRY1CT0;
            }
          }
        }
      }
    }

    if( best==0 && nasplit== DHistogram.NASplitDir.None) {
      if (LOG.isTraceEnabled()) LOG.trace("can't split " + hs._name + ": no optimal split found:\n" + hs);
      return null;
    }

    if (!(best_seL+ best_seR < seBefore * (1- hs._minSplitImprovement))) {
      if (LOG.isTraceEnabled()) LOG.trace("can't split " + hs._name + ": not enough relative improvement: " + (1-(best_seL + best_seR) / seBefore) + "\n" + hs);
      return null;
    }

    assert(Math.abs(tot - (nRight + nLeft)) < 1e-5*tot);

    if( MathUtils.equalsWithinOneSmallUlp((float)(predLeft / nLeft),(float)(predRight / nRight)) ) {
      if (LOG.isTraceEnabled()) LOG.trace("can't split " + hs._name + ": Predictions for left/right are the same.");
      return null;
    }

    if (nLeft < min_rows || nRight < min_rows) {
      if (LOG.isTraceEnabled()) LOG.trace("can't split " + hs._name + ": split would violate min_rows limit.");
      return null;
    }

    final double node_p0 = predLeft / nLeft;
    final double node_p1 = predRight / nRight;

    if( MathUtils.equalsWithinOneSmallUlp((float) tree_p0,(float) tree_p1) ) {
      if (LOG.isTraceEnabled()) LOG.trace("can't split " + hs._name + ": Predictions for left/right are the same.");
      return null;
    }
    
    // For categorical (unordered) predictors, we sorted the bins by average
    // prediction then found the optimal split on sorted bins
    IcedBitSet bs = null;       // In case we need an arbitrary bitset
    if( idxs != null ) {        // We sorted bins; need to build a bitset
      final int off = (int) hs._min;
      bs = new IcedBitSet(nbins, off);
      equal = fillBitSet(hs, off, idxs, best, nbins, bs);
      if (equal < 0)
        return null;
    }

    // if still undecided (e.g., if there are no NAs in training), pick a good default direction for NAs in test time
    if (nasplit == DHistogram.NASplitDir.None) {
      nasplit = nLeft > nRight ? DHistogram.NASplitDir.Left : DHistogram.NASplitDir.Right;
    }

    Split split = new Split(col, best, nasplit, bs, equal, seBefore, best_seL, best_seR, nLeft, nRight, node_p0, node_p1, tree_p0, tree_p1, bestPrLY1CT1, bestPrLY1CT0, bestPrRY1CT1, bestPrRY1CT0, bestNLCT1, bestNLCT0, bestNRCT1, bestNRCT0);
    if (LOG.isTraceEnabled()) LOG.trace("splitting on " + hs._name + ": " + split);
    return split;
  }

  private static byte fillBitSet(DHistogram hs, int off, int[] idxs, int best, int nbins, IcedBitSet bs) {
    for( int i=best; i<nbins; i++ )
      bs.set(idxs[i] + off);

    // Throw empty (unseen) categorical buckets into the majority direction (should behave like NAs during testing)
    int nonEmptyThatWentRight = 0;
    int nonEmptyThatWentLeft = 0;
    for (int i=0; i<nbins; i++) {
      if (hs.w(i) > 0) {
        if (bs.contains(i + off))
          nonEmptyThatWentRight++;
        else
          nonEmptyThatWentLeft++;
      }
    }
    boolean shouldGoLeft = nonEmptyThatWentLeft >= nonEmptyThatWentRight;
    for (int i=0; i<nbins; i++) {
      assert(bs.isInRange(i + off));
      if (hs.w(i) == 0) {
        if (bs.contains(i + off) && shouldGoLeft) {
          bs.clear(i + off);
        }
        if (!bs.contains(i + off) && !shouldGoLeft) {
          bs.set(i + off);
        }
      }
    }

    if (bs.cardinality()==0 || bs.cardinality()==bs.size()) {
      if (LOG.isTraceEnabled()) LOG.trace("can't split " + hs._name + ": no separation of categoricals possible");
      return -1;
    }

    return (byte)(bs.max() <= 32 ? 2 : 3); // Flag for bitset split; also check max size
  }
}
