package hex.glrm;

import hex.DataInfo;
import hex.glrm.GLRMModel.GLRMParameters;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;

import java.util.concurrent.ExecutionException;

public class GLRMTest extends TestUtil {
  public final double TOLERANCE = 1e-6;
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  public void checkStddev(double[] expected, double[] actual) { checkStddev(expected, actual, TOLERANCE); }
  public void checkStddev(double[] expected, double[] actual, double threshold) {
    for(int i = 0; i < actual.length; i++)
      Assert.assertEquals(expected[i], actual[i], threshold);
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

  @Test public void testArrests() throws InterruptedException, ExecutionException {
    // Initialize using first k rows of training frame
    Frame yinit = frame(ard(ard(13.2, 236, 58, 21.2),
            ard(10.0, 263, 48, 44.5),
            ard(8.1, 294, 80, 31.0),
            ard(8.8, 190, 50, 19.5)));

    double[] stddev = new double[] {202.7230564, 27.8322637, 6.5230482, 2.5813652};
    double[][] eigvec = ard(ard(-0.04239181, 0.01616262, -0.06588426, 0.99679535),
            ard(-0.94395706, 0.32068580, 0.06655170, -0.04094568),
            ard(-0.30842767, -0.93845891, 0.15496743, 0.01234261),
            ard(-0.10963744, -0.12725666, -0.98347101, -0.06760284));

    GLRM job = null;
    GLRMModel model = null;
    Frame train = null;
    try {
      train = parse_test_file(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._k = 4;
      parms._loss = GLRMParameters.Loss.L2;
      parms._gamma = 0;
      parms._transform = DataInfo.TransformType.NONE;
      parms._user_points = yinit._key;

      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
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
      yinit.delete();
      if (train != null) train.delete();
      if (model != null) {
        model._parms._loading_key.get().delete();
        model.delete();
      }
    }
  }

  @Test public void testArrestsPCA() throws InterruptedException, ExecutionException {
    // Initialize using first k rows of standardized training frame
    Frame yinit = frame(ard(ard(1.24256408, 0.7828393, -0.5209066, -0.003416473),
            ard(0.50786248, 1.1068225, -1.2117642, 2.484202941),
            ard(0.07163341, 1.4788032, 0.9989801, 1.042878388),
            ard(0.23234938, 0.2308680, -1.0735927, -0.184916602)));
    double[] stddev = new double[] {1.5748783, 0.9948694, 0.5971291, 0.4164494};
    double[][] eigvec = ard(ard(-0.5358995, 0.4181809, -0.3412327, 0.64922780),
            ard(-0.5831836, 0.1879856, -0.2681484, -0.74340748),
            ard(-0.2781909, -0.8728062, -0.3780158, 0.13387773),
            ard(-0.5434321, -0.1673186, 0.8177779, 0.08902432));

    GLRMModel model = null;
    Frame train = null;
    try {
      train = parse_test_file(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      GLRMModel.GLRMParameters parms = new GLRMModel.GLRMParameters();
      parms._train = train._key;
      parms._k = 4;
      parms._gamma = 0;
      parms._transform = DataInfo.TransformType.STANDARDIZE;
      parms._max_iterations = 1000;
      parms._user_points = yinit._key;
      parms._recover_pca = true;

      GLRM job = new GLRM(parms);
      try {
        model = job.trainModel().get();
        checkStddev(stddev, model._output._std_deviation);
        checkEigvec(eigvec, model._output._eigenvectors_raw);
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
      yinit.delete();
      if (train != null) train.delete();
      if (model != null) {
        model._parms._loading_key.get().delete();
        model.delete();
      }
    }
  }
}
