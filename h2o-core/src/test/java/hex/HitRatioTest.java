package hex;

import static hex.ModelMetricsMultinomial.updateHits;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import static water.TestUtil.stall_till_cloudsize;
import water.util.ArrayUtils;

import java.util.Arrays;

public class HitRatioTest {

  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test
  @Ignore
  public void testHits() {
    long[] hits = new long[4];
    double[] pred_dist;
    int actual_label;

    // No ties
    //top 1
    Arrays.fill(hits, 0);
    actual_label = 0; pred_dist = new double[]{0,.4f,.1f,.2f,.3f}; updateHits(actual_label, pred_dist, hits);
    Assert.assertTrue(Arrays.equals(hits, new long[]{1, 0, 0, 0}));

    // top-2
    Arrays.fill(hits, 0);
    actual_label = 3; pred_dist = new double[]{0,.4f,.1f,.2f,.3f}; updateHits(actual_label, pred_dist, hits);
    Assert.assertTrue(Arrays.equals(hits, new long[]{0, 1, 0, 0}));

    // top-2
    Arrays.fill(hits, 0);
    actual_label = 0; pred_dist = new double[]{3,.3f,.2f,.1f,.4f}; updateHits(actual_label, pred_dist, hits);
    Assert.assertTrue(Arrays.equals(hits, new long[]{0, 1, 0, 0}));

    // top-3
    Arrays.fill(hits, 0);
    actual_label = 1; pred_dist = new double[]{3,.3f,.2f,.1f,.4f}; updateHits(actual_label, pred_dist, hits);
    Assert.assertTrue(Arrays.equals(hits, new long[]{0, 0, 1, 0}));

    // top-4
    Arrays.fill(hits, 0);
    actual_label = 1; pred_dist = new double[]{0,.4f,.1f,.3f,.2f}; updateHits(actual_label, pred_dist, hits);
    Assert.assertTrue(Arrays.equals(hits, new long[]{0, 0, 0, 1}));


    // 2 Ties
    // top-1 or top-2
    Arrays.fill(hits, 0);
    actual_label = 1; pred_dist = new double[]{0,.3f,.3f,.2f,.2f}; updateHits(actual_label, pred_dist, hits);
    Assert.assertTrue(
            Arrays.equals(hits, new long[]{1, 0, 0, 0}) ||
            Arrays.equals(hits, new long[]{0, 1, 0, 0})
    );

    // top-2 or top-3
    Arrays.fill(hits, 0);
    actual_label = 1; pred_dist = new double[]{0,.3f,.3f,.35f,.05f}; updateHits(actual_label, pred_dist, hits);
    Assert.assertTrue(
            Arrays.equals(hits, new long[]{0, 1, 0, 0}) ||
            Arrays.equals(hits, new long[]{0, 0, 1, 0})
    );

    // top-3 or top-4
    Arrays.fill(hits, 0);
    actual_label = 1; pred_dist = new double[]{0,.3f,.1f,.2f,.1f}; updateHits(actual_label, pred_dist, hits);
    Assert.assertTrue(
            Arrays.equals(hits, new long[]{0, 0, 1, 0}) ||
            Arrays.equals(hits, new long[]{0, 0, 0, 1})
    );


    // 3 Ties
    Arrays.fill(hits, 0);
    actual_label = 1; pred_dist = new double[]{0,.3f,.3f,.1f,.3f}; updateHits(actual_label, pred_dist, hits);
    Assert.assertTrue(
            Arrays.equals(hits, new long[]{1, 0, 0, 0}) ||
            Arrays.equals(hits, new long[]{0, 1, 0, 0}) ||
            Arrays.equals(hits, new long[]{0, 0, 1, 0})
    );

    Arrays.fill(hits, 0);
    actual_label = 1; pred_dist = new double[]{0,.1f,.1f,.1f,.7f}; updateHits(actual_label, pred_dist, hits);
    Assert.assertTrue(
            Arrays.equals(hits, new long[]{0, 1, 0, 0}) ||
                    Arrays.equals(hits, new long[]{0, 0, 1, 0}) ||
                    Arrays.equals(hits, new long[]{0, 0, 0, 1})
    );

    Arrays.fill(hits, 0);
    actual_label = 2; pred_dist = new double[]{0,.1f,.1f,.1f,.7f}; updateHits(actual_label, pred_dist, hits);
    Assert.assertTrue(
            Arrays.equals(hits, new long[]{0, 1, 0, 0}) ||
            Arrays.equals(hits, new long[]{0, 0, 1, 0}) ||
            Arrays.equals(hits, new long[]{0, 0, 0, 1})
    );

    // 4 Ties
    Arrays.fill(hits, 0);
    actual_label = 1; pred_dist = new double[]{0,.25f,.25f,.25f,.25f}; updateHits(actual_label, pred_dist, hits);
    Assert.assertTrue(
            Arrays.equals(hits, new long[]{1, 0, 0, 0}) ||
            Arrays.equals(hits, new long[]{0, 1, 0, 0}) ||
            Arrays.equals(hits, new long[]{0, 0, 1, 0}) ||
            Arrays.equals(hits, new long[]{0, 0, 0, 1})
    );
  }


}
