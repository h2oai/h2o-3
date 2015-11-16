package water.util;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import java.util.Arrays;
import java.util.Random;

public class SortTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void runSmall() {
    int[] idxs = new int[]{0,1,2,3,4};
    final double[] values = new double[]{4.2,1.0,-1,4.3,2.0};
    ArrayUtils.sort(idxs, values);
    Assert.assertTrue(Arrays.equals(idxs, new int[]{2, 1, 4, 0, 3}));
  }

  @Test public void runMid() {
    int[] idxs = new int[]{0,1,2,3,4,5,6,7,8};
    final double[] values = new double[]{0.1,-0.3,0.5,9.0,4.2,1.0,-1,4.3,2.0};
    ArrayUtils.sort(idxs, values);
    Assert.assertTrue(Arrays.equals(idxs, new int[]{6, 1, 0, 2, 5, 8, 4, 7, 3}));
  }

  @Test public void runHuge() {
    for (int N=1<<6;N<1<<17;N<<=1) {
      System.err.println("N: " + N);
      int[] idxs = new int[N];
      int[] idxs2 = new int[N];

      long merge = 0;
      long insertion = 0;
      int reps = 1; //increase for better timing
      for (int rep = 0; rep < reps; ++rep) {
        final double[] values = new double[idxs.length];
        Random rng = new Random();
        for (int i = 0; i < idxs.length; ++i) {
          idxs[i] = i;
          idxs2[i] = i;
          values[i] = rng.nextDouble();
        }
        long before = System.nanoTime();
        ArrayUtils.sort(idxs, values);
        merge += System.nanoTime() - before;

        before = System.nanoTime();
        ArrayUtils.sort(idxs2, values, Integer.MAX_VALUE); //always do insertion sort
        insertion += System.nanoTime() - before;
      }
      System.err.println("Merge sort: " + (double)merge/1e9/reps );
      System.err.println("Insertion sort: " + (double)insertion/1e9/reps);

      Assert.assertTrue(Arrays.equals(idxs, idxs2));
    }
  }
}
