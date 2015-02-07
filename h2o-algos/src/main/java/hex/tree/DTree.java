package hex.tree;

import water.*;
import water.fvec.Chunk;
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
  final char _nbins;     // Max number of bins to split over
  final char _nclass;    // #classes, or 1 for regression trees
  public final int _min_rows;   // Fewest allowed rows in any split
  final long _seed;      // RNG seed; drives sampling seeds if necessary
  private Node[] _ns;    // All the nodes in the tree.  Node 0 is the root.
  public int _len;       // Resizable array
  // Public stats about tree
  public int _leaves;
  public int _depth;

  public DTree( String[] names, int ncols, char nbins, char nclass, int min_rows ) { this(names,ncols,nbins,nclass,min_rows,-1); }
  public DTree( String[] names, int ncols, char nbins, char nclass, int min_rows, long seed ) {
    _names = names; _ncols = ncols; _nbins=nbins; _nclass=nclass; _min_rows = min_rows; _ns = new Node[1]; _seed = seed; 
  }

  public final Node root() { return _ns[0]; }
  // One-time local init after wire transfer
  void init_tree( ) { for( int j=0; j<_len; j++ ) _ns[j]._tree = this; }

  // Return Node i
  public final Node node( int i ) {
    if( i >= _len ) throw new ArrayIndexOutOfBoundsException(i);
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
  }

  // --------------------------------------------------------------------------
  // Records a column, a bin to split at within the column, and the MSE.
  public static class Split extends Iced {
    final public int _col, _bin;// Column to split, bin where being split
    final IcedBitSet _bs;       // For binary y and categorical x (with >= 4 levels), split into 2 non-contiguous groups
    final byte _equal;          // Split is 0: <, 1: == with single split point, 2: == with group split (<= 32 levels), 3: == with group split (> 32 levels)
    final double _se0, _se1;    // Squared error of each subsplit
    final long    _n0,  _n1;    // Rows in each final split
    final double  _p0,  _p1;    // Predicted value for each split

    public Split( int col, int bin, IcedBitSet bs, byte equal, double se0, double se1, long n0, long n1, double p0, double p1 ) {
      _col = col;  _bin = bin;  _bs = bs;  _equal = equal;
      _n0 = n0;  _n1 = n1;  _se0 = se0;  _se1 = se1;
      _p0 = p0;  _p1 = p1;
    }
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
    public DHistogram[] split( int way, char nbins, int min_rows, DHistogram hs[], float splat ) {
      long n = way==0 ? _n0 : _n1;
      if( n < min_rows || n <= 1 ) return null; // Too few elements
      double se = way==0 ? _se0 : _se1;
      if( se <= 1e-30 ) return null; // No point in splitting a perfect prediction

      // Build a next-gen split point from the splitting bin
      int cnt=0;                  // Count of possible splits
      DHistogram nhists[] = new DHistogram[hs.length]; // A new histogram set
      for( int j=0; j<hs.length; j++ ) { // For every column in the new split
        DHistogram h = hs[j];            // old histogram of column
        if( h == null ) continue;        // Column was not being tracked?
        int adj_nbins  = Math.max(h.nbins()>>1,nbins);
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
        if( MathUtils.equalsWithinOneSmallUlp(min, maxEx) ) continue; // This column will not split again
        if( h._isInt > 0 && !(min+1 < maxEx ) ) continue; // This column will not split again
        if( min >  maxEx ) continue; // Happens for all-NA subsplits
        assert min < maxEx && n > 1 : ""+min+"<"+maxEx+" n="+n;
        nhists[j] = DHistogram.make(h._name,adj_nbins,h._isInt,min,maxEx,n,h.isBinom());
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
  public static abstract class UndecidedNode extends Node {
    public transient DHistogram[] _hs;
    public final int _scoreCols[];      // A list of columns to score; could be null for all
    public UndecidedNode( DTree tree, int pid, DHistogram[] hs ) {
      super(tree,pid);
      assert hs.length==tree._ncols;
      _scoreCols = scoreCols(_hs=hs);
    }

    // Pick a random selection of columns to compute best score.
    // Can return null for 'all columns'.
    abstract public int[] scoreCols( DHistogram[] hs );

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
  public static abstract class DecidedNode extends Node {
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
    public abstract UndecidedNode makeUndecidedNode(DHistogram hs[]);

    // Pick the best column from the given histograms
    public abstract Split bestCol( UndecidedNode u, DHistogram hs[] );

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
      final int min_rows = _tree._min_rows;

      for( int b=0; b<2; b++ ) { // For all split-points
        // Setup for children splits
        DHistogram nhists[] = _split.split(b,nbins,min_rows,hs,_splat);
        assert nhists==null || nhists.length==_tree._ncols;
        _nids[b] = nhists == null ? -1 : makeUndecidedNode(nhists)._nid;
      }
    }

    // Bin #.
    public int bin( Chunk chks[], int row ) {
      float d = (float)chks[_split._col].atd(row); // Value to split on for this row
      if( Float.isNaN(d) )               // Missing data?
        return 0;                        // NAs always to bin 0
      // Note that during *scoring* (as opposed to training), we can be exposed
      // to data which is outside the bin limits.
      if(_split._equal == 0)
        return d < _splat ? 0 : 1;
      else if(_split._equal == 1)
        return d != _splat ? 0 : 1;
      else
        return _split._bs.contains((int)d) ? 1 : 0;
    }

    public int ns( Chunk chks[], int row ) { return _nids[bin(chks,row)]; }

    @Override public String toString() {
      if( _split._col == -1 ) return "Decided has col = -1";
      int col = _split._col;
      if( _split._equal == 1 )
        return
          _tree._names[col]+" != "+_splat+"\n"+
          _tree._names[col]+" == "+_splat+"\n";
      else if( _split._equal == 2 || _split._equal == 3 )
        return
          _tree._names[col]+" != "+_split._bs.toString()+"\n"+
          _tree._names[col]+" == "+_split._bs.toString()+"\n";
      return
        _tree._names[col]+" < "+_splat+"\n"+
        _splat+" <="+_tree._names[col]+"\n";
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
          sb.append(_split._equal != 0
                    ? (i==0 ? " != " : " == ")
                    : (i==0 ? " <  " : " >= "));
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

  public static abstract class LeafNode extends Node {
    public double _pred;
    public LeafNode( DTree tree, int pid ) { super(tree,pid); }
    public LeafNode( DTree tree, int pid, int nid ) { super(tree,pid,nid); }
    @Override public String toString() { return "Leaf#"+_nid+" = "+_pred; }
    @Override public final StringBuilder toString2(StringBuilder sb, int depth) {
      for( int d=0; d<depth; d++ ) sb.append("  ");
      sb.append(_nid).append(" ");
      return sb.append("pred=").append(_pred).append("\n");
    }
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

  private static final SB TO_JAVA_BENCH_FUNC = new SB().
      nl().
      p("  /**").nl().
      p("   * Run a predict() benchmark with the generated model and some synthetic test data.").nl().
      p("   *").nl().
      p("   * @param iters number of iterations to run; each iteration predicts on every sample (i.e. row) in the test data").nl().
      p("   * @param data test data to predict on").nl().
      p("   * @param preds output predictions").nl().
      p("   * @param ntrees number of trees").nl().
      p("   */").nl().
      p("  public void bench(int iters, double[][] data, float[] preds, int ntrees) {").nl().
      p("    System.out.println(\"Iterations: \" + iters);").nl().
      p("    System.out.println(\"Data rows : \" + data.length);").nl().
      p("    System.out.println(\"Trees     : \" + ntrees + \"x\" + (preds.length-1));").nl().
      nl().
      p("    long startMillis;").nl().
      p("    long endMillis;").nl().
      p("    long deltaMillis;").nl().
      p("    double deltaSeconds;").nl().
      p("    double samplesPredicted;").nl().
      p("    double samplesPredictedPerSecond;").nl().
      p("    System.out.println(\"Starting timing phase of \"+iters+\" iterations...\");").nl().
      nl().
      p("    startMillis = System.currentTimeMillis();").nl().
      p("    for (int i=0; i<iters; i++) {").nl().
      p("      // Uncomment the nanoTime logic for per-iteration prediction times.").nl().
      p("      // long startTime = System.nanoTime();").nl().
      nl().
      p("      for (double[] row : data) {").nl().
      p("        predict(row, preds);").nl().
      p("        // System.out.println(java.util.Arrays.toString(preds) + \" : \" + (DOMAINS[DOMAINS.length-1]!=null?(DOMAINS[DOMAINS.length-1][(int)preds[0]]+\"~\"+DOMAINS[DOMAINS.length-1][(int)row[row.length-1]]):(preds[0] + \" ~ \" + row[row.length-1])) );").nl().
      p("      }").nl().
      nl().
      p("      // long ttime = System.nanoTime()-startTime;").nl().
      p("      // System.out.println(i+\". iteration took \" + (ttime) + \"ns: scoring time per row: \" + ttime/data.length +\"ns, scoring time per row and tree: \" + ttime/data.length/ntrees + \"ns\");").nl().
      nl().
      p("      if ((i % 1000) == 0) {").nl().
      p("        System.out.println(\"finished \"+i+\" iterations (of \"+iters+\")...\");").nl().
      p("      }").nl().
      p("    }").nl().
      p("    endMillis = System.currentTimeMillis();").nl().
      nl().
      p("    deltaMillis = endMillis - startMillis;").nl().
      p("    deltaSeconds = (double)deltaMillis / 1000.0;").nl().
      p("    samplesPredicted = data.length * iters;").nl().
      p("    samplesPredictedPerSecond = samplesPredicted / deltaSeconds;").nl().
      p("    System.out.println(\"finished in \"+deltaSeconds+\" seconds.\");").nl().
      p("    System.out.println(\"samplesPredicted: \" + samplesPredicted);").nl().
      p("    System.out.println(\"samplesPredictedPerSecond: \" + samplesPredictedPerSecond);").nl().
      p("  }").nl().
  nl();

  static class TreeJCodeGen extends TreeVisitor<RuntimeException> {
    public static final int MAX_NODES = (1 << 12) / 4; // limit for a number decision nodes
    final byte  _bits[]  = new byte [100];
    final float _fs  []  = new float[100];
    final SB    _sbs []  = new SB   [100];
    final int   _nodesCnt[] = new int  [100];
    final SharedTreeModel _tm;
    SB _sb;
    SB _csb;
    SB _grpsplit;

    int _subtrees = 0;
    int _grpcnt = 0;

    public TreeJCodeGen(SharedTreeModel tm, CompressedTree ct, SB sb) {
      super(ct);
      _tm = tm;
      _sb = sb;
      _csb = new SB();
      _grpsplit = new SB();
    }

    // code preamble
    protected void preamble(SB sb, int subtree) throws RuntimeException {
      String subt = subtree>0?String.valueOf(subtree):"";
      sb.i().p("static final ").p(SharedTreeModel.PRED_TYPE).p(" predict").p(subt).p("(double[] data) {").nl().ii(1); // predict method for one tree
      sb.i().p(SharedTreeModel.PRED_TYPE).p(" pred = ");
    }

    // close the code
    protected void closure(SB sb) throws RuntimeException {
      sb.p(";").nl();
      sb.i(1).p("return pred;").nl().di(1);
      sb.i().p("}").nl();
      // sb.p(_grpsplit).di(1);
    }

    @Override protected void pre( int col, float fcmp, IcedBitSet gcmp, int equal ) {
      if(equal == 2 || equal == 3 && gcmp != null) {
        _grpsplit.i(1).p("// ").p(gcmp.toString()).nl();
        _grpsplit.i(1).p("public static final byte[] GRPSPLIT").p(_grpcnt).p(" = new byte[] ").p(gcmp.toStrArray()).p(";").nl();
      }

      if( _depth > 0 ) {
        int b = _bits[_depth-1];
        assert b > 0 : Arrays.toString(_bits)+"\n"+_sb.toString();
        if( b==1         ) _bits[_depth-1]=3;
        if( b==1 || b==2 ) _sb.p('\n').i(_depth).p("?");
        if( b==2         ) _sb.p(' ').pj(_fs[_depth-1]); // Dump the leaf containing float value
        if( b==2 || b==3 ) _sb.p('\n').i(_depth).p(":");
      }
      if (_nodes>MAX_NODES) {
        _sb.p("predict").p(_subtrees).p("(data)");
        _nodesCnt[_depth] = _nodes;
        _sbs[_depth] = _sb;
        _sb = new SB();
        _nodes = 0;
        preamble(_sb, _subtrees);
        _subtrees++;
      }
      // All NAs are going always to the left
      _sb.p(" (Double.isNaN(data[").p(col).p("]) || ");
      if(equal == 0 || equal == 1) {
        String scmp = _tm.isFromSpeeDRF() ? "<= " : "< ";
        _sb.p("(float) data[").p(col).p(" /* ").p(_tm._output._names[col]).p(" */").p("] ").p(equal == 1 ? "!= " : scmp).pj(fcmp); // then left and then right (left is !=)
      } else {
        //_sb.p("!water.genmodel.GeneratedModel.grpContains(GRPSPLIT").p(_grpcnt).p(", ").p(gcmp._offset).p(", (int) data[").p(col).p(" /* ").p(_tm._names[col]).p(" */").p("])");
        _grpcnt++;
        throw H2O.unimpl();     // TODO: fold offset into IcedBitSet
      }
      assert _bits[_depth]==0;
      _bits[_depth]=1;
    }
    @Override protected void leaf( float pred  ) {
      assert _depth==0 || _bits[_depth-1] > 0 : Arrays.toString(_bits); // it can be degenerated tree
      if( _depth==0) { // it is de-generated tree
        _sb.pj(pred);
      } else if( _bits[_depth-1] == 1 ) { // No prior leaf; just memorize this leaf
        _bits[_depth-1]=2; _fs[_depth-1]=pred;
      } else {          // Else==2 (prior leaf) or 3 (prior tree)
        if( _bits[_depth-1] == 2 ) _sb.p(" ? ").pj(_fs[_depth-1]).p(" ");
        else                       _sb.p('\n').i(_depth);
        _sb.p(": ").pj(pred);
      }
    }
    @Override protected void post( int col, float fcmp, int equal ) {
      _sb.p(')');
      _bits[_depth]=0;
      if (_sbs[_depth]!=null) {
        closure(_sb);
        _csb.p(_sb);
        _sb = _sbs[_depth];
        _nodes = _nodesCnt[_depth];
        _sbs[_depth] = null;
      }
    }
    public void generate() {
      preamble(_sb, _subtrees++);   // TODO: Need to pass along group split BitSet
      visit();
      closure(_sb);
      _sb.p(_grpsplit).di(1);
      _sb.p(_csb);
    }
  }
}
