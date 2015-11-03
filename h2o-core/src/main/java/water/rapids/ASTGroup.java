package water.rapids;

import water.H2O;
import water.Iced;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.ArrayUtils;
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
  enum NAHandling { ALL, RM, IGNORE }

  // Functions handled by GroupBy
  enum FCN {
    nrow() { 
      @Override void op( double[] d0s, double d1 ) { d0s[0]++; }
      @Override void atomic_op( double[] d0s, double[] d1s ) { d0s[0] += d1s[0]; }
      @Override double postPass( double ds[], long n ) { return ds[0]; }
    },
    mean() { 
      @Override void op( double[] d0s, double d1 ) { d0s[0]+=d1; }
      @Override void atomic_op( double[] d0s, double[] d1s ) { d0s[0] += d1s[0]; }
      @Override double postPass( double ds[], long n ) { return ds[0]/n; }
    },
    sum() { 
      @Override void op( double[] d0s, double d1 ) { d0s[0]+=d1; }
      @Override void atomic_op( double[] d0s, double[] d1s ) { d0s[0] += d1s[0]; }
      @Override double postPass( double ds[], long n ) { return ds[0]; }
    },
    sumSquares() {
      @Override void op( double[] d0s, double d1 ) { d0s[0]+=d1*d1; }
      @Override void atomic_op( double[] d0s, double[] d1s ) { d0s[0] += d1s[0]; }
      @Override double postPass( double ds[], long n) { return ds[0]; }
    },
    var() {
      @Override void op( double[] d0s, double d1 ) { d0s[0]+=d1*d1; d0s[1]+=d1; }
      @Override void atomic_op( double[] d0s, double[] d1s ) { ArrayUtils.add(d0s,d1s); }
      @Override double postPass( double ds[], long n) { return (ds[0] - ds[1]*ds[1]/n)/(n-1); }
      @Override double[] initVal(int ignored) { return new double[2]; /* 0 -> sum_squares; 1 -> sum*/}
    },
    sdev() {
      @Override void op( double[] d0s, double d1 ) { d0s[0]+=d1*d1; d0s[1]+=d1; }
      @Override void atomic_op( double[] d0s, double[] d1s ) { ArrayUtils.add(d0s,d1s); }
      @Override double postPass( double ds[], long n) { return Math.sqrt((ds[0] - ds[1]*ds[1]/n)/(n-1)); }
      @Override double[] initVal(int ignored) { return new double[2]; /* 0 -> sum_squares; 1 -> sum*/}
    },
    min() { 
      @Override void op( double[] d0s, double d1 ) { d0s[0]= Math.min(d0s[0],d1); }
      @Override void atomic_op( double[] d0s, double[] d1s ) { op(d0s,d1s[0]); }
      @Override double postPass( double ds[], long n ) { return ds[0]; }
      @Override double[] initVal(int maxx) { return new double[]{ Double.MAX_VALUE}; }
    },
    max() { 
      @Override void op( double[] d0s, double d1 ) { d0s[0]= Math.max(d0s[0],d1); }
      @Override void atomic_op( double[] d0s, double[] d1s ) { op(d0s,d1s[0]); }
      @Override double postPass( double ds[], long n ) { return ds[0]; }
      @Override double[] initVal(int maxx) { return new double[]{-Double.MAX_VALUE}; }
    },
    mode() { 
      @Override void op( double[] d0s, double d1 ) { d0s[(int)d1]++; }
      @Override void atomic_op( double[] d0s, double[] d1s ) { ArrayUtils.add(d0s,d1s); }
      @Override double postPass( double ds[], long n ) { return ArrayUtils.maxIndex(ds); }
      @Override double[] initVal(int maxx) { return new double[maxx]; }
    },
    ;
    abstract void op( double[] d0, double d1 );
    abstract void atomic_op( double[] d0, double[] d1 );
    abstract double postPass( double ds[], long n );
    double[] initVal(int maxx) { return new double[]{0}; }
  }

  @Override int nargs() { return -1; } // (GB data [group-by-cols] [order-by-cols] {fcn col "na"}...)
  @Override
  public String[] args() { return new String[]{"..."}; }
  @Override public String str() { return "GB"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    int ncols = fr.numCols();

    ASTNumList groupby = check(ncols, asts[2]);
    int[] gbCols = groupby.expand4();

    ASTNumList orderby = check(ncols, asts[3]);
    final int[] ordCols = orderby.expand4();

    // Count of aggregates; knock off the first 4 ASTs (GB data [group-by] [order-by]...),
    // then count by triples.
    int naggs = (asts.length-4)/3;
    final AGG[] aggs = new AGG[naggs];
    for( int idx = 4; idx < asts.length; idx += 3 ) {
      Val v = asts[idx].exec(env);
      String fn = v instanceof ValFun ? v.getFun().str() : v.getStr();
      FCN fcn = FCN.valueOf(fn);
      ASTNumList col = check(ncols,asts[idx+1]);
      if( col.cnt() != 1 ) throw new IllegalArgumentException("Group-By functions take only a single column");
      int agg_col = (int)col.min(); // Aggregate column
      if( fcn==FCN.mode && !fr.vec(agg_col).isCategorical() )
        throw new IllegalArgumentException("Mode only allowed on categorical columns");
      NAHandling na = NAHandling.valueOf(asts[idx+2].exec(env).getStr().toUpperCase());
      aggs[(idx-4)/3] = new AGG(fcn,agg_col,na, (int)fr.vec(agg_col).max()+1);
    }

    // do the group by work now
    IcedHashMap<G,String> gss = doGroups(fr,gbCols,aggs);
    final G[] grps = gss.keySet().toArray(new G[gss.size()]);

    // apply an ORDER by here...
    if( ordCols.length > 0 )
      Arrays.sort(grps,new java.util.Comparator<G>() {
          // Compare 2 groups.  Iterate down _gs, stop when _gs[i] > that._gs[i],
          // or _gs[i] < that._gs[i].  Order by various columns specified by
          // _orderByCols.  NaN is treated as least
          @Override public int compare( G g1, G g2 ) {
            for( int i : ordCols ) {
              if(  Double.isNaN(g1._gs[i]) && !Double.isNaN(g2._gs[i]) ) return -1;
              if( !Double.isNaN(g1._gs[i]) &&  Double.isNaN(g2._gs[i]) ) return  1;
              if( g1._gs[i] != g2._gs[i] ) return g1._gs[i] < g2._gs[i] ? -1 : 1;
            }
            return 0;
          }
          // I do not believe sort() calls equals() at this time, so no need to implement
          @Override public boolean equals( Object o ) { throw H2O.unimpl(); }
        });

    // Build the output!
    String[] fcnames = new String[aggs.length];
    for( int i=0; i<aggs.length; i++ )
      fcnames[i] = aggs[i]._fcn.toString()+"_"+fr.name(aggs[i]._col);

    MRTask mrfill = new MRTask() {
      @Override public void map(Chunk[] c, NewChunk[] ncs) {
        int start=(int)c[0].start();
        for( int i=0;i<c[0]._len;++i) {
          G g = grps[i+start];  // One Group per row
          int j;
          for( j=0; j<g._gs.length; j++ ) // The Group Key, as a row
            ncs[j].addNum(g._gs[j]);
          for( int a=0; a<aggs.length; a++ )
            ncs[j++].addNum(aggs[a]._fcn.postPass(g._dss[a],g._ns[a]));
        }
      }
      };

    Frame f = buildOutput(gbCols, naggs, fr, fcnames, grps.length, mrfill);
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

  // Do all the grouping work.  Find groups in frame 'fr', grouped according to
  // the selected 'gbCols' columns, and for each group compute aggregrate
  // results using 'aggs'.  Return an array of groups, with the aggregate results.
  static IcedHashMap<G,String> doGroups(Frame fr, int[] gbCols, AGG[] aggs) {
    // do the group by work now
    long start = System.currentTimeMillis();
    GBTask p1 = new GBTask(gbCols, aggs).doAll(fr);
    Log.info("Group By Task done in " + (System.currentTimeMillis() - start)/1000. + " (s)");
    return p1._gss;
  }

  // Utility for ASTDdply; return a single aggregate for counting rows-per-group
  static AGG[] aggNRows() { return new AGG[]{new AGG(FCN.nrow,0,NAHandling.IGNORE,0)};  }

  // Build output frame from the multi-column results
  static Frame buildOutput(int[] gbCols, int noutCols, Frame fr, String[] fcnames, int ngrps, MRTask mrfill) {
    // Build the output!
    // the names of columns
    final int nCols = gbCols.length+noutCols;
    String[] names = new String[nCols];
    String[][] domains = new String[nCols][];
    for( int i=0;i<gbCols.length; i++ ) {
      names  [i] = fr.name     (gbCols[i]);
      domains[i] = fr.domains()[gbCols[i]];
    }
    for( int i=0; i<fcnames.length; i++ )
      names[i+gbCols.length] = fcnames[i];
    Vec v = Vec.makeZero(ngrps); // dummy layout vec

    // Convert the output arrays into a Frame, also doing the post-pass work
    Frame f= mrfill.doAll(nCols, Vec.T_NUM, new Frame(v)).outputFrame(names,domains);
    v.remove();
    return f;
  }



  // Description of a single aggregate, including the reduction function, the
  // column and specified NA handling
  static class AGG extends Iced {
    final FCN _fcn;
    final int _col;
    final NAHandling _na;
    final int _maxx;            // Largest integer this column
    AGG( FCN fcn, int col, NAHandling na, int maxx ) { _fcn = fcn; _col = col; _na = na; _maxx = maxx; }
    // Update the array pair {ds[i],ns[i]} with d1.
    // ds is the reduction array
    // ns is the element count
    void op( double[][] d0ss, long[] n0s, int i, double d1 ) {
      // Normal number or ALL   : call op()
      if( !Double.isNaN(d1) || _na==NAHandling.ALL    ) _fcn.op(d0ss[i],d1);
      // Normal number or IGNORE: bump count; RM: do not bump count
      if( !Double.isNaN(d1) || _na==NAHandling.IGNORE ) n0s[i]++; 
    }
    // Atomically update the array pair {dss[i],ns[i]} with the pair {d1,n1}.
    // Same as op() above, but called racily and updates atomically.
    void atomic_op( double[][] d0ss, long[] n0s, int i, double[] d1s, long n1 ) {
      synchronized(d0ss[i]) { 
        _fcn.atomic_op(d0ss[i],d1s); 
        n0s[i] += n1;
      }
    }
    double[] initVal() { return _fcn.initVal(_maxx); }
  }

  // --------------------------------------------------------------------------
  // Main worker MRTask.  Makes 1 pass over the data, and accumulates both all
  // groups and all aggregates
  static class GBTask extends MRTask<GBTask> {
    final IcedHashMap<G,String> _gss; // Shared per-node, common, racy
    private final int[] _gbCols; // Columns used to define group
    private final AGG[] _aggs;   // Aggregate descriptions
    GBTask(int[] gbCols, AGG[] aggs) { _gbCols=gbCols; _aggs=aggs; _gss = new IcedHashMap<>(); }
    @Override public void map(Chunk[] cs) {
      // Groups found in this Chunk
      IcedHashMap<G,String> gs = new IcedHashMap<>();
      G gWork = new G(_gbCols.length,_aggs); // Working Group
      G gOld;                   // Existing Group to be filled in
      for( int row=0; row<cs[0]._len; row++ ) {
        // Find the Group being worked on
        gWork.fill(row,cs,_gbCols);            // Fill the worker Group for the hashtable lookup
        if( gs.putIfAbsent(gWork,"")==null ) { // Insert if not absent (note: no race, no need for atomic)
          gOld=gWork;                          // Inserted 'gWork' into table
          gWork=new G(_gbCols.length,_aggs);   // need entirely new G
        } else gOld=gs.getk(gWork);            // Else get existing group

        for( int i=0; i<_aggs.length; i++ ) // Accumulate aggregate reductions
          _aggs[i].op(gOld._dss,gOld._ns,i, cs[_aggs[i]._col].atd(row));
      }
      // This is a racy update into the node-local shared table of groups
      reduce(gs);               // Atomically merge Group stats
    }
    // Racy update on a subtle path: reduction is always single-threaded, but
    // the shared global hashtable being reduced into is ALSO being written by
    // parallel map calls.
    @Override public void reduce(GBTask t) { if( _gss != t._gss ) reduce(t._gss); }
    // Non-blocking race-safe update of the shared per-node groups hashtable
    private void reduce( IcedHashMap<G,String> r ) {
      for( G rg : r.keySet() )
        if( _gss.putIfAbsent(rg,"")!=null ) {
          G lg = _gss.getk(rg);
          for( int i=0; i<_aggs.length; i++ )
            _aggs[i].atomic_op(lg._dss,lg._ns,i, rg._dss[i], rg._ns[i]); // Need to atomically merge groups here
        }
    }
  }

  // Groups!  Contains a Group Key - an array of doubles (often just 1 entry
  // long) that defines the Group.  Also contains an array of doubles for the
  // aggregate results, one per aggregate.
  static class G extends Iced {
    final double _gs[];  // Group Key: Array is final; contents change with the "fill"
    int _hash;           // Hash is not final; changes with the "fill"

    final double _dss[][];      // Aggregates: usually sum or sum*2
    final long   _ns[];         // row counts per aggregate, varies by NA handling and column

    G( int ncols, AGG[] aggs ) { 
      _gs = new double[ncols]; 
      int len = aggs==null ? 0 : aggs.length;
      _dss= new double[len][];
      _ns = new long  [len]; 
      for( int i=0; i<len; i++ ) _dss[i] = aggs[i].initVal();
    }
    G fill(int row, Chunk chks[], int cols[]) {
      for( int c=0; c<cols.length; c++ ) // For all selection cols
        _gs[c] = chks[cols[c]].atd(row); // Load into working array
      _hash = hash();
      return this;
    }
    private int hash() {
      long h=0;                 // hash is sum of field bits
      for( double d : _gs ) h += Double.doubleToRawLongBits(d);
      // Doubles are lousy hashes; mix up the bits some
      h ^= (h>>>20) ^ (h>>>12);
      h ^= (h>>> 7) ^ (h>>> 4);
      return (int)((h^(h>>32))&0x7FFFFFFF);
    }
    @Override public boolean equals( Object o ) {
      return o instanceof G && Arrays.equals(_gs, ((G) o)._gs); }
    @Override public int hashCode() { return _hash; }
    @Override public String toString() { return Arrays.toString(_gs); }
  }
}
