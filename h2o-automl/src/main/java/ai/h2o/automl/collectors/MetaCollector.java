package ai.h2o.automl.collectors;


import ai.h2o.automl.ColMeta;
import water.H2O;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.ArrayUtils;

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


  public static class ParallelTasks<T extends MRTask<T>> extends H2O.H2OCountedCompleter {

    // IN
    private final AtomicInteger _ctr; // Concurrency control
    private static int MAXP = 100;    // Max number of concurrent columns
    private final Frame _fr;          // Frame to compute all the metadata on
    private final T[] _tasks;         // task holder

    public ParallelTasks(Frame fr, T[] tasks) {
      _fr = fr;
      _ctr = new AtomicInteger(MAXP-1);
      _tasks = tasks;
    }

    @Override protected void compute2() {
      final int ncols = _fr.numCols();
      addToPendingCount(ncols-1);
      for (int i=0; i < Math.min(MAXP, ncols); ++i) asyncVecTask(i);
    }

    // Compute ColMeta for each column
    private void asyncVecTask(final int colnum) {
      _tasks[colnum].setCompleter(new Callback());
      _tasks[colnum].asyncExec(_fr.vec(colnum));
    }

    private class Callback extends H2O.H2OCallback {
      public Callback(){super(ParallelTasks.this);}
      @Override public void callback(H2O.H2OCountedCompleter cc) {
        int i = _ctr.incrementAndGet();
        if( i < _fr.numCols() )
          asyncVecTask(i);
      }
    }
  }

  public static class ColMetaTaskPass1 extends MRTask<ColMetaTaskPass1> {

    // IN
    private final boolean _response;

    // OUT
    public final ColMeta _colMeta;

    public ColMetaTaskPass1(boolean response, String colname, int idx) {
      _colMeta = new ColMeta(_fr.anyVec(), colname, idx, _response=response);
    }

    @Override public void map( Chunk c ) {  // TODO: roll chunk type specific methods

    }
  }
}