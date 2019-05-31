package hex.tree;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.util.*;

import java.util.Arrays;
import java.util.Random;

/**
 * PUBDEV-451: Prove that histogram addition of float-casted doubles leads to reproducible AND accurate histogram counts
 */
public class HistogramTest extends TestUtil {
  final static int BUCKETS = 100;      //how many histogram buckets
  final static int THREADS = 100;      //how many threads
  final static int THREAD_LOOPS = 100; //how much work per thread

  @BeforeClass
  public static void stall() { stall_till_cloudsize(1); }

  @Test
  public void run() {
    Futures fs = new Futures();
    long seed = 0xDECAF;
    Log.info("Histogram size: " + BUCKETS);
    Log.info("Threads: " + THREADS);
    Log.info("Loops per Thread: " + THREAD_LOOPS);

    // Run 1
    Histo hist = new Histo(BUCKETS);
    for (int i=0; i<THREADS; ++i)
      fs.add(H2O.submitTask(new Filler(hist, seed+i)));
    fs.blockForPending();

    // Run 2
    Histo hist2 = new Histo(BUCKETS);
    for (int i=0; i<THREADS; ++i)
      fs.add(H2O.submitTask(new Filler(hist2, seed+i)));
    fs.blockForPending();

    // Check that only the float-casted histograms are reproducible
    double maxRelErrorDD = 0;
    for (int i = 0; i < hist._sumsD.length; ++i) {
      maxRelErrorDD = Math.max( Math.abs(hist._sumsD[i] - hist2._sumsD[i]) / Math.abs(hist._sumsD[i]), maxRelErrorDD);
    }
    Log.info("Max rel. error between D and D: " + maxRelErrorDD);
    assert(!Arrays.equals(hist._sumsD, hist2._sumsD)); //FP noise leads to indeterminism (max error > double epsilon)

    double maxRelErrorFF = 0;
    for (int i = 0; i < hist._sumsF.length; ++i) {
      maxRelErrorFF = Math.max( Math.abs(hist._sumsF[i] - hist2._sumsF[i]) / Math.abs(hist._sumsF[i]), maxRelErrorFF);
    }
    Log.info("Max rel. error between F and F: " + maxRelErrorFF);
    assert(maxRelErrorDD > maxRelErrorFF);

    // Check that we don't lose accuracy by doing the float-casting
    double maxRelErrorDF = 0;
    for (Histo h : new Histo[]{hist, hist2}) {
      for (int i = 0; i < h._sumsD.length; ++i) {
        maxRelErrorDF = Math.max( Math.abs(h._sumsD[i] - h._sumsF[i]) / Math.abs(h._sumsD[i]), maxRelErrorDF);
      }
    }
    Log.info("Max rel. error between D and F: " + maxRelErrorDF);
    assert(maxRelErrorDF < 1e-6);
  }

  /**
   * Helper class to fill two histograms in the same way as DHistogram
   */
  private class Histo {
    Histo(int len) {
      _sumsD = new double[len];
      _sumsF = new double[len];
    }
    public double _sumsD[];
    public double _sumsF[];
    public void incrDouble(int b, double y) {
      AtomicUtils.DoubleArray.add(_sumsD,b,y);
    }
    public void incrFloat(int b, double y) {
      AtomicUtils.DoubleArray.add(_sumsF,b,(float)y);
    }
  }

  /**
   * Each thread adds a deterministic set of numbers to the histograms owned by histo, but in a race with other threads
   */
  static public class Filler extends H2O.H2OCountedCompleter<Filler> {
    private final long _seed;
    private final Histo _histo;
    Filler(Histo histo, long seed) { _seed = seed; _histo = histo; }

    @Override
    public void compute2() {
      Random rng = new Random(_seed);
      // make sure there's enough work for each thread (and hence enough race conditions)
      for (int loop=0; loop<THREAD_LOOPS; ++loop) {
        // add to every bucket in the histogram
        for (int b = 0; b < _histo._sumsD.length; ++b) {
          double val = rng.nextDouble();
          _histo.incrDouble(b, val);
          _histo.incrFloat(b, val);
        }
      }
      tryComplete();
    }
  }

  @Test public void testSplits() {
    int nbins = 13;
    int nbins_cats = nbins;
    byte isInt = 0;
    double min = 1;
    double maxEx = 6.900000000000001;
    for (SharedTreeModel.SharedTreeParameters.HistogramType histoType : SharedTreeModel.SharedTreeParameters.HistogramType.values()) {
      Log.info();
      Log.info("random split points: " + histoType);
      long seed = new Random().nextLong();
      if (histoType== SharedTreeModel.SharedTreeParameters.HistogramType.Random)
        Log.info("random seed: " + seed);
      double[] splitPts = null;
      if (histoType == SharedTreeModel.SharedTreeParameters.HistogramType.QuantilesGlobal) {
        splitPts = new double[]{1,1.5,2,2.5,3,4,5,6.1,6.2,6.3,6.7,6.8,6.85};
      }
      Key k = Key.make();
      DKV.put(new DHistogram.HistoQuantiles(k,splitPts));
      DHistogram hist = new DHistogram("myhisto",nbins,nbins_cats,isInt,min,maxEx,0,histoType,seed,k,null);
      hist.init();
      int N=10000000;
      int bin=-1;
      double[] l1 = new double[nbins];
      for (int i=0;i<N;++i) {
        double col_data = min + (double)i/N*(maxEx-min);
        int b = hist.bin(col_data);
        if (b>bin) {
          bin=b;
          Log.info("Histogram maps " + col_data + " to bin  : " + hist.bin(col_data));
          l1[b] = col_data;
        }
      }
      double[] l2 = new double[nbins];
      for (int i=0;i<nbins;++i) {
        double col_data = hist.binAt(i);
        Log.info("Histogram maps bin " + i + " to col_data: " + col_data);
        l2[i] = col_data;
      }

      for (int i=0;i<nbins;++i) {
        Assert.assertTrue(Math.abs(l1[i]-l2[i]) < 1e-6);
      }
      k.remove();
    }
  }
  @Test public void testUniformAdaptiveRange() {
    int nbins = 13;
    int nbins_cats = nbins;
    byte isInt = 0;
    double min = 1;
    double maxEx = 6.900000000000001;
    long seed = 1234;
    SharedTreeModel.SharedTreeParameters.HistogramType histoType = SharedTreeModel.SharedTreeParameters.HistogramType.UniformAdaptive;
    DHistogram hist = new DHistogram("myhisto", nbins, nbins_cats, isInt, min, maxEx, 0, histoType, seed, null, null);
    hist.init();
    assert(hist.binAt(0)==min);
    assert(hist.binAt(nbins-1)<maxEx);
    assert(hist.bin(min) == 0);
    assert(hist.bin(maxEx-1e-15) == nbins-1);
  }

  @Test public void testRandomRange() {
    int nbins = 13;
    int nbins_cats = nbins;
    byte isInt = 0;
    double min = 1;
    double maxEx = 6.900000000000001;
    long seed = 1234;
    SharedTreeModel.SharedTreeParameters.HistogramType histoType = SharedTreeModel.SharedTreeParameters.HistogramType.Random;
    DHistogram hist = new DHistogram("myhisto", nbins, nbins_cats, isInt, min, maxEx, 0, histoType, seed, null, null);
    hist.init();
    assert(hist.binAt(0)==min);
    assert(hist.binAt(nbins-1)<maxEx);
    assert(hist.bin(min) == 0);
    assert(hist.bin(maxEx-1e-15) == nbins-1);
  }

  @Test public void testQuantilesRange() {
    int nbins = 13;
    int nbins_cats = nbins;
    byte isInt = 0;
    double min = 1;
    double maxEx = 6.900000000000001;
    long seed = 1234;
    SharedTreeModel.SharedTreeParameters.HistogramType histoType = SharedTreeModel.SharedTreeParameters.HistogramType.QuantilesGlobal;
    double[] splitPts = new double[]{1,1.5,2,2.5,3,4,5,6.1,6.2,6.3,6.7,6.8,6.85};
    Key k = Key.make();
    DKV.put(new DHistogram.HistoQuantiles(k,splitPts));
    DHistogram hist = new DHistogram("myhisto",nbins,nbins_cats,isInt,min,maxEx,0,histoType,seed,k,null);
    hist.init();
    assert(hist.binAt(0)==min);
    assert(hist.binAt(nbins-1)<maxEx);
    assert(hist.bin(min) == 0);
    assert(hist.bin(maxEx-1e-15) == nbins-1);
    k.remove();
  }

  @Test public void testShrinking() {
    double[] before = new double[]{0.2,0.28,0.31,0.32,0.32,0.4,0.7,0.81,0.84};
    double[] after = ArrayUtils.makeUniqueAndLimitToRange(before, 0.3,0.8);
    assert(Arrays.equals(after, new double[]{0.3,0.31,0.32,0.4,0.7}));
  }
  @Test public void testShrinking2() {
    double[] before = new double[]{-0.3,0.2,0.28,0.28,0.3,0.3,0.31,0.32,0.32,0.4,0.7,0.7,0.8,0.8,0.81,0.84};
    double[] after = ArrayUtils.makeUniqueAndLimitToRange(before, 0.3,0.8);
    assert(Arrays.equals(after, new double[]{0.3,0.31,0.32,0.4,0.7}));
  }
  @Test public void testShrinking3() {
    double[] before = new double[]{-0.3,0.2,0.28,0.28,0.3,0.3,0.31,0.32,0.32,0.4,0.7,0.7,0.8,0.8,0.81,0.84};
    double[] after = ArrayUtils.makeUniqueAndLimitToRange(before, 0.3,0.9);
    assert(Arrays.equals(after, new double[]{0.3,0.31,0.32,0.4,0.7,0.8,0.81,0.84}));
  }
  @Test public void testShrinking4() {
    double[] before = new double[]{0.31,0.32,0.32,0.4,0.7,0.7};
    double[] after = ArrayUtils.makeUniqueAndLimitToRange(before, 0.3,0.9);
    assert(Arrays.equals(after, new double[]{0.3,0.31,0.32,0.4,0.7}));
  }
  @Test public void testShrinking5() {
    double[] before = new double[]{0.3,0.31,0.32,0.4,0.7};
    double[] after = ArrayUtils.limitToRange(before,0.31,0.9);
    assert(Arrays.equals(after, new double[]{0.31,0.32,0.4,0.7}));
  }
  @Test public void testShrinking6() {
    double[] before = new double[]{0.3,0.31,0.32,0.4,0.7};
    double[] after = ArrayUtils.limitToRange(before,0.305,0.9);
    assert(Arrays.equals(after, new double[]{0.3,0.31,0.32,0.4,0.7}));
  }
  @Test public void testShrinking7() {
    double[] before = new double[]{0.3,0.31,0.32,0.4,0.7};
    double[] after = ArrayUtils.limitToRange(before,0.305,0.699);
    assert(Arrays.equals(after, new double[]{0.3,0.31,0.32,0.4}));
  }
  @Test public void testShrinking8() {
    double[] before = new double[]{0.3,0.31,0.32,0.4,0.7};
    double[] after = ArrayUtils.limitToRange(before,0.7,0.9);
    assert(Arrays.equals(after, new double[]{0.7}));
  }
  @Test public void testShrinking9() {
    double[] before = new double[]{0.3,0.31,0.32,0.4,0.7};
    double[] after = ArrayUtils.limitToRange(before,0.8,0.9);
    assert(Arrays.equals(after, new double[]{0.7}));
  }
  @Test public void testPadding() {
    double[] before = new double[]{0.3,0.31,0.32,0.4,0.7};
    double[] after = ArrayUtils.padUniformly(before,9);
    assert(Arrays.equals(after, new double[]{0.3,0.305,0.31,0.315,0.32,0.36,0.4,0.55,0.7}));
  }
  @Test public void testPadding2() {
    double[] before = new double[]{0.3,0.31,0.32,0.4,0.7};
    double[] after = ArrayUtils.padUniformly(before,10);
    assert(Arrays.equals(after, new double[]{0.3,0.3025,0.3075,0.31,0.315,0.32,0.36,0.4,0.55,0.7}));
  }
  @Test public void testPadding3() {
    double[] before = new double[]{0.3,0.31,0.32,0.4,0.7};
    double[] after = ArrayUtils.padUniformly(before,8);
    assert(Arrays.equals(after, new double[]{0.3,0.305,0.31,0.315,0.32,0.36,0.4,0.7}));
  }
  @Test public void binarySearch() {
    int R=1000000;
    for (int N : new int[]{20,50,100}) {
      double[] vals = new double[N];
      for (int i = 0; i < N; ++i) {
        vals[i] = i * 1.0 / N;
      }
      double[] pts = new double[N];
      Random rnd = RandomUtils.getRNG(123);
      for (int i = 0; i < N; ++i) {
        pts[i] = rnd.nextInt(N) * 1. / N;
      }
      long sum = 0;
      for (int r = 0; r < R; ++r) {
        sum += Arrays.binarySearch(vals, pts[r % N]);
      }
      long start = System.currentTimeMillis();
      for (int r = 0; r < R; ++r) {
        sum += Arrays.binarySearch(vals, pts[r % N]);
      }
      long done = System.currentTimeMillis();
      Log.info("N=" + N + " Sum:" + sum + " Time: " + PrettyPrint.msecs(done - start, true));
    }
  }
  @Test public void linearSearch() {
    int R=1000000;
    for (int N : new int[]{20,50,100}) {
      double[] vals = new double[N];
      for (int i = 0; i < N; ++i) {
        vals[i] = i * 1.0 / N;
      }
      double[] pts = new double[N];
      Random rnd = RandomUtils.getRNG(123);
      for (int i = 0; i < N; ++i) {
        pts[i] = rnd.nextInt(N) * 1. / N;
      }
      long sum = 0;
      for (int r = 0; r < R; ++r) {
        sum += ArrayUtils.linearSearch(vals, pts[r % N]);
      }
      long start = System.currentTimeMillis();
      for (int r = 0; r < R; ++r) {
        sum += ArrayUtils.linearSearch(vals, pts[r % N]);
      }
      long done = System.currentTimeMillis();
      Log.info("N=" + N + " Sum:" + sum + " Time: " + PrettyPrint.msecs(done - start, true));
    }
  }
}
