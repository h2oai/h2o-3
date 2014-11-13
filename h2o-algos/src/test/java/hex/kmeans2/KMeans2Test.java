package hex.kmeans2;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;

public class KMeans2Test extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  
  @Test public void testIris() {
    KMeans2Model kmm = null;
    Frame fr = null;
    try {
      long start = System.currentTimeMillis();
      System.out.println("Start Parse");
      fr = parse_test_file("smalldata/iris/iris_wheader.csv");
      System.out.println("Done Parse: "+(System.currentTimeMillis()-start));

      KMeans2Model.KMeans2Parameters parms = new KMeans2Model.KMeans2Parameters();
      parms._train = fr._key;
      parms._max_iters = 10;
      parms._K = 3;

      KMeans2 job = new KMeans2(parms).trainModel();
      kmm = job.get();
      job.remove();

    } finally {
      if( fr  != null ) fr .remove();
      if( kmm != null ) kmm.delete();
    }
  }
}
