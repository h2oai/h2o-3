package ai.h2o.automl;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.fvec.Frame;
import water.util.TwoDimTable;

public class LeaderboardTest extends water.TestUtil {

  // TODO: plenty of tests to write for this class

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void test_toTwoDimTable_with_empty_models_and_without_sort_metric() {
    Leaderboard lb = null;
    UserFeedback ufb = new UserFeedback(new AutoML());
    try {
      lb = Leaderboard.getOrMakeLeaderboard("dummy_lb_no_sort_metric", ufb, new Frame(), null);

      TwoDimTable table = lb.toTwoDimTable();
      Assert.assertNotNull("empty leaderboard should also produce a TwoDimTable", table);
      Assert.assertEquals("no models in this leaderboard", table.getTableDescription());
    } finally {
      if (lb != null) lb.deleteWithChildren();
      ufb.delete();
    }
  }

  @Test
  public void test_toTwoDimTable_with_empty_models_and_with_sort_metric() {
    Leaderboard lb = null;
    UserFeedback ufb = new UserFeedback(new AutoML());
    try {
      lb = Leaderboard.getOrMakeLeaderboard("dummy_lb_logloss_sort_metric", ufb, new Frame(), "logloss");

      TwoDimTable table = lb.toTwoDimTable();
      Assert.assertNotNull("empty leaderboard should also produce a TwoDimTable", table);
      Assert.assertEquals("no models in this leaderboard", table.getTableDescription());
    } finally {
      if (lb != null) lb.deleteWithChildren();
      ufb.delete();
    }
  }

}
