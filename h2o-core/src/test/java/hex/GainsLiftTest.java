package hex;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Vec;
import water.util.Log;

import java.util.Random;

public class GainsLiftTest extends TestUtil {
  @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  @Test public void run() {
    int len = 100000;
    double[] p = new double[len];
    long[] a = new long[len];
    Random rng = new Random();
    for (int i=0; i<len; ++i) {
      a[i] = rng.nextDouble() > 0.8 ? 1 : 0;
//      p[i] = rng.nextDouble();
      p[i] = a[i] == 0 ? 0.5*rng.nextDouble() : 0.5 + rng.nextDouble() * 0.5;
    }
    Vec actual = Vec.makeVec(a, new String[]{"N","Y"}, Vec.newKey());
    Vec predict = Vec.makeVec(p, Vec.newKey());

    GainsLift gl = new GainsLift(predict, actual);
    gl.exec();
    Log.info(gl);

    actual.remove();
    predict.remove();
  }

}
