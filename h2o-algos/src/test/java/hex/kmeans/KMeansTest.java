package hex.kmeans;

import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;

public class KMeansTest extends TestUtil {

  @Test public void testIris() {
    KMeans job = null;
    KMeansModel kmm = null;
    Frame fr = null, fr2= null;
    try {
      fr = parse_test_file("smalldata/iris/iris_wheader.csv");

      KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
      parms._src = fr._key;
      parms._K = 3;
      parms._normalize = true;
      parms._max_iters = 10;
      parms._init = KMeans.Initialization.None;
      parms._seed = 0;

      job = new KMeans(parms);
      kmm = job.get();

      // Done building model; produce a score column with cluster choices
      fr2 = kmm.score(fr);

    } finally {
      if( fr  != null ) fr .remove();
      if( fr2 != null ) fr2.remove();
      if( job != null ) job.remove();
      if( kmm != null ) kmm.delete();
    }
  }
}
