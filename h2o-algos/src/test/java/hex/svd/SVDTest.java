package hex.svd;

import hex.DataInfo;
import hex.svd.SVDModel.SVDParameters;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.FrameUtils;
import water.util.Log;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public class SVDTest extends TestUtil {
  public final double TOLERANCE = 1e-6;

  @BeforeClass public static void setup() {
    stall_till_cloudsize(1);
  }

  public boolean[] checkEigvec(double[][] expected, double[][] actual) { return checkEigvec(expected, actual, TOLERANCE); }
  public boolean[] checkEigvec(double[][] expected, double[][] actual, double threshold) {
    int nfeat = actual.length;
    int ncomp = actual[0].length;
    boolean[] flipped = new boolean[ncomp];

    for(int j = 0; j < ncomp; j++) {
      flipped[j] = Math.abs(expected[0][j] - actual[0][j]) > threshold;
      for(int i = 0; i < nfeat; i++) {
        Assert.assertEquals(expected[i][j], flipped[j] ? -actual[i][j] : actual[i][j], threshold);
      }
    }
    return flipped;
  }

  @Test public void testPowerMethod() throws InterruptedException, ExecutionException {
    // Expected right singular values
    double[] sval = new double[] {1419.06139510, 194.82584611, 45.66133763, 18.06955662};
    double[][] svec = ard(ard(-0.04239181,  0.01616262, -0.06588426,  0.99679535),
                      ard(-0.94395706,  0.32068580,  0.06655170, -0.04094568),
                      ard(-0.30842767, -0.93845891,  0.15496743,  0.01234261),
                      ard(-0.10963744, -0.12725666, -0.98347101, -0.06760284));
    SVDModel model = null;
    Frame train = null;
    try {
      train = parse_test_file(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      SVDModel.SVDParameters parms = new SVDModel.SVDParameters();
      parms._train = train._key;
      parms._k = 4;
      parms._seed = 1234;

      SVD job = new SVD(parms);
      try {
        model = job.trainModel().get();
        checkEigvec(svec, model._output._v);
        Assert.assertArrayEquals(sval, model._output._singular_vals, TOLERANCE);
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        job.remove();
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      if (train != null) train.delete();
      if (model != null) model.delete();
    }
  }

  @Test public void testMissingVals() throws InterruptedException, ExecutionException {
    SVDModel model = null;
    SVDParameters parms = null;
    Frame train = null;
    long seed = 1234;

    for (double missing_fraction : new double[]{0, 0.1, 0.25, 0.5, 0.75, 0.9}) {
      try {
        Scope.enter();
        train = parse_test_file(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");

        // Add missing values to the training data
        if (missing_fraction > 0) {
          Frame frtmp = new Frame(Key.make(), train.names(), train.vecs());
          DKV.put(frtmp._key, frtmp); // Need to put the frame (to be modified) into DKV for MissingInserter to pick up
          FrameUtils.MissingInserter j = new FrameUtils.MissingInserter(frtmp._key, seed, missing_fraction);
          j.execImpl();
          j.get(); // MissingInserter is non-blocking, must block here explicitly
          DKV.remove(frtmp._key); // Delete the frame header (not the data)
        }

        parms = new SVDParameters();
        parms._train = train._key;
        parms._k = train.numCols();
        parms._transform = DataInfo.TransformType.STANDARDIZE;
        parms._max_iterations = 1000;
        parms._seed = seed;

        SVD job = new SVD(parms);
        try {
          model = job.trainModel().get();
          Log.info(100 * missing_fraction + "% missing values: Singular values = " + Arrays.toString(model._output._singular_vals));
        } catch (Throwable t) {
          t.printStackTrace();
          throw new RuntimeException(t);
        } finally {
          job.remove();
        }
        Scope.exit();
      } catch(Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        if (train != null) train.delete();
        if (model != null) model.delete();
      }
    }
  }
}