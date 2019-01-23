package ai.h2o.automl.collectors;


import hex.tree.DHistogram;
import hex.tree.SharedTreeModel;
import jsr166y.CountedCompleter;
import water.H2O;
import water.MRTask;
import water.MemoryManager;
import water.fvec.Chunk;
import water.util.ArrayUtils;
import water.util.AtomicUtils;
import water.util.Log;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collect metadata over a single column.
 */
public class MetaCollector {
  enum COLLECT {
    skew() {
      @Override void op( double[] d0s, double d1 ) { d0s[0]++; }
      @Override void atomic_op( double[] d0s, double[] d1s ) { d0s[0] += d1s[0]; }
      @Override double postPass( double ds[], long n ) { return ds[0]; }
    },
    kurtosis() {
      @Override void op( double[] d0s, double d1 ) { d0s[0]+=d1; }
      @Override void atomic_op( double[] d0s, double[] d1s ) { d0s[0] += d1s[0]; }
      @Override double postPass( double ds[], long n ) { return ds[0]/n; }
    },
    uniqPerChk() {
      @Override void op( double[] d0s, double d1 ) { d0s[0]+=d1; }
      @Override void atomic_op( double[] d0s, double[] d1s ) { d0s[0] += d1s[0]; }
      @Override double postPass( double ds[], long n ) { return ds[0]; }
    },
    timePerChunk() {
      @Override void op( double[] d0s, double d1 ) { d0s[0]+=d1*d1; }
      @Override void atomic_op( double[] d0s, double[] d1s ) { d0s[0] += d1s[0]; }
      @Override double postPass( double ds[], long n) { return ds[0]; }
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
  // TODO: add hiddenNAFinder https://0xdata.atlassian.net/browse/STEAM-76


  public static class ParallelTasks<T extends H2O.H2OCountedCompleter<T>> extends H2O.H2OCountedCompleter {
    private final AtomicInteger _ctr; // Concurrency control
    private static int MAXP = 100;    // Max number of concurrent columns
    private final T[] _tasks;         // task holder (will be 1 per column)

    public ParallelTasks(T[] tasks) {
      _ctr = new AtomicInteger(MAXP-1);
      _tasks = tasks;
    }

    @Override public void compute2() {
      final int nTasks = _tasks.length;
      addToPendingCount(nTasks-1);
      for (int i=0; i < Math.min(MAXP, nTasks); ++i) asyncVecTask(i);
    }

    private void asyncVecTask(final int task) {
      _tasks[task].setCompleter(new Callback());
      _tasks[task].fork();
    }

    private class Callback extends H2O.H2OCallback{
      public Callback(){super(ParallelTasks.this);}
      @Override public void callback(H2O.H2OCountedCompleter cc) {
          int i = _ctr.incrementAndGet();
          if (i < _tasks.length)
            asyncVecTask(i);
      }

      @Override
      public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
        ex.printStackTrace();
        return super.onExceptionalCompletion(ex, caller);
      }
    }
  }

  /**
   * A wrapper class around DHistogram.
   *
   * NB: _sums and _ssqs are not the same as those found in DHistogram instances.
   *     The difference being that these are compounded over the column data, rather than
   *     over the target column.
   */
  public final static class DynamicHisto extends MRTask<DynamicHisto> {
    public DHistogram _h;
    public double[] _sums; // different from _h._sums
    public double[] _ssqs; // different from _h._ssqs
    public DynamicHisto(DHistogram h) { _h=h; }
    DynamicHisto(String name, final int nbins, int nbins_cats, byte isInt,
                        double min, double max) {
      if(!(Double.isNaN(min)) && !(Double.isNaN(max))) { //If both are NaN then we don't need a histogram
        _h = makeDHistogram(name, nbins, nbins_cats, isInt, min, max);
      }else{
        Log.info("Ignoring all NaN column -> "+ name);
      }
    }

    private static class SharedTreeParameters extends SharedTreeModel.SharedTreeParameters {
      public String algoName() { return "DUM"; }
      public String fullName() { return "dummy"; }
      public String javaName() { return "this.is.unused"; }
    }
    public static DHistogram makeDHistogram(String name, int nbins, int nbins_cats, byte isInt,
                                  double min, double max) {
      final double minIn = Math.max(min,-Double.MAX_VALUE);   // inclusive vector min
      final double maxIn = Math.min(max, Double.MAX_VALUE);   // inclusive vector max
      final double maxEx = DHistogram.find_maxEx(maxIn,isInt==1?1:0); // smallest exclusive max

      SharedTreeModel.SharedTreeParameters parms = new SharedTreeParameters();
      // make(String name, final int nbins, byte isInt, double min, double maxEx, long seed, SharedTreeModel.SharedTreeParameters parms, Key globalQuantilesKey) {
      parms._nbins = nbins;
      parms._nbins_cats = nbins_cats;

      return DHistogram.make(name, nbins, isInt, minIn, maxEx, 0, parms, null, null);
    }
    public double binAt(int b) { return _h.binAt(b); }

    // TODO: move into DHistogram
    public double mean(int b ) {
      double n = _h.w(b);
      return n>0 ? _sums[b]/n : _h.binAt(b);
    }

    // TODO: move into DHistogram
    public double var (int b) { // sample variance
      double n = _h.w(b);
      if( n<=1 ) return 0;
      return Math.max(0, (_ssqs[b] - _sums[b]*_sums[b]/n)/(n-1));
    }

    protected void init() {
      _h.init();
      _sums = MemoryManager.malloc8d(_h._nbin);
      _ssqs = MemoryManager.malloc8d(_h._nbin);
    }
    @Override public void setupLocal() { init(); }
    @Override public void map(Chunk c) { accum(c); }
    @Override public void reduce(DynamicHisto ht) {
      merge(ht._h);
      if( _sums!=ht._sums ) ArrayUtils.add(_sums, ht._sums);
      if( _ssqs!=ht._ssqs ) ArrayUtils.add(_ssqs, ht._ssqs);
    }

    void accum(Chunk C) {
      double min = _h.find_min();
      double max = _h.find_maxIn();
      double[] bins = new double[_h._nbin];
      double[] sums = new double[_h._nbin];
      double[] ssqs = new double[_h._nbin];

      for(int r=0; r<C._len; ++r) {
        double colData = C.atd(r);
        if( colData < min ) min = colData;
        if( colData > max ) max = colData;
        int b = _h.bin(colData);
        bins[b] += 1;
        sums[b] += colData;
        ssqs[b] += colData*colData;
      }
      _h.setMin(min); _h.setMaxIn(max);
      for(int b=0; b<bins.length; ++b)
        if( bins[b]!=0 ) {
          _h.addWAtomic(b, bins[b]);
          AtomicUtils.DoubleArray.add(_sums, b, sums[b]);
          AtomicUtils.DoubleArray.add(_ssqs, b, ssqs[b]);
        }
    }

    public void merge(DHistogram h) {
      if( _h==h ) return;
      if( _h==null ) _h=h;
      else if( h!=null )
        _h.add(h);
    }
  }
}
