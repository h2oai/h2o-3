package water;

import hex.CreateFrame;
import org.junit.BeforeClass;
import org.junit.Test;
import water.fvec.Frame;
import water.util.*;

public class FFTTest extends TestUtil {
  @BeforeClass()
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void run() {
    Frame frame = null;
    Frame frameFFT = null;
    Frame frameRec = null;
    try {
      CreateFrame cf = new CreateFrame();
      cf.rows = 1000;
      cf.cols = 1000;
      cf.categorical_fraction = 0.0;
      cf.integer_fraction = 0;
      cf.binary_fraction = 0;
      cf.missing_fraction = 0;
      cf.factors = 0;
      cf.seed = 1234;
      cf.execImpl();
      cf.get();
      frame = DKV.getGet(cf.dest());
      long now = System.currentTimeMillis();
      frameFFT = MathUtils.FFT.transform1D(frame, cf.cols, false);
      Log.info("Computed 1D DFT of " + cf.rows + " rows of size " + cf.cols + " in" + PrettyPrint.msecs(System.currentTimeMillis() - now, true));

      now = System.currentTimeMillis();
      frameRec = MathUtils.FFT.transform1D(frameFFT, cf.cols, true);
      Log.info("Computed inverse 1D DFT of " + cf.rows + " rows of size " + cf.cols + " in" + PrettyPrint.msecs(System.currentTimeMillis() - now, true));

      for (int i=0; i<frame.vecs().length; ++i)
        TestUtil.assertVecEquals(frame.vecs()[i], frameRec.vecs()[i], 1e-5);
      Log.info("Identity test passed: FFT^-1(FFT(frame)) == frame");
    } finally {
      if (frame!=null) frame.delete();
      if (frameFFT!=null) frameFFT.delete();
      if (frameRec!=null) frameRec.delete();
    }
  }

}
