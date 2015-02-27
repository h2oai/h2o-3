package hex.pca;

import hex.DataInfo;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;

public class PCATest extends TestUtil {
  public final double TOLERANCE = 1e-6;
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  public void checkStddev(double[] expected, double[] actual) {
    checkStddev(expected, actual, TOLERANCE);
  }
  public void checkStddev(double[] expected, double[] actual, double threshold) {
    for(int i = 0; i < actual.length; i++)
      Assert.assertEquals(expected[i], actual[i], threshold);
  }

  public void checkEigvec(double[][] expected, double[][] actual) {
    checkEigvec(expected, actual, TOLERANCE);
  }
  public void checkEigvec(double[][] expected, double[][] actual, double threshold) {
    int nfeat = actual.length;
    int ncomp = actual[0].length;
    for(int j = 0; j < ncomp; j++) {
      boolean flipped = Math.abs(expected[0][j] - actual[0][j]) > threshold;
      for(int i = 0; i < nfeat; i++) {
        if(flipped)
          Assert.assertEquals(expected[i][j], -actual[i][j], threshold);
        else
          Assert.assertEquals(expected[i][j], actual[i][j], threshold);
      }
    }
  }

  public void checkProjection(Frame expected, Frame actual, double threshold) {
    assert expected.numCols() == actual.numCols();
    for(int j = 0; j < expected.numCols(); j++) {
      Vec vexp = expected.vec(j);
      Vec vact = actual.vec(j);
      Assert.assertEquals(vexp.length(), vact.length());
      boolean flipped = Math.abs(vexp.at8(0) - vact.at8(0)) > threshold;
      for (int i = 0; i < vexp.length(); i++) {
        if(flipped)
          Assert.assertEquals(vexp.at8(i), -vact.at8(i), threshold);
        else
          Assert.assertEquals(vexp.at8(i), vact.at8(i), threshold);
      }
    }
  }

  @Test public void testArrests() throws InterruptedException, ExecutionException {
    // Initialize using first k rows of de-meaned training frame
    Frame yinit = frame(ard(ard(5.412, 65.24, -7.54, -0.032),
                            ard(2.212, 92.24, -17.54, 23.268),
                            ard(0.312, 123.24, 14.46, 9.768),
                            ard(1.012, 19.24, -15.54, -1.732)));
    double[] stddev = new double[] {83.732400, 14.212402, 6.489426, 2.482790};
    double[][] eigvec = ard(ard(0.04170432, -0.04482166, 0.07989066, -0.99492173),
                            ard(0.99522128, -0.05876003, -0.06756974, 0.03893830),
                            ard(0.04633575, 0.97685748, -0.20054629, -0.05816914),
                            ard(0.07515550, 0.20071807, 0.97408059, 0.07232502));

    // Initialize using first k rows of standardized training frame
    Frame yinit_std = frame(ard(ard(1.24256408, 0.7828393, -0.5209066, -0.003416473),
                                ard(0.50786248, 1.1068225, -1.2117642, 2.484202941),
                                ard(0.07163341, 1.4788032, 0.9989801, 1.042878388),
                                ard(0.23234938, 0.2308680, -1.0735927, -0.184916602)));
    double[] stddev_std = new double[] {1.5748783, 0.9948694, 0.5971291, 0.4164494};
    double[][] eigvec_std = ard(ard(-0.5358995, 0.4181809, -0.3412327, 0.64922780),
                                ard(-0.5831836, 0.1879856, -0.2681484, -0.74340748),
                                ard(-0.2781909, -0.8728062, -0.3780158, 0.13387773),
                                ard(-0.5434321, -0.1673186, 0.8177779, 0.08902432));

    Frame train = null;
    try {
      for (DataInfo.TransformType std : new DataInfo.TransformType[] {
              DataInfo.TransformType.DEMEAN,
              DataInfo.TransformType.STANDARDIZE }) {
        PCAModel model = null;
        train = parse_test_file(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");   // TODO: Move this outside loop
        try {
          PCAModel.PCAParameters parms = new PCAModel.PCAParameters();
          parms._train = train._key;
          parms._k = 4;
          parms._gamma = 0;
          parms._transform = std;
          parms._max_iterations = 1000;
          parms._user_points = (std == DataInfo.TransformType.DEMEAN) ? yinit._key : yinit_std._key;

          PCA job = new PCA(parms);
          try {
            model = job.trainModel().get();
          } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException(t);
          } finally {
            job.remove();
          }

          if (std == DataInfo.TransformType.DEMEAN) {
            checkStddev(stddev, model._output._std_deviation);
            checkEigvec(eigvec, model._output._eigenvectors_raw);
          } else if (std == DataInfo.TransformType.STANDARDIZE) {
            checkStddev(stddev_std, model._output._std_deviation);
            checkEigvec(eigvec_std, model._output._eigenvectors_raw);
          }
        } catch (Throwable t) {
          t.printStackTrace();
          throw new RuntimeException(t);
        } finally {
          if( model != null ) {
            model._parms._loading_key.get().delete();
            model.delete();
          }
        }
      }
    } finally {
      yinit    .delete();
      yinit_std.delete();
      if(train != null) train.delete();
    }
  }

  @Test public void testArrestsScoring() {
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

    PCA job = null;
    PCAModel model = null;
    Frame train = null, score = null, scoreR = null;
    try {
      train = parse_test_file(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      PCAModel.PCAParameters parms = new PCAModel.PCAParameters();
      parms._train = train._key;
      parms._k = 4;
      parms._gamma = 0;
      parms._transform = DataInfo.TransformType.NONE;

      try {
        job = new PCA(parms);
        model = job.trainModel().get();
        checkStddev(stddev, model._output._std_deviation, 1e-5);
        checkEigvec(eigvec, model._output._eigenvectors_raw, 1e-5);

        scoreR = parse_test_file(Key.make("scoreR.hex"), "smalldata/pca_test/USArrests_PCAscore.csv");
        score = model.score(train);
        checkProjection(scoreR, score, TOLERANCE);
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
      yinit.delete();
      if (train != null) train.delete();
      if (score != null) score.delete();
      if (scoreR != null) scoreR.delete();
      if (model != null) {
        model._parms._loading_key.get().delete();
        model.delete();
      }
    }
  }

  @Test public void testCholeskyRegularization() {
    PCA job = null;
    PCAModel model = null;
    Frame train = null;

    try {
      train = parse_test_file(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      PCAModel.PCAParameters parms = new PCAModel.PCAParameters();
      parms._train = train._key;
      parms._k = 4;
      parms._gamma = 0;
      parms._transform = DataInfo.TransformType.STANDARDIZE;
      parms._max_iterations = 0;
      parms._seed = 1234;

      try {
        job = new PCA(parms);
        model = job.trainModel().get();
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
      if (model != null) {
        model._parms._loading_key.get().delete();
        model.delete();
      }
    }
  }

  @Test public void testGram() {
    double[][] x = ard(ard(1, 2, 3), ard(4, 5, 6));
    double[][] xgram = ard(ard(17, 22, 27), ard(22, 29, 36), ard(27, 36, 45));  // X'X
    double[][] xtgram = ard(ard(14, 32), ard(32, 77));    // (X')'X' = XX'

    double[][] xgram_glrm = PCA.formGram(x, false);
    double[][] xtgram_glrm = PCA.formGram(x, true);
    Assert.assertArrayEquals(xgram, xgram_glrm);
    Assert.assertArrayEquals(xtgram, xtgram_glrm);
  }
}
