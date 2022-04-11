package hex;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.util.ArrayUtils;

import java.util.Arrays;

/**
 * To reproduce causalm data run this code in Python with specific data:
 * from causalml.metrics import auuc_score
 * import pandas as pd
 *
 * treatment_column = "treatment"
 * response_column = "outcome"
 *
 * results = pd.DataFrame({'outcome': [0, 0, 0, 1, 1, 0, 0, 0, 1, 1],
 *                         'treatment': [0, 0, 0, 0, 1, 1, 1, 1, 1, 1],
 *                         'uplift': [0.1, -0.1, 0.2, 0.5, 0.55, 0.13, -0.2, 0.11, 0.3, 0.9]})
 *
 * auuc = auuc_score(results, outcome_col=response_column, treatment_col=treatment_column, normalize=False)
 * auuc_normalized = auuc_score(results, outcome_col=response_column, treatment_col=treatment_column, normalize=True)
 */
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
        assert equalTwoArrays(causalMlQini, auuc.upliftByType(AUUC.AUUCType.qini), 1e-2);

        double[] causalMlLift = new double[]{0, 0, 0, 0, 0.33333, 0, -0.16667, 0, 0.1, 0};
        auuc = doAUUC(10, probs, y, treatment, AUUC.AUUCType.lift);
        assert equalTwoArrays(causalMlLift, auuc.upliftByType(AUUC.AUUCType.lift), 1e-2);

        double[] causalMlGain = new double[]{0, 0, 0, 0, 1.6667, 0, -1.16667, 0, 0.9, 0};
        auuc = doAUUC(10, probs, y, treatment, AUUC.AUUCType.gain);
        assert equalTwoArrays(causalMlGain, auuc.upliftByType(AUUC.AUUCType.gain), 1e-2);

        double resultAUUC = ArrayUtils.sum(causalMlGain)/(causalMlGain.length+1);
        assert auuc.auuc() - resultAUUC < 10e-10;
    }
    
    @Test
    public void testAUUCNormalizedCompareCausalMl() {
        double[] probs =  new double[]{0.1, -0.1, 0.2, 0.5, 0.55, 0.13, -0.2, 0.11, 0.3, 0.9};
        double[] y =  new double[]     {0, 0, 0, 1, 1, 0, 0, 0, 1, 1};
        double[] treatment =  new double[]{0, 0, 0, 0, 1, 1, 1, 1, 1, 1};
        AUUC auuc = doAUUC(10, probs, y, treatment, AUUC.AUUCType.gain);

        double[] causalMlGainNormalized = new double[]{0, 0, 0, 0, 1, 0.6, 0.28, 0.85333, 1.26, 1};
        assert equalTwoArrays(causalMlGainNormalized, auuc.upliftNormalizedByType(AUUC.AUUCType.gain), 1e-2);
        
        double resultAUUCNormalized = ArrayUtils.sum(causalMlGainNormalized)/(causalMlGainNormalized.length+1);
        assert auuc.auucNormalized() - resultAUUCNormalized < 10e-6;
    }

    private static AUUC doAUUC(int nbins, double[] probs, double[] y, double[] treatment) {
        return doAUUC(nbins, probs, y, treatment, AUUC.AUUCType.AUTO);
    }

    private static AUUC doAUUC(int nbins, double[] probs, double[] y, double[] treatment, AUUC.AUUCType type) {
        double[][] rows = new double[probs.length][];
        for (int i=0; i < probs.length; i++) {
            rows[i] = new double[]{probs[i], y[i], treatment[i]};
        }
        Frame fr = ArrayUtils.frame(new String[]{"probs", "y", "treatment"}, rows);
        fr.vec("treatment").setDomain(new String[]{"0", "1"});
        AUUC auuc = new AUUC(nbins, fr.vec("probs"),fr.vec("y"), fr.vec("treatment"), type);
        fr.remove();
        return auuc;
    }
}
