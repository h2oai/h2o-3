package hex.tree.isofor;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
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
      p._sample_size = 5;

      IsolationForestModel model = new IsolationForest(p).trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);

      Frame preds = Scope.track(model.score(train));
      assertArrayEquals(new String[]{"predict", "mean_length"}, preds.names());
      assertEquals(train.numRows(), preds.numRows());

      assertTrue(model.testJavaScoring(train, preds, 1e-8));
    } finally {
      Scope.exit();
    }
  }

}