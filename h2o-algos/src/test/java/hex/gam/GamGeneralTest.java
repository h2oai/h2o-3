package hex.gam;

import org.apache.commons.math3.stat.inference.TestUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.FVecFactory;
import water.fvec.Frame;
import water.parser.ParseDataset;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static hex.gam.GamThinPlateRegressionBasicTest.assertCorrectStarT;
import static hex.glm.GLMModel.GLMParameters.Family.binomial;
import static org.junit.Assert.assertTrue;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GamGeneralTest extends TestUtil {
    public static final double EPS = 1e-6;
    /**
     * multiple run test for thin-plate splines.
     */
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
            String[] ignoredCols = new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"};
            String[][] gamCols = new String[][]{{"C11", "C12", "C13"}, {"C14", "C15", "C16"}, {"C17", "C18", "C19"}};
            int[] bs = new int[]{1,1,1};
            double[] scale = new double[]{0.01, 0.01, 0.01};
            int[] numKnots = new int[]{12, 12, 12};
            String response = "C21";
            GAMModel gam1 = buildOneGam(train, null, ignoredCols, gamCols, scale, bs, numKnots, null,
                    response);
            Scope.track_generic(gam1);
            GAMModel gam2 = buildOneGam(train, null, ignoredCols, gamCols, scale, bs, numKnots, null,
                    response);
            Scope.track_generic(gam2);
            GAMModel gam3 = buildOneGam(train, null, ignoredCols, gamCols, scale, bs, numKnots, null,
                    response);
            Scope.track_generic(gam3);
            Frame pred1 = gam1.score(train);
            Frame pred2 = gam2.score(train);
            Frame pred3 = gam3.score(train);
            Scope.track(pred1);
            Scope.track(pred2);
            Scope.track(pred3);
            TestUtil.assertFrameEquals(pred1, pred2, EPS); // scoring frames should be the same
            TestUtil.assertFrameEquals(pred2, pred3, EPS);
            TestUtil.assertFrameEquals(train, originFile, EPS);// training frame not changed
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testISBetaConstraints() {
        Scope.enter();
        try {
            Frame train = parseAndTrackTestFile("smalldata/gam_test/synthetic_20Cols_binomial_20KRows.csv");
            train.replace(train.numCols() - 1, train.vec("response").toCategoricalVec()).remove();
            DKV.put(train);
            Key betaConsKey = Key.make("beta_constraints");
            FVecFactory.makeByteVec(betaConsKey, "names, lower_bounds, upper_bounds\n c_4, 0.0, 0.5\n " +
                    "C10, 0.0, 0.5");
            Frame betaConstraints = ParseDataset.parse(Key.make("beta_constraints.hex"), betaConsKey);
            betaConstraints.replace(0, betaConstraints.vec(0).toStringVec()).remove();
            DKV.put(betaConstraints);
            Scope.track(betaConstraints);
            GAMModel.GAMParameters params = new GAMModel.GAMParameters();
            params._response_column = "response";
            params._gam_columns = new String[][]{{"c_0"}, {"c_0", "c_1"}, {"c_2"}, {"c_3"}};
            params._train = train._key;
            params._family = binomial;
            params._keep_gam_cols = true;
            params._spline_orders = new int[]{5, -1, -1, 10};
            params._bs = new int[]{2, 1, 0, 2};
            params._ignored_columns = new String[]{"c_0", "c_1", "c_2", "c_3", "c_5", "c_6", "c_7", "c_8", "c_9", "C1",
                    "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9"};
            params._beta_constraints = betaConstraints._key;
            final GAMModel gam = new GAM(params).trainModel().get();
            Scope.track_generic(gam);
            double[] coefficients = gam._output._model_beta;
            String[] colNames = gam._output._coefficient_names;
            for (int index=0; index<colNames.length; index++) {
                if (colNames[index].contains("c_4"))
                    assertTrue(coefficients[index] >= 0 && coefficients[index] <= 0.5);
                if (colNames[index].contains("C10"))
                    assertTrue(coefficients[index] >= 0 && coefficients[index] <= 0.5); 
                if (colNames[index].contains("_is_"))
                    assertTrue(coefficients[index] >= 0);
            }
            
        } finally {
            Scope.exit();
        }
    }

    /***
     * Mutliple runs test with I-spline, CS, thin-plate splines.
     */
    @Test
    public void testMultipleRunsISCSTP() {
        Scope.enter();
        try {
            Frame originFile = Scope.track(parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv"));
            originFile.replace((20), originFile.vec(20).toCategoricalVec()).remove();
            DKV.put(originFile);
            Frame train = Scope.track(parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv"));
            train.replace((20), train.vec(20).toCategoricalVec()).remove();
            DKV.put(train);
            String[] ignoredCols = new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"};
            String[][] gamCols = new String[][]{{"C11", "C12", "C13"}, {"C11"}, {"C12"}, {"C13"}, {"C11", "C12"},
                    {"C12", "C13"}, {"C14", "C15", "C16"}, {"C14"}};
            int[] bs = new int[]{1, 0, 1, 2, 1, 1, 1, 0};
            double[] scale = new double[]{0.01, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01};
            int[] numKnots = new int[]{12, 12, 12, 12, 12, 12, 12, 12};
            int[] spline_orders = new int[]{1,2,3,4,5,6,7,8};
            String response = "C21";
            GAMModel gam1 = buildOneGam(train, null, ignoredCols, gamCols, scale, bs, numKnots, spline_orders, response);
            Scope.track_generic(gam1);
            GAMModel gam2 = buildOneGam(train, null, ignoredCols, gamCols, scale, bs, numKnots, spline_orders, response);
            Scope.track_generic(gam2);
            GAMModel gam3 = buildOneGam(train, null, ignoredCols, gamCols, scale, bs, numKnots, spline_orders, response);
            Scope.track_generic(gam3);
            Frame pred1 = gam1.score(train);
            Frame pred2 = gam2.score(train);
            Frame pred3 = gam3.score(train);
            Scope.track(pred1);
            Scope.track(pred2);
            Scope.track(pred3);
            TestUtil.assertFrameEquals(pred1, pred2, EPS); // scoring frames should be the same
            TestUtil.assertFrameEquals(pred2, pred3, EPS);
            TestUtil.assertFrameEquals(train, originFile, EPS);// training frame not changed
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testMultipleRunsISCSTPValid() {
        Scope.enter();
        try {
            Frame originFile = Scope.track(parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv"));
            originFile.replace((20), originFile.vec(20).toCategoricalVec()).remove();
            DKV.put(originFile);
            Frame train = Scope.track(parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv"));
            Frame valid = Scope.track(parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv"));
            train.replace((20), train.vec(20).toCategoricalVec()).remove();
            DKV.put(train);
            valid.replace((20), valid.vec(20).toCategoricalVec()).remove();
            DKV.put(valid);
            String[] ignoredCols = new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"};
            String[][] gamCols = new String[][]{{"C11", "C12", "C13"}, {"C11"}, {"C12"}, {"C13"}};
            int[] bs = new int[]{1, 0, 1, 2};
            double[] scale = new double[]{0.01, 0.01, 0.01, 0.01};
            int[] numKnots = new int[]{12, 12, 12, 12};
            int[] spline_orders = new int[]{1,2,3,4};
            String response = "C21";
            GAMModel gam1 = buildOneGam(train, valid, ignoredCols, gamCols, scale, bs, numKnots, spline_orders, response);
            Scope.track_generic(gam1);
            GAMModel gam2 = buildOneGam(train, valid, ignoredCols, gamCols, scale, bs, numKnots, spline_orders, response);
            Scope.track_generic(gam2);
            GAMModel gam3 = buildOneGam(train, valid, ignoredCols, gamCols, scale, bs, numKnots, spline_orders, response);
            Scope.track_generic(gam3);
            Frame pred1 = gam1.score(train);
            Frame pred2 = gam2.score(train);
            Frame pred3 = gam3.score(train);
            Scope.track(pred1);
            Scope.track(pred2);
            Scope.track(pred3);
            TestUtil.assertFrameEquals(pred1, pred2, EPS); // scoring frames should be the same
            TestUtil.assertFrameEquals(pred2, pred3, EPS);
            TestUtil.assertFrameEquals(train, originFile, EPS);// training frame not changed
            assertTrue(gam1._output._validation_metrics != null);
        } finally {
            Scope.exit();
        }
    }

    GAMModel buildOneGam(Frame train, Frame valid, String[] ignoredCols, String[][] gamCols, double[] scale, int[] bs, 
                         int[] numKnots, int[] spline_order, String response) {
        GAMModel.GAMParameters params = new GAMModel.GAMParameters();
        int k = 12;
        params._response_column = response;
        params._ignored_columns = ignoredCols;
        params._num_knots = numKnots;
        params._gam_columns = gamCols;
        params._bs = bs;
        params._scale = scale;
        params._train = train._key;
        if (valid != null)
            params._valid = valid._key;
        params._savePenaltyMat = true;
        params._standardize_tp_gam_cols = true;
        params._spline_orders = spline_order;
        return new GAM(params).trainModel().get();
    }
}
