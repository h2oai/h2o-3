package hex.glrm;

import hex.DataInfo;
import hex.gram.Gram;
import hex.gram.Gram.Cholesky;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.util.ArrayUtils;

import java.util.concurrent.ExecutionException;

public class GLRMTest extends TestUtil {
  public final double threshold = 0.000001;
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  public void checkStddev(double[] expected, double[] actual) {
    for(int i = 0; i < actual.length; i++)
      Assert.assertEquals(expected[i], actual[i], threshold);
  }

  public void checkEigvec(double[][] expected, double[][] actual) {
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

  @Test public void testArrests() throws InterruptedException, ExecutionException {
    double[] stddev = new double[] {83.732400, 14.212402, 6.489426, 2.482790};
    double[][] eigvec = ard(ard(0.04170432, -0.04482166, 0.07989066, -0.99492173),
                            ard(0.99522128, -0.05876003, -0.06756974, 0.03893830),
                            ard(0.04633575, 0.97685748, -0.20054629, -0.05816914),
                            ard(0.07515550, 0.20071807, 0.97408059, 0.07232502));

    double[] stddev_std = new double[] {1.5748783, 0.9948694, 0.5971291, 0.4164494};
    double[][] eigvec_std = ard(ard(-0.5358995, 0.4181809, -0.3412327, 0.64922780),
                                ard(-0.5831836, 0.1879856, -0.2681484, -0.74340748),
                                ard(-0.2781909, -0.8728062, -0.3780158, 0.13387773),
                                ard(-0.5434321, -0.1673186, 0.8177779, 0.08902432));
    GLRM job = null;
    GLRMModel model = null;
    Frame fr = null;
    try {
      Key ksrc = Key.make("arrests.hex");
      fr = parse_test_file(ksrc, "smalldata/pca_test/USArrests.csv");

      for (DataInfo.TransformType std : new DataInfo.TransformType[] {
              DataInfo.TransformType.DEMEAN,
              DataInfo.TransformType.STANDARDIZE }) {
        GLRMModel.GLRMParameters parms = new GLRMModel.GLRMParameters();
        parms._train = fr._key;
        parms._k = 4;
        parms._gamma = 0;
        parms._transform = std;
        parms._max_iterations = 1000;

        try {
          job = new GLRM(parms);
          model = job.trainModel().get();
        } finally {
          if (job != null) job.remove();
        }
        if (std == DataInfo.TransformType.DEMEAN) {
          checkStddev(stddev, model._output._std_deviation);
          checkEigvec(eigvec, model._output._eigenvectors);
        } else {
          checkStddev(stddev_std, model._output._std_deviation);
          checkEigvec(eigvec_std, model._output._eigenvectors);
        }
      }
    } finally {
      if( fr    != null ) fr   .delete();
      if( model != null ) {
        DKV.remove(model._parms._loading_key);
        model.delete();
      }
    }
  }

  @Test public void testGram() {
    double[][] x = ard(ard(1, 2, 3), ard(4, 5, 6));
    double[][] xgram = ard(ard(17, 22, 27), ard(22, 29, 36), ard(27, 36, 45));  // X'X
    double[][] xtgram = ard(ard(14, 32), ard(32, 77));    // (X')'X' = XX'

    double[][] xgram_glrm = GLRM.formGram(x, false);
    double[][] xtgram_glrm = GLRM.formGram(x, true);
    Assert.assertArrayEquals(xgram, xgram_glrm);
    Assert.assertArrayEquals(xtgram, xtgram_glrm);
  }
}
