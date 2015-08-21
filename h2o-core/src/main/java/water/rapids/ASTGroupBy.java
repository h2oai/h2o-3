package water.rapids;


import sun.misc.Unsafe;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.nbhm.NonBlockingHashSet;
import water.nbhm.UtilUnsafe;
import water.util.IcedHashMap;
import water.util.Log;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * GROUPBY: Single pass aggregation by columns.
 *
 * NA handling:
 *
 *  AGG.T_IG: case 0
 *    Count NA rows, but discard values in sums, mins, maxs
 *      FIRST/LAST return the first nonNA first/last, or NA if all NA
 *
 *  AGG.T_RM: case 1
 *    Count NA rows separately, discard values in sums, mins, maxs and compute aggregates less NA row counts
 *      FIRST/LAST treated as above
 *
 *  AGG.T_ALL: case 2
 *    Include NA in all aggregates -- any NA encountered forces aggregate to be NA.
 *      FIRST/LAST return first/last row regardless of NAs.
 *
 * Aggregates:
 *  MIN
 *  MAX
 *  MEAN
 *  COUNT
 *  SUM
 *  SD
 *  VAR
 *  COUNT_DISTINCT
 *  FIRST
 *  LAST
 *  Aggregations on time and numeric columns only.
 */
  public class ASTGroupBy extends ASTUniPrefixOp {
  // AST: (GB fr cols AGGS ORDERBY)
  //      (GB %k (llist #1;#3) (AGGS #2 "min" #4 "mean" #6) ())  for no order by..., otherwise is a llist or single number
  private long[] _gbCols; // group by columns
  private AGG[] _agg;
  private AST[] _gbColsDelayed;
  private String[] _gbColsDelayedByName;
  private long[] _orderByCols;
  ASTGroupBy() { super(null); }
  @Override String opStr() { return "GB"; }
  @Override ASTOp make() {return new ASTGroupBy();}
  ASTGroupBy parse_impl(Exec E) {
    AST ary = E.parse();
    // parse gby columns
    AST s = E.parse();
    if( s instanceof ASTLongList ) _gbCols = ((ASTLongList)s)._l;
    else if( s instanceof ASTNum ) _gbCols = new long[]{(long)((ASTNum)s)._d};
    else if( s instanceof ASTAry ) _gbColsDelayed = ((ASTAry)s)._a;
    else if( s instanceof  ASTStringList) _gbColsDelayedByName = ((ASTStringList)s)._s;
    else if( s instanceof ASTDoubleList ) {
      double[] d = ((ASTDoubleList)s)._d;
      _gbCols = new long[d.length];
      for(int i=0;i<d.length;++i) _gbCols[i]=(long)d[i];
    }
    else throw new IllegalArgumentException("Badly formed AST. Columns argument must be a llist or number. Got: " +s.getClass());

    //parse AGGs
    _agg = ((AGG)E.parse())._aggs;

    // parse order by
    s = E.parse();
    if( s instanceof ASTLongList ) _orderByCols = ((ASTLongList)s)._l;
    else if( s instanceof ASTNum ) _orderByCols = new long[]{(long)((ASTNum)s)._d};
    else if( s instanceof ASTNull) _orderByCols = null;
    else throw new IllegalArgumentException("Order by column must be an index or list of indexes. Got " + s.getClass());

    E.eatEnd();
    ASTGroupBy res = (ASTGroupBy)clone();
    res._asts = new AST[]{ary};
    return res;
  }
  @Override void apply(Env e) {
    // only allow reductions on time and numeric columns
    Frame fr = e.popAry();

    // for delayed column lookups
    if( _gbCols==null ) _gbCols = _gbColsDelayed==null? findCols(fr, _gbColsDelayedByName): findCols(fr, _gbColsDelayed);
    computeCols(_agg,fr); // delayed column set

    // do the group by work now
    long s = System.currentTimeMillis();
    GBTask p1 = new GBTask(_gbCols, _agg).doAll(fr);
    Log.info("Group By Task done in " + (System.currentTimeMillis() - s)/1000. + " (s)");
    int nGrps = p1._g.size();
    G[] tmpGrps = p1._g.keySet().toArray(new G[nGrps]);
    while( tmpGrps[nGrps-1]==null ) nGrps--;
    final G[] grps = new G[nGrps];
    System.arraycopy(tmpGrps,0,grps,0,nGrps);
    H2O.submitTask(new ParallelPostGlobal(grps,nGrps,_orderByCols)).join();

    // apply an ORDER by here...
    if( _orderByCols != null )
      Arrays.sort(grps);

    // build the output
    final int nCols = _gbCols.length+_agg.length;

    // dummy vec
    Vec v = Vec.makeZero(nGrps);

    // the names of columns
    String[] names = new String[nCols];
    String[][] domains = new String[nCols][];
    for( int i=0;i<_gbCols.length;++i) {
      names[i] = fr.name((int) _gbCols[i]);
      domains[i] = fr.domains()[(int)_gbCols[i]];
    }
    System.arraycopy(AGG.names(_agg),0,names,_gbCols.length,_agg.length);

    final AGG[] agg=_agg;
    Frame f=new MRTask() {
      @Override public void map(Chunk[] c, NewChunk[] ncs) {
        int start=(int)c[0].start();
        for( int i=0;i<c[0]._len;++i) {
          G g = grps[i+start];
          int j=0;
          for(;j<g._ds.length;++j)
            ncs[j].addNum(g._ds[j]);

          for(int a=0; a<agg.length;++a) {
            byte type = agg[a]._type;
            switch( type ) {
              case AGG.T_N:  ncs[j++].addNum(g._N       );  break;
              case AGG.T_AVG:ncs[j++].addNum(g._avs[a]  );  break;
              case AGG.T_MIN:ncs[j++].addNum(g._min[a]  );  break;
              case AGG.T_MAX:ncs[j++].addNum(g._max[a]  );  break;
              case AGG.T_VAR:ncs[j++].addNum(g._vars[a] );  break;
              case AGG.T_SD :ncs[j++].addNum(g._sdevs[a]);  break;
              case AGG.T_SUM:ncs[j++].addNum(g._sum[a]  );  break;
              case AGG.T_SS :ncs[j++].addNum(g._ss [a]  );  break;
              case AGG.T_ND: ncs[j++].addNum(g._ND[a]   );  break;
              case AGG.T_F:  ncs[j++].addNum(g._f[a]    );  break;
              case AGG.T_L:  ncs[j++].addNum(g._l[a]    );  break;
              default:
                throw new IllegalArgumentException("Unsupported aggregation type: " + type);
            }
          }
        }
      }
    }.doAll(nCols,v).outputFrame(names,domains);
    p1._g=null;
    Keyed.remove(v._key);
    e.pushAry(f);
  }

  private long[] findCols(Frame f, String[] names) {
    long[] res = new long[names.length];
    int i=0;
    for( String name:names ) {
      long c = f.find(name);
      if( c == -1 ) throw new IllegalArgumentException("Column not found: " + name);
      res[i++] = c;
    }
    return res;
  }

  private long[] findCols(Frame f, AST[] asts) {
    long[] res = new long[asts.length];
    int i=0;
    for( AST ast:asts ) {
      Env e = treeWalk(new Env(new HashSet<Key>()));
      if( e.isAry()      ) res[i++] = f.find(e.popAry().anyVec());
      else if( e.isNum() ) res[i++] = (int)e.popDbl();
      else if( e.isStr() ) res[i++] = f.find(e.popStr());
      else throw new IllegalArgumentException("Don't know what to do with: " + ast.getClass() + "; " + e.pop());
    }
    return res;
  }

  private void computeCols(AGG[] aggs, Frame f) {
    for(AGG a:aggs ) {
      if( a._c == null ) {
        if( a._delayedColByName!=null ) a._c = f.find(a._delayedColByName);
        else if( a._delayedCol!=null  ) {
          Env e = treeWalk(new Env(new HashSet<Key>()));
          if( e.isAry() ) a._c = f.find(e.popAry().anyVec());
          else if( e.isNum() ) a._c = (int)e.popDbl();
          else if( e.isStr() ) a._c = f.find(e.popStr());
          else throw new IllegalArgumentException("No column found for: " + e.pop());
        }
        else throw new IllegalArgumentException("Missing column for aggregate: " + a._name);
      }
    }
  }

  public static class IcedNBHS<T extends Iced> extends Iced implements Iterable<T> {
    NonBlockingHashSet<T> _g;
    IcedNBHS() {_g=new NonBlockingHashSet<>();}
    boolean add(T t) { return _g.add(t); }
    boolean addAll(Collection<? extends T> c) { return _g.addAll(c); }
    T get(T g) { return _g.get(g); }
    int size() { return _g.size(); }
    @Override public AutoBuffer write_impl( AutoBuffer ab ) {
      if( _g == null ) return ab.put4(0);
      ab.put4(_g.size());
      for( T g: _g ) ab.put(g);
      return ab;
    }
    @Override public IcedNBHS read_impl(AutoBuffer ab) {
      int len = ab.get4();
      if( len == 0 ) return this;
      _g = new NonBlockingHashSet<>();
      for( int i=0;i<len;++i) _g.add((T)ab.get());
      return this;
    }
    @Override public Iterator<T> iterator() {return _g.iterator(); }
  }

  public static class GBTask extends MRTask<GBTask> {
    IcedHashMap<G,String> _g;
    private long[] _gbCols;
    private AGG[] _agg;
    GBTask(long[] gbCols, AGG[] agg) { _gbCols=gbCols; _agg=agg; }
    @Override public void map(Chunk[] c) {
      _g = new IcedHashMap<>();
      long start = c[0].start();
      byte[] naMethods = AGG.naMethods(_agg);
      G g = new G(_gbCols.length,_agg.length,naMethods);
      G gOld;  // fill this one in for all the CAS'ing
      for( int i=0;i<c[0]._len;++i ) {
        g.fill(i,c,_gbCols);
        String g_old = _g.putIfAbsent(g,"");
        if( g_old==null ) {  // won the race w/ this group
          gOld=g;
          g=new G(_gbCols.length,_agg.length,naMethods); // need entirely new G
        } else gOld=_g.getk(g);
        // cas in COUNT
        long r=gOld._N;
        while(!G.CAS_N(gOld, r, r + 1))
          r=gOld._N;
        perRow(_agg,i,start,c,gOld);
      }
    }
    @Override public void reduce(GBTask t) {
      if( _g!=t._g ) {
        IcedHashMap<G,String> l = _g;
        IcedHashMap<G,String> r = t._g;
        if( l.size() < r.size() ) { l=r; r=_g; }  // larger on the left
        // loop over the smaller set of grps
        for( G rg:r.keySet() ) {
          G lg = l.getk(rg);
          if( l.putIfAbsent(rg,"")!=null ) {
            assert lg!=null;
            long R = lg._N;
            while (!G.CAS_N(lg, R, R + rg._N))
              R = lg._N;
            reduceGroup(_agg, lg, rg);
          }
        }
        _g=l;
        t._g=null;
      }
    }
    // task helper functions
    private static void perRow(AGG[] agg, int chkRow, long rowOffset, Chunk[] c, G g) { perRow(agg,chkRow,rowOffset,c,g,null); }
    private static void reduceGroup(AGG[] agg, G g, G that) { perRow(agg,-1,-1,null,g,that);}
    private static void perRow(AGG[] agg, int chkRow, long rowOffset, Chunk[] c, G g, G that) {
      byte type; int col;
      for( int i=0;i<agg.length;++i ) {
        col = agg[i]._c;

        // update NA value for this (group, aggregate) pair:
        if( c!=null ) {
          if( c[col].isNA(chkRow) ) setNA(g,1L,i);
        } else {
          // reduce NA counts together...
          setNA(g,that._NA[i],i);
        }

        if( (type=agg[i]._type) == AGG.T_N ) continue; //immediate short circuit if COUNT

        if( c!= null )
          if( !agg[i].isAll() && c[col].isNA(chkRow) )
            continue;

        // build up a long[] of vals, to handle the case when c is and isn't null.
        long bits=-1;
        if( c!=null ) {
          if( c[col].isNA(chkRow) ) continue;
          bits = Double.doubleToRawLongBits(c[col].atd(chkRow));
        }
        if( type == AGG.T_ND ) {
//          if( c==null ) g._nd._nd[i].addAll(that._nd._nd[i]);
//          else          g._nd._nd[i].add(c[col].atd(chkRow));
          continue;
        }

        switch( type ) {  // ordered by "popularity"
          case AGG.T_AVG: /* fall through */
          case AGG.T_SUM: setSum(g, c == null ? Double.doubleToRawLongBits(that._sum[i]) : bits, i);   break;
          case AGG.T_MIN: setMin(  g,c==null ? Double.doubleToRawLongBits(that._min[i]) : bits,i);   break;
          case AGG.T_MAX: setMax(  g,c==null ? Double.doubleToRawLongBits(that._max[i]) : bits,i);   break;
          case AGG.T_VAR:
          case AGG.T_SD:  setSum(g, c == null ? Double.doubleToRawLongBits(that._sum[i]) : bits, i); /* fall through */
          case AGG.T_SS:  setSS(g, c == null ? Double.doubleToRawLongBits(that._ss[i]) : bits, i, c==null);   break;
          case AGG.T_F:   setFirst(g,c==null ? that._f[i] : chkRow+rowOffset,i);   break;
          case AGG.T_L:   setLast(g, c == null ? that._l[i] : chkRow + rowOffset, i);   break;
          default:
            throw new IllegalArgumentException("Unsupported aggregation type: " + type);
        }
      }
    }

    // all the CAS'ing helpers
    private static void setFirst(G g, long v, int c) {
      long o = g._f[c];
      while( v < o && !G.CAS_f(g,G.longRawIdx(c),o,v))
        o = g._f[c];
    }
    private static void setLast(G g, long v, int c) {
      long o = g._l[c];
      while( v > o && !G.CAS_l(g, G.longRawIdx(c), o, v))
        o = g._l[c];
    }
    private static void setMin(G g, long v, int c) {
      double o = g._min[c];
      double vv = Double.longBitsToDouble(v);
      while( vv < o && !G.CAS_min(g,G.doubleRawIdx(c),Double.doubleToRawLongBits(o),v))
        o = g._min[c];
    }
    private static void setMax(G g, long v, int c) {
      double o = g._max[c];
      double vv = Double.longBitsToDouble(v);
      while( vv > o && !G.CAS_max(g, G.doubleRawIdx(c), Double.doubleToRawLongBits(o), v))
        o = g._max[c];
    }
    private static void setSum(G g, long vv, int c) {
      double v = Double.longBitsToDouble(vv);
      double o = g._sum[c];
      while(!G.CAS_sum(g,G.doubleRawIdx(c),Double.doubleToRawLongBits(o),Double.doubleToRawLongBits(o+v)))
        o=g._sum[c];
    }
    private static void setSS(G g, long vv, int c, boolean isReduce) {
      double v = Double.longBitsToDouble(vv);
      double o = g._ss[c];
      if( isReduce ) {
        while(!G.CAS_ss(g,G.doubleRawIdx(c), Double.doubleToRawLongBits(o), Double.doubleToRawLongBits(o+v)))
          o = g._ss[c];
      } else {
        while (!G.CAS_ss(g, G.doubleRawIdx(c), Double.doubleToRawLongBits(o), Double.doubleToRawLongBits(o + v * v)))
          o = g._ss[c];
      }
    }
    private static void setNA(G g, long n, int c) {
      long o = g._NA[c];
      while(!G.CAS_NA(g,G.longRawIdx(c),o,o+n))
        o=g._NA[c];
    }
  }

  private static class GTask extends H2O.H2OCountedCompleter<GTask> {
    private final G _g;
    private final long[] _orderByCols;
    GTask(H2O.H2OCountedCompleter cc, G g,long[] orderByCols) { super(cc); _g=g; _orderByCols=orderByCols; }
    @Override protected void compute2() {
      _g.close();
      int[] orderByCols = _orderByCols==null?null:new int[_orderByCols.length];
      if( orderByCols != null )
        for(int i=0;i<orderByCols.length;++i) orderByCols[i]=(int)_orderByCols[i];
      _g._orderByCols=orderByCols;
      tryComplete();
    }
  }

  public static class ParallelPostGlobal extends H2O.H2OCountedCompleter<ParallelPostGlobal> {
    private final G[] _g;
    private final int _ngrps;
    private final long[] _orderByCols;
    private final int _maxP=50*1000; // burn 50K at a time
    private final AtomicInteger _ctr;
    ParallelPostGlobal(G[] g, int ngrps, long[] orderByCols) { _g=g; _ctr=new AtomicInteger(_maxP-1); _ngrps=ngrps; _orderByCols = orderByCols; }


    @Override protected void compute2(){
      addToPendingCount(_g.length-1);
      for( int i=0;i<Math.min(_g.length,_maxP);++i) frkTsk(i);
    }

    private void frkTsk(final int i) { new GTask(new Callback(), _g[i], _orderByCols).fork(); }

    private class Callback extends H2O.H2OCallback {
      public Callback(){super(ParallelPostGlobal.this);}
      @Override public void callback(H2O.H2OCountedCompleter cc) {
        int i = _ctr.incrementAndGet();
        if( i < _g.length )
          frkTsk(i);
      }
    }
  }

  private static class NBHSAD extends Iced {
    private transient NonBlockingHashSet _nd[];
    private int _n;
    NBHSAD(int n) { _nd = new NonBlockingHashSet[n]; _n=n; }
    @Override public AutoBuffer write_impl(AutoBuffer ab) {
      int len=_nd.length;
      ab.put4(len);
      for (NonBlockingHashSet a_nd : _nd) {
        if( a_nd==null ) {
          ab.put4(0);
          continue;
        }
        int s = a_nd.size();
        ab.put4(s);
        for (Object d : a_nd) ab.put8d((double)d);
      }
      return ab;
    }
    @Override public NBHSAD read_impl(AutoBuffer ab) {
      int len = ab.get4();
      _n=len;
      _nd=new NonBlockingHashSet[len];
      for(int i=0;i<len;++i) {
        _nd[i] = new NonBlockingHashSet<>();
        int s = ab.get4();
        if( s==0 ) continue;
        for(int j=0;j<s;++j) _nd[i].add(ab.get8d());
      }
      return this;
    }
  }

  public static class G extends Iced implements Comparable<G> {
    public int[] _orderByCols;  // set during the ParallelPostGlobal if there is to be any order by
    public final double _ds[];  // Array is final; contents change with the "fill"
    public int _hash;           // Hash is not final; changes with the "fill"
    public G fill(int row, Chunk chks[], long cols[]) {
      for( int c=0; c<cols.length; c++ ) // For all selection cols
        _ds[c] = chks[(int)cols[c]].atd(row); // Load into working array
      _hash = hash();
      return this;
    }
    private int hash() {
      long h=0;                 // hash is sum of field bits
      for( double d : _ds ) h += Double.doubleToRawLongBits(d);
      // Doubles are lousy hashes; mix up the bits some
      h ^= (h>>>20) ^ (h>>>12);
      h ^= (h>>> 7) ^ (h>>> 4);
      return (int)((h^(h>>32))&0x7FFFFFFF);
    }
    @Override public boolean equals( Object o ) {
      return o instanceof G && Arrays.equals(_ds, ((G) o)._ds); }
    @Override public int hashCode() { return _hash; }
    @Override public String toString() { return Arrays.toString(_ds); }

    // compare 2 groups
    // iterate down _ds, stop when _ds[i] > that._ds[i], or _ds[i] < that._ds[i]
    // order by various columns specified by _orderByCols
    // NaN is treated as least
    @Override public int compareTo(G g) {
      for(int i:_orderByCols)
        if(      Double.isNaN(_ds[i])   || _ds[i] < g._ds[i] ) return -1;
        else if( Double.isNaN(g._ds[i]) || _ds[i] > g._ds[i] ) return 1;
      return 0;
    }

    public long     _N;         // number of rows in the group, updated atomically
    public long[]   _ND;        // count of distincts, built from the NBHS<Double>
    public long[]   _NA;        // count of NAs for each aggregate, updated atomically
    public long[]   _f;         // first row, updated atomically
    public long[]   _l;         // last row, atomically updated
    public double[] _min;       // updated atomically
    public double[] _max;       // updated atomically
    public double[] _sum;       // sum, updated atomically
    public double[] _ss;        // sum of squares, updated atomically
    public double[] _avs;       // means, computed in the close
    public double[] _vars;      // vars, computed in the close
    public double[] _sdevs;     // sds,  computed in the close
//    private NBHSAD _nd;         // count distinct helper data structure
    private byte[] _NAMethod;

    // offset crud for unsafe
    private static final Unsafe U = UtilUnsafe.getUnsafe();
    private static final long _NOffset;

    // long[] offset and scale
    private static final int _8B = U.arrayBaseOffset(long[].class);
    private static final int _8S = U.arrayIndexScale(long[].class);
    // double[] offset and scale
    private static final int _dB = U.arrayBaseOffset(double[].class);
    private static final int _dS = U.arrayIndexScale(double[].class);

    // get the raw indices for the long[] and double[]
    private static long longRawIdx(int i)   { return _8B + _8S * i; }
    private static long doubleRawIdx(int i) { return _dB + _dS * i; }

    static {
      try {
        _NOffset   = U.objectFieldOffset(G.class.getDeclaredField("_N"));
      } catch(Exception e) { throw H2O.fail(); }
    }

    G(int row, Chunk[] cs, long[] cols,int aggs, byte[] naMethod) {
      this(cols.length,aggs,naMethod);
      fill(row, cs, cols);
    }

    G(int len, int aggs, byte[] naMethod) {
      _ds=new double[len];
      _NAMethod=naMethod;
//      _nd=new NBHSAD(aggs);
      _ND=new long[aggs];
      _NA=new long[aggs];
      _f =new long[aggs];
      _l =new long[aggs];
      _min=new double[aggs];
      _max=new double[aggs];
      _sum=new double[aggs];
      _ss =new double[aggs];
      _avs=new double[aggs];
      _vars=new double[aggs];
      _sdevs=new double[aggs];
      for( int i=0; i<_min.length; ++i) _min[i]=Double.POSITIVE_INFINITY;
      for( int i=0; i<_max.length; ++i) _max[i]=Double.NEGATIVE_INFINITY;
    }

    G(int len) {_ds=new double[len];}
    G(){ _ds=null;}
    G(double[] ds) { _ds=ds; }

    private void close() {
      for( int i=0;i<_NAMethod.length;++i ) {
        long n = _NAMethod[i]==AGG.T_RM?_N-_NA[i]:_N;
        _avs[i] = _sum[i]/n;
//        _ND[i] = _nd._nd[i]==null?0:_nd._nd[i].size(); _nd._nd[i]=null; // b free!
        _vars[i] = (_ss[i] - (_sum[i]*_sum[i])/n)/n;
        _sdevs[i]=Math.sqrt(_vars[i]);
      }
    }

    protected static boolean CAS_N (G g, long o, long n          ) { return U.compareAndSwapLong(g,_NOffset,o,n); }
    private static boolean CAS_NA(G g, long off, long o, long n) { return U.compareAndSwapLong(g._NA,off,o,n);  }
    private static boolean CAS_f (G g, long off, long o, long n) { return U.compareAndSwapLong(g._f,off,o,n);   }
    private static boolean CAS_l (G g, long off, long o, long n) { return U.compareAndSwapLong(g._l,off,o,n);   }

    // doubles are toRawLongBits'ized, and passed as longs
    private static boolean CAS_min(G g, long off, long o, long n) { return U.compareAndSwapLong(g._min,off,o,n);}
    private static boolean CAS_max(G g, long off, long o, long n) { return U.compareAndSwapLong(g._max,off,o,n);}
    private static boolean CAS_sum(G g, long off, long o, long n) { return U.compareAndSwapLong(g._sum,off,o,n);}
    private static boolean CAS_ss (G g, long off, long o, long n) { return U.compareAndSwapLong(g._ss ,off,o,n);}
  }

  static class AGG extends AST {
    @Override AGG make() { return new AGG(); }
    // (AGG "agg" #col "na"  "agg" #col "na"   => string num string   string num string
    String opStr() { return "agg";  }
    private AGG[] _aggs;
    AGG parse_impl(Exec E) {
      ArrayList<AGG> aggs = new ArrayList<>();
      while( !E.isEnd() ) {
        String type = E.parseString(E.peekPlus());
        AST colast = E.parse();
        Integer col=null;
        AST delayedCol=null;
        String delayedColByName=null;
        if( colast instanceof ASTNum ) col = (int)((ASTNum)colast)._d;
        else if( colast instanceof ASTString ) delayedColByName = ((ASTString)colast)._s;
        else delayedCol = colast; // check for badness sometime later...
        String   na = E.parseString(E.peekPlus());
        String name = E.parseString(E.peekPlus());
        aggs.add(new AGG(type,col,na,name,delayedColByName,delayedCol));
      }
      _aggs = aggs.toArray(new AGG[aggs.size()]);
      E.eatEnd();
      return this;
    }

    // Aggregate types
    private static final byte T_N  = 0;
    private static final byte T_ND = 1;
    private static final byte T_F  = 2;
    private static final byte T_L  = 3;
    private static final byte T_MIN= 4;
    private static final byte T_MAX= 5;
    private static final byte T_AVG= 6;
    private static final byte T_SD = 7;
    private static final byte T_VAR= 8;
    private static final byte T_SUM= 9;
    private static final byte T_SS = 10;

    // How to handle NAs
    private static final byte T_ALL = 0;
    private static final byte T_IG  = 1;
    private static final byte T_RM  = 2;

    private static transient HashMap<String,Byte> TM = new HashMap<>();
    static{
      // aggregates
      TM.put("count",       (byte)0);
      TM.put("nrow",        (byte)0);
      TM.put("count_unique",(byte)1);
      TM.put("first",       (byte)2);
      TM.put("last",        (byte)3);
      TM.put("min",         (byte)4);
      TM.put("max",         (byte)5);
      TM.put("mean",        (byte)6);
      TM.put("avg",         (byte)6);
      TM.put("sd",          (byte)7);
      TM.put("stdev",       (byte)7);
      TM.put("var",         (byte)8);
      TM.put("sum",         (byte)9);
      TM.put("ss",          (byte)10);
      // na handling
      TM.put("all"         ,(byte)0);
      TM.put("ignore"      ,(byte)1);
      TM.put("rm"          ,(byte)2);
    }

    private final byte _type;
    private Integer _c;
    private final String _name;
    private final byte _na_handle;
    private AST _delayedCol;
    private String _delayedColByName;
    AGG() {_type=0;_c=-1;_name=null;_na_handle=0;}
    AGG(String s, Integer c, String na, String name, String delayedColByName, AST delayedCol) {  // big I Integer allows for nullness
      _type=TM.get(s.toLowerCase());
      _c=c;
      _delayedCol = delayedCol;
      _delayedColByName = delayedColByName;
      _name=(name==null || name.equals(""))?s+"_C"+(c+1):name;
      if( !TM.keySet().contains(na) ) {
        Log.info("Unknown NA handle type given: `" + na + "`. Switching to \"ignore\" method.");
        _na_handle=0;
      } else _na_handle = TM.get(na);
    }

    private static String[] names(AGG[] _agg) {
      String[] names = new String[_agg.length];
      for(int i=0;i<names.length;++i)
        names[i] = _agg[i]._name;
      return names;
    }

    private static byte[] naMethods(AGG[] agg) {

      byte[] methods = new byte[agg.length];
      for(int i=0;i<agg.length;++i)
        methods[i]=agg[i]._na_handle;
      return methods;
    }

    private boolean isIgnore() { return _na_handle == 0; }
    private boolean isRemove() { return _na_handle == 1; }
    private boolean isAll()    { return _na_handle == 2; }

    // satisfy the extends
    @Override void exec(Env e) { throw H2O.fail();}
    @Override String value() { return "agg"; }
    @Override int type() { return 0; }
  }
}