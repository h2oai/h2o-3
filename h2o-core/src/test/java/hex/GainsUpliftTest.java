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
        int len = 100000;
        double[] p = new double[len];
        long[] a = new long[len];
        long[] u = new long[len];
        Random rng = new Random(0xDECAF);
        for (int i=0; i<len; ++i) {
            a[i] = rng.nextDouble() > 0.8 ? 1 : 0;
            u[i] = rng.nextDouble() > 0.5 ? 1 : 0;
            p[i] = 0.343424;
        }
        Vec actual = Vec.makeVec(a, new String[]{"N","Y"}, Vec.newKey());
        Vec predict = Vec.makeVec(p, Vec.newKey());
        Vec treatment = Vec.makeVec(u, new String[]{"0","1"}, Vec.newKey());

        GainsUplift gl = new GainsUplift(predict, actual, treatment);
        gl._groups = 10;
        gl.exec();
        Log.info(gl);

        actual.remove();
        predict.remove();
        treatment.remove();
    }
}
