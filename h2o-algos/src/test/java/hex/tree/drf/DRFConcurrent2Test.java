package hex.tree.drf;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import hex.tree.drf.DRFModel.DRFParameters;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.*;
import water.fvec.Frame;
import water.parser.ParseSetup;

import static org.junit.Assert.assertNotNull;

/**
 * Based on a reproducer of a bug PUBDEV-7193 kindly provided by https://github.com/SimonSchmid
 */
@RunWith(Parameterized.class)
public class DRFConcurrent2Test extends TestUtil {
  
  private static final int REPEAT_N = 3;
  
  @Parameterized.Parameters
  public static Object[] repeated() { // emulating multiple runs
    return new Object[REPEAT_N];
  }
  
  @Parameterized.Parameter
  public Object run; 
  
  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testConcurrent() throws Exception {
    Assume.assumeTrue(H2O.getCloudSize() == 1); // don't test in multi-node, not worth it - this tests already takes a long time
    Frame fr = null;
    try {
      fr = parseTestFile("./smalldata/jira/pubdev_7193.csv", setup -> setup.setCheckHeader(1));

      ExecutorService executor = Executors.newFixedThreadPool(2);
      List<Callable<DRFModel>> runnables = new ArrayList<>();
      for (int i = 0; i < 50; i++) {
        runnables.add(new DRFBuilder(fr));
      }
      for (Future<DRFModel> future : executor.invokeAll(runnables)) {
        assertNotNull(future.get());
      }
    } finally {
      if (fr != null)
        fr.delete();
    }
  }

  private static class DRFBuilder implements Callable<DRFModel> {

    private final Key<Frame> _train;

    private DRFBuilder(Frame trainingFrame) {
      _train = trainingFrame._key;
    }

    public DRFModel call() {
      DRFParameters params = new DRFParameters();
      params._response_column = "C2";
      params._train = _train;
      DRF builder = new DRF(params);
      DRFModel model = null;
      try {
        model = builder.trainModel().get();
      } finally {
        if (model != null) {
          model.delete();
        }
      }
      return model;
    }
  }
}
