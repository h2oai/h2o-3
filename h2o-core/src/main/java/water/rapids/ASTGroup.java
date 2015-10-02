package water.rapids;

import water.H2O;
import water.Iced;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.AtomicUtils;
import water.util.IcedHashMap;
import water.util.Log;

import java.util.Arrays;

/** GroupBy
 *  Group the rows of 'data' by unique combinations of '[group-by-cols]',
 *  ordering the results by [order-by-cols].  Apply function 'fcn' to a Frame
 *  for each group, with a single column argument, and a NA-handling flag.
 *  Sets of tuples {fun,col,na} are allowed.
 *
 *  'fcn' must be a one of a small set of functions, all reductions, and 'GB'
 *  returns a row per unique group, with the first columns being the grouping
 *  column, and the last column the reduction result(s).
 *
 *  The returned column(s).
 *  
 */
class ASTGroup extends ASTPrim {
  enum NAHandling { ALL, RM }

  // Functions handled by GroupBy
  enum FCN {
    nrow() { 
      @Override void atomic_op ( double[][] dss, int agnum, int gnum, double d ) { }
      @Override double postPass( double[][] dss, int agnum, int gnum, int naggcols, long n ) { return n; }
      @Override int ncols(int maxx) { return 0; } 
    },
    mean() { 
      @Override void atomic_op ( double[][] dss, int agnum, int gnum, double d ) { aadd(dss[agnum],gnum,d); }
      @Override double postPass( double[][] dss, int agnum, int gnum, int naggcols, long n ) { return dss[agnum][gnum]/n; }
    },
    sum() { 
      @Override void atomic_op ( double[][] dss, int agnum, int gnum, double d ) { aadd(dss[agnum],gnum,d); }
    },
//    sumSquares() {
//      @Override void op( double[] d0s, double d1 ) { d0s[0]+=d1*d1; }
//      @Override void atomic_op( double[] d0s, double[] d1s ) { d0s[0] += d1s[0]; }
//      @Override double postPass( double ds[], long n) { return ds[0]; }
//    },
    var() {
      @Override void atomic_op ( double[][] dss, int agnum, int gnum, double d ) { aadd(dss[agnum],gnum,d); aadd(dss[agnum+1],gnum,d*d); }
      @Override double postPass( double[][] dss, int agnum, int gnum, int naggcols, long n ) {
        double sum=dss[agnum][gnum], ssq=dss[agnum+1][gnum];
        return (ssq - sum*sum/n)/(n-1); }
      @Override int ncols(int maxx) { return 2; } 
    },
    sd() {
      @Override void atomic_op ( double[][] dss, int agnum, int gnum, double d ) { aadd(dss[agnum],gnum,d); aadd(dss[agnum+1],gnum,d*d); }
      @Override double postPass( double[][] dss, int agnum, int gnum, int naggcols, long n ) {
        double sum=dss[agnum][gnum], ssq=dss[agnum+1][gnum];
        return Math.sqrt((ssq - sum*sum/n)/(n-1)); }
      @Override int ncols(int maxx) { return 2; } 
    },
    min() { 
      @Override void atomic_op ( double[][] dss, int agnum, int gnum, double d ) { AtomicUtils.DoubleArray.min(dss[agnum],gnum,d); }
      @Override double initVal() { return Double.MAX_VALUE; }
    },
    max() { 
      @Override void atomic_op ( double[][] dss, int agnum, int gnum, double d ) { AtomicUtils.DoubleArray.max(dss[agnum],gnum,d); }
      @Override double initVal() { return -Double.MAX_VALUE; }
    },
    mode() { 
      @Override void atomic_op( double[][] dss, int agnum, int gnum, double d ) { aadd(dss[agnum+(int)d],gnum,1); }
      @Override double postPass( double[][] dss, int agnum, int gnum, int naggcols, long n ) { 
        int idx=agnum;
        for( int i=agnum; i<agnum+naggcols; i++ )
          if( dss[i][gnum] > dss[idx][gnum] )
            idx = i;
        return idx-agnum;
      }
      @Override int ncols(int maxx) { return maxx; } 
    },
    ;
    abstract void atomic_op( double[][] dss, int agnum, int gnum, double d );
    // Default postPass is just return the 1 aggregate
    double postPass( double[][] dss, int agnum, int gnum, int naggcols, long n ) { return dss[agnum][gnum]; }
    // Default columns is 1
    int ncols(int maxx) { return 1; } 
    // Default initial value is 0
    double initVal() { return 0; } // zero fill all cols by default
    void aadd( double[] ds, int x, double d ) { AtomicUtils.DoubleArray.add(ds,x,d); }
  }

  @Override int nargs() { return -1; } // (GB data [group-by-cols] {fcn col "na"}...)
  @Override public String[] args() { return new String[]{"..."}; }
  @Override public String str() { return "GB"; }

  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    // Get and check the basic arguments
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    final int ncols = fr.numCols();

    ASTNumList groupby = check(ncols, asts[2]);
    final Frame fr_keys = gbFrame(fr,groupby.expand4()); // Frame of just the group-by keys
    final int ngbCols = fr_keys.numCols(); 

    // Count of aggregates; knock off the first 3 ASTs (GB data [group-by] ...),
    // then count by triples.  Error check aggregate selections
    int naggs = (asts.length-3)/3;
    final AGG[] aggs = new AGG[naggs];
    int naggcols = 0;
    for( int idx = 3; idx < asts.length; idx += 3 ) {
      Val v = asts[idx].exec(env);
      String fn = v instanceof ValFun ? v.getFun().str() : v.getStr();
      FCN fcn = FCN.valueOf(fn);
      ASTNumList col = check(ncols,asts[idx+1]);
      if( col.cnt() != 1 ) throw new IllegalArgumentException("Group-By functions take only a single column");
      int agg_col = (int)col.min(); // Aggregate column
      if( fcn==FCN.mode && !fr.vec(agg_col).isCategorical() )
        throw new IllegalArgumentException("Mode only allowed on categorical columns");
      NAHandling na = NAHandling.valueOf(asts[idx+2].exec(env).getStr().toUpperCase());
      AGG agg = aggs[(idx-3)/3] = new AGG(fcn,agg_col,na, (int)fr.vec(agg_col).max()+1, naggcols);
      naggcols += agg.ncols();
    }

    // ---
    // Find the groups, either by hashing, or by sorting
    IcedHashMap<GKX,String> gs = findGroups(fr_keys);
    long grs[][] = (gs == null || _testing_force_sorted ) ? sortingGroup(fr) : null;

    // ---
    // Shared between methods, allocate memory for aggregates and initialize
    long nlgrps = grs == null ? gs.size() : ArrayUtils.maxValue(grs[0]);
    if( nlgrps > Integer.MAX_VALUE ) throw H2O.unimpl(); // more than 2b groups?
    int ngrps = (int)nlgrps;

    // Data Layout: 2-d array of doubles; Groups going down, and Aggregates
    // going across.  Each AGG needs 1 (most), 2 (var, sdev), or N (mode)
    // columns to hold the aggregations.
    double[/*agg col*/][/*group num*/] dss = new double[naggcols][ngrps];
    for( int a=0; a<naggs; a++ ) {
      double d = aggs[a]._fcn.initVal();
      if( d != 0 ) Arrays.fill(dss[aggs[a]._aggcol],d);
    }
    long[/*agg col*/][/*groupnum*/] nrows = new long[naggs][ngrps];

    // ---
    // Compute the aggregates into dss.
    // For hashing, sort the groups before numbering them.
    // For the sorting group-by, groups are already sorted and numbered
    GK0[] gkxs0;
    if( grs == null ) {
      gkxs0 = sortGroups(gs);
      HashingCompute hc = new HashingCompute(gs,aggs,ngbCols,dss, nrows).doAll(new Frame(fr_keys).add(fr));
      dss = hc._dss;
      nrows = hc._nrows;
    } else {
      throw H2O.unimpl();
    }
    final GK0[] gkxs=gkxs0;

    // ---
    // Build the output!  Run the postPass on the grouped summaries, then stuff into a Frame
    String[] fcnames = new String[aggs.length];
    for( int i=0; i<aggs.length; i++ )
      fcnames[i] = aggs[i]._fcn.toString()+"_"+fr.name(aggs[i]._col);
    final double[][] fdss = dss;
    final long[][] fnrows = nrows;
    MRTask mrfill = new MRTask() {
      @Override public void map(Chunk[] c, NewChunk[] ncs) {
        final int start = (int)c[0].start();
        final int len = c[0]._len;
        for( int i=0; i<len; i++ ) {
          int gnum = i+start;
          GK0 gk0 = gkxs[gnum]; // One Group per row
          gk0.setkey(ncs);      // The Group Key in the first cols
          for( int a=0; a<aggs.length; a++ )
            ncs[a+ngbCols].addNum(aggs[a].postPass(fdss, gnum, fnrows[a][gnum]));
        }
      }
      };

    Frame f = buildOutput(fr_keys, naggs, fr, fcnames, ngrps, mrfill);
    return new ValFrame(f);
  }

  // Argument check helper
  static ASTNumList check( long dstX, AST ast ) {
    // Sanity check vs dst.  To simplify logic, jam the 1 col/row case in as a ASTNumList
    ASTNumList dim;
    if( ast instanceof ASTNumList  ) dim = (ASTNumList)ast;
    else if( ast instanceof ASTNum ) dim = new ASTNumList(((ASTNum)ast)._v.getNum());
    else throw new IllegalArgumentException("Requires a number-list, but found a "+ast.getClass());
    if( dim.isEmpty() ) return dim; // Allow empty
    if( !(0 <= dim.min() && dim.max()-1 <  dstX) &&
        !(1 == dim.cnt() && dim.max()-1 == dstX) ) // Special case of append
      throw new IllegalArgumentException("Selection must be an integer from 0 to "+dstX);
    return dim;
  }

  // Build a Frame of just the Key columns, usually just 1 column
  static Frame gbFrame( Frame fr, int[] gbCols ) {
    Vec[] vecs = fr.vecs();
    Frame fr_keys = new Frame();
    for( int col : gbCols ) fr_keys.add(fr._names[col],vecs[col]);
    return fr_keys;
  }

  // --------------------------------------------------------------------------
  // Do all the grouping work.  Find groups in frame 'fr', grouped according to
  // the selected 'gbCols' columns, and for each group compute aggregrate
  // results using 'aggs'.  Return an array of groups, with the aggregate results.
  static IcedHashMap<GKX,String> findGroups(Frame fr_keys) {
    // do the group by work now
    long start = System.currentTimeMillis();
    HashingGroup p1 = new HashingGroup(GKX.init(fr_keys.numCols())).doAll(fr_keys);
    if( p1._kill._killed ) {
      Log.info("FindGroups aborted after " + (System.currentTimeMillis() - start)/1000. + " (s) due to size, switching to sorting GroupBy");
      return null;
    }
    Log.info("FindGroups done in " + (System.currentTimeMillis() - start)/1000. + " (s)");
    return p1._gs;
  }

  // Sort the groups from the hash table, and uniquely number them
  static GK0[] sortGroups(IcedHashMap<GKX,String> gs) {
    GK0[] gkxs = gs.keySet().toArray(new GK0[gs.size()]);
    // Sort the groups by group key, when treated as a double.
    Arrays.sort(gkxs,new java.util.Comparator<GK0>() {
        @Override public int compare( GK0 g1, GK0 g2 ) { return g1.compare(g2); }
        // I do not believe sort() calls equals() at this time, so no need to implement
        @Override public boolean equals( Object o ) { throw H2O.unimpl(); }
      });
    
    // Set group number
    for( int gnum=0; gnum < gkxs.length; gnum++ )
      gkxs[gnum]._gnum = gnum;
    return gkxs;
  }

  // Find the Groups via hash table.  Blow out if the count of groups exceeds a
  // reasonable size for a hash table (perhaps 1M unique groups or so), and
  // switch to a sorting groupby.
  private static class Kill extends Iced { volatile boolean _killed; }

  static class HashingGroup extends MRTask<HashingGroup> {
    final Kill _kill;           // Shared per-node, checks for size blowout
    final IcedHashMap<GKX,String> _gs; // Shared per-node, common, racy
    final GKX _gTemplate;
    
    HashingGroup(GKX gTemplate) { _kill = new Kill(); _gs = new IcedHashMap<>(); _gTemplate = gTemplate; }
    @Override public void map(Chunk[] cs) {
      if( _kill._killed ) return; // Abort if MRTask already dead for size blowout
      IcedHashMap<GKX,String> gs = _gs;
      GKX gOld, gWork = _gTemplate.clone();
      final int rlen = cs[0]._len;
      for( int row=0; row<rlen; row++ ) {
        // Find the Group being worked on
        gWork.fill(cs,row,0); // Fill the worker Group for the hashtable lookup
        if( gs.putIfAbsent(gWork,"")==null ) // Insert if not absent
          gWork=_gTemplate.clone();          // need entirely new G
      }
    }
  }

  static class HashingCompute extends MRTask<HashingCompute> {
    final IcedHashMap<GKX,String> _gs;
    final int _gbCols;          // Number of prefix columns holding the group key
    final AGG[] _aggs;          // Aggregates
    final double[][] _dss;      // Aggregate results, shared per-node
    final long[][] _nrows;      // Aggregate row counts, shared per-node
    HashingCompute( IcedHashMap<GKX,String> gs, AGG[] aggs, int gbCols, double[][] dss, long[][] nrows ) { 
      _gs = gs; _aggs = aggs; _gbCols = gbCols; _dss = dss; _nrows = nrows; }
    @Override public void map( Chunk cs[] ) {
      GKX g = GKX.init(_gbCols);
      final int len = cs[0]._len;
      for( int row=0; row<len; row++ ) {
        int gnum = _gs.getk(g.fill(cs,row,0))._gnum;
        for( int i=0; i<_aggs.length; i++ ) { // Accumulate aggregate reductions
          AGG A = _aggs[i];
          double d = cs[A._col+_gbCols].atd(row);
          if( !Double.isNaN(d) || A._na==NAHandling.ALL ) {
            AtomicUtils.LongArray.incr(_nrows[i],gnum);
            A.atomic_op(_dss,gnum,d);
          }
        }
      }
    }
    @Override public void reduce(HashingCompute hc) {
      if( _dss == hc._dss ) return; // Node-local shared
      throw H2O.unimpl();
    }
  }


  // --------------------------------------------------------------------------
  /** Use a sorting groupby, probably because the hash table size exceeded
   *  MAX_HASH_SIZE; i.e. the number of unique keys in the GBTask.
   */
  public static boolean _testing_force_sorted;
  private long[][] sortingGroup(Frame fr_key) {

    // Sort rows by Group.  Returns group-number per-row
    final long[][] rowss = new ASTGroupSorted().sort(fr_key);
    if( rowss.length != 1 ) throw H2O.unimpl(); // more than 2b rows?
    return rowss;
    //long ngrps = ArrayUtils.maxValue(rowss[0])+1;
    //if( ngrps > Integer.MAX_VALUE ) throw H2O.unimpl(); // more than 2b groups?
    //
    //final G[] gs = new G[(int)ngrps];
    //for( int i=0; i<ngrps; i++ ) gs[i] = new G(gbCols.length,aggs);
    //
    //// Now apply the aggregates using the group numbers.
    //new MRTask() {
    //  @Override public void map( Chunk[] cs ) {
    //    long start = cs[0].start();
    //    if( (int)start != start ) throw H2O.unimpl(); // wrapping math for >2b groups
    //    final int istart = (int)start;
    //    final long[] rows = rowss[0]; // wrapping math for >2b groups
    //    final int len = cs[0]._len;
    //    
    //    for( int row=0; row<len; row++ ) { // For all rows in Chunk
    //      final G g = gs[(int)rows[istart+row]];
    //      for( int a=0; a<aggs.length; a++ ) { // Accumulate aggregate reductions
    //        // since dss & ns are shared across all map calls, must be atomic
    //        // here, in the bad place
    //        //aggs[a].op(g._dss,g._ns,a, cs[aggs[a]._col].atd(row));
    //        //aggs[i].atomic_op(g._dss,g._ns,i, cs[aggs[i]._col].atd(row));
    //      }
    //    }
    //  }
    //  @Override public void reduce( MRTask t ) {
    //    throw H2O.unimpl();
    //  }
    //}.doAll(fr);
    //
    //return gs;
  }
  
  // --------------------------------------------------------------------------
  // Utility for ASTDdply; return a single aggregate for counting rows-per-group
  //static AGG[] aggNRows() { return new AGG[]{new AGG(FCN.nrow,0,NAHandling.ALL,0)};  }

  // Build output frame from the multi-column results
  static Frame buildOutput(Frame fr_keys, int noutCols, Frame fr, String[] fcnames, int ngrps, MRTask mrfill) {
    // Build the output!
    // the names of columns
    final int nCols = fr_keys.numCols()+noutCols;
    String[]     names = Arrays.copyOf(fr_keys.  names(),nCols);
    String[][] domains = Arrays.copyOf(fr_keys.domains(),nCols);
    for( int i=0; i<fcnames.length; i++ )
      names[i+fr_keys.numCols()] = fcnames[i];
    Vec v = Vec.makeZero(ngrps); // dummy layout vec

    // Convert the output arrays into a Frame, also doing the post-pass work
    Frame f= mrfill.doAll(nCols,v).outputFrame(names,domains);
    v.remove();
    return f;
  }

  // --------------------------------------------------------------------------
  // Description of a single aggregate, including the reduction function, the
  // column and specified NA handling
  static class AGG extends Iced {
    final FCN _fcn;             // Function to do
    final int _col;             // Column to apply function on
    final NAHandling _na;       // NA handling for this aggregate
    final int _maxx;            // Largest integer this column, used for mode
    final int _aggcol;          // Start column to hold agg results (runs for FCN.ncols)
    AGG( FCN fcn, int col, NAHandling na, int maxx, int aggcol ) { _fcn = fcn; _col = col; _na = na; _maxx = maxx; _aggcol = aggcol; }
    void atomic_op( double[][] dss, int gnum, double d ) { _fcn.atomic_op(dss,_aggcol,gnum,d); }
    double postPass( double[][] dss, int gnum, long n ) { return _fcn.postPass(dss,_aggcol,gnum,ncols(),n); }
    int ncols() { return _fcn.ncols(_maxx); }
  }

  // Groups!  Contains a Group Key - an array of doubles (often just 1 entry
  // long) that defines the Group.
  static class GKX extends Iced<GKX> { 
    int _hash; 
    int _gnum;                  // Group number, filled in later
    @Override public int hashCode() { return _hash; }
    GKX fill( Chunk[] cs, int row, double hash ) {
      long h = Double.doubleToRawLongBits(hash);
      // Doubles are lousy hashes; mix up the bits some
      h ^= (h>>>20) ^ (h>>>12);
      h ^= (h>>> 7) ^ (h>>> 4);
      _hash = (int)(h^(h>>32));
      return this;
    }
    void setkey( NewChunk[] ncs ) { }
    int compare( GK0 gk0 ) { return 0; }
    @Override public boolean equals( Object o ) { return o instanceof GKX; }
    boolean equals2( GKX gkx  ) { return true; }
    static GKX init( int ncols ) { return new GKX[]{new GKX(),new GK0(),new GK1(),new GK2()}[ncols]; }
  }
  static class GK0 extends GKX { 
    double _d0; 
    @Override GKX fill( Chunk[] cs, int row, double hash ) { _d0 = cs[0].atd(row); return super.fill(cs,row,hash+_d0); }
    @Override void setkey( NewChunk[] ncs ) { ncs[0].addNum(_d0); }
    @Override public boolean equals( Object o ) { return o instanceof GK0 && equals2(((GK0)o)); }
    @Override int compare( GK0 gk0 ) { return Double.compare(_d0,gk0._d0); }
    boolean equals2( GK0 gk0  ) { return _d0==gk0._d0 && super.equals2(gk0); }
  }
  static class GK1 extends GK0 { 
    double _d1; 
    @Override GKX fill( Chunk[] cs, int row, double hash ) { _d1 = cs[1].atd(row); return super.fill(cs,row,hash+_d1); }
    @Override void setkey( NewChunk[] ncs ) { ncs[1].addNum(_d1); super.setkey(ncs); }
    @Override public boolean equals( Object o ) { return o instanceof GK1 && equals2(((GK1)o)); }
    @Override int compare( GK0 gk1 ) { int x = super.compare(gk1); return x==0 ? Double.compare(_d1,((GK1)gk1)._d1) : x; }
    boolean equals2( GK1 gk1  ) { return _d1==gk1._d1 && super.equals2(gk1); }
  }
  static class GK2 extends GK1 { 
    double _d2; 
    @Override GKX fill( Chunk[] cs, int row, double hash ) { _d2 = cs[2].atd(row); return super.fill(cs,row,hash+_d2); }
    @Override void setkey( NewChunk[] ncs ) { ncs[2].addNum(_d2); super.setkey(ncs); }
    @Override public boolean equals( Object o ) { return o instanceof GK2 && equals2(((GK2)o)); }
    @Override int compare( GK0 gk2 ) { int x = super.compare(gk2); return x==0 ? Double.compare(_d0,((GK2)gk2)._d2) : x; }
    boolean equals2( GK2 gk2  ) { return _d2==gk2._d2 && super.equals2(gk2); }
  }




//  static class GBTask extends MRTask<GBTask> {
//    final Kill _kill;                 // Shared per-node, checks for size blowout
//    final IcedHashMap<G,String> _gss; // Shared per-node, common, racy
//    private final int[] _gbCols; // Columns used to define group
//    private final AGG[] _aggs;   // Aggregate descriptions
//    GBTask(int[] gbCols, AGG[] aggs) { _kill = new Kill(); _gss = new IcedHashMap<>(); _gbCols=gbCols; _aggs=aggs; }
//    @Override public void map(Chunk[] cs) {
//      if( _kill._killed ) return; // Abort if MRTask already dead for size blowout
//      // Groups found in this Chunk
//      IcedHashMap<G,String> gs = new IcedHashMap<>();
//      G gWork = new G(_gbCols.length,_aggs); // Working Group
//      G gOld;                   // Existing Group to be filled in
//      for( int row=0; row<cs[0]._len; row++ ) {
//        // Find the Group being worked on
//        gWork.fill(row,cs,_gbCols);            // Fill the worker Group for the hashtable lookup
//        if( gs.putIfAbsent(gWork,"")==null ) { // Insert if not absent (note: no race, no need for atomic)
//          gOld=gWork;                          // Inserted 'gWork' into table
//          gWork=new G(_gbCols.length,_aggs);   // need entirely new G
//        } else gOld=gs.getk(gWork);            // Else get existing group
//
//        for( int i=0; i<_aggs.length; i++ ) // Accumulate aggregate reductions
//          _aggs[i].op(gOld._dss,gOld._ns,i, cs[_aggs[i]._col].atd(row));
//      }
//      // This is a racy update into the node-local shared table of groups
//      reduce(gs);               // Atomically merge Group stats
//    }
//    // Racy update on a subtle path: reduction is always single-threaded, but
//    // the shared global hashtable being reduced into is ALSO being written by
//    // parallel map calls.
//    @Override public void reduce(GBTask t) {
//      if( t._kill._killed && !_kill._killed ) { _kill._killed = true; _gss.clear(); }
//      if( _kill._killed ) return;
//      if( _gss != t._gss ) reduce(t._gss);
//    }
//    // Non-blocking race-safe update of the shared per-node groups hashtable
//    private void reduce( IcedHashMap<G,String> r ) {
//      // Abort if dead for size blowout
//      if( r.size() > ASTMerge.MAX_HASH_SIZE ) { _kill._killed = true; _gss.clear(); return; }
//      for( G rg : r.keySet() ) {
//        if( _gss.putIfAbsent(rg,"")!=null ) {
//          G lg = _gss.getk(rg);
//          for( int i=0; i<_aggs.length; i++ )
//            _aggs[i].atomic_op(lg._dss,lg._ns,i, rg._dss[i], rg._ns[i]); // Need to atomically merge groups here
//        } else {
//          if( _gss.size() > ASTMerge.MAX_HASH_SIZE ) { _kill._killed = true; _gss.clear(); }
//          if( _kill._killed ) return;
//        }
//      }
//    }
//  }
//
//  // Groups!  Contains a Group Key - an array of doubles (often just 1 entry
//  // long) that defines the Group.  Also contains an array of doubles for the
//  // aggregate results, one per aggregate.
//  static class G extends Iced {
//    double _gs[];  // Group Key: Array is final; contents change with the "fill"
//    int _hash;           // Hash is not final; changes with the "fill"
//
//    final double _dss[][];      // Aggregates: usually sum or sum*2
//    final long   _ns[];         // row counts per aggregate, varies by NA handling and column
//
//    G( int ncols, AGG[] aggs ) { 
//      _gs = new double[ncols]; 
//      int len = aggs==null ? 0 : aggs.length;
//      _dss= new double[len][];
//      _ns = new long  [len]; 
//      for( int i=0; i<len; i++ ) _dss[i] = aggs[i].initVal();
//    }
//    G fill(int row, Chunk chks[], int cols[]) {
//      for( int c=0; c<cols.length; c++ ) // For all selection cols
//        _gs[c] = chks[cols[c]].atd(row); // Load into working array
//      _hash = hash();
//      return this;
//    }
//    private int hash() {
//      long h=0;                 // hash is sum of field bits
//      for( double d : _gs ) h += Double.doubleToRawLongBits(d);
//      // Doubles are lousy hashes; mix up the bits some
//      h ^= (h>>>20) ^ (h>>>12);
//      h ^= (h>>> 7) ^ (h>>> 4);
//      return (int)((h^(h>>32))&0x7FFFFFFF);
//    }
//    @Override public boolean equals( Object o ) {
//      return o instanceof G && Arrays.equals(_gs, ((G) o)._gs); }
//    @Override public int hashCode() { return _hash; }
//    @Override public String toString() { return Arrays.toString(_gs); }
//  }

}
