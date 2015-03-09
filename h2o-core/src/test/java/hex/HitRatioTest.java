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
    int actual_label = 3; int[] pred_labels = {0,3,2,1}; updateHits(hits, actual_label, pred_labels);
    Assert.assertTrue(hits[0] == 0);
    Assert.assertTrue(hits[1] == 1);
    Assert.assertTrue(hits[2] == 1);
    Assert.assertTrue(hits[3] == 1);
    actual_label = 0; pred_labels = new int[]{1,2,3,0}; updateHits(hits, actual_label, pred_labels);
    Assert.assertTrue(hits[0] == 0+0);
    Assert.assertTrue(hits[1] == 1+0);
    Assert.assertTrue(hits[2] == 1+0);
    Assert.assertTrue(hits[3] == 1+1);
    actual_label = 1; pred_labels = new int[]{0,3,1,2}; updateHits(hits, actual_label, pred_labels);
    Assert.assertTrue(hits[0] == 0+0+0);
    Assert.assertTrue(hits[1] == 1+0+0);
    Assert.assertTrue(hits[2] == 1+0+1);
    Assert.assertTrue(hits[3] == 1+1+1);
  }


}
