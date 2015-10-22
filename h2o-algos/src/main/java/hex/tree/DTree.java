package hex.tree;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.*;

import java.util.*;

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
  final String[] _names; // Column names
  final int _ncols;      // Active training columns
  final char _nbins;     // Numerical columns: Max number of bins to split over
  final char _nbins_cats; // Categorical columns: Max number of bins to split over
  final char _nclass;    // #classes, or 1 for regression trees
  public final double _min_rows;   // Fewest allowed (weighted) rows in any split
  final long _seed;      // RNG seed; drives sampling seeds if necessary
  private Node[] _ns;    // All the nodes in the tree.  Node 0 is the root.
  public int _len;       // Resizable array
  // Public stats about tree
  public int _leaves;
  public int _depth;
  public final int _mtrys;           // Number of columns to choose amongst in splits
  final long _seeds[];        // One seed for each chunk, for sampling
  public final transient Random _rand; // RNG for split decisions & sampling

  public DTree( Frame fr, int ncols, char nbins, char nbins_cats, char nclass, double min_rows, int mtrys, long seed ) {
    _names = fr.names();
    _ncols = ncols;
    _nbins=nbins;
    _nbins_cats=nbins_cats;
    _nclass=nclass;
    _min_rows = min_rows;
    _ns = new Node[1];
    _mtrys = mtrys;
    _seed = seed;
    _rand = SharedTree.createRNG(seed);
    _seeds = new long[fr.vecs()[0].nChunks()];
    for (int i = 0; i < _seeds.length; i++)
      _seeds[i] = _rand.nextLong();
  }

  // Return a deterministic chunk-local RNG.  Can be kinda expensive.
  public Random rngForChunk(int cidx) {
    long seed = _seeds[cidx];
    return SharedTree.createRNG(seed);
  }

  public final Node root() { return _ns[0]; }
  // One-time local init after wire transfer
  void init_tree( ) { for( int j=0; j<_len; j++ ) _ns[j]._tree = this; }

  // Return Node i
  public final Node node( int i ) {
    if( i >= _len )
      throw new ArrayIndexOutOfBoundsException(i);
    return _ns[i];
  }
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
    final public int _pid;    // Parent node id, root has no parent and uses -1
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

    // Recursively print the decision-line from tree root to this child.
    StringBuilder printLine(StringBuilder sb ) {
      if( _pid==-1 ) return sb.append("[root]");
      DecidedNode parent = _tree.decided(_pid);
      parent.printLine(sb).append(" to ");
      return parent.printChild(sb,_nid);
    }
    abstract public StringBuilder toString2(StringBuilder sb, int depth);
    abstract protected AutoBuffer compress(AutoBuffer ab);
    abstract protected int size();

    public final int nid() { return _nid; }
    public final int pid() { return _pid; }
  }

  // --------------------------------------------------------------------------
  // Records a column, a bin to split at within the column, and the MSE.
  public static class Split extends Iced {
    final public int _col, _bin;// Column to split, bin where being split
    final IcedBitSet _bs;       // For binary y and categorical x (with >= 4 levels), split into 2 non-contiguous groups
    final byte _equal;          // Split is 0: <, 1: == with single split point, 2: == with group split (<= 32 levels), 3: == with group split (> 32 levels)
    final double _se;           // Squared error without a split
    final double _se0, _se1;    // Squared error of each subsplit
    final double _n0,  _n1;     // (Weighted) Rows in each final split
    final double _p0,  _p1;     // Predicted value for each split

    public Split( int col, int bin, IcedBitSet bs, byte equal, double se, double se0, double se1, double n0, double n1, double p0, double p1 ) {
      _col = col;  _bin = bin;  _bs = bs;  _equal = equal;  _se = se;
      _n0 = n0;  _n1 = n1;  _se0 = se0;  _se1 = se1;
      _p0 = p0;  _p1 = p1;
      assert se > se0+se1 || se==Double.MAX_VALUE; // No point in splitting unless error goes down
    }
    public final double pre_split_se() { return _se; }
    public final double se() { return _se0+_se1; }
    public final int   col() { return _col; }
    public final int   bin() { return _bin; }

    // Split-at dividing point.  Don't use the step*bin+bmin, due to roundoff
    // error we can have that point be slightly higher or lower than the bin
    // min/max - which would allow values outside the stated bin-range into the
    // split sub-bins.  Always go for a value which splits the nearest two
    // elements.
    float splat(DHistogram hs[]) {
      DHistogram h = hs[_col];
      assert _bin > 0 && _bin < h.nbins();
      assert _bs==null : "Dividing point is a bitset, not a bin#, so dont call splat() as result is meaningless";
      if( _equal == 1 ) { assert h.bins(_bin)!=0; return h.binAt(_bin); }
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
      float lo = h.binAt(x+1);
      float hi = h.binAt(n  );
      if( h._isInt > 0 ) lo = h._step==1 ? lo-1 : (float)Math.floor(lo);
      if( h._isInt > 0 ) hi = h._step==1 ? hi   : (float)Math.ceil (hi);
      return (lo+hi)/2.0f;
    }

    // Split a DHistogram.  Return null if there is no point in splitting
    // this bin further (such as there's fewer than min_row elements, or zero
    // error in the response column).  Return an array of DHistograms (one
    // per column), which are bounded by the split bin-limits.  If the column
    // has constant data, or was not being tracked by a prior DHistogram
    // (for being constant data from a prior split), then that column will be
    // null in the returned array.
    public DHistogram[] split(int way, char nbins, char nbins_cats, double min_rows, DHistogram hs[], float splat) {
      double n = way==0 ? _n0 : _n1;
      if( n < min_rows || n <= 1 ) return null; // Too few elements
      double se = way==0 ? _se0 : _se1;
      if( se <= 1e-30 ) return null; // No point in splitting a perfect prediction

      // Build a next-gen split point from the splitting bin
      int cnt=0;                  // Count of possible splits
      DHistogram nhists[] = new DHistogram[hs.length]; // A new histogram set
      for( int j=0; j<hs.length; j++ ) { // For every column in the new split
        DHistogram h = hs[j];            // old histogram of column
        if( h == null ) continue;        // Column was not being tracked?
        int adj_nbins      = Math.max(h.nbins()>>1,nbins);
        // min & max come from the original column data, since splitting on an
        // unrelated column will not change the j'th columns min/max.
        // Tighten min/max based on actual observed data for tracked columns
        float min, maxEx;
        if( h._bins == null ) { // Not tracked this last pass?
          min = h._min;         // Then no improvement over last go
          maxEx = h._maxEx;
        } else {                // Else pick up tighter observed bounds
          min = h.find_min();   // Tracked inclusive lower bound
          if( h.find_maxIn() == min ) continue; // This column will not split again
          maxEx = h.find_maxEx(); // Exclusive max
        }

        // Tighter bounds on the column getting split: exactly each new
        // DHistogram's bound are the bins' min & max.
        if( _col==j ) {
          switch( _equal ) {
          case 0:  // Ranged split; know something about the left & right sides
            if( h._bins[_bin]==0 )
              throw H2O.unimpl(); // Here I should walk up & down same as split() above.
            assert _bs==null : "splat not defined for BitSet splits";
            float split = splat;
            if( h._isInt > 0 ) split = (float)Math.ceil(split);
            if( way == 0 ) maxEx= split;
            else           min  = split;
            break;
          case 1:               // Equality split; no change on unequals-side
            if( way == 1 ) continue; // but know exact bounds on equals-side - and this col will not split again
            break;
          case 2:               // BitSet (small) split
          case 3:               // BitSet (big)   split
            break;
          default: throw H2O.fail();
          }
        }
        if( min >  maxEx ) continue; // Happens for all-NA subsplits
        if( MathUtils.equalsWithinOneSmallUlp(min, maxEx) ) continue; // This column will not split again
        if( Float.isInfinite(adj_nbins/(maxEx-min)) ) continue;
        if( h._isInt > 0 && !(min+1 < maxEx ) ) continue; // This column will not split again
        assert min < maxEx && adj_nbins > 1 : ""+min+"<"+maxEx+" nbins="+adj_nbins;
        nhists[j] = DHistogram.make(h._name, adj_nbins, nbins_cats, h._isInt, min, maxEx);
        cnt++;                    // At least some chance of splitting
      }
      return cnt == 0 ? null : nhists;
    }

    @Override public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("{").append(_col).append("/");
      UndecidedNode.p(sb,_bin,2);
      sb.append(", se0=").append(_se0);
      sb.append(", se1=").append(_se1);
      sb.append(", n0=" ).append(_n0 );
      sb.append(", n1=" ).append(_n1 );
      return sb.append("}").toString();
    }
  }

  // --------------------------------------------------------------------------
  // An UndecidedNode: Has a DHistogram which is filled in (in parallel
  // with other histograms) in a single pass over the data.  Does not contain
  // any split-decision.
  public static class UndecidedNode extends Node {
    public transient DHistogram[] _hs; //(up to) one histogram per column
    public final int _scoreCols[];      // A list of columns to score; could be null for all
    public UndecidedNode( DTree tree, int pid, DHistogram[] hs ) {
      super(tree,pid);
      assert hs.length==tree._ncols;
      _hs = hs; //these histograms have no bins yet (just constructed)
      _scoreCols = scoreCols();
    }

    // Pick a random selection of columns to compute best score.
    // Can return null for 'all columns'.
    public int[] scoreCols() {
      DTree tree = _tree;
      if (tree._mtrys == _hs.length) return null;
      int[] cols = new int[_hs.length];
      int len=0;
      // Gather all active columns to choose from.
      for( int i=0; i<_hs.length; i++ ) {
        if( _hs[i]==null ) continue; // Ignore not-tracked cols
        assert _hs[i]._min < _hs[i]._maxEx && _hs[i].nbins() > 1 : "broken histo range "+_hs[i];
        cols[len++] = i;        // Gather active column
      }
      int choices = len;        // Number of columns I can choose from
      assert choices > 0;

      // Draw up to mtry columns at random without replacement.
      for( int i=0; i<tree._mtrys; i++ ) {
        if( len == 0 ) break;   // Out of choices!
        int idx2 = tree._rand.nextInt(len);
        int col = cols[idx2];     // The chosen column
        cols[idx2] = cols[--len]; // Compress out of array; do not choose again
        cols[len] = col;          // Swap chosen in just after 'len'
      }
      assert choices - len > 0;
      return Arrays.copyOfRange(cols, len, choices);
    }

    // Make the parent of this Node use a -1 NID to prevent the split that this
    // node otherwise induces.  Happens if we find out too-late that we have a
    // perfect prediction here, and we want to turn into a leaf.
    public void do_not_split( ) {
      if( _pid == -1 ) return; // skip root
      DecidedNode dn = _tree.decided(_pid);
      for( int i=0; i<dn._nids.length; i++ )
        if( dn._nids[i]==_nid )
          { dn._nids[i] = -1; return; }
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
          if( i < h.nbins() && h._bins != null ) {
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
      return sb.append(Log.fixedLength(s,w));
    }
    static private StringBuilder p(StringBuilder sb, long l, int w) {
      return p(sb,Long.toString(l),w);
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
    @Override protected AutoBuffer compress(AutoBuffer ab) { throw H2O.fail(); }
    @Override protected int size() { throw H2O.fail(); }
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

    // Make a correctly flavored Undecided
    public UndecidedNode makeUndecidedNode(DHistogram hs[]) {
      return new UndecidedNode(_tree, _nid, hs);
    }

    // Pick the best column from the given histograms
    public Split bestCol( UndecidedNode u, DHistogram hs[] ) {
      // Find the column with the best split (lowest score).  Unlike RF, DRF
      // scores on all columns and selects splits on all columns.
      DTree.Split best = new DTree.Split(-1,-1,null,(byte)0,Double.MAX_VALUE,Double.MAX_VALUE,Double.MAX_VALUE,0L,0L,0,0);
      if( hs == null ) return best;
      final int maxCols = u._scoreCols == null /* all cols */ ? hs.length : u._scoreCols.length;
      for( int i=0; i<maxCols; i++ ) {
        int col = u._scoreCols == null ? i : u._scoreCols[i];
        if( hs[col]==null || hs[col].nbins() <= 1 ) continue;
        DTree.Split s = hs[col].scoreMSE(col, _tree._min_rows);
        if( s == null ) continue;
        if( s.se() < best.se() ) best = s;
        if( s.se() <= 0 ) break; // No point in looking further!
      }
      return best;
    }

    public DecidedNode( UndecidedNode n, DHistogram hs[] ) {
      super(n._tree,n._pid,n._nid); // Replace Undecided with this DecidedNode
      _nids = new int[2];           // Split into 2 subsets
      _split = bestCol(n,hs);       // Best split-point for this tree
      if( _split._col == -1 ) {     // No good split?
        // Happens because the predictor columns cannot split the responses -
        // which might be because all predictor columns are now constant, or
        // because all responses are now constant.
        _splat = Float.NaN;
        Arrays.fill(_nids,-1);
        return;
      }
      _splat = (_split._equal == 0 || _split._equal == 1) ? _split.splat(hs) : -1; // Split-at value (-1 for group-wise splits)
      final char nbins   = _tree._nbins;
      final char nbins_cats = _tree._nbins_cats;
      final double min_rows = _tree._min_rows;

      for( int b=0; b<2; b++ ) { // For all split-points
        // Setup for children splits
        DHistogram nhists[] = _split.split(b,nbins, nbins_cats, min_rows, hs, _splat);
        assert nhists==null || nhists.length==_tree._ncols;
        _nids[b] = nhists == null ? -1 : makeUndecidedNode(nhists)._nid;
      }
    }

    public int ns( Chunk chks[], int row ) {
      float d = (float)chks[_split._col].atd(row);
      int bin;
      // Note that during *scoring* (as opposed to training), we can be exposed
      // to data which is outside the bin limits.
      if(_split._equal == 0)
        bin = d >= _splat ? 1 : 0; //NaN goes to 0 // >= goes right
      else if(_split._equal == 1)
        bin = d == _splat ? 1 : 0; //NaN goes to 0
      else
        bin = _split._bs.contains((int)d) ? 1 : 0; // contains goes right
      return _nids[bin];
    }

    public double pred( int nid ) {
      return nid==0 ? _split._p0 : _split._p1;
    }

    @Override public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("DecidedNode:\n");
      sb.append("_nid: " + _nid);
      sb.append("_nids (children): " + Arrays.toString(_nids));
      sb.append("_split:" + _split.toString());
      sb.append("_splat:" + _splat);
      if( _split._col == -1 ) {
        sb.append(" col = -1 ");
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
      for( int i=0; i<_nids.length; i++ ) {
        for( int d=0; d<depth; d++ ) sb.append("  ");
        sb.append(_nid).append(" ");
        if( _split._col < 0 ) sb.append("init");
        else {
          sb.append(_tree._names[_split._col]);
          if (_split._equal < 2) {
            sb.append(_split._equal != 0
                    ? (i == 0 ? " != " : " == ")
                    : (i == 0 ? " <  " : " >= "));
          } else {
            sb.append(i == 0 ? " not in " : "  is in ");
          }
          sb.append((_split._equal == 2 || _split._equal == 3) ? _split._bs.toString() : _splat).append("\n");
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
      // 1B node type + flags, 2B colId, 4B split val/small group or (2B offset + 2B size) + large group
      int res = _split._equal == 3 ? 7 + _split._bs.numBytes() : 7;

      Node left = _tree.node(_nids[0]);
      int lsz = left.size();
      res += lsz;
      if( left instanceof LeafNode ) _nodeType |= (byte)(48 << 0*2);
      else {
        int slen = lsz < 256 ? 0 : (lsz < 65535 ? 1 : (lsz<(1<<24) ? 2 : 3));
        _nodeType |= slen; // Set the size-skip bits
        res += (slen+1); //
      }

      Node rite = _tree.node(_nids[1]);
      if( rite instanceof LeafNode ) _nodeType |= (byte)(48 << 1*2);
      res += rite.size();
      assert (_nodeType&0x33) != 51;
      assert res != 0;
      return (_size = res);
    }

    // Compress this tree into the AutoBuffer
    @Override public AutoBuffer compress(AutoBuffer ab) {
      int pos = ab.position();
      if( _nodeType == 0 ) size(); // Sets _nodeType & _size both
      ab.put1(_nodeType);          // Includes left-child skip-size bits
      assert _split._col != -1;    // Not a broken root non-decision?
      ab.put2((short)_split._col);

      // Save split-at-value or group
      if(_split._equal == 0 || _split._equal == 1) ab.put4f(_splat);
      else if(_split._equal == 2) _split._bs.compress2(ab);
      else _split._bs.compress3(ab);

      Node left = _tree.node(_nids[0]);
      if( (_nodeType&48) == 0 ) { // Size bits are optional for left leaves !
        int sz = left.size();
        if(sz < 256)            ab.put1(       sz);
        else if (sz < 65535)    ab.put2((short)sz);
        else if (sz < (1<<24))  ab.put3(       sz);
        else                    ab.put4(       sz); // 1<<31-1
      }
      // now write the subtree in
      left.compress(ab);
      Node rite = _tree.node(_nids[1]);
      rite.compress(ab);
      assert _size == ab.position()-pos:"reported size = " + _size + " , real size = " + (ab.position()-pos);
      return ab;
    }
  }

  public final static class LeafNode extends Node {
    public float _pred;
    public LeafNode( DTree tree, int pid ) { super(tree,pid); tree._leaves++; }
    public LeafNode( DTree tree, int pid, int nid ) { super(tree,pid,nid); tree._leaves++; }
    @Override public String toString() { return "Leaf#"+_nid+" = "+_pred; }
    @Override public final StringBuilder toString2(StringBuilder sb, int depth) {
      for( int d=0; d<depth; d++ ) sb.append("  ");
      sb.append(_nid).append(" ");
      return sb.append("pred=").append(_pred).append("\n");
    }
    // Insert just the predictions: a single byte/short if we are predicting a
    // single class, or else the full distribution.
    @Override protected AutoBuffer compress(AutoBuffer ab) { assert !Double.isNaN(_pred); return ab.put4f(_pred); }
    @Override protected int size() { return 4; }
    public final double pred() { return _pred; }
  }

  static public boolean isRootNode(Node n)   { return n._pid == -1; }

  // Build a compressed-tree struct
  public CompressedTree compress(int tid, int cls) {
    int sz = root().size();
    if( root() instanceof LeafNode ) sz += 3; // Oops - tree-stump
    AutoBuffer ab = new AutoBuffer(sz);
    if( root() instanceof LeafNode ) // Oops - tree-stump
      ab.put1(0).put2((char)65535); // Flag it special so the decompress doesn't look for top-level decision
    root().compress(ab);      // Compress whole tree
    assert ab.position() == sz;
    return new CompressedTree(ab.buf(),_nclass,_seed,tid,cls);
  }
}
