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
      if (train != null) train.delete();
      if (model != null) {
        model._parms._loading_key.get().delete();
        model.delete();
      }
    }
  }
}
