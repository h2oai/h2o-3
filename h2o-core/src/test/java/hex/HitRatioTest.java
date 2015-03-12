package hex;

import static hex.ModelMetricsMultinomial.updateHits;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import static water.TestUtil.stall_till_cloudsize;

public class HitRatioTest {

  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void testHits() {
    long[] hits = new long[4];
    int actual_label = 3; float[] pred_dist = {0,.4f,.1f,.2f,.3f}; updateHits(actual_label, pred_dist, hits);
    Assert.assertTrue(hits[0] == 0);
    Assert.assertTrue(hits[1] == 1);
    Assert.assertTrue(hits[2] == 0);
    Assert.assertTrue(hits[3] == 0);
    actual_label = 0; pred_dist = new float[]{3,.3f,.2f,.1f,.4f}; updateHits(actual_label, pred_dist, hits);
    Assert.assertTrue(hits[0] == 0+0);
    Assert.assertTrue(hits[1] == 1+1);
    Assert.assertTrue(hits[2] == 0+0);
    Assert.assertTrue(hits[3] == 0+0);
    actual_label = 1; pred_dist = new float[]{0,.4f,.1f,.3f,.2f}; updateHits(actual_label, pred_dist, hits);
    Assert.assertTrue(hits[0] == 0+0+0);
    Assert.assertTrue(hits[1] == 1+1+0);
    Assert.assertTrue(hits[2] == 0+0+0);
    Assert.assertTrue(hits[3] == 0+0+1);
  }


}
