package hex.tree.isofor;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;

import static org.junit.Assert.*;

public class IsolationForestTest extends TestUtil {

  @BeforeClass() public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testBasic() {
    try {
      Scope.enter();
      Frame train = Scope.track(parse_test_file("smalldata/anomaly/ecg_discord_train.csv"));

      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._seed = 0xDECAF;
      p._ntrees = 7;
      p._min_rows = 1;
      p._sample_size = 5;

      IsolationForestModel model = new IsolationForest(p).trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);

      Frame preds = Scope.track(model.score(train));
      assertArrayEquals(new String[]{"predict", "mean_length"}, preds.names());
      assertEquals(train.numRows(), preds.numRows());

      assertTrue(model.testJavaScoring(train, preds, 1e-8));

      assertTrue(model._output._min_path_length < Integer.MAX_VALUE);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testEarlyStopping() {
    try {
      Scope.enter();
      Frame train = Scope.track(parse_test_file("smalldata/anomaly/ecg_discord_train.csv"));

      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._seed = 0xDECAF;
      p._ntrees = 1000;
      p._min_rows = 1;
      p._sample_size = 5;
      p._stopping_rounds = 3;
      p._score_each_iteration = true;
      p._stopping_tolerance = 0.05;

      IsolationForestModel model = new IsolationForest(p).trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);

      assertEquals(0, model._output._ntrees, 20); // stops in 20 trees or less
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testEmptyOOB() {
    try {
      Scope.enter();
      Frame train = Scope.track(parse_test_file("smalldata/anomaly/ecg_discord_train.csv"));

      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._seed = 0xDECAF;
      p._ntrees = 7;
      p._sample_size = train.numRows(); // => no OOB observations

      IsolationForestModel model = new IsolationForest(p).trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);

      Frame preds = Scope.track(model.score(train));
      assertArrayEquals(new String[]{"predict", "mean_length"}, preds.names());
      assertEquals(train.numRows(), preds.numRows());

      assertTrue(model.testJavaScoring(train, preds, 1e-8));

      assertTrue(model._output._min_path_length < Integer.MAX_VALUE);
    } finally {
      Scope.exit();
    }
  }
  
  @Test // check that mtries can be set to full number of features (same as mtries = 2)
  public void testPubDev6483() {
    try {
      Scope.enter();
      Frame train = Scope.track(parse_test_file("smalldata/anomaly/ecg_discord_train.csv"));

      // should pass with all features
      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._seed = 0xDECAF;
      p._ntrees = 7;
      p._mtries = train.numCols();

      IsolationForestModel model = new IsolationForest(p).trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);
      
      // should fail with #features + 1
      IsolationForestModel.IsolationForestParameters p_invalid = new IsolationForestModel.IsolationForestParameters();
      p_invalid._train = train._key;
      p_invalid._seed = 0xDECAF;
      p_invalid._ntrees = 7;
      p_invalid._mtries = train.numCols() + 1;
      try {
        Scope.track_generic(new IsolationForest(p_invalid).trainModel().get());
        fail();
      } catch (H2OIllegalArgumentException e) {
        assertTrue(e.getMessage().contains("ERRR on field: _mtries: Computed mtries should be -1 or -2 or in interval [1,210] but it is 211"));
      }
    } finally {
      Scope.exit();
    }
  }


}
