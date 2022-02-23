package hex.gam;

import hex.glm.GLMModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.api.schemas3.KeyV3;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;

import static hex.gam.GamTestPiping.genFrameKnots;
import static hex.gam.GamTestPiping.massageFrame;
import static hex.glm.GLMModel.GLMParameters.Family.gaussian;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GAMISplineTest extends TestUtil {

    /***
     * Test that columns are gamified correctly by comparing the one in GAM.java and using the one manually derived.
     */
    @Test
    public void testGamification() {
        Scope.enter();
        try {
            Frame train = Scope.track(generateRealWithRangeOnly(4, 100, 0, 12345, 
                    4)); // generate training frame
            // generate knots frames
            double[] pctilesV0 = train.vec(0).pctiles();
            double[] pctilesV1 = train.vec(1).pctiles();
            double[] pctilesV2 = train.vec(2).pctiles();
            int numRow = pctilesV0.length/2;
            double[][] pctiles0 = new double[numRow+2][1];
            double[][] pctiles1 = new double[numRow+2][1];
            double[][] pctiles2 = new double[numRow+2][1];
            for (int rind = 0; rind < numRow+1; rind++) {
                pctiles0[rind][0] = pctilesV0[2*rind];
                pctiles1[rind][0] = pctilesV1[2*rind];
                pctiles2[rind][0] = pctilesV2[2*rind];
            }
            pctiles0[numRow+1][0] = train.vec(0).max();
            pctiles1[numRow+1][0] = train.vec(1).max();
            pctiles2[numRow+1][0] = train.vec(2).max();
            pctiles0[0][0] = train.vec(0).min();
            pctiles1[0][0] = train.vec(1).min();
            pctiles2[0][0] = train.vec(2).min();
            Frame knotsFrame1 = genFrameKnots(pctiles0);
            DKV.put(knotsFrame1);
            Scope.track(knotsFrame1);
            Frame knotsFrame2 = genFrameKnots(pctiles1);
            DKV.put(knotsFrame2);
            Scope.track(knotsFrame2);
            Frame knotsFrame3 = genFrameKnots(pctiles2);
            DKV.put(knotsFrame3);
            Scope.track(knotsFrame3);
            // generate gamified frame
            String[][] gamCols = new String[][]{{"C1"}, {"C2"}, {"C3"}};
            GAMModel.GAMParameters params = new GAMModel.GAMParameters();
            params._scale = new double[]{0.1, 0.1, 0.1};
            params._bs = new int[]{2,2,2};
            params._family = gaussian;
            params._response_column = "C4";
            params._spline_orders = new int[]{2,2,2};
            params._max_iterations = 1;
            params._savePenaltyMat = true;
            params._gam_columns = gamCols;
            params._knot_ids = new Key[]{knotsFrame1._key, knotsFrame2._key, knotsFrame3._key};
            params._train = train._key;
            params._solver = GLMModel.GLMParameters.Solver.IRLSM;
            params._keep_gam_cols = true;
            final GAMModel gam = new GAM(params).trainModel().get();
            Scope.track_generic(gam);
            
           
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testGamificationSimple() {
        Scope.enter();
        try {
            Frame train = Scope.track(generateRealWithRangeOnly(4, 100, 0, 12345,
                    1)); // generate training frame
            // generate knots frame
            double[][] pctiles = new double[][]{{-1},{-0.6},{-0.5},{-0.3},{0},{0.3},{0.5},{0.6},{1}};
            Frame knotsFrame1 = genFrameKnots(pctiles);
            DKV.put(knotsFrame1);
            Scope.track(knotsFrame1);
            // generate gamified frame
            String[][] gamCols = new String[][]{{"C1"}, {"C2"}, {"C3"}};
            GAMModel.GAMParameters params = new GAMModel.GAMParameters();
            params._scale = new double[]{0.1, 0.1, 0.1};
            params._bs = new int[]{2,2,2};
            params._family = gaussian;
            params._response_column = "C4";
            params._spline_orders = new int[]{2,2,2};
            params._max_iterations = 1;
            params._savePenaltyMat = true;
            params._gam_columns = gamCols;
            params._knot_ids = new Key[]{knotsFrame1._key, knotsFrame1._key, knotsFrame1._key};
            params._train = train._key;
            params._solver = GLMModel.GLMParameters.Solver.IRLSM;
            params._keep_gam_cols = true;
            final GAMModel gam = new GAM(params).trainModel().get();
            Scope.track_generic(gam);


        } finally {
            Scope.exit();
        }
    }

}
