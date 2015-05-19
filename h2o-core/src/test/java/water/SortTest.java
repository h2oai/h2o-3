package water;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.util.ArrayUtils;
import java.util.Arrays;

public class SortTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void run() {
    int[] idxs = new int[]{0,1,2,3,4};
    final double[] avgs = new double[]{4.2,1.0,-1,4.3,2.0};
    ArrayUtils.sort(idxs, new ArrayUtils.IntComparator() {
      @Override
      public int compare(int x, int y) {
        return avgs[x] < avgs[y] ? -1 : (avgs[x] > avgs[y] ? 1 : 0);
      }
    });
    Assert.assertTrue(Arrays.equals(idxs, new int[]{2, 1, 4, 0, 3}));
  }
}
