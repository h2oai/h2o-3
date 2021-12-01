package hex.gam;

import org.apache.commons.math3.stat.inference.TestUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static hex.gam.GamThinPlateRegressionBasicTest.assertCorrectStarT;
import static org.junit.Assert.assertTrue;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GamGeneralTest extends TestUtil {
    @Test
    public void testMultipleRuns() {
        Scope.enter();
        try {
            Frame originFile = Scope.track(parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv"));
            originFile.replace((20), originFile.vec(20).toCategoricalVec()).remove();
            DKV.put(originFile);
            Frame train = Scope.track(parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv"));
            train.replace((20), train.vec(20).toCategoricalVec()).remove();
            DKV.put(train);
            GAMModel gam1 = buildOneGam(train);
            Scope.track_generic(gam1);
            GAMModel gam2 = buildOneGam(train);
            Scope.track_generic(gam2);
            GAMModel gam3 = buildOneGam(train);
            Scope.track_generic(gam3);
            Frame pred1 = gam1.score(train);
            Frame pred2 = gam2.score(train);
            Scope.track(pred1);
            Scope.track(pred2);
            TestUtil.assertFrameEquals(pred1, pred2, 1e-6); // scoring frames should be the same
            TestUtil.assertFrameEquals(train, originFile, 1e-6);// training frame not changed
        } finally {
            Scope.exit();
        }
    }

    GAMModel buildOneGam(Frame train) {
        String[] ignoredCols = new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"};
        String[][] gamCols = new String[][]{{"C11", "C12", "C13"}, {"C14", "C15", "C16"}, {"C17", "C18", "C19"}};
        GAMModel.GAMParameters params = new GAMModel.GAMParameters();
        int k = 12;
        params._response_column = "C21";
        params._ignored_columns = ignoredCols;
        params._num_knots = new int[]{k, k, k};
        params._gam_columns = gamCols;
        params._bs = new int[]{1, 1, 1};
        params._scale = new double[]{10, 10, 10};
        params._train = train._key;
        params._savePenaltyMat = true;
        params._standardize_tp_gam_cols = true;
        return new GAM(params).trainModel().get();
    }
}
