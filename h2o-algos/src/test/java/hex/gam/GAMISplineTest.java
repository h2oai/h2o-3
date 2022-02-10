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

    @Test
    public void testGaussian() {
        Scope.enter();
        try {
            Frame train = Scope.track(massageFrame(
                    parseTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv"), gaussian));
            DKV.put(train);
            Scope.track(train);
            final double[][] knots = new double[][]{{-1.9990569949269443}, {-0.9814307533427584}, {0.025991586992542004},
                    {1.0077098743127828}, {1.999422899675758}};
            Frame knotsFrame = genFrameKnots(knots);
            DKV.put(knotsFrame);
            Scope.track(knotsFrame);
            String[][] gamCols = new String[][]{{"C11"}, {"C12"}, {"C13"}};
            String[] ignoredCols = new String[]{"C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C15", "C16",
                    "C17", "C18", "C19", "C20"};
            GAMModel.GAMParameters params = new GAMModel.GAMParameters();
            params._scale = new double[]{0.1, 0.1, 0.1};
            params._bs = new int[]{2,2,2};
            params._family = gaussian;
            params._response_column = "C21";
         //   params._max_iterations = 1;
            params._savePenaltyMat = true;
            params._ignored_columns = ignoredCols;
            params._gam_columns = gamCols;
            params._knot_ids = new Key[]{knotsFrame._key, knotsFrame._key, knotsFrame._key};
            params._train = train._key;
            params._solver = GLMModel.GLMParameters.Solver.IRLSM;
            final GAMModel gam = new GAM(params).trainModel().get();
            Scope.track_generic(gam);
        } finally {
            Scope.exit();
        }
    }

}
