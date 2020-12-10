package hex;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static water.TestUtil.stall_till_cloudsize;

public class MutlinomialAUCTest {
    
    private static MultinomialAUC _auc;
    
    @BeforeClass() public static void setup() { 
        stall_till_cloudsize(1); 
        fillAucObject(MultinomialAucType.MACRO_OVR);
    }
    
    private static void fillAucObject(MultinomialAucType type){
        int n = 2;
        AUC2.AUCBuilder[] ovr = new AUC2.AUCBuilder[3];
        AUC2.AUCBuilder[][] ovo = new AUC2.AUCBuilder[3][3];
        // Multinomial  problem
        // True values
        // A : 0 0 1 0 1 0 0 1 0 0 1 1 1
        // B : 0 1 0 0 0 1 0 0 0 1 0 0 0
        // C : 1 0 0 1 0 0 1 0 1 0 0 0 0
        // Predicted Values
        // A : 0 0 1 0 0 0 0 0 1 0 1 1 1
        // B : 0 1 0 1 1 0 0 1 0 1 0 0 0
        // C : 1 0 0 0 0 1 1 0 0 0 0 0 0

        // tps and fps for A, B, C
        double[] tpsa = {4};
        double[] fpsa = {1};

        double[] tpsc = {2};
        double[] fpsc = {1};

        double[] tpsb = {2};
        double[] fpsb = {3};
        
        // ovo auc builders
        // A vs B
        ovo[0][1] = getAucBuilder(tpsa, fpsa, 1);
        // B vs A
        ovo[1][0] = getAucBuilder(tpsb, fpsb, 1);

        // B vs C
        ovo[1][2] = getAucBuilder(tpsb, fpsb, 1);
        // C vs B

        ovo[2][1] = getAucBuilder(tpsc, fpsc, 1);

        // A vs C
        ovo[0][2] = getAucBuilder(tpsa, fpsa, 1);
        // C vs A
        ovo[2][0] = getAucBuilder(tpsc, fpsc, 1);

        // ovr auc builders
        // A vs rest
        ovr[0] = getAucBuilder(tpsa, fpsa, 1);
        // B vs rest
        ovr[1] = getAucBuilder(tpsb, fpsb, 1);
        // C vs rest
        ovr[2] = getAucBuilder(tpsc, fpsc, 1);

        _auc = new MultinomialAUC(ovr, ovo, new String[]{"A", "B", "C"}, false, type);
        System.out.println(_auc.getAucTable().toString());
        System.out.println(_auc.getAucPrTable().toString());
    }

    private static AUC2.AUCBuilder getAucBuilder(double[] tps, double[] fps, int n){
        AUC2.AUCBuilder bldr = new AUC2.AUCBuilder(n);
        bldr._n = n;
        System.arraycopy(tps, 0, bldr._tps, 0, tps.length);
        System.arraycopy(fps, 0, bldr._fps, 0, tps.length);
        return bldr;
    }
    
    @Test 
    public void testValuesRange(){
        assertTrue("Weighted OVO AUC should be value in [0, 1] interval.",
                _auc.getWeightedOvoAuc() >= 0 && _auc.getWeightedOvoAuc() <= 1);
        assertTrue("Weighted OVR AUC should be value in [0, 1] interval.",
                _auc.getWeightedOvrAuc() >= 0 && _auc.getWeightedOvrAuc() <= 1);
        assertTrue("Macro OVO AUC should be value in [0, 1] interval.",
                _auc.getMacroOvoAuc() >= 0 && _auc.getMacroOvoAuc() <= 1);
        assertTrue("Macro OVR AUC should be value in [0, 1] interval.",
                _auc.getMacroOvrAuc() >= 0 && _auc.getMacroOvrAuc() <= 1);

        assertTrue("Weighted OVO PR AUC should be value in [0, 1] interval.",
                _auc.getWeightedOvoAucPr() >= 0 && _auc.getWeightedOvoAucPr() <= 1);
        assertTrue("Weighted OVR PR AUC should be value in [0, 1] interval.",
                _auc.getWeightedOvrAucPr() >= 0 && _auc.getWeightedOvrAucPr() <= 1);
        assertTrue("Macro OVO PR AUC should be value in [0, 1] interval.",
                _auc.getMacroOvoAucPr() >= 0 && _auc.getMacroOvoAucPr() <= 1);
        assertTrue("Macro OVR PR AUC should be value in [0, 1] interval.",
                _auc.get_macroOvrAucPr() >= 0 && _auc.get_macroOvrAucPr() <= 1);
    }
    
    @Test
    public void testDefaultValues(){
        assertEquals("Default AUC should be the same as Macro OVR AUC", _auc.getMacroOvrAuc(), _auc.auc(), 0);
        fillAucObject(MultinomialAucType.WEIGHTED_OVR);
        assertEquals("Default AUC should be the same as Weighted OVR AUC", _auc.getWeightedOvrAuc(), _auc.auc(), 0);
        fillAucObject(MultinomialAucType.WEIGHTED_OVO);
        assertEquals("Default AUC should be the same as Weighted OVO AUC", _auc.getWeightedOvoAuc(), _auc.auc(), 0);
        fillAucObject(MultinomialAucType.MACRO_OVO);
        assertEquals("Default AUC should be the same as Macro OVO AUC", _auc.getMacroOvoAuc(), _auc.auc(), 0);
    }
}
