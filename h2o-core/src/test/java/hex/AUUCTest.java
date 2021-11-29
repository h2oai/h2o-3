package hex;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.util.ArrayUtils;

import java.util.Arrays;

public class AUUCTest extends TestUtil {

    @BeforeClass public static void stall() { stall_till_cloudsize(1); }

    @Test
    public void testAUUCSimple() {
        double[] probs =  new double[]{0.1, 0.2, 0.3, 0.4, 0.5};
        double[] y =  new double[]{0, 0, 1, 1, 1};
        double[] treatment =  new double[]{0, 1, 0, 0, 1};
        AUUC auuc = doAUUC(5, probs, y, treatment);
        
        assert Arrays.equals(auuc._treatment, new long[]{1, 1, 1, 2, 2});
        assert Arrays.equals(auuc._control, new long[]{0, 1, 2, 2, 3});
        assert Arrays.equals(auuc._yTreatment, new long[]{1, 1, 1, 1, 1});
        assert Arrays.equals(auuc._yControl, new long[]{0, 1, 2, 2, 2});
        assert Arrays.equals(auuc._frequency, new long[]{1, 1, 1, 1, 1});
        assert Arrays.equals(auuc._frequencyCumsum, new long[]{1, 2, 3, 4, 5});
    }

    @Test
    public void testAUUCCompareCausalMl() {
        double[] probs =  new double[]{0.1, -0.1, 0.2, 0.5, 0.55, 0.13, -0.2, 0.11, 0.3, 0.9};
        double[] y =  new double[]     {0, 0, 0, 1, 1, 0, 0, 0, 1, 1};
        double[] treatment =  new double[]{0, 0, 0, 0, 0, 1, 1, 1, 1, 1};
        AUUC auuc = doAUUC(10, probs, y, treatment, AUUC.AUUCType.qini);

        assert Arrays.equals(auuc._treatment, new long[]{1, 1, 1, 2, 2, 3, 4, 4, 4, 5});
        assert Arrays.equals(auuc._control, new long[]{0, 1, 2, 2, 3, 3, 3, 4, 5, 5});
        assert Arrays.equals(auuc._yTreatment, new long[]{1, 1, 1, 2, 2, 2, 2, 2, 2, 2});
        assert Arrays.equals(auuc._yControl, new long[]{0, 1, 2, 2, 2, 2, 2, 2, 2, 2});

        double[] causalMlQini = new double[]{0, 0, 0, 0, 0.66667, 0, -0.66667, 0, 0.4, 0};
        assert equalTwoArrays(causalMlQini, auuc._uplift, 1e-2);

        double[] causalMlLift = new double[]{0, 0, 0, 0, 0.33333, 0, -0.16667, 0, 0.1, 0};
        auuc = doAUUC(10, probs, y, treatment, AUUC.AUUCType.lift);
        assert equalTwoArrays(causalMlLift, auuc._uplift, 1e-2);

        double[] causalMlGain = new double[]{0, 0, 0, 0, 1.6667, 0, -1.16667, 0, 0.9, 0};
        auuc = doAUUC(10, probs, y, treatment, AUUC.AUUCType.gain);
        assert equalTwoArrays(causalMlGain, auuc._uplift, 1e-2);

        double resultAUUC = ArrayUtils.sum(causalMlGain)/(causalMlGain.length+1);
        assert auuc._auuc - resultAUUC < 10e-10;
    }

    private static AUUC doAUUC(int nbins, double[] probs, double[] y, double[] treatment) {
        return doAUUC(nbins, probs, y, treatment, AUUC.AUUCType.AUTO);
    }

    private static AUUC doAUUC(int nbins, double[] probs, double[] y, double[] treatment, AUUC.AUUCType type) {
        double[][] rows = new double[probs.length][];
        for( int i=0; i<probs.length; i++ )
            rows[i] = new double[]{probs[i], y[i], treatment[i]};
        Frame fr = ArrayUtils.frame(new String[]{"probs", "y", "treatment"}, rows);
        fr.vec("treatment").setDomain(new String[]{"0", "1"});
        AUUC auuc = new AUUC(nbins, fr.vec("probs"),fr.vec("y"), fr.vec("treatment"), type);
        fr.remove();
        return auuc;
    }
}
