package hex.quantile;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.Job;
import water.TestUtil;
import water.fvec.Frame;
import water.util.ArrayUtils;

import java.util.Arrays;

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
      Assert.assertTrue(kmm._output._quantiles[0][5] == d[3][0]);

    } finally {
      if( fr  != null ) fr .remove();
      if( kmm != null ) kmm.delete();
    }
  }

  @Test public void testShuffled() {
    QuantileModel kmm1;
    QuantileModel kmm2;
    Frame fr1 = null;
    Frame fr2 = null;
    try {
      fr1 = parse_test_file("smalldata/junit/no_weights.csv");
      fr2 = parse_test_file("smalldata/junit/no_weights_shuffled.csv");

      for (QuantileModel.CombineMethod comb : new QuantileModel.CombineMethod[]{
              QuantileModel.CombineMethod.AVERAGE,
              QuantileModel.CombineMethod.LOW,
              QuantileModel.CombineMethod.HIGH,
              QuantileModel.CombineMethod.INTERPOLATE
      }) {
        {
          QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
          parms._train = fr1._key;
          parms._combine_method = comb;
          Job<QuantileModel> job1 = new Quantile(parms).trainModel();
          kmm1 = job1.get();
          job1.remove();
        }

        {
          QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
          parms._train = fr2._key;
          parms._combine_method = comb;
          Job<QuantileModel> job2 = new Quantile(parms).trainModel();
          kmm2 = job2.get();
          job2.remove();
        }

        Assert.assertTrue(Arrays.deepEquals(kmm1._output._quantiles, kmm2._output._quantiles));
        if( kmm1 != null ) kmm1.delete();
        if( kmm2 != null ) kmm2.delete();
      }

    } finally {
      if( fr1  != null ) fr1.remove();
      if( fr2  != null ) fr2.remove();
    }
  }

  @Test public void testWeights0() {
    QuantileModel kmm1;
    QuantileModel kmm2;
    Frame fr1 = null;
    Frame fr2 = null;
    try {
      fr1 = parse_test_file("smalldata/junit/no_weights.csv");
      fr2 = parse_test_file("smalldata/junit/weights_all_ones.csv");

      for (QuantileModel.CombineMethod comb : new QuantileModel.CombineMethod[]{
              QuantileModel.CombineMethod.AVERAGE,
              QuantileModel.CombineMethod.LOW,
              QuantileModel.CombineMethod.HIGH,
              QuantileModel.CombineMethod.INTERPOLATE
      }) {
        {
          QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
          parms._train = fr1._key;
          parms._combine_method = comb;
          parms._weights_column = null;
          Job<QuantileModel> job1 = new Quantile(parms).trainModel();
          kmm1 = job1.get();
          job1.remove();
        }

        {
          QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
          parms._train = fr2._key;
          parms._combine_method = comb;
          parms._weights_column = "weight";
          Job<QuantileModel> job2 = new Quantile(parms).trainModel();
          kmm2 = job2.get();
          job2.remove();
        }

        Assert.assertTrue(Arrays.deepEquals(kmm1._output._quantiles, kmm2._output._quantiles));
        if( kmm1 != null ) kmm1.delete();
        if( kmm2 != null ) kmm2.delete();
      }

    } finally {
      if( fr1  != null ) fr1.remove();
      if( fr2  != null ) fr2.remove();
    }
  }

  @Test public void testWeights1() {
    QuantileModel kmm1;
    QuantileModel kmm2;
    Frame fr1 = null;
    Frame fr2 = null;
    try {
      fr1 = parse_test_file("smalldata/junit/no_weights.csv");
      fr2 = parse_test_file("smalldata/junit/weights.csv");

      for (QuantileModel.CombineMethod comb : new QuantileModel.CombineMethod[]{
              QuantileModel.CombineMethod.AVERAGE,
              QuantileModel.CombineMethod.LOW,
              QuantileModel.CombineMethod.HIGH,
              QuantileModel.CombineMethod.INTERPOLATE
      }) {
        {
          QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
          parms._train = fr1._key;
          parms._combine_method = comb;
          parms._weights_column = null;
          Job<QuantileModel> job1 = new Quantile(parms).trainModel();
          kmm1 = job1.get();
          job1.remove();
        }

        {
          QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
          parms._train = fr2._key;
          parms._combine_method = comb;
          parms._weights_column = "weight";
          Job<QuantileModel> job2 = new Quantile(parms).trainModel();
          kmm2 = job2.get();
          job2.remove();
        }

        Assert.assertTrue(Arrays.deepEquals(kmm1._output._quantiles, kmm2._output._quantiles));
        if( kmm1 != null ) kmm1.delete();
        if( kmm2 != null ) kmm2.delete();
      }

    } finally {
      if( fr1  != null ) fr1.remove();
      if( fr2  != null ) fr2.remove();
    }
  }

  @Ignore @Test public void testWeights2() {
    QuantileModel kmm1;
    QuantileModel kmm2;
    Frame fr1 = null;
    Frame fr2 = null;
    try {
      fr1 = parse_test_file("smalldata/junit/weights_all_twos.csv");
      fr2 = parse_test_file("smalldata/junit/weights_all_ones.csv");

      for (QuantileModel.CombineMethod comb : new QuantileModel.CombineMethod[]{
              QuantileModel.CombineMethod.AVERAGE,
              QuantileModel.CombineMethod.LOW,
              QuantileModel.CombineMethod.HIGH,
              QuantileModel.CombineMethod.INTERPOLATE
      }) {
        {
          QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
          parms._train = fr1._key;
          parms._combine_method = comb;
          parms._weights_column = "weight";
          Job<QuantileModel> job1 = new Quantile(parms).trainModel();
          kmm1 = job1.get();
          job1.remove();
        }

        {
          QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
          parms._train = fr2._key;
          parms._combine_method = comb;
          parms._weights_column = "weight";
          Job<QuantileModel> job2 = new Quantile(parms).trainModel();
          kmm2 = job2.get();
          job2.remove();
        }

        Assert.assertTrue(Arrays.deepEquals(kmm1._output._quantiles, kmm2._output._quantiles));
        if( kmm1 != null ) kmm1.delete();
        if( kmm2 != null ) kmm2.delete();
      }

    } finally {
      if( fr1  != null ) fr1.remove();
      if( fr2  != null ) fr2.remove();
    }
  }

  @Ignore @Test public void testWeights3() {
    QuantileModel kmm1;
    QuantileModel kmm2;
    Frame fr1 = null;
    Frame fr2 = null;
    try {
      fr1 = parse_test_file("smalldata/junit/weights_all_tiny.csv");
      fr2 = parse_test_file("smalldata/junit/weights_all_ones.csv");

      for (QuantileModel.CombineMethod comb : new QuantileModel.CombineMethod[]{
              QuantileModel.CombineMethod.AVERAGE,
              QuantileModel.CombineMethod.LOW,
              QuantileModel.CombineMethod.HIGH,
              QuantileModel.CombineMethod.INTERPOLATE
      }) {
        {
          QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
          parms._train = fr1._key;
          parms._combine_method = comb;
          parms._weights_column = "weight";
          Job<QuantileModel> job1 = new Quantile(parms).trainModel();
          kmm1 = job1.get();
          job1.remove();
        }

        {
          QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
          parms._train = fr2._key;
          parms._combine_method = comb;
          parms._weights_column = "weight";
          Job<QuantileModel> job2 = new Quantile(parms).trainModel();
          kmm2 = job2.get();
          job2.remove();
        }

        Assert.assertTrue(Arrays.deepEquals(kmm1._output._quantiles, kmm2._output._quantiles));
        if( kmm1 != null ) kmm1.delete();
        if( kmm2 != null ) kmm2.delete();
      }

    } finally {
      if( fr1  != null ) fr1.remove();
      if( fr2  != null ) fr2.remove();
    }
  }
}
