package hex.tree;

import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;

import java.util.Arrays;

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
    assertTrue(ary[0] == val);
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

      DHistogram histo = new DHistogram("test", 20, 1024, (byte) 1, -1, 2, false, -0.001,
              SharedTreeModel.SharedTreeParameters.HistogramType.QuantilesGlobal, 42L, hq._key, null);
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

      DHistogram histo = new DHistogram("test", 20, 1024, (byte) 1, -1, 2, false, -0.001,
              SharedTreeModel.SharedTreeParameters.HistogramType.QuantilesGlobal, 42L, hq._key, null);
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

      DHistogram histoRand = new DHistogram("rand", 20, 1024, (byte) 0, min, maxEx, false, -0.001,
              SharedTreeModel.SharedTreeParameters.HistogramType.Random, 42L, null, null);
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
      DHistogram histoQuant = new DHistogram("quant", 20, 1024, (byte) 0, min, maxEx, false, -0.001,
              SharedTreeModel.SharedTreeParameters.HistogramType.QuantilesGlobal, 42L, hq._key, null);
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

}
