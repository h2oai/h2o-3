package hex;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.TestUtil;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.Log;

import java.util.Random;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GainsUpliftTest extends TestUtil {

    @Test
    public void constant() {
        int len = 100;
        double[] p = new double[len];
        long[] a = new long[len];
        long[] t = new long[len];
        Random rng = new Random(0xDECAF);
        for (int i=0; i<len; ++i) {
            a[i] = rng.nextDouble() > 0.8 ? 1 : 0;
            t[i] = rng.nextDouble() > 0.5 ? 1 : 0;
            p[i] = 0.343424;
        }
        Vec actual = Vec.makeVec(a, new String[]{"N","Y"}, Vec.newKey());
        Vec predict = Vec.makeVec(p, Vec.newKey());
        Vec treatment = Vec.makeVec(t, new String[]{"0","1"}, Vec.newKey());

        GainsUplift gl = new GainsUplift(predict, actual, treatment);
        gl._groups = 10;
        gl.exec();
        Log.info(gl);
        Assert.assertEquals(gl._nct1.length, 1);
        double qini = AUUC.AUUCType.qini.exec(gl._nct1[0], gl._nct0[0], gl._ny1ct1[0], gl._ny1ct0[0]);
        Assert.assertEquals(gl._qiniUplift[0], qini, 0);
        actual.remove();
        predict.remove();
        treatment.remove();
    }
}
