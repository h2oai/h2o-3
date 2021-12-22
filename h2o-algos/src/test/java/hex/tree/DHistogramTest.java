package hex.tree;

import hex.tree.uplift.UpliftDRFModel;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.C0DChunk;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.ArrayUtils;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

public class DHistogramTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }
  
  @Test
  public void showBinarySearchFailsOnNegativeZero() {
    final double val = -0.0d;
    final double[] ary = new double[]{0.0};
    assertEquals(ary[0], val, 0);
    int pos = Arrays.binarySearch(ary, val);
    assertEquals(-1 , pos); // -0.0d was not found
    // it is because binarySearch internally uses doubleToLongBits
    assertNotEquals(Double.doubleToLongBits(-0.0d), Double.doubleToLongBits(0.0d));
  }

  @Test
  public void initCachesZeroPosition() {
    Scope.enter();
    try {
      DHistogram.HistoQuantiles hq = new DHistogram.HistoQuantiles(Key.make(), new double[]{-1.0d, -0.3, -0.0d, 1.0, 1.2, 1.8});
      DKV.put(hq);
      Scope.track_generic(hq);

      DHistogram histo = new DHistogram("test", 20, 1024, (byte) 1, -1, 2, false, false, -0.001,
              SharedTreeModel.SharedTreeParameters.HistogramType.QuantilesGlobal, 42L, hq._key, null, false, false, null);
      histo.init();

      // check that -0.0 was converted to 0.0 by the init method
      assertEquals(-3, Arrays.binarySearch(histo._splitPts, -0.0));
      assertEquals(2, Arrays.binarySearch(histo._splitPts, 0.0));
      // and the position is cached
      assertEquals(2, histo._zeroSplitPntPos);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void findBinForNegativeZero() {
    Scope.enter();
    try {
      DHistogram.HistoQuantiles hq = new DHistogram.HistoQuantiles(Key.make(), new double[]{-1.0d, -0.0d, 1.0});
      DKV.put(hq);
      Scope.track_generic(hq);

      DHistogram histo = new DHistogram("test", 20, 1024, (byte) 1, -1, 2, false, false, -0.001,
              SharedTreeModel.SharedTreeParameters.HistogramType.QuantilesGlobal, 42L, hq._key, null, false, false, null);
      histo.init();

      // check that negative zero can be found
      int bin = histo.bin(-0.0);
      assertEquals(1, bin);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void randomSplitPointsAreConsistent() {
    Scope.enter();
    try {
      final double min = -1;
      final double maxEx = 2;
      final double[] values = new double[101];
      final double step = (maxEx - min) / (values.length - 1);
      values[0] = min;        
      for (int i = 1; i < values.length; i++) {
        values[i] = values[i - 1] + step;
      }
      assertEquals(maxEx, values[values.length - 1], 1e-8);
      values[values.length - 1] = maxEx - 1e-8;

      DHistogram histoRand = new DHistogram("rand", 20, 1024, (byte) 0, min, maxEx, false, false, -0.001,
              SharedTreeModel.SharedTreeParameters.HistogramType.Random, 42L, null, null, false, false, null);
      histoRand.init();

      // project the random split points into regular space (original values of the column)
      double[] splitPointsQuant = new double[histoRand._splitPts.length];
      for (int i = 0; i < splitPointsQuant.length; i++) {
        splitPointsQuant[i] = histoRand.binAt(i);
      }
      // make a quantile-global estimator with conceptually the same split points
      DHistogram.HistoQuantiles hq = new DHistogram.HistoQuantiles(Key.make(), splitPointsQuant);
      DKV.put(hq);
      Scope.track_generic(hq);
      DHistogram histoQuant = new DHistogram("quant", 20, 1024, (byte) 0, min, maxEx, false, false, -0.001,
              SharedTreeModel.SharedTreeParameters.HistogramType.QuantilesGlobal, 42L, hq._key, null, false, false, null);
      histoQuant.init();

      int[] bins_rand = new int[values.length];
      int[] bins_quant = new int[values.length];
      for (int i = 0; i < values.length; i++) {
        bins_rand[i] = histoRand.bin(values[i]);
        bins_quant[i] = histoQuant.bin(values[i]);
      }
      assertArrayEquals(bins_rand, bins_quant);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testExtractData_double() {
    DHistogram histo = new DHistogram("test", 20, 1024, (byte) 1, -1, 2, false, false, -0.001,
            SharedTreeModel.SharedTreeParameters.HistogramType.AUTO, 42L, null, null, false, false, null);
    histo.init();

    // init with c1
    Chunk c1 = new C0DChunk(Math.PI, 10);

    Object cache = histo.extractData(c1, null, c1.len(), 42);
    assertNotNull(cache);
    assertTrue(cache instanceof double[]);

    double[] expected = new double[10];
    Arrays.fill(expected, Math.PI);

    assertArrayEquals(ArrayUtils.append(expected, new double[32]), (double[]) cache, 0);

    // re-use with c2
    Chunk c2 = new C0DChunk(Math.E, 1);
    cache = histo.extractData(c2, cache, c2.len(), -1);
    expected[0] = Math.E;
    
    assertArrayEquals(ArrayUtils.append(expected, new double[32]), (double[]) cache, 0);
  }

  @Test
  public void testExtractData_int() {
    DHistogram histo = new DHistogram("test", 20, 1024, (byte) 1, -1, 2, true, false, -0.001,
            SharedTreeModel.SharedTreeParameters.HistogramType.AUTO, 42L, null, null, false, false, null);
    histo.init();

    // init with c1
    Chunk c1 = new C0DChunk(314, 10);

    Object cache = histo.extractData(c1, null, c1.len(), 42);
    assertNotNull(cache);
    assertTrue(cache instanceof int[]);

    int[] expected = new int[10];
    Arrays.fill(expected, 314);

    assertArrayEquals(ArrayUtils.append(expected, new int[32]), (int[]) cache);

    // re-use with c2
    Chunk c2 = new C0DChunk(272, 1);
    cache = histo.extractData(c2, cache, c2.len(), -1);
    expected[0] = 272;

    assertArrayEquals(ArrayUtils.append(expected, new int[32]), (int[]) cache);
  }

  @Test
  public void testUpdateHistoWithIntOpt() {
    int N = 10000;
    double[] weights = ArrayUtils.toDouble(ArrayUtils.seq(0, N));
    double[] ys = new double[N];
    double[] data = new double[N];
    int[] dataInt = new int[N];
    int[] rows = ArrayUtils.seq(0, N);

    Random r = new Random(42);
    for (int i = 0; i < N; i++) {
      ys[i] = r.nextGaussian();
      dataInt[i] = 13 + r.nextInt(900);
      data[i] = dataInt[i];
    }

    // optimization enabled
    DHistogram histoOpt = new DHistogram("intOpt-on", 1000, 1024, (byte) 1, 0, 1000, true, false, -0.001,
            SharedTreeModel.SharedTreeParameters.HistogramType.UniformAdaptive, 42L, null, null, false, false, null);
    histoOpt.init();

    histoOpt.updateHisto(weights, null, dataInt, ys, null, rows, N, 0, null);

    // optimization OFF
    DHistogram histo = new DHistogram("intOpt-off", 1000, 1024, (byte) 1, 0, 1000, false, false, -0.001,
            SharedTreeModel.SharedTreeParameters.HistogramType.UniformAdaptive, 42L, null, null, false, false, null);
    histo.init();

    histo.updateHisto(weights, null, data, ys, null, rows, N, 0, null);

    assertEquals(histo._min2, histoOpt._min2, 0);
    assertEquals(histo._maxIn, histoOpt._maxIn, 0);
    assertArrayEquals(histo._vals, histoOpt._vals, 0);
  }

  @Test
  public void testUseIntOpt() {
    try {
      Scope.enter();
      Frame f = TestFrameCatalog.oneChunkFewRows();
      f.setNames(new String[]{"float", "int", "cat", "cat2"});
      DKV.put(f);

      TreeParameters defaultTP = new TreeParameters();

      // disabled for Uplift
      assertFalse(DHistogram.useIntOpt(null, new UpliftDRFModel.UpliftDRFParameters(), null));
      
      // disabled when constraints are used
      assertFalse(DHistogram.useIntOpt(null, null, new Constraints(new int[0], null, false)));

      // disabled for floating point columns
      assertFalse(DHistogram.useIntOpt(f.vec("float"), defaultTP, null));

      // enabled for a small int column
      assertTrue(DHistogram.useIntOpt(f.vec("int"), defaultTP, null));

      // enabled for a small cat column
      assertTrue(DHistogram.useIntOpt(f.vec("cat"), defaultTP, null));

      // check that only AUTO and UniformAdaptive enabled the optimization
      for (SharedTreeModel.SharedTreeParameters.HistogramType ht : SharedTreeModel.SharedTreeParameters.HistogramType.values()) {
        TreeParameters tp = new TreeParameters();
        tp._histogram_type = ht;
        if (ht == SharedTreeModel.SharedTreeParameters.HistogramType.AUTO || ht == SharedTreeModel.SharedTreeParameters.HistogramType.UniformAdaptive) {
          assertTrue(DHistogram.useIntOpt(f.vec("int"), tp, null));
        } else {
          assertFalse(DHistogram.useIntOpt(f.vec("int"), tp, null));
        }
      }

      // disabled for "large" categoricals
      {
        TreeParameters largeCatTP = new TreeParameters();
        largeCatTP._nbins_cats = f.vec("cat").domain().length - 1;
        assertFalse(DHistogram.useIntOpt(f.vec("cat"), largeCatTP, null));
      }

      // disabled for "large" integer columns
      {
        TreeParameters largeNumTP = new TreeParameters();
        largeNumTP._nbins = 2;
        assertFalse(DHistogram.useIntOpt(f.vec("int"), largeNumTP, null));
      }
    } finally {
      Scope.exit();
    }

  }

  private static class TreeParameters extends SharedTreeModel.SharedTreeParameters {
    public TreeParameters() {
    }

    @Override
    public String algoName() {
      return null;
    }

    @Override
    public String fullName() {
      return null;
    }

    @Override
    public String javaName() {
      return null;
    }
  }
  
}
