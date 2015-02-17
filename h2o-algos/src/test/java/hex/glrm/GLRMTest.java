package hex.glrm;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;

import java.util.concurrent.ExecutionException;

public class GLRMTest extends TestUtil {
  @BeforeClass
  public static void setup() { stall_till_cloudsize(1); }

  @Test public void testArrests() throws InterruptedException, ExecutionException {
    GLRM job = null;
    GLRMModel model = null;
    Frame fr = null;

    try {
      Key ksrc = Key.make("arrests.hex");
      fr = parse_test_file(ksrc, "smalldata/pca_test/USArrests.csv");

      GLRMModel.GLRMParameters parms = new GLRMModel.GLRMParameters();
      parms._train = fr._key;
      parms._k = 4;
      parms._gamma = 0;
      parms._standardize = true;
      parms._max_iterations = 1;

      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
      } finally {
        if (job != null) job.remove();
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
