package hex.quantile;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;

public class QuantileTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  
  @Test public void testIris() {
    QuantileModel kmm = null;
    Frame fr = null;
    try {
      long start = System.currentTimeMillis();
      System.out.println("Start Parse");
      //fr = parse_test_file("smalldata/iris/iris_wheader.csv");
      fr = parse_test_file("../../datasets/UCI/UCI-large/covtype/covtype.data");
      //fr = parse_test_file("../../datasets/billion_rows.csv.gz");
      System.out.println("Done Parse: "+(System.currentTimeMillis()-start));

      QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
      parms._train = fr._key;
      //parms._max_iters = 10;

      Quantile job = new Quantile(parms).trainModel();
      kmm = job.get();
      job.remove();

    } finally {
      if( fr  != null ) fr .remove();
      if( kmm != null ) kmm.delete();
    }
  }

  @Test public void testInts() {
    QuantileModel kmm = null;
    Frame fr = null;
    try {
      fr = frame(new double[][]{{0},{0},{0},{0},{0},{0},{0},{0},{0},{0},
                                {1},{1},{1},{1},{1},{1},{1},{1},{1},{1},
                                {2},{2},{2},{2},{2},{2},{2},{2},{2},{2}});

      QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
      parms._train = fr._key;

      Quantile job = new Quantile(parms).trainModel();
      kmm = job.get();
      job.remove();

    } finally {
      if( fr  != null ) fr .remove();
      if( kmm != null ) kmm.delete();
    }
  }
}
