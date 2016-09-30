package hex.kmeans;

import hex.Model;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;

import java.util.Random;

public class KMeansRandomTest extends TestUtil {
  @BeforeClass()
  public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void run() {
    long seed = 0xDECAF;
    Random rng = new Random(seed);
    String[] datasets = new String[]{
            "smalldata/logreg/prostate.csv",
            "smalldata/iris/iris_wheader.csv",
            "smalldata/junit/weather.csv"
    };

    int testcount = 0;
    int count = 0;
    for( String dataset : datasets ) {
      Frame frame = parse_test_file(dataset);

      try {
        for (int centers : new int[]{1, 2, 10, 100}) {
          for (int max_iter : new int[]{1, 10}) {
            for (boolean estimate_k : new boolean[]{false, true}) {
              for (boolean standardize : new boolean[]{false, true}) {
                for (Model.Parameters.CategoricalEncodingScheme catEncoding : Model.Parameters.CategoricalEncodingScheme.values()) {
                  for (KMeans.Initialization init : new KMeans.Initialization[]{
                          KMeans.Initialization.Random,
                          KMeans.Initialization.Furthest,
                          KMeans.Initialization.PlusPlus,
                  }) {

                    count++;

//                    if (count!=1303) {
//                      rng.nextDouble();
//                      rng.nextLong();
//                      continue;
//                    }
                    if (rng.nextDouble() > 0.2) continue;

                    Frame score = null;
                    KMeansModel.KMeansParameters parms;
                    KMeansModel m = null;
                    try {
                      parms = new KMeansModel.KMeansParameters();
                      parms._train = frame._key;
                      if(dataset != null && dataset.equals("smalldata/iris/iris_wheader.csv"))
                        parms._ignored_columns = new String[] {"class"};
                      parms._k = centers;
                      parms._seed = rng.nextLong();
                      parms._max_iterations = max_iter;
                      parms._standardize = standardize;
                      parms._init = init;
                      parms._estimate_k = estimate_k;
                      parms._categorical_encoding = catEncoding;

                      KMeans job = new KMeans(parms);
                      m = job.trainModel().get();
                      Assert.assertTrue("Progress not 100%, but " + job._job.progress() *100, job._job.progress() == 1.0);

                      for (int j = 0; j < m._output._k[m._output._k.length-1]; j++)
                        Assert.assertTrue(m._output._size[j] != 0);

                      Assert.assertTrue(m._output._iterations <= max_iter);
                      for (double d : m._output._withinss) Assert.assertFalse(Double.isNaN(d));
                      Assert.assertFalse(Double.isNaN(m._output._tot_withinss));
                      for (long o : m._output._size) Assert.assertTrue(o > 0); //have at least one point per centroid
                      for (double[] dc : m._output._centers_raw) for (double d : dc) Assert.assertFalse(Double.isNaN(d));

                      // make prediction (cluster assignment)
                      score = m.score(frame);
                      Vec.Reader vr = score.anyVec().new Reader();
                      for (long j = 0; j < score.numRows(); ++j)
                        Assert.assertTrue(vr.at8(j) >= 0 && vr.at8(j) < m._output._k[m._output._k.length-1]);

                      Log.info("Parameters combination " + count + ": PASS");
                      testcount++;
                    } finally {
                      if (m!=null) m.delete();
                      if (score!=null) score.delete();
                    }
                  }
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


