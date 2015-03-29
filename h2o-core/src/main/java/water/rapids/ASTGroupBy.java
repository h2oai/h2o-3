package water.rapids;


import sun.misc.Unsafe;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.nbhm.NonBlockingHashSet;
import water.nbhm.UtilUnsafe;
import water.util.ArrayUtils;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * GROUPBY: Single pass aggregation by columns.
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
  // AST: (GB fr {cols} AGGS)
  //      (GB %k {#1;#3} (AGGS #2 "min" #4 "mean" #6))
  private long[] _gbCols; // group by columns
  private AGG[] _agg;
  ASTGroupBy() { super(null); }
  @Override String opStr() { return "GB"; }
  @Override ASTOp make() {return new ASTGroupBy();}
  ASTGroupBy parse_impl(Exec E) {
    AST ary = E.parse();
    if( ary instanceof ASTId ) ary = Env.staticLookup((ASTId)ary);

    // parse gby columns
    AST s=null;
    try {
      s=E.skipWS().parse();
      _gbCols=((ASTSeries)s).toArray();
      if(_gbCols.length > 1000 )
        throw new IllegalArgumentException("Too many columns selected. Please select < 1000 columns.");
    } catch (ClassCastException e) {
      assert s!=null;
      try {
        _gbCols = new long[]{(long)((ASTNum)s).dbl()};
      } catch (ClassCastException e2) {
        throw new IllegalArgumentException("Badly formed AST. Columns argument must be a ASTSeries or ASTNum");
      }
    }

    //parse AGGs
    _agg = ((AGG)E.parse())._aggs;

    ASTGroupBy res = (ASTGroupBy)clone();
    res._asts = new AST[]{ary};
    return res;
  }
  @Override void apply(Env e) {
    // only allow reductions on time and numeric columns

    Frame fr = e.popAry();

    GBTask p1 = new GBTask(_gbCols, _agg).doAll(fr);

    // build the output
    final int nGrps = p1._g.size();
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
    System.arraycopy(AGG.names(_agg),0,names, _gbCols.length,_agg.length);

    final G[] grps = p1._g.toArray(new G[nGrps]);
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
              case AGG.T_N:  ncs[j++].addNum(g._N);     break;
              case AGG.T_ND: ncs[j++].addNum(g._ND[a]); break;
              case AGG.T_F:  ncs[j++].addNum(g._f[a]);  break;
              case AGG.T_L:  ncs[j++].addNum(g._l[a]);  break;
              case AGG.T_MIN:ncs[j++].addNum(g._min[a]); break;
              case AGG.T_MAX:ncs[j++].addNum(g._max[a]); break;
              case AGG.T_AVG:ncs[j++].addNum(g._avs[a]); break;
              case AGG.T_SUM:ncs[j++].addNum(g._sum[a]); break;
              case AGG.T_SS :ncs[j++].addNum(g._ss [a]); break;
              default:
                throw new IllegalArgumentException("Unsupported aggregation type: " + type);
            }
          }
        }
      }
    }.doAll(nCols,v).outputFrame(Key.make(),names,domains);
    Keyed.remove(v._key);
    e.pushAry(f);
  }


  private static class GBTask extends MRTask<GBTask> {
    NonBlockingHashSet<G> _g;
    private long[] _gbCols;
    private AGG[] _agg;
    GBTask(long[] gbCols, AGG[] agg) { _gbCols=gbCols; _agg=agg; }
    @Override public void setupLocal() { _g = new NonBlockingHashSet<>(); }
    @Override public void map(Chunk[] c) {
      long start = c[0].start();
      for (int i=0;i<c[0]._len;++i) {
        G g = new G(i,c,_gbCols,_agg.length);
        if( !_g.add(g) ) g=_g.get(g);
        // cas in COUNT
        long r=g._N;
        while(!G.CAS_N(g, r, r + 1))
          r=g._N;
        perRow(_agg,i,start,c,g);
      }
    }
    @Override public void reduce(GBTask t) {
      if(_g!=t._g) {
        NonBlockingHashSet<G> l = _g;
        NonBlockingHashSet<G> r = t._g;
        if( l.size() < r.size() ) { l=r; r=_g; }  // larger on the left

        // loop over the smaller set of grps
        for( G rg:r ) {
          G lg = l.get(rg);
          if( lg == null) l.add(rg); // not in left, so put it and continue...
          else {                     // found the group, CAS in the row counts
            // cas in COUNT
            long R=lg._N;
            while(!G.CAS_N(lg, R, R + rg._N))
              R=lg._N;
            reduceGroup(_agg, lg, rg);
          }
        }
      }
    }
    @Override public void postGlobal() { H2O.submitTask(new ParallelPostGlobal(_g.toArray(new G[_g.size()]))).join(); }

    private static void perRow(AGG[] agg, int chkRow, long rowOffset, Chunk[] c, G g) { perRow(agg,chkRow,rowOffset,c,g,null); }
    private static void reduceGroup(AGG[] agg, G g, G that) { perRow(agg,-1,-1,null,g,that);}
    private static void perRow(AGG[] agg, int chkRow, long rowOffset, Chunk[] c, G g, G that) {
      byte type; int col;
      for (int i=0;i<agg.length;++i) {
        if( (type=agg[i]._type) == AGG.T_N ) continue; //immediate short circuit if COUNT
        col = agg[i]._c;

        // build up a long[] of vals, to handle the case when c is and isn't null.
        // c is null in the reduce  of the MRTask
        long[] vals = new long[6]; // 6 cases in the switch.
        long bits=-1;
        if( c!=null ) bits = Double.doubleToRawLongBits(c[col].atd(chkRow));
        vals[0] = c==null ? that._f[col] : chkRow+rowOffset;
        vals[1] = c==null ? that._l[col] : chkRow+rowOffset;
        vals[2] = c==null ? Double.doubleToRawLongBits(that._min[i]) : bits;
        vals[3] = c==null ? Double.doubleToRawLongBits(that._max[i]) : bits;
        vals[4] = c==null ? Double.doubleToRawLongBits(that._sum[i]) : bits;
        vals[5] = c==null ? Double.doubleToRawLongBits(that._ss[i])  : bits;
        if( type == AGG.T_ND ) {
          if( c==null ) g._nd[i].addAll(that._nd[i]);
          else          g._nd[i].add(c[col].atd(chkRow));
          continue;
        }

        switch (type) {
          case AGG.T_F:   setFirst(g,vals[0],i);   break;
          case AGG.T_L:   setLast( g,vals[1],i);   break;
          case AGG.T_MIN: setMin(  g,vals[2],i);   break;
          case AGG.T_MAX: setMax(  g,vals[3],i);   break;
          case AGG.T_AVG: /* fall through */
          case AGG.T_SUM: setSum(  g,vals[4],i);   break;
          case AGG.T_VAR: /* fall through */
          case AGG.T_SD:
          case AGG.T_SS:  setSS(   g,vals[5],i);   break;
          default:
            throw new IllegalArgumentException("Unsupported aggregation type: " + type);
        }
      }
    }

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
    private static void setSS(G g, long vv, int c) {
      double v = Double.longBitsToDouble(vv);
      double o = g._ss[c];
      while(!G.CAS_ss(g, G.doubleRawIdx(c), Double.doubleToRawLongBits(o), Double.doubleToRawLongBits(o + v * v)))
        o=g._ss[c];
    }

    @Override public AutoBuffer write_impl( AutoBuffer ab ) {
      ab.putA8(_gbCols);
      if( _g == null ) return ab.put4(0);
      ab.put4(_g.size());
      for( G g: _g) ab.put(g);
      return ab;
    }

    @Override public GBTask read_impl(AutoBuffer ab) {
      _gbCols = ab.getA8();
      int len = ab.get4();
      if( len == 0 ) return this;
      _g = new NonBlockingHashSet<>();
      for( int i=0;i<len;++i) _g.add(ab.get(G.class));
      return this;
    }
  }

  private static class GTask extends H2O.H2OCountedCompleter<GTask> {
    private final G _g;
    GTask(H2O.H2OCountedCompleter cc, G g) { super(cc); _g=g; }
    @Override protected void compute2() {
      _g.close();
      tryComplete();
    }
  }

  private static class ParallelPostGlobal extends H2O.H2OCountedCompleter<ParallelPostGlobal> {
    private final G[] _g;
    private final int _maxP=50*1000; // burn 50K at a time
    private final AtomicInteger _ctr;
    ParallelPostGlobal(G[] g) { _g=g; _ctr=new AtomicInteger(_maxP-1); }


    @Override protected void compute2(){
      addToPendingCount(_g.length-1);
      for( int i=0;i<Math.min(_g.length,_maxP);++i) frkTsk(i);
    }

    private void frkTsk(final int i) { new GTask(new Callback(), _g[i]).fork(); }

    private class Callback extends H2O.H2OCallback {
      public Callback(){super(ParallelPostGlobal.this);}
      @Override public void callback(H2O.H2OCountedCompleter cc) {
        int i = _ctr.incrementAndGet();
        if( i < _g.length )
          frkTsk(i);
      }
    }
  }

  private static class G extends ASTddply.Group {
    public long     _N;         // number of rows in the group, updated atomically
    public long[]   _ND;        // count of distincts, built from the NBHS<Double>
    public long[]   _f;         // first row, updated atomically
    public long[]   _l;         // last row, atomically updated
    public double[] _min;       // updated atomically
    public double[] _max;       // updated atomically
    public double[] _sum;       // sum, updated atomically
    public double[] _ss;        // sum of squares, updated atomically
    public double[] _avs;       // means, computed in the close
    public double[] _vars;      // vars, computed in the close
    public double[] _sdevs;     // sds,  computed in the close
    private NonBlockingHashSet<Double> _nd[]; // count distinct helper data structure

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

    G(int row, Chunk[] cs, long[] cols,int aggs) {
      super(cols.length);
      this.fill(row,cs,cols);
      _nd=new NonBlockingHashSet[aggs];
      _ND=new long[aggs];
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

    private void close() {
      _avs = ArrayUtils.div(_sum.clone(),_N);
      for( int i=0;i<_vars.length;++i) {
        _ND[i] = _nd[i]==null?-1:_nd[i].size(); _nd[i]=null; // b free!
        _vars[i] = (_ss[i] - (_sum[i]*_sum[i])/_N)/_N;
        _sdevs[i]=Math.sqrt(_vars[i]);
      }


    }
    private static boolean CAS_N (G g, long o, long n          ) { return U.compareAndSwapLong(g,_NOffset,o,n); }
    private static void    put_ND(G g, int c, double d         ) { g._nd[c].add(d);                             }
    private static boolean CAS_f (G g, long off, long o, long n) { return U.compareAndSwapLong(g._f,off,o,n);   }
    private static boolean CAS_l (G g, long off, long o, long n) { return U.compareAndSwapLong(g._l,off,o,n);   }

    // doubles are toRawLongBits'ized, and passed as longs
    private static boolean CAS_min(G g, long off, long o, long n) { return U.compareAndSwapLong(g._min,off,o,n);}
    private static boolean CAS_max(G g, long off, long o, long n) { return U.compareAndSwapLong(g._max,off,o,n);}
    private static boolean CAS_sum(G g, long off, long o, long n) { return U.compareAndSwapLong(g._sum,off,o,n);}
    private static boolean CAS_ss (G g, long off, long o, long n) { return U.compareAndSwapLong(g._ss ,off,o,n);}

    @Override public AutoBuffer write_impl(AutoBuffer ab) {
      ab.put8(_N);     ab.putA8(_ND);
      ab.putA8(_f);    ab.putA8(_l);
      ab.putA8d(_min); ab.putA8d(_max);
      ab.putA8d(_sum); ab.putA8d(_ss);
      int len=_nd.length;
      ab.put4(len);
      for (NonBlockingHashSet<Double> a_nd : _nd) {
        int s = a_nd.size();
        ab.put4(s);
        for (double d : a_nd) ab.put8d(d);
      }
      return ab;
    }
    @Override public G read_impl(AutoBuffer ab) {
      _N   = ab.get8();   _ND  = ab.getA8();
      _f   = ab.getA8();  _l   = ab.getA8();
      _min = ab.getA8d(); _max = ab.getA8d();
      _sum = ab.getA8d(); _ss  = ab.getA8d();
      int len = ab.get4();
      _nd=new NonBlockingHashSet[len];
      for(int i=0;i<len;++i) {
        _nd[i] = new NonBlockingHashSet<>();
        int s = ab.get4();
        for(int j=0;j<s;++j) _nd[i].add(ab.get8d());
      }
      return this;
    }
  }

  static class AGG extends AST {
    // (AGG #N "agg" #col  "agg" #col   => string num string num
    private AGG[] _aggs;
    AGG parse_impl(Exec E) {
      int n = (int)((ASTNum)(E.parse()))._d; E.skipWS();
      _aggs=new AGG[n];
      for( int i=0;i<n;++i) {
        String type = E.parseString(E.peekPlus()); E.skipWS();
        int     col = (int)((ASTNum)E.parse()).dbl();
        _aggs[i]=new AGG(type,col);
      }
      return this;
    }

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

    private static transient HashMap<String,Byte> TM = new HashMap<>();
    private static transient HashMap<Byte,String> TM2 =new HashMap<>();
    static{
      TM.put("count",       (byte)0);  TM2.put((byte)0,"count");
      TM.put("count_unique",(byte)1);  TM2.put((byte)1,"count_unique");
      TM.put("first",       (byte)2);  TM2.put((byte)2,"first");
      TM.put("last",        (byte)3);  TM2.put((byte)3,"last");
      TM.put("min",         (byte)4);  TM2.put((byte)4,"min");
      TM.put("max",         (byte)5);  TM2.put((byte)5,"max");
      TM.put("mean",        (byte)6);  TM2.put((byte)6,"mean");
      TM.put("avg",         (byte)6);  /*TM2.put((byte)6,"avg");*/
      TM.put("sd",          (byte)7);  TM2.put((byte)7,"sd");
      TM.put("stdev",       (byte)7);  /*TM2.put((byte)7,"stdev");*/
      TM.put("var",         (byte)8);  TM2.put((byte)8,"var");
      TM.put("sum",         (byte)9);  TM2.put((byte)9,"sum");
      TM.put("ss",          (byte)10); TM2.put((byte)10,"ss");
    }

    private final byte _type;
    private final int _c;
    private final String _name;
    AGG() {_type=0;_c=-1;_name=null;}
    AGG(String s,  int c) { _type=TM.get(s.toLowerCase()); _c=c; _name=s+"_C"+(c+1); }

    private static String[] names(AGG[] _agg) {
      String[] names = new String[_agg.length];
      for(int i=0;i<names.length;++i)
        names[i] = _agg[i]._name;
      return names;
    }


    // satisfy the extends
    @Override void exec(Env e) { throw H2O.fail();}
    @Override String value() { return "agg"; }
    @Override int type() { return 0; }
  }
}