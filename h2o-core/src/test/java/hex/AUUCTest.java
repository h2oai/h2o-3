package hex;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.util.ArrayUtils;

public class AUUCTest extends TestUtil {

    @BeforeClass public static void stall() { stall_till_cloudsize(1); }

    @Test
    public void testAUUCSimple() {
        double[] probs =  new double[]{0.1, 0.2, 0.3, 0.4, 0.5};
        double[] y =  new double[]{0, 0, 1, 1, 1};
        double[] uplift =  new double[]{0, 1, 0, 0, 1};
        AUUC auuc = doAUUC(5, probs, y, uplift);
        
        assert equalTwoArrays(auuc._treatment, new double[]{1, 1, 1, 2, 2}, 0);
        assert equalTwoArrays(auuc._control, new double[]{0, 1, 2, 2, 3}, 0);
        assert equalTwoArrays(auuc._yTreatment, new double[]{1, 1, 1, 1, 1}, 0);
        assert equalTwoArrays(auuc._yControl, new double[]{0, 1, 2, 2, 2}, 0);
        
        System.out.println(auuc._auuc_gain);
        System.out.println(auuc._auuc_lift);
        System.out.println(auuc._auuc_qini);
    }

    private static AUUC doAUUC(int nbins, double[] probs, double[] y,  double[] uplift) {
        double[][] rows = new double[probs.length][];
        for( int i=0; i<probs.length; i++ )
            rows[i] = new double[]{probs[i], y[i], uplift[i]};
        Frame fr = ArrayUtils.frame(new String[]{"probs", "y", "uplift"}, rows);
        AUC2 auc = new AUC2(nbins, fr.vec("probs"),fr.vec("y"));
        AUUC auuc = new AUUC(nbins, fr.vec("probs"),fr.vec("y"), fr.vec("uplift"));
        
        fr.remove();
        return auuc;
    }
}
