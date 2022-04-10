package hex.gam;

import hex.glm.GLMModel;
import javassist.compiler.ast.StringL;
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
import water.util.ArrayUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static hex.gam.GAMModel.adaptValidFrame;
import static hex.gam.GamBasicISplineTest.EPS;
import static hex.gam.GamBasicISplineTest.assert2DArrayEqual;
import static hex.gam.GamTestPiping.genFrameKnots;
import static hex.glm.GLMModel.GLMParameters.Family.binomial;
import static hex.glm.GLMModel.GLMParameters.Family.gaussian;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GAMISplineTest extends TestUtil {

    /***
     * This test is used to make sure that the gamification is performed properly for validation dataset and brand new
     * dataset used for scoring.  It should equal to the gamification on the training dataset.
     */
    @Test
    public void testGamificationValid() {
        Scope.enter();
        try {
            Frame train = Scope.track(generateRealWithRangeOnly(4, 100, 0, 12345,
                    4)); // generate training frame
            Frame test = Scope.track(generateRealWithRangeOnly(4, 100, 0, 12345,
                    4)); // generate test frame that is exactly the same as train
            // generate knots frames
            double[] pctilesV0 = train.vec(0).pctiles();
            double[] pctilesV1 = train.vec(1).pctiles();
            double[] pctilesV2 = train.vec(2).pctiles();
            int numRow = pctilesV0.length / 2;
            double[][] pctiles0 = new double[numRow + 2][1];
            double[][] pctiles1 = new double[numRow + 2][1];
            double[][] pctiles2 = new double[numRow + 2][1];
            for (int rind = 0; rind < numRow + 1; rind++) {
                pctiles0[rind][0] = pctilesV0[2 * rind];
                pctiles1[rind][0] = pctilesV1[2 * rind];
                pctiles2[rind][0] = pctilesV2[2 * rind];
            }
            pctiles0[numRow + 1][0] = train.vec(0).max();
            pctiles1[numRow + 1][0] = train.vec(1).max();
            pctiles2[numRow + 1][0] = train.vec(2).max();
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
            params._bs = new int[]{2, 2, 2};
            params._family = gaussian;
            params._response_column = "C4";
            params._spline_orders = new int[]{2, 2, 2};
            params._max_iterations = 1;
            params._savePenaltyMat = true;
            params._gam_columns = gamCols;
            params._knot_ids = new String[]{knotsFrame1._key.toString(), knotsFrame2._key.toString(), 
                    knotsFrame3._key.toString()};
            params._train = train._key;
            params._solver = GLMModel.GLMParameters.Solver.IRLSM;
            params._keep_gam_cols = true;
            final GAMModel gam = new GAM(params).trainModel().get();
            Scope.track_generic(gam);
            Frame validGamified = adaptValidFrame(test, test, params, gam._output._gamColNames, null,
                    gam._output._zTranspose, gam._output._knots, null, null, null, null, 0);
            DKV.put(validGamified);
            Scope.track(validGamified);
            Frame trainGamified = DKV.getGet(gam._output._gamTransformedTrainCenter);
            Scope.track(trainGamified);
            TestUtil.assertIdenticalUpToRelTolerance(validGamified, trainGamified, EPS);
        } finally {
            Scope.exit();
        }
    }

    /**
     * This test will check the penalty matrix calculation for order = 2, 3, 4.  The manually derived penalty matrix
     * will be centralized by multiplying with z-matrix as Z'*p*Z.  In addition, I will make sure of the following
     * items that have been tested before to arrive at the manually derived matrix:
     * 1. derivative of coefficients;
     * 2. polynomial multiplications;
     * 3. integration of polynomials;
     * 4. derivation of z-matrix
     */
    @Test
    public void testPenaltyMatrix() {
        Scope.enter();
        try {
            Frame train = Scope.track(generateRealWithRangeOnly(4, 100, 0, 12345,
                    1)); // generate training frame
            // generate knots frame
            double[][] pctiles = new double[][]{{-1}, {-0.5}, {0}, {0.5}, {1}};
            Frame knotsFrame1 = genFrameKnots(pctiles);
            DKV.put(knotsFrame1);
            Scope.track(knotsFrame1);
            // generate gamified frame
            String[][] gamCols = new String[][]{{"C1"}, {"C2"}, {"C3"}};
            GAMModel.GAMParameters params = new GAMModel.GAMParameters();
            params._scale = new double[]{0.1, 0.1, 0.1};
            params._bs = new int[]{2, 2, 2};
            params._family = gaussian;
            params._response_column = "C4";
            params._spline_orders = new int[]{2, 3, 4};
            params._max_iterations = 1;
            params._savePenaltyMat = true;
            params._gam_columns = gamCols;
            params._knot_ids = new String[]{knotsFrame1._key.toString(), knotsFrame1._key.toString(), 
                    knotsFrame1._key.toString()};
            params._train = train._key;
            params._solver = GLMModel.GLMParameters.Solver.IRLSM;
            params._keep_gam_cols = true;
            final GAMModel gam = new GAM(params).trainModel().get();
            Scope.track_generic(gam);
            // manually generating penalty matrix and check for order = 2
            double[][] penaltyMat2 = new double[][]{{32, -16, 0, 0, 0}, {-16, 16, -8, 0, 0}, {0, -8, 16, -8, 0}, {0, 0,
                    -8, 16, -16}, {0, 0, 0, -16, 32}}; // manually derived.
            ArrayUtils.mult(penaltyMat2, 1.0 / gam._output._penaltyScale[0]);
            assert2DArrayEqual(penaltyMat2, gam._output._penaltyMatrices[0]);
            // manual penalty matrix check for order = 3
            double[][] penaltyMat3 = new double[][]{{96, -36, -8, 0, 0, 0}, {-36, 24, -2, -2, 0, 0}, {-8, -2, 8, -2 - 2.0 / 3, -2, 0},
                    {0, -2, -2 - 2.0 / 3, 8, -2, -8}, {0, 0, -2, -2, 24, -36}, {0, 0, 0, -8, -36, 96}};
            ArrayUtils.mult(penaltyMat3, 1.0 / gam._output._penaltyScale[1]);
            assert2DArrayEqual(penaltyMat3, gam._output._penaltyMatrices[1]);
            // manual penalty matrix check for order = 4, yes, I am overdoing it here.
            double[][] penaltyMat4 = new double[][]{{230.4, -81.6, -20.266666666667, -1.6, 0, 0, 0},
                    {-81.6, 48, 0.8, -4, -0.26666666667, 0, 0},
                    {-20.266666666667, 0.8, 9.6, 1.0666666666667 - 2.044444444444 + 0.622222222222, -2.725925925925927, -0.26666666667, 0},
                    {-1.6, -4, 1.0666666666667 - 2.044444444444 + 0.622222222222, 5 + 1.0 / 3.0, -0.3555555555554313, -4, -1.6},
                    {0, -0.266666666667, -2.725925925925927, -0.3555555555554313, 9.6, 0.8, -20.26666666666597},
                    {0, 0, -0.26666666666667, -4, 0.8, 48, -81.6},
                    {0, 0, 0, -1.6, -20.26666666666597, -81.6, 230.4}};
            ArrayUtils.mult(penaltyMat4, 1.0 / gam._output._penaltyScale[2]);
            assert2DArrayEqual(penaltyMat4, gam._output._penaltyMatrices[2]);
        } finally {
            Scope.exit();
        }
    }
    
    /***
     * Test correct gamification of gam columns when there are thin plate splines and I-splines. 
     * To check for correct implementation, I compare the gamified columns when all gam columns are specified all
     * at once to the gamification columns generated by a single gam column at a time.
     */
    @Test
    public void testTPISTransform() {
        Scope.enter();
        try {
            Frame train = parseAndTrackTestFile("smalldata/gam_test/synthetic_20Cols_binomial_20KRows.csv");
            train.replace(train.numCols() - 1, train.vec("response").toCategoricalVec()).remove();
            DKV.put(train);
            Frame allGamifiedColumns = extractGamifiedColumns(train, new String[][]{{"c_0", "c_1"}, {"c_2"}, {"c_3"},
                    {"c_4", "c_5", "c_6"}, {"c_7"}, {"c_7", "c_8", "c_9"}}, new int[]{-1, 2, 3, -1, 4, -1}, 
                    new double[]{0.001, 0.001, 0.001, 0.001, 0.001, 0.001}, new int[]{1, 2, 2, 1, 2, 1},
                    new int[]{11, 5, 6, 12, 6, 13}, null, binomial);
            List<String> colNames = new ArrayList<>(Arrays.asList(train.names()));
            colNames.remove(colNames.size() - 1); // remove response name
            Frame tpc0c1 = extractGamifiedColumns(train, new String[][]{{"c_0", "c_1"}}, null,
                    new double[]{0.001}, new int[]{1}, new int[]{11}, ignoredCols(colNames, 
                            Arrays.asList("c_0", "c_1")), binomial);
            assertCorrectGamification(allGamifiedColumns, tpc0c1);
            Frame isc2 = extractGamifiedColumns(train, new String[][]{{"c_2"}}, new int[]{2}, new double[]{0.001},
                    new int[]{2}, new int[]{5}, ignoredCols(colNames, Arrays.asList("c_2")), binomial);
            assertCorrectGamification(allGamifiedColumns, isc2);            
            Frame isc3 = extractGamifiedColumns(train, new String[][]{{"c_3"}}, new int[]{3}, new double[]{0.001},
                    new int[]{2}, new int[]{6}, ignoredCols(colNames, Arrays.asList("c_3")), binomial);
            assertCorrectGamification(allGamifiedColumns, isc3);
            Frame tpc4c5c6 = extractGamifiedColumns(train, new String[][]{{"c_4", "c_5", "c_6"}}, null,
                    new double[]{0.001}, new int[]{1}, new int[]{12}, ignoredCols(colNames, Arrays.asList("c_4", "c_5",
                            "c_6")), binomial);
            assertCorrectGamification(allGamifiedColumns, tpc4c5c6);
            Frame isc7 = extractGamifiedColumns(train, new String[][]{{"c_7"}}, new int[]{4}, new double[]{0.001},
                    new int[]{2}, new int[]{6}, ignoredCols(colNames, Arrays.asList("c_7")), binomial);
            assertCorrectGamification(allGamifiedColumns, isc7);
            Frame tpc7c8c9 = extractGamifiedColumns(train, new String[][]{{"c_7", "c_8", "c_9"}}, null,
                    new double[]{0.001}, new int[]{1}, new int[]{13}, ignoredCols(colNames, Arrays.asList("c_7", "c_8",
                            "c_9")), binomial);
            assertCorrectGamification(allGamifiedColumns, tpc7c8c9);
        } finally {
            Scope.exit();
        }
    }

    public static String[] ignoredCols(List<String> originalNames, List<String> currNames) {
        return originalNames.stream().filter(x -> !currNames.contains(x)).collect(Collectors.toList()).toArray(new String[0]);
    }

    public static void assertCorrectGamification(Frame allGams, Frame oneGam) {
        String[] gamNames = oneGam.names();
        Frame extractedGam = new Frame(gamNames, allGams.vecs(gamNames));
        Scope.track(extractedGam);
        TestUtil.assertIdenticalUpToRelTolerance(extractedGam, oneGam, EPS);
    }

    public static Frame extractGamifiedColumns(Frame trainData, String[][] gamColumns, int[] splineOrders,
                                               double[] scales, int[] bs, int[] numKnots, String[] ignoredColumns, 
                                               GLMModel.GLMParameters.Family family) {
        GAMModel.GAMParameters params = new GAMModel.GAMParameters();
        params._response_column = "response";
        params._gam_columns = gamColumns;
        params._train = trainData._key;
        params._family = family;
        params._keep_gam_cols = true;
        params._spline_orders = splineOrders;
        params._scale = scales;
        params._bs = bs;
        params._num_knots = numKnots;
        params._max_iterations = 1;
        params._ignored_columns = ignoredColumns;
        final GAMModel gam = new GAM(params).trainModel().get();
        Scope.track_generic(gam);
        Frame gamifiedColumns = DKV.getGet(gam._output._gamTransformedTrainCenter);
        Scope.track(gamifiedColumns);
        return gamifiedColumns;
    }

    /***
     * Test correct gamification of data when there are CS splines and I-splines are specified
     */
    @Test
    public void testCSISTransform() {
        Scope.enter();
        try {
            Frame train = parseAndTrackTestFile("smalldata/gam_test/synthetic_20Cols_gaussian_20KRows.csv");
            Frame allGamifiedColumns = extractGamifiedColumns(train, new String[][]{{"c_0"}, {"c_1"}, {"c_2"}, {"c_2"},
                            {"c_3"}, {"c_5"}, {"c_6"}, {"c_7"}, {"c_8"}, {"c_9"}}, new int[]{-1, 2, 3, -1, -1, 4, 5, 6,
                            -1, -1}, new double[]{0.001, 0.001, 0.001, 0.001, 0.001, 0.001, 0.001, 0.001, 0.001, 
                            0.001}, new int[]{0, 2, 2, 0, 0, 2, 2, 2, 0, 0}, new int[]{5, 6, 7, 8, 9, 10, 9, 8, 7, 6}, 
                    null, gaussian);
            List<String> colNames = new ArrayList<>(Arrays.asList(train.names()));
            colNames.remove(colNames.size() - 1); // remove response name
            Frame csc_0 = extractGamifiedColumns(train, new String[][]{{"c_0"}}, null,
                    new double[]{0.001}, new int[]{0}, new int[]{5}, ignoredCols(colNames,
                            Arrays.asList("c_0")), gaussian);
            assertCorrectGamification(allGamifiedColumns,  csc_0);
            Frame isc_1  = extractGamifiedColumns(train, new String[][]{{"c_1"}}, new int[]{2}, new double[]{0.001}, 
                    new int[]{2}, new int[]{6}, ignoredCols(colNames, Arrays.asList("c_1")), gaussian);
            assertCorrectGamification(allGamifiedColumns, isc_1);
            Frame isc_2  = extractGamifiedColumns(train, new String[][]{{"c_2"}}, new int[]{3}, new double[]{0.001},
                    new int[]{2}, new int[]{7}, ignoredCols(colNames, Arrays.asList("c_2")), gaussian);
            assertCorrectGamification(allGamifiedColumns, isc_2);
            Frame csc_2  = extractGamifiedColumns(train, new String[][]{{"c_2"}}, new int[]{-1}, new double[]{0.001},
                    new int[]{0}, new int[]{8}, ignoredCols(colNames, Arrays.asList("c_2")), gaussian);
            assertCorrectGamification(allGamifiedColumns, csc_2);
            Frame csc_3  = extractGamifiedColumns(train, new String[][]{{"c_3"}}, new int[]{-1}, new double[]{0.001},
                    new int[]{0}, new int[]{9}, ignoredCols(colNames, Arrays.asList("c_3")), gaussian);
            assertCorrectGamification(allGamifiedColumns, csc_3);
            Frame isc_5  = extractGamifiedColumns(train, new String[][]{{"c_5"}}, new int[]{4}, new double[]{0.001},
                    new int[]{2}, new int[]{10}, ignoredCols(colNames, Arrays.asList("c_5")), gaussian);
            assertCorrectGamification(allGamifiedColumns, isc_5);
            Frame isc_6  = extractGamifiedColumns(train, new String[][]{{"c_6"}}, new int[]{5}, new double[]{0.001},
                    new int[]{2}, new int[]{9}, ignoredCols(colNames, Arrays.asList("c_6")), gaussian);
            assertCorrectGamification(allGamifiedColumns, isc_6);
            Frame isc_7  = extractGamifiedColumns(train, new String[][]{{"c_7"}}, new int[]{6}, new double[]{0.001},
                    new int[]{2}, new int[]{8}, ignoredCols(colNames, Arrays.asList("c_7")), gaussian);
            assertCorrectGamification(allGamifiedColumns, isc_7);
            Frame csc_8  = extractGamifiedColumns(train, new String[][]{{"c_8"}}, new int[]{-1}, new double[]{0.001},
                    new int[]{0}, new int[]{7}, ignoredCols(colNames, Arrays.asList("c_8")), gaussian);
            assertCorrectGamification(allGamifiedColumns, csc_8);
            Frame csc_9  = extractGamifiedColumns(train, new String[][]{{"c_9"}}, new int[]{-1}, new double[]{0.001},
                    new int[]{0}, new int[]{6}, ignoredCols(colNames, Arrays.asList("c_9")), gaussian);
            assertCorrectGamification(allGamifiedColumns, csc_9);
        } finally {
            Scope.exit();
        }
    }

    /***
     * Test correct gamification of data when there are thin plate splines, CS splines and I-splines are specified
     */
    @Test
    public void testISCSTPTransform() {
        Scope.enter();
        try {
            Frame train = parseAndTrackTestFile("smalldata/gam_test/synthetic_20Cols_gaussian_20KRows.csv");
            Frame allGamifiedColumns = extractGamifiedColumns(train, new String[][]{{"c_0"}, {"c_1", "c_2"}, {"c_2"},
                            {"c_3"}, {"c_5"}, {"c_6", "c_7", "c_8"}, {"c_9"}, {"c_9"}, {"c_9"}}, new int[]{-1, -1, 9, 
                            -1, 10, -1, -1, -1, 8}, new double[]{0.001, 0.001, 0.001, 0.001, 0.001, 0.001, 0.001, 
                            0.001, 0.001}, new int[]{0, 1, 2, 0, 2, 1, 0, 1, 2}, new int[]{5, 11, 7, 8, 9, 12, 8, 11, 6},
                    null, gaussian);
            List<String> colNames = new ArrayList<>(Arrays.asList(train.names()));
            colNames.remove(colNames.size() - 1); // remove response name
            Frame csc_0 = extractGamifiedColumns(train, new String[][]{{"c_0"}}, null,
                    new double[]{0.001}, new int[]{0}, new int[]{5}, ignoredCols(colNames,
                            Arrays.asList("c_0")), gaussian);
            assertCorrectGamification(allGamifiedColumns,  csc_0);
            Frame tpc1c2 = extractGamifiedColumns(train, new String[][]{{"c_1", "c_2"}}, null,
                    new double[]{0.001}, new int[]{1}, new int[]{11}, ignoredCols(colNames,
                            Arrays.asList("c_1", "c_2")), gaussian);
            assertCorrectGamification(allGamifiedColumns, tpc1c2);
            Frame isc_2  = extractGamifiedColumns(train, new String[][]{{"c_2"}}, new int[]{9}, new double[]{0.001},
                    new int[]{2}, new int[]{7}, ignoredCols(colNames, Arrays.asList("c_2")), gaussian);
            assertCorrectGamification(allGamifiedColumns, isc_2);
            Frame csc_3 = extractGamifiedColumns(train, new String[][]{{"c_3"}}, null,
                    new double[]{0.001}, new int[]{0}, new int[]{8}, ignoredCols(colNames,
                            Arrays.asList("c_3")), gaussian);
            assertCorrectGamification(allGamifiedColumns,  csc_3);
            Frame isc_5  = extractGamifiedColumns(train, new String[][]{{"c_5"}}, new int[]{10}, new double[]{0.001},
                    new int[]{2}, new int[]{9}, ignoredCols(colNames, Arrays.asList("c_5")), gaussian);
            assertCorrectGamification(allGamifiedColumns, isc_2);
            Frame tpc6c7c8 = extractGamifiedColumns(train, new String[][]{{"c_6", "c_7", "c_8"}}, null,
                    new double[]{0.001}, new int[]{1}, new int[]{12}, ignoredCols(colNames,
                            Arrays.asList("c_6", "c_7", "c_8")), gaussian);
            assertCorrectGamification(allGamifiedColumns, tpc6c7c8);
            Frame csc_9 = extractGamifiedColumns(train, new String[][]{{"c_9"}}, null,
                    new double[]{0.001}, new int[]{0}, new int[]{8}, ignoredCols(colNames,
                            Arrays.asList("c_9")), gaussian);
            assertCorrectGamification(allGamifiedColumns,  csc_9);
            Frame tpc9 = extractGamifiedColumns(train, new String[][]{{"c_9"}}, null,
                    new double[]{0.001}, new int[]{1}, new int[]{11}, ignoredCols(colNames,
                            Arrays.asList("c_9")), gaussian);
            assertCorrectGamification(allGamifiedColumns, tpc9);
            Frame isc_9  = extractGamifiedColumns(train, new String[][]{{"c_9"}}, new int[]{8}, new double[]{0.001},
                    new int[]{2}, new int[]{6}, ignoredCols(colNames, Arrays.asList("c_9")), gaussian);
            assertCorrectGamification(allGamifiedColumns, isc_9);
        } finally {
            Scope.exit();
        }
    }
}
