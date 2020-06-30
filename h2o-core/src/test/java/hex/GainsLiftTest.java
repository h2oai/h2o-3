package hex;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Vec;
import water.util.Log;

import java.util.Random;

public class GainsLiftTest extends TestUtil {
  @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  @Test public void constant() {
    int len = 100000;
    double[] p = new double[len];
    long[] a = new long[len];
    Random rng = new Random(0xDECAF);
    for (int i=0; i<len; ++i) {
      a[i] = rng.nextDouble() > 0.8 ? 1 : 0;
      p[i] = 0.343424;
    }
    Vec actual = Vec.makeVec(a, new String[]{"N","Y"}, Vec.newKey());
    Vec predict = Vec.makeVec(p, Vec.newKey());

    GainsLift gl = new GainsLift(predict, actual);
    gl._groups = 10;
    gl.exec();
    Log.info(gl);
    Assert.assertTrue(gl.response_rates[0] == gl.avg_response_rate);

    actual.remove();
    predict.remove();
  }

  @Test public void good() {
    int len = 100000;
    double[] p = new double[len];
    long[] a = new long[len];
    Random rng = new Random(0xDECAF);
    for (int i=0; i<len; ++i) {
      a[i] = rng.nextDouble() > 0.8 ? 1 : 0;
      p[i] = a[i] == 0 ? 0.5*rng.nextDouble() : 0.5 + rng.nextDouble() * 0.5;
    }
    Vec actual = Vec.makeVec(a, new String[]{"N","Y"}, Vec.newKey());
    Vec predict = Vec.makeVec(p, Vec.newKey());

    GainsLift gl = new GainsLift(predict, actual);
    gl._groups = 10;
    gl.exec();
    Log.info(gl);
    for (int i=0;i<2;++i)
      Assert.assertTrue(gl.response_rates[i] > 0.9);
    for (int i=2;i<gl.response_rates.length;++i)
      Assert.assertTrue(gl.response_rates[i] < 0.1);

    actual.remove();
    predict.remove();
  }

  @Test public void bad() {
    int len = 100000;
    double[] p = new double[len];
    long[] a = new long[len];
    Random rng = new Random(0xDECAF);
    for (int i=0; i<len; ++i) {
      a[i] = rng.nextDouble() > 0.8 ? 1 : 0;
      p[i] = a[i] == 0 ? 0.5 + 0.5*rng.nextDouble() : 0.5*rng.nextDouble();
    }
    Vec actual = Vec.makeVec(a, new String[]{"N","Y"}, Vec.newKey());
    Vec predict = Vec.makeVec(p, Vec.newKey());

    GainsLift gl = new GainsLift(predict, actual);
    gl._groups = 10;
    gl.exec();
    Log.info(gl);
    for (int i=gl.response_rates.length-2;i<gl.response_rates.length;++i)
      Assert.assertTrue(gl.response_rates[i] > 0.9);
    for (int i=0;i<gl.response_rates.length-2;++i)
      Assert.assertTrue(gl.response_rates[i] < 0.1);

    actual.remove();
    predict.remove();
  }

  @Test public void random() {
    int len = 100000;
    double[] p = new double[len];
    long[] a = new long[len];
    Random rng = new Random(0xDECAF);
    for (int i=0; i<len; ++i) {
      a[i] = rng.nextDouble() > 0.8 ? 1 : 0;
      p[i] = rng.nextDouble();
    }
    Vec actual = Vec.makeVec(a, new String[]{"N","Y"}, Vec.newKey());
    Vec predict = Vec.makeVec(p, Vec.newKey());

    GainsLift gl = new GainsLift(predict, actual);
    gl._groups = 10;
    gl.exec();
    Log.info(gl);
    for (int i=0;i<gl.response_rates.length;++i)
      Assert.assertTrue(gl.response_rates[i] > 0.19 && gl.response_rates[i] < 0.21);

    actual.remove();
    predict.remove();
  }

  @Test public void tiesNApreds() {
    int len = 100000;
    double[] p = new double[len];
    long[] a = new long[len];
    Random rng = new Random(0xDECAF);
    for (int i=0; i<len; ++i) {
      a[i] = rng.nextDouble() > 0.8 ? 1 : 0;
      p[i] = rng.nextDouble() > 0.5 ? 0.7 : 0.4;
      if (rng.nextDouble() > 0.85) p[i] = Double.NaN;
    }
    Vec actual = Vec.makeVec(a, new String[]{"N","Y"}, Vec.newKey());
    Vec predict = Vec.makeVec(p, Vec.newKey());

    GainsLift gl = new GainsLift(predict, actual);
    gl._groups = 10;
    gl.exec();
    Log.info(gl);
    for (int i=0;i<gl.response_rates.length;++i)
      Assert.assertTrue(gl.response_rates[i] > 0.19 && gl.response_rates[i] < 0.21);

    actual.remove();
    predict.remove();
  }

  @Test public void tiesNAlabels() {
    int len = 100000;
    double[] p = new double[len];
    double[] a = new double[len];
    Random rng = new Random(0xDECAF);
    for (int i=0; i<len; ++i) {
      a[i] = rng.nextDouble() > 0.8 ? 1 : 0;
      p[i] = rng.nextDouble() > 0.5 ? 0.7 : 0.4;
      if (rng.nextDouble() > 0.85) a[i] = Double.NaN;
    }
    Vec actual = Vec.makeVec(a, new String[]{"N","Y"}, Vec.newKey());
    Vec predict = Vec.makeVec(p, Vec.newKey());

    GainsLift gl = new GainsLift(predict, actual);
    gl._groups = 10;
    gl.exec();
    Log.info(gl);
    for (int i=0;i<gl.response_rates.length;++i)
      Assert.assertTrue(gl.response_rates[i] > 0.19 && gl.response_rates[i] < 0.21);

    actual.remove();
    predict.remove();
  }

  @Test public void tiesNAlabels_preds() {
    int len = 100000;
    double[] p = new double[len];
    double[] a = new double[len];
    Random rng = new Random(0xDECAF);
    for (int i=0; i<len; ++i) {
      a[i] = rng.nextDouble() > 0.8 ? 1 : 0;
      p[i] = rng.nextDouble() > 0.5 ? 0.7 : 0.4;
      if (rng.nextDouble() > 0.85) a[i] = Double.NaN;
      if (rng.nextDouble() > 0.85) p[i] = Double.NaN;
    }
    Vec actual = Vec.makeVec(a, new String[]{"N","Y"}, Vec.newKey());
    Vec predict = Vec.makeVec(p, Vec.newKey());

    GainsLift gl = new GainsLift(predict, actual);
    gl._groups = 10;
    gl.exec();
    Log.info(gl);
    for (int i=0;i<gl.response_rates.length;++i)
      Assert.assertTrue(gl.response_rates[i] > 0.19 && gl.response_rates[i] < 0.21);

    actual.remove();
    predict.remove();
  }

  @Test public void imbalanced() {
    int len = 50000;
    double thresh = 1e-7;
    double[] p = new double[2*len];
    long[] a = new long[2*len];
    Random rng = new Random(0xDECAF);
    int i;
    for (i=0; i<len; ++i) {
      a[i] = rng.nextDouble() > 0.8 ? 1 : 0;
      p[i] = rng.nextDouble()*thresh;
    }
    for (i=len; i<2*len; ++i) {
      a[i] = rng.nextDouble() > 0.8 ? 1 : 0;
      p[i] = (1-thresh)+thresh*rng.nextDouble();
    }
    Vec actual = Vec.makeVec(a, new String[]{"N","Y"}, Vec.newKey());
    Vec predict = Vec.makeVec(p, Vec.newKey());

    GainsLift gl = new GainsLift(predict, actual);
    gl._groups = 10;
    gl.exec();
    Log.info(gl);
    for (i=0;i<gl.response_rates.length;++i)
      Assert.assertTrue(gl.response_rates[i] > 0.19 && gl.response_rates[i] < 0.21);

    actual.remove();
    predict.remove();
  }

  @Test public void rareEvents() {
    int len = 100000;
    double[] p = new double[len];
    long[] a = new long[len];
    Random rng = new Random(0xDECAF);
    for (int i=0; i<len; ++i) {
      a[i] = rng.nextDouble() > 0.999 ? 1 : 0;
      p[i] = a[i] == 0 ? 0.5*rng.nextDouble() : 0.5 + rng.nextDouble() * 0.5;
    }
    Vec actual = Vec.makeVec(a, new String[]{"N","Y"}, Vec.newKey());
    Vec predict = Vec.makeVec(p, Vec.newKey());

    GainsLift gl = new GainsLift(predict, actual);
    gl._groups = 10;
    gl.exec();
    Log.info(gl);
    Assert.assertTrue(gl.response_rates[0] <= 0.011 && gl.response_rates[0] >= 0.009);
    for (int i=1;i<gl.response_rates.length;++i)
      Assert.assertTrue(gl.response_rates[i] == 0);

    actual.remove();
    predict.remove();
  }

  @Test public void rareEvents20() {
    int len = 100000;
    double[] p = new double[len];
    long[] a = new long[len];
    Random rng = new Random(0xDECAF);
    for (int i=0; i<len; ++i) {
      a[i] = rng.nextDouble() > 0.999 ? 1 : 0;
      p[i] = a[i] == 0 ? 0.5*rng.nextDouble() : 0.5 + rng.nextDouble() * 0.5;
    }
    Vec actual = Vec.makeVec(a, new String[]{"N","Y"}, Vec.newKey());
    Vec predict = Vec.makeVec(p, Vec.newKey());

    GainsLift gl = new GainsLift(predict, actual);
    gl._groups = 20;
    gl.exec();
    Log.info(gl);
    Assert.assertTrue(gl.response_rates[0] <= 0.022 && gl.response_rates[0] >= 0.018);
    for (int i=1;i<gl.response_rates.length;++i)
      Assert.assertTrue(gl.response_rates[i] == 0);

    actual.remove();
    predict.remove();
  }
}
