package hex.tree;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Futures;
import water.H2O;
import water.TestUtil;
import water.util.AtomicUtils;
import water.util.Log;

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

    @Override protected void compute2() {
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
}
