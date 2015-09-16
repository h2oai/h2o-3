package water.currents;

import sun.misc.Unsafe;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.nbhm.UtilUnsafe;
import water.util.ArrayUtils;
import water.util.MRUtils;

class ASTHist extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary", "breaks"}; }
  @Override int nargs() { return 1+2; } // (hist x breaks)
  @Override String str() { return "hist"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    // stack is [ ..., ary, breaks]
    // handle the breaks
    Frame fr2;
    Frame f = stk.track(asts[1].exec(env)).getFrame();
    if( f.numCols() != 1)  throw new IllegalArgumentException("Hist only applies to single numeric columns.");
    Vec vec = f.anyVec();
    if( !vec.isNumeric() ) throw new IllegalArgumentException("Hist only applies to single numeric columns.");

    AST a = asts[2];
    String algo=null;
    int numBreaks=-1;
    double[] breaks=null;

    if( a instanceof ASTStr ) algo=a.str().toLowerCase();
    else if( a instanceof ASTNumList ) breaks=((ASTNumList)a).expand();
    else if( a instanceof ASTNum ) numBreaks = (int)a.exec(env).getNum();

    HistTask t;
    double h;
    double x1=vec.max();
    double x0=vec.min();
    if( breaks != null ) t = new HistTask(breaks,-1,-1/*ignored if _h==-1*/).doAll(vec);
    else if( algo!=null ) {
      switch (algo) {
        case "sturges": numBreaks = sturges(vec); h=(x1-x0)/numBreaks; break;
        case "rice":    numBreaks = rice(vec);    h=(x1-x0)/numBreaks; break;
        case "sqrt":    numBreaks = sqrt(vec);    h=(x1-x0)/numBreaks; break;
        case "doane":   numBreaks = doane(vec);   h=(x1-x0)/numBreaks; break;
        case "scott":   h=scotts_h(vec); numBreaks = scott(vec,h);     break;  // special bin width computation
        case "fd":      h=fds_h(vec);    numBreaks = fd(vec, h);       break;  // special bin width computation
        default:        numBreaks = sturges(vec); h=(x1-x0)/numBreaks;         // just do sturges even if junk passed in
      }
      t = new HistTask(computeCuts(vec,numBreaks),h,x0).doAll(vec);
    }
    else {
      h = (x1-x0)/numBreaks;
      t = new HistTask(computeCuts(vec,numBreaks),h,x0).doAll(vec);
    }
    // wanna make a new frame here [breaks,counts,mids]
    final double[] brks=t._breaks;
    final long  [] cnts=t._counts;
    final double[] mids_true=t._mids;
    final double[] mids = new double[t._breaks.length-1];
    for(int i=1;i<brks.length;++i) mids[i-1] = .5*(t._breaks[i-1]+t._breaks[i]);
    Vec layoutVec = Vec.makeZero(brks.length);
    fr2 = new MRTask() {
      @Override public void map(Chunk[] c, NewChunk[] nc) {
        int start = (int)c[0].start();
        for(int i=0;i<c[0]._len;++i) {
          nc[0].addNum(brks[i+start]);
          if(i==0) {
            nc[1].addNA();
            nc[2].addNA();
            nc[3].addNA();
          } else {
            nc[1].addNum(cnts[(i-1)+start]);
            nc[2].addNum(mids_true[(i-1)+start]);
            nc[3].addNum(mids[(i-1)+start]);
          }
        }
      }
    }.doAll(4, layoutVec).outputFrame(null, new String[]{"breaks", "counts", "mids_true", "mids"},null);
    layoutVec.remove();
    return new ValFrame(fr2);
  }

  private static int sturges(Vec v) { return (int)Math.ceil( 1 + log2(v.length()) ); }
  private static int rice   (Vec v) { return (int)Math.ceil( 2*Math.pow(v.length(),1./3.)); }
  private static int sqrt   (Vec v) { return (int)Math.sqrt(v.length()); }
  private static int doane  (Vec v) { return (int)(1 + log2(v.length()) + log2(1+ (Math.abs(third_moment(v)) / sigma_g1(v))) );  }
  private static int scott  (Vec v, double h) { return (int)Math.ceil((v.max()-v.min()) / h); }
  private static int fd     (Vec v, double h) { return (int)Math.ceil((v.max() - v.min()) / h); }   // Freedman-Diaconis slightly modified to use MAD instead of IQR
  private static double fds_h(Vec v) { return 2*ASTMad.mad(new Frame(v), null, 1.4826)*Math.pow(v.length(),-1./3.); }
  private static double scotts_h(Vec v) { return 3.5*Math.sqrt(ASTVariance.getVar(v)) / (Math.pow(v.length(),1./3.)); }
  private static double log2(double numerator) { return (Math.log(numerator))/Math.log(2)+1e-10; }
  private static double sigma_g1(Vec v) { return Math.sqrt( (6*(v.length()-2)) / ((v.length()+1)*(v.length()+3)) ); }
  private static double third_moment(Vec v) {
    final double mean = v.mean();
    ThirdMomTask t = new ThirdMomTask(mean).doAll(v);
    double m2 = t._ss / v.length();
    double m3 = t._sc / v.length();
    return m3 / Math.pow(m2, 1.5);
  }

  private static class ThirdMomTask extends MRTask<ThirdMomTask> {
    double _ss;
    double _sc;
    final double _mean;
    ThirdMomTask(double mean) { _mean=mean; }
    @Override public void setupLocal() { _ss=0;_sc=0; }
    @Override public void map(Chunk c) {
      for( int i=0;i<c._len;++i ) {
        if( !c.isNA(i) ) {
          double d = c.atd(i) - _mean;
          double d2 = d*d;
          _ss+= d2;
          _sc+= d2*d;
        }
      }
    }
    @Override public void reduce(ThirdMomTask t) { _ss+=t._ss; _sc+=t._sc; }
  }

  private double[] computeCuts(Vec v, int numBreaks) {
    if( numBreaks <= 0 ) throw new IllegalArgumentException("breaks must be a positive number");
    // just make numBreaks cuts equidistant from each other spanning range of [v.min, v.max]
    double min;
    double w = ( v.max() - (min=v.min()) ) / numBreaks;
    double[] res= new double[numBreaks];
    for( int i=0;i<numBreaks;++i ) res[i] = min + w * (i+1);
    return res;
  }

  private static class HistTask extends MRTask<HistTask> {
    final private double _h;      // bin width
    final private double _x0;     // far left bin edge
    final private double[] _min;  // min for each bin, updated atomically
    final private double[] _max;  // max for each bin, updated atomically
    // unsafe crap for mins/maxs of bins
    private static final Unsafe U = UtilUnsafe.getUnsafe();
    // double[] offset and scale
    private static final int _dB = U.arrayBaseOffset(double[].class);
    private static final int _dS = U.arrayIndexScale(double[].class);
    private static long doubleRawIdx(int i) { return _dB + _dS * i; }
    // long[] offset and scale
    private static final int _8B = U.arrayBaseOffset(long[].class);
    private static final int _8S = U.arrayIndexScale(long[].class);
    private static long longRawIdx(int i)   { return _8B + _8S * i; }

    // out
    private final double[] _breaks;
    private final long  [] _counts;
    private final double[] _mids;

    HistTask(double[] cuts, double h, double x0) {
      _breaks=cuts;
      _min=new double[_breaks.length-1];
      _max=new double[_breaks.length-1];
      _counts=new long[_breaks.length-1];
      _mids=new double[_breaks.length-1];
      _h=h;
      _x0=x0;
    }
    @Override public void map(Chunk c) {
      // if _h==-1, then don't have fixed bin widths... must loop over bins to obtain the correct bin #
      for( int i = 0; i < c._len; ++i ) {
        int x=1;
        if( c.isNA(i) ) continue;
        double r = c.atd(i);
        if( _h==-1 ) {
          for(; x < _counts.length; x++)
            if( r <= _breaks[x] ) break;
          x--; // back into the bin where count should go
        } else
          x = Math.min( _counts.length-1, (int)Math.floor( (r-_x0) / _h ) );     // Pick the bin   floor( (x - x0) / h ) or ceil( (x-x0)/h - 1 ), choose the first since fewer ops
        bumpCount(x);
        setMinMax(Double.doubleToRawLongBits(r),x);
      }
    }
    @Override public void reduce(HistTask t) {
      if(_counts!=t._counts) ArrayUtils.add(_counts, t._counts);
      for(int i=0;i<_mids.length;++i) {
        _min[i] = t._min[i] < _min[i] ? t._min[i] : _min[i];
        _max[i] = t._max[i] > _max[i] ? t._max[i] : _max[i];
      }
    }
    @Override public void postGlobal() { for(int i=0;i<_mids.length;++i) _mids[i] = 0.5*(_max[i] + _min[i]); }

    private void bumpCount(int x) {
      long o = _counts[x];
      while(!U.compareAndSwapLong(_counts,longRawIdx(x),o,o+1))
        o=_counts[x];
    }
    private void setMinMax(long v, int x) {
      double o = _min[x];
      double vv = Double.longBitsToDouble(v);
      while( vv < o && U.compareAndSwapLong(_min,doubleRawIdx(x),Double.doubleToRawLongBits(o),v))
        o = _min[x];
      setMax(v,x);
    }
    private void setMax(long v, int x) {
      double o = _max[x];
      double vv = Double.longBitsToDouble(v);
      while( vv > o && U.compareAndSwapLong(_min,doubleRawIdx(x),Double.doubleToRawLongBits(o),v))
        o = _max[x];
    }
  }
}

// Find the mode: the most popular element.  Here because you need to histogram the data.
class ASTMode extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary"}; }
  @Override String str() { return "mode"; }
  @Override int nargs() { return 1+1; } // (mode ary)
  @Override ValNum apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if( fr.numCols() != 1 || !fr.anyVec().isEnum() )
      throw new IllegalArgumentException("mean only works on a single categorical column");
    return new ValNum(mode(fr.anyVec()));
  }
  static int mode(Vec v) {
    if( v.isNumeric() ) {
      MRUtils.Dist t = new MRUtils.Dist().doAll(v);
      int mode = ArrayUtils.maxIndex(t.dist());
      return (int)t.keys()[mode];
    }
    double[] dist = new MRUtils.ClassDist(v).doAll(v).dist();
    return ArrayUtils.maxIndex(dist);
  }
}
