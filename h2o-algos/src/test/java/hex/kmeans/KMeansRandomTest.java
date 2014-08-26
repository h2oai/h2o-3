package hex.kmeans;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.util.Log;

import java.util.Random;

public class KMeansRandomTest extends TestUtil {
  @BeforeClass()
  public static void setup() { stall_till_cloudsize(3); }

  @Test
  public void run() {
    long seed = 0xDECAF;
    Random rng = new Random(seed);
    String[] datasets = new String[2];
    int[][] responses = new int[datasets.length][];
    datasets[0] = "smalldata/logreg/prostate.csv";
    responses[0] = new int[]{1, 2, 8}; //CAPSULE (binomial), AGE (regression), GLEASON (multi-class)
    datasets[1] = "smalldata/junit/iris.csv";
    responses[1] = new int[]{4}; //Iris-type (multi-class)


    int testcount = 0;
    int count = 0;
    for (int i = 0; i < datasets.length; ++i) {
      String dataset = datasets[i];
      Frame frame = parse_test_file(dataset);

      try {
        for (int clusters : new int[]{1, 10, 100}) {
          for (int max_iter : new int[]{1, 10, 100}) {
            for (boolean normalize : new boolean[]{false, true}) {
              for (KMeans.Initialization init : new KMeans.Initialization[]{
                      KMeans.Initialization.None,
                      KMeans.Initialization.Furthest,
                      KMeans.Initialization.PlusPlus,
              }) {

                count++;

                KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
                parms._src = frame._key;
                parms._K = clusters;
                parms._seed = rng.nextLong();
                parms._max_iters = max_iter;
                parms._normalize = normalize;
                parms._init = init;

                KMeans job = new KMeans(parms);
                job.train();
                KMeansModel m = job.get();
                job.remove();

                try {
                  for (int j = 0; j < parms._K; j++)
                    Assert.assertTrue(m._output._rows[j] != 0);

                  Assert.assertTrue(m._output._iters <= max_iter);
                  for (double d : m._output._mses) Assert.assertFalse(Double.isNaN(d));
                  Assert.assertFalse(Double.isNaN(m._output._mse));
                  for (long o : m._output._rows) Assert.assertTrue(o > 0); //have at least one point per centroid
                  for (double[] dc : m._output._clusters) for (double d : dc) Assert.assertFalse(Double.isNaN(d));

                  // make prediction (cluster assignment)
                  Frame score = m.score(frame);
                  for (long j = 0; j < score.numRows(); ++j)
                    Assert.assertTrue(score.anyVec().at8(j) >= 0 && score.anyVec().at8(j) < clusters);
                  score.delete();

                  Log.info("Parameters combination " + count + ": PASS");
                  testcount++;
                } catch (Throwable t) {
                  t.printStackTrace();
                  throw new RuntimeException(t);
                } finally {
                  m.delete();
                }
              }
            }
          }
        }
      } finally {
        frame.delete();
      }
    }
    Log.info("\n\n=============================================");
    Log.info("Tested " + testcount + " out of " + count + " parameter combinations.");
    Log.info("=============================================");
  }
}


