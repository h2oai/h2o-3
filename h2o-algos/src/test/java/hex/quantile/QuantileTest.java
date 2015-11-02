package hex.quantile;

import org.junit.*;
import water.Job;
import water.TestUtil;
import water.fvec.Frame;
import water.util.ArrayUtils;

public class QuantileTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void testIris() {
    QuantileModel kmm = null;
    Frame fr = null;
    try {
      long start = System.currentTimeMillis();
      System.out.println("Start Parse");
      fr = parse_test_file("smalldata/iris/iris_wheader.csv");
      //fr = parse_test_file("../../datasets/UCI/UCI-large/covtype/covtype.data");
      //fr = parse_test_file("../../datasets/billion_rows.csv.gz");
      System.out.println("Done Parse: "+(System.currentTimeMillis()-start));

      QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
      parms._train = fr._key;
      //parms._max_iterations = 10;

      Job<QuantileModel> job = new Quantile(parms).trainModel();
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
      fr = ArrayUtils.frame(new double[][]{{0}, {0}, {0}, {0}, {0}, {0}, {0}, {0}, {0}, {0},
                                            {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1}, {1},
                                            {2}, {2}, {2}, {2}, {2}, {2}, {2}, {2}, {2}, {2}});

      QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
      parms._train = fr._key;

      Job<QuantileModel> job = new Quantile(parms).trainModel();
      kmm = job.get();
      job.remove();

    } finally {
      if( fr  != null ) fr .remove();
      if( kmm != null ) kmm.delete();
    }
  }

  @Test public void test50pct() {
    QuantileModel kmm = null;
    Frame fr = null;
    try {
      double[][] d = new double[][]{{1.56386606237}, {0.812834256224}, {3.68417563302}, {3.12702210880}, {5.51277746586}};
      fr = ArrayUtils.frame(d);
      QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
      parms._train = fr._key;
      Job<QuantileModel> job = new Quantile(parms).trainModel();
      kmm = job.get();
      job.remove();
      Assert.assertTrue(kmm._output._quantiles[0][5]==d[3][0]);

    } finally {
      if( fr  != null ) fr .remove();
      if( kmm != null ) kmm.delete();
    }
  }
}
