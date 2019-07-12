package ai.h2o.automl;

import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.fvec.Frame;
import water.util.TwoDimTable;

public class LeaderboardTest extends water.TestUtil {

  // TODO: plenty of tests to write for this class

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  private static Key<AutoML> dummy = Key.make();

  @Test
  public void test_toTwoDimTable_with_empty_models_and_without_sort_metric() {
    Leaderboard lb = null;
    EventLog eventLog = EventLog.make(dummy);
    try {
      lb = Leaderboard.getOrMake("dummy_lb_no_sort_metric", eventLog, new Frame(), null);

      TwoDimTable table = lb.toTwoDimTable();
      Assert.assertNotNull("empty leaderboard should also produce a TwoDimTable", table);
      Assert.assertEquals("no models in this leaderboard", table.getTableDescription());
    } finally {
      if (lb != null) lb.remove();
      eventLog.remove();
    }
  }

  @Test
  public void test_toTwoDimTable_with_empty_models_and_with_sort_metric() {
    Leaderboard lb = null;
    EventLog eventLog = EventLog.make(dummy);
    try {
      lb = Leaderboard.getOrMake("dummy_lb_logloss_sort_metric", eventLog, new Frame(), "logloss");

      TwoDimTable table = lb.toTwoDimTable();
      Assert.assertNotNull("empty leaderboard should also produce a TwoDimTable", table);
      Assert.assertEquals("no models in this leaderboard", table.getTableDescription());
    } finally {
      if (lb != null) lb.remove();
      eventLog.remove();
    }
  }


  @Test
  public void test_rank_tsv() {
    Leaderboard lb = null;
    EventLog eventLog = EventLog.make(dummy);
    GBMModel model = null;
    Frame fr  = null;
    try {
      fr = parse_test_file("./smalldata/logreg/prostate_train.csv");
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = fr._key;
      parms._nfolds = 2;
      parms._seed = 1;
      parms._response_column = "CAPSULE";
      GBM job = new GBM(parms);
      model = job.trainModel().get();
      
      lb = Leaderboard.getOrMake("dummy_rank_tsv", eventLog, null, "mae");
      lb.addModel(model);
      Assert.assertEquals("Error\n[0.19959320678410908, 0.44675855535636816, 0.19959320678410908, 0.3448260574357465, 0.31468498072970547]\n", lb.rankTsv()); 
    } finally {
      if (lb != null){
        lb.remove();
      }
      eventLog.remove();
      if (model != null) {
        model.deleteCrossValidationModels();
        model.delete();
      }
      if( fr != null) {
        fr.delete();
      }
    }
  }
}
