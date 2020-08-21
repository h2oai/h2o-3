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

}
