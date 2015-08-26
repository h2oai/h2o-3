package water.currents;

import java.util.Arrays;
import water.*;
import water.util.*;
import water.fvec.*;
import water.nbhm.NonBlockingHashMap;

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
    count() { 
      @Override double op( double d0, double d1 ) { return d0+ 1; } 
      @Override double postPass( double d, long n ) { return d; }
    },
    nrow() { 
      @Override double op( double d0, double d1 ) { return d0+ 1; }
      @Override double postPass( double d, long n ) { return d; }
    },
    mean() { 
      @Override double op( double d0, double d1 ) { return d0+d1; }
      @Override double postPass( double d, long n ) { return d/n; }
    },
    sum() { 
      @Override double op( double d0, double d1 ) { return d0+d1; }
      @Override double postPass( double d, long n ) { return d; }
    },
    ;
    abstract double op( double d0, double d1 );
    abstract double postPass( double d, long n );
  }

  @Override int nargs() { return -1; } // (GB data [group-by-cols] [order-by-cols] {fcn col "na"}...)
  @Override public String str() { return "GB"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    int ncols = fr.numCols();

    ASTNumList groupby = check(ncols, asts[2]);
    int[] gbCols = groupby.expand4();

    ASTNumList orderby = check(ncols, asts[3]);
    if( orderby.isEmpty() ) orderby = new ASTNumList(0,gbCols.length); // If missing, sort by groups in-order
    else throw H2O.unimpl();
    final int[] ordCols = orderby.expand4();

    // Count of aggregates; knock off the first 4 ASTs (GB data [group-by] [order-by]...),
    // then count by triples.
    int naggs = (asts.length-4)/3;
    final AGG[] aggs = new AGG[naggs];
    for( int idx = 4; idx < asts.length; idx += 3 ) {
      FCN fcn = FCN.valueOf(asts[idx].exec(env).getFun().str());
      ASTNumList col = check(ncols,asts[idx+1]);
      if( col.cnt() != 1 ) throw new IllegalArgumentException("Group-By functions take only a single column");
      NAHandling na = NAHandling.valueOf(asts[idx+2].exec(env).getStr().toUpperCase());
      aggs[(idx-4)/3] = new AGG(fcn,(int)col.min(),na);
    }

    // do the group by work now
    long start = System.currentTimeMillis();
    GBTask p1 = new GBTask(gbCols, aggs).doAll(fr);
    final G[] grps = p1._gss.keySet().toArray(new G[p1._gss.size()]);
    Log.info("Group By Task done in " + (System.currentTimeMillis() - start)/1000. + " (s)");

    // apply an ORDER by here...
    Arrays.sort(grps,new java.util.Comparator<G>() {
        // compare 2 groups
        // iterate down _gs, stop when _gs[i] > that._gs[i], or _gs[i] < that._gs[i]
        // order by various columns specified by _orderByCols
        // NaN is treated as least
        @Override public int compare( G g1, G g2 ) {
          for( int i : ordCols ) {
            if(  Double.isNaN(g1._gs[i]) && !Double.isNaN(g2._gs[i]) ) return -1;
            if( !Double.isNaN(g1._gs[i]) &&  Double.isNaN(g2._gs[i]) ) return  1;
            if( g1._gs[i] != g2._gs[i] ) return g1._gs[i] < g2._gs[i] ? -1 : 1;
          }
          return 0;
        }
        @Override public boolean equals( Object o ) {
          throw H2O.unimpl();
        }
      });

    // build the output
    final int nCols = gbCols.length+naggs;

    // the names of columns
    String[] names = new String[nCols];
    String[][] domains = new String[nCols][];
    for( int i=0;i<gbCols.length; i++ ) {
      names[i] = fr.name(gbCols[i]);
      domains[i] = fr.domains()[gbCols[i]];
    }
    for( int i=0; i<aggs.length; i++ )
      names[i+gbCols.length] = aggs[i]._fcn.toString()+"_"+fr.name(aggs[i]._col);

    // dummy vec
    Vec v = Vec.makeZero(grps.length);

    Frame f=new MRTask() {
      @Override public void map(Chunk[] c, NewChunk[] ncs) {
        int start=(int)c[0].start();
        for( int i=0;i<c[0]._len;++i) {
          G g = grps[i+start];  // One Group per row
          int j;
          for( j=0; j<g._gs.length; j++ ) // The Group Key, as a row
            ncs[j].addNum(g._gs[j]);
          for( int a=0; a<aggs.length; a++ )
            ncs[j++].addNum(aggs[a]._fcn.postPass(g._ds[a],g._ns[a]));
        }
      }
    }.doAll(nCols,v).outputFrame(names,domains);
    v.remove();

    return new ValFrame(f);
  }

  private ASTNumList check( long dstX, AST ast ) {
    // Sanity check vs dst.  To simplify logic, jam the 1 col/row case in as a ASTNumList
    ASTNumList dim;
    if( ast instanceof ASTNumList  ) dim = (ASTNumList)ast;
    else if( ast instanceof ASTNum ) dim = new ASTNumList(((ASTNum)ast)._d.getNum());
    else throw new IllegalArgumentException("Requires a number-list, but found a "+ast.getClass());
    if( dim.isEmpty() ) return dim; // Allow empty
    if( !(0 <= dim.min() && dim.max()-1 <  dstX) &&
        !(1 == dim.cnt() && dim.max()-1 == dstX) ) // Special case of append
      throw new IllegalArgumentException("Selection must be an integer from 0 to "+dstX);
    return dim;
  }

  private static class AGG extends Iced {
    final FCN _fcn;
    final int _col;
    final NAHandling _na;
    AGG( FCN fcn, int col, NAHandling na ) { _fcn = fcn; _col = col; _na = na; }
    // Update the array pair {ds[i],ns[i]}.
    // ds is the reduction
    // ns is the element count
    void op( double[] ds, long[] ns, int i, double d1 ) {
      // Normal number or ALL   : call op()
      if( !Double.isNaN(d1) || _na==NAHandling.ALL    ) ds[i] = _fcn.op(ds[i],d1);
      // Normal number or IGNORE: bump count; RM: do not bump count
      if( !Double.isNaN(d1) || _na==NAHandling.IGNORE ) ns[i]++; 
    }
    void atomic_op( double[] d0s, long[] n0s, int i, double d1, long n1 ) {
      double d;  long n;
      // Normal number or ALL   : call op()
      if( !Double.isNaN(d1) || _na==NAHandling.ALL    )
        while( !AtomicUtils.DoubleArray.CAS(d0s,i, d=d0s[i], _fcn.op(d,d1) ) ) ;
      // Normal number or IGNORE: bump count; RM: do not bump count
      if( !Double.isNaN(d1) || _na==NAHandling.IGNORE )
        while( !AtomicUtils.  LongArray.CAS(n0s,i, n=n0s[i], n+n1 ) ) ;
    }
  }

  // --------------------------------------------------------------------------
  // Main worker MRTask.  Makes 1 pass over the data, and accumulates both all
  // groups and all aggregates
  static class GBTask extends MRTask<GBTask> {
    private final IcedHashMap<G,String> _gss; // Shared per-node, common, racy
    private final int[] _gbCols; // Columns used to define group
    private final AGG[] _aggs;   // Aggregate descriptions
    GBTask(int[] gbCols, AGG[] aggs) { _gbCols=gbCols; _aggs=aggs; _gss = new IcedHashMap<>(); }
    @Override public void map(Chunk[] cs) {
      // Groups found in this Chunk
      IcedHashMap<G,String> gs = new IcedHashMap<>();
      G gWork = new G(_gbCols.length,_aggs.length); // Working Group
      G gOld;                   // Existing Group to be filled in
      for( int row=0; row<cs[0]._len; row++ ) {

        // Find the Group being worked on
        gWork.fill(row,cs,_gbCols);
        if( gs.putIfAbsent(gWork,"")==null ) {  // won the race w/ this group?
          gOld=gWork;                       // Inserted 'gWork' into table
          gWork=new G(_gbCols.length,_aggs.length); // need entirely new G
        } else gOld=gs.getk(gWork);         // Else get existing group

        for( int i=0; i<_aggs.length; i++ )
          _aggs[i].op(gOld._ds,gOld._ns,i,cs[_aggs[i]._col].atd(row));
      }
      reduce(_gss,gs);          // Atomically merge Group stats
    }
    // Reduce, but no need for atomic on this path
    @Override public void reduce(GBTask t) { if( _gss != t._gss ) reduce(_gss,t._gss); }
    // If coming from the map() call, must atomically merge groups
    private void reduce( IcedHashMap<G,String> l, IcedHashMap<G,String> r ) {
      for( G rg : r.keySet() )
        if( l.putIfAbsent(rg,"")!=null ) {
          G lg = l.getk(rg);
          for( int i=0; i<_aggs.length; i++ )
            _aggs[i].atomic_op(lg._ds,lg._ns,i,rg._ds[i],rg._ns[i]); // Need to atomically merge groups here
        }
    }
  }

  // Groups!  Contains a Group Key - an array of doubles (often just 1 entry
  // long) that defines the Group.  Also contains an array of doubles for the
  // aggregate results, one per aggregate.

  private static class G extends Iced {
    final double _gs[];  // Group Key: Array is final; contents change with the "fill"
    int _hash;           // Hash is not final; changes with the "fill"

    final double _ds[];         // Aggregates: usually sum or sum*2
    final long   _ns[];         // row counts per aggregate, varies by NA handling and column

    G( int ncols, int naggs ) { _gs = new double[ncols]; _ds = new double[naggs]; _ns = new long[naggs]; }
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
