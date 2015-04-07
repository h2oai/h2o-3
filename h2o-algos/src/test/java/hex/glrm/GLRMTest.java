package hex.glrm;

import hex.DataInfo;
import hex.glrm.GLRMModel.GLRMParameters;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;

import java.util.concurrent.ExecutionException;

public class GLRMTest extends TestUtil {
  public final double TOLERANCE = 1e-6;
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

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
}
