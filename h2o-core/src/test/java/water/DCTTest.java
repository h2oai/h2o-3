package water;

import hex.CreateFrame;
import org.junit.BeforeClass;
import org.junit.Test;
import water.fvec.Frame;
import water.util.*;

public class DCTTest extends TestUtil {
  @BeforeClass()
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void DCT_1D() {
    Frame frame = null;
    Frame frameDCT = null;
    Frame frameRec = null;
    try {
      CreateFrame cf = new CreateFrame();
      cf.rows = 100;
      int height = 513;
      int width = 1;
      int depth = 1;
      cf.cols = height*width*depth;
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
      frameDCT = MathUtils.DCT.transform1D(frame, height, false);
      Log.info("Computed 1D DCT of " + cf.rows + " rows of size " + cf.cols + " in" + PrettyPrint.msecs(System.currentTimeMillis() - now, true));

      now = System.currentTimeMillis();
      frameRec = MathUtils.DCT.transform1D(frameDCT, height, true);
      Log.info("Computed inverse 1D DCT of " + cf.rows + " rows of size " + cf.cols + " in" + PrettyPrint.msecs(System.currentTimeMillis() - now, true));

      for (int i=0; i<frame.vecs().length; ++i)
        TestUtil.assertVecEquals(frame.vecs()[i], frameRec.vecs()[i], 1e-5);
      Log.info("Identity test passed: DCT^-1(DCT(frame)) == frame");
    } finally {
      if (frame!=null) frame.delete();
      if (frameDCT!=null) frameDCT.delete();
      if (frameRec!=null) frameRec.delete();
    }
  }

  @Test
  public void DCT_2D() {
    Frame frame = null;
    Frame frameDCT = null;
    Frame frameRec = null;
    try {
      CreateFrame cf = new CreateFrame();
      cf.rows = 100;
      int height = 47;
      int width = 29;
      int depth = 1;
      cf.cols = height*width*depth;
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
      frameDCT = MathUtils.DCT.transform2D(frame, height, width, false);
      Log.info("Computed 2D DCT of " + cf.rows + " rows of size " + cf.cols + " in" + PrettyPrint.msecs(System.currentTimeMillis() - now, true));

      now = System.currentTimeMillis();
      frameRec = MathUtils.DCT.transform2D(frameDCT, height, width, true);
      Log.info("Computed inverse 2D DCT of " + cf.rows + " rows of size " + cf.cols + " in" + PrettyPrint.msecs(System.currentTimeMillis() - now, true));

      for (int i=0; i<frame.vecs().length; ++i)
        TestUtil.assertVecEquals(frame.vecs()[i], frameRec.vecs()[i], 1e-5);
      Log.info("Identity test passed: DCT^-1(DCT(frame)) == frame");
    } finally {
      if (frame!=null) frame.delete();
      if (frameDCT!=null) frameDCT.delete();
      if (frameRec!=null) frameRec.delete();
    }
  }

  @Test
  public void DCT_3D() {
    Frame frame = null;
    Frame frameDCT = null;
    Frame frameRec = null;
    try {
      CreateFrame cf = new CreateFrame();
      cf.rows = 100;
      int height = 35;
      int width = 11;
      int depth = 14;
      cf.cols = height*width*depth;
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
      frameDCT = MathUtils.DCT.transform3D(frame, height, width, depth, false);
      Log.info("Computed 3D DCT of " + cf.rows + " rows of size " + cf.cols + " in" + PrettyPrint.msecs(System.currentTimeMillis() - now, true));

      now = System.currentTimeMillis();
      frameRec = MathUtils.DCT.transform3D(frameDCT, height, width, depth, true);
      Log.info("Computed inverse 3D DCT of " + cf.rows + " rows of size " + cf.cols + " in" + PrettyPrint.msecs(System.currentTimeMillis() - now, true));

      for (int i=0; i<frame.vecs().length; ++i)
        TestUtil.assertVecEquals(frame.vecs()[i], frameRec.vecs()[i], 1e-5);
      Log.info("Identity test passed: DCT^-1(DCT(frame)) == frame");
    } finally {
      if (frame!=null) frame.delete();
      if (frameDCT!=null) frameDCT.delete();
      if (frameRec!=null) frameRec.delete();
    }
  }

}
