package hex.svd;

import hex.DataInfo;
import hex.svd.SVDModel.SVDParameters;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Ignore;
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
  public static final double TOLERANCE = 1e-6;

  @BeforeClass public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test public void testArrestsSVD() throws InterruptedException, ExecutionException {
    // Expected right singular values and vectors
    double[] sdev_expected = new double[] {202.723056, 27.832264, 6.523048, 2.581365};
    double[] d_expected = new double[] {1419.06139510, 194.82584611, 45.66133763, 18.06955662};
    double[][] v_expected = ard(ard(-0.04239181,  0.01616262, -0.06588426,  0.99679535),
                                ard(-0.94395706,  0.32068580,  0.06655170, -0.04094568),
                                ard(-0.30842767, -0.93845891,  0.15496743,  0.01234261),
                                ard(-0.10963744, -0.12725666, -0.98347101, -0.06760284));
    SVDModel model = null;
    Frame train = null;
    try {
      train = parse_test_file(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      SVDModel.SVDParameters parms = new SVDModel.SVDParameters();
      parms._train = train._key;
      parms._nv = 4;
      parms._seed = 1234;
      parms._only_v = false;

      SVD job = new SVD(parms);
      try {
        model = job.trainModel().get();
        TestUtil.checkEigvec(v_expected, model._output._v, TOLERANCE);
        Assert.assertArrayEquals(d_expected, model._output._d, TOLERANCE);
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
      if (model != null) {
        if (model._parms._keep_u)
          model._parms._u_key.get().delete();
        model.delete();
      }
    }
  }

  @Test public void testArrestsOnlyV() throws InterruptedException, ExecutionException {
    // Expected right singular vectors
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
      parms._nv = 4;
      parms._seed = 1234;
      parms._only_v = true;

      SVD job = new SVD(parms);
      try {
        model = job.trainModel().get();
        TestUtil.checkEigvec(svec, model._output._v, TOLERANCE);
        assert model._output._d == null;
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

  @Test public void testArrestsScoring() throws InterruptedException, ExecutionException {
    double[] stddev = new double[] {202.7230564, 27.8322637, 6.5230482, 2.5813652};
    double[][] eigvec = ard(ard(-0.04239181, 0.01616262, -0.06588426, 0.99679535),
            ard(-0.94395706, 0.32068580, 0.06655170, -0.04094568),
            ard(-0.30842767, -0.93845891, 0.15496743, 0.01234261),
            ard(-0.10963744, -0.12725666, -0.98347101, -0.06760284));

    SVD job = null;
    SVDModel model = null;
    Frame train = null, score = null, scoreR = null;
    try {
      train = parse_test_file(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      SVDModel.SVDParameters parms = new SVDModel.SVDParameters();
      parms._train = train._key;
      parms._nv = 4;
      parms._transform = DataInfo.TransformType.NONE;
      parms._only_v = false;
      parms._keep_u = false;

      try {
        job = new SVD(parms);
        model = job.trainModel().get();
        boolean[] flippedEig = TestUtil.checkEigvec(eigvec, model._output._v, TOLERANCE);

        score = model.score(train);
        scoreR = parse_test_file(Key.make("scoreR.hex"), "smalldata/pca_test/USArrests_PCAscore.csv");
        TestUtil.checkProjection(scoreR, score, TOLERANCE, flippedEig);    // Flipped cols must match those from eigenvectors
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        if (job != null) job.remove();
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      if (train != null) train.delete();
      if (score != null) score.delete();
      if (scoreR != null) scoreR.delete();
      if (model != null) model.delete();
    }
  }

  @Test @Ignore public void testArrestsMissing() throws InterruptedException, ExecutionException {
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
        parms._nv = train.numCols();
        parms._transform = DataInfo.TransformType.STANDARDIZE;
        parms._max_iterations = 1000;
        parms._seed = seed;

        SVD job = new SVD(parms);
        try {
          model = job.trainModel().get();
          Log.info(100 * missing_fraction + "% missing values: Singular values = " + Arrays.toString(model._output._d));
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
        if (model != null) {
          model._parms._u_key.get().delete();
          model.delete();
        }
      }
    }
  }

  @Test @Ignore public void testIrisSVD() throws InterruptedException, ExecutionException {
    // Expected right singular values and vectors
    double[] d_expected = new double[] {96.2182677, 19.0780817, 7.2270380, 3.1502152, 1.8849634, 1.1474731, 0.5814097};
    double[][] v_expected = ard(ard(-0.03170407, -0.32247642, 0.184499940, -0.12245506, -0.14348647, 0.76017824, -0.497502783),
                                ard(-0.04288799,  0.04054823, -0.780684855,  0.19827972,  0.07363250, -0.12354271, -0.571881302),
                                ard(-0.05018593,  0.16796988,  0.551546107, -0.07177990,  0.08109974, -0.48442099, -0.647048040),
                                ard(-0.74909171, -0.26482593, -0.102057243, -0.49272847,  0.31969417, -0.09379235,  0.067308615),
                                ard(-0.37906736, -0.50824062,  0.142810811,  0.69139828, -0.25849391, -0.17606099,  0.041908520),
                                ard(-0.51162932,  0.65943465, -0.004420703,  0.05479795, -0.51969015,  0.17392232,  0.005674672),
                                ard(-0.16745698,  0.32071102,  0.146484369,  0.46553390,  0.72685033,  0.31962337,  0.021274748));
    SVDModel model = null;
    Frame train = null;
    try {
      train = parse_test_file(Key.make("iris.hex"), "smalldata/iris/iris_wheader.csv");
      SVDModel.SVDParameters parms = new SVDModel.SVDParameters();
      parms._train = train._key;
      parms._nv = 7;
      parms._seed = 1234;
      parms._useAllFactorLevels = true;
      parms._only_v = false;

      SVD job = new SVD(parms);
      try {
        model = job.trainModel().get();
        TestUtil.checkEigvec(v_expected, model._output._v, TOLERANCE);
        Assert.assertArrayEquals(d_expected, model._output._d, TOLERANCE);
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
      if (model != null) {
        if (model._parms._keep_u)
          model._parms._u_key.get().delete();
        model.delete();
      }
    }
  }
}
