package hex.glm;

import hex.generic.Generic;
import hex.generic.GenericModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.*;

/**
 * Tests GLM MOJO scoring for all combinations of remove_offset_effects and control_variables
 * across binomial, gaussian, and tweedie families.
 */
@RunWith(H2ORunner.class)
@CloudSize(1)
public class GLMMojoControlVarsOffsetTest extends TestUtil {

    // -- Binomial --

    @Test
    public void testMojoBinomialBaseline() throws Exception {
        testCombination(buildBinomialFrame(), GLMModel.GLMParameters.Family.binomial,
                false, false, "binomial_baseline");
    }

    @Test
    public void testMojoBinomialRemoveOffsetOnly() throws Exception {
        testCombination(buildBinomialFrame(), GLMModel.GLMParameters.Family.binomial,
                true, false, "binomial_roe");
    }

    @Test
    public void testMojoBinomialControlVarsOnly() throws Exception {
        testCombination(buildBinomialFrame(), GLMModel.GLMParameters.Family.binomial,
                false, true, "binomial_cv");
    }

    @Test
    public void testMojoBinomialBoth() throws Exception {
        testCombination(buildBinomialFrame(), GLMModel.GLMParameters.Family.binomial,
                true, true, "binomial_both");
    }

    // -- Gaussian --

    @Test
    public void testMojoGaussianBaseline() throws Exception {
        testCombination(buildRegressionFrame(false), GLMModel.GLMParameters.Family.gaussian,
                false, false, "gaussian_baseline");
    }

    @Test
    public void testMojoGaussianRemoveOffsetOnly() throws Exception {
        testCombination(buildRegressionFrame(false), GLMModel.GLMParameters.Family.gaussian,
                true, false, "gaussian_roe");
    }

    @Test
    public void testMojoGaussianControlVarsOnly() throws Exception {
        testCombination(buildRegressionFrame(false), GLMModel.GLMParameters.Family.gaussian,
                false, true, "gaussian_cv");
    }

    @Test
    public void testMojoGaussianBoth() throws Exception {
        testCombination(buildRegressionFrame(false), GLMModel.GLMParameters.Family.gaussian,
                true, true, "gaussian_both");
    }

    // -- Tweedie --

    @Test
    public void testMojoTweedieBaseline() throws Exception {
        testCombination(buildRegressionFrame(true), GLMModel.GLMParameters.Family.tweedie,
                false, false, "tweedie_baseline");
    }

    @Test
    public void testMojoTweedieRemoveOffsetOnly() throws Exception {
        testCombination(buildRegressionFrame(true), GLMModel.GLMParameters.Family.tweedie,
                true, false, "tweedie_roe");
    }

    @Test
    public void testMojoTweedieControlVarsOnly() throws Exception {
        testCombination(buildRegressionFrame(true), GLMModel.GLMParameters.Family.tweedie,
                false, true, "tweedie_cv");
    }

    @Test
    public void testMojoTweedieBoth() throws Exception {
        testCombination(buildRegressionFrame(true), GLMModel.GLMParameters.Family.tweedie,
                true, true, "tweedie_both");
    }

    // -- Feature-effect tests: verify all combinations produce different predictions --

    @Test
    public void testAllCombinationsDifferGaussian() throws Exception {
        assertAllCombinationsDiffer(buildRegressionFrame(false), GLMModel.GLMParameters.Family.gaussian);
    }

    // -- Test logic --

    private void assertAllCombinationsDiffer(Frame train, GLMModel.GLMParameters.Family family) throws Exception {
        try {
            Scope.enter();
            Scope.track(train);

            // Train all four combinations
            Frame predsBase = trainAndScore(train, family, false, false);
            Frame predsRO   = trainAndScore(train, family, true,  false);
            Frame predsCV   = trainAndScore(train, family, false, true);
            Frame predsBoth = trainAndScore(train, family, true,  true);

            // Each combination should produce different predictions from every other
            assertPredictionsDiffer(predsBase, predsRO,   "baseline vs RO");
            assertPredictionsDiffer(predsBase, predsCV,   "baseline vs CV");
            assertPredictionsDiffer(predsBase, predsBoth, "baseline vs RO+CV");
            assertPredictionsDiffer(predsRO,   predsCV,   "RO vs CV");
            assertPredictionsDiffer(predsRO,   predsBoth, "RO vs RO+CV");
            assertPredictionsDiffer(predsCV,   predsBoth, "CV vs RO+CV");
        } finally {
            Scope.exit();
        }
    }

    private Frame trainAndScore(Frame train, GLMModel.GLMParameters.Family family,
                                boolean removeOffsetEffects, boolean useControlVars) {
        GLMModel.GLMParameters params = makeParams(train, family, removeOffsetEffects, useControlVars);
        GLMModel model = new GLM(params).trainModel().get();
        Scope.track_generic(model);
        Frame preds = model.score(train);
        Scope.track(preds);
        return preds;
    }

    private GLMModel.GLMParameters makeParams(Frame train, GLMModel.GLMParameters.Family family,
                                               boolean removeOffsetEffects, boolean useControlVars) {
        GLMModel.GLMParameters params = new GLMModel.GLMParameters();
        params._response_column = "response";
        params._train = train._key;
        params._family = family;
        params._offset_column = "offset";
        params._lambda = new double[]{0};
        if (removeOffsetEffects) params._remove_offset_effects = true;
        if (useControlVars) params._control_variables = new String[]{"x3"};
        if (family == GLMModel.GLMParameters.Family.tweedie) {
            params._tweedie_variance_power = 1.5;
            params._tweedie_link_power = 0;
        }
        return params;
    }

    private void assertPredictionsDiffer(Frame pred1, Frame pred2, String label) {
        int colIdx = pred1.numCols() > 1 ? 1 : 0;
        long nrows = Math.min(pred1.numRows(), 100);
        int differ = 0;
        for (int i = 0; i < nrows; i++) {
            if (Math.abs(pred1.vec(colIdx).at(i) - pred2.vec(colIdx).at(i)) > 1e-10) differ++;
        }
        assertTrue(label + ": predictions should differ (only " + differ + "/" + nrows + " rows differed)",
                differ > nrows / 10);
    }

    private void testCombination(Frame train, GLMModel.GLMParameters.Family family,
                                 boolean removeOffsetEffects, boolean useControlVars, String label) throws Exception {
        try {
            Scope.enter();
            Scope.track(train);

            GLMModel.GLMParameters params = makeParams(train, family, removeOffsetEffects, useControlVars);
            GLMModel model = new GLM(params).trainModel().get();
            Scope.track_generic(model);
            assertTrue(label + ": should support MOJO", model.haveMojo());

            Frame h2oPreds = model.score(train);
            Scope.track(h2oPreds);

            // Save MOJO, reimport as GenericModel, score, and compare
            File mojoFile = File.createTempFile("glm_mojo", ".zip");
            mojoFile.deleteOnExit();
            try (FileOutputStream fos = new FileOutputStream(mojoFile)) {
                model.getMojo().writeTo(fos);
            }

            GenericModel genericModel = Generic.importMojoModel(mojoFile.getAbsolutePath(), false);
            Scope.track_generic(genericModel);

            Frame mojoPreds = genericModel.score(train);
            Scope.track(mojoPreds);

            assertFrameEquals(h2oPreds, mojoPreds, 1e-8);
        } finally {
            Scope.exit();
        }
    }

    // -- Frame builders --

    private Frame buildBinomialFrame() {
        double[] x1 = {1.2, 2.3, 3.1, 0.5, 1.8, 2.7, 3.5, 0.9, 1.5, 2.1, 3.0, 0.7, 1.1, 2.5, 3.3, 0.4, 1.6, 2.9, 3.7, 0.8};
        double[] x2 = {0.5, 1.5, 0.8, 1.2, 0.3, 1.8, 0.6, 1.1, 0.9, 1.4, 0.7, 1.6, 0.4, 1.3, 0.2, 1.7, 0.1, 1.9, 0.5, 1.0};
        double[] x3 = {3.0, 1.0, 2.0, 4.0, 3.5, 1.5, 2.5, 3.2, 1.8, 2.8, 3.8, 1.2, 2.2, 3.6, 1.6, 2.6, 4.2, 0.8, 3.4, 1.4};
        double[] offset = {0.1, -0.2, 0.3, -0.1, 0.2, -0.3, 0.15, -0.15, 0.25, -0.25, 0.05, -0.05, 0.12, -0.12, 0.22, -0.22, 0.08, -0.08, 0.18, -0.18};
        String[] response = {"1", "1", "1", "0", "1", "1", "1", "0", "1", "1", "1", "0", "0", "1", "1", "0", "1", "1", "1", "0"};

        Frame f = new TestFrameBuilder()
                .withColNames("x1", "x2", "x3", "offset", "response")
                .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
                .withDataForCol(0, x1).withDataForCol(1, x2).withDataForCol(2, x3)
                .withDataForCol(3, offset).withDataForCol(4, response)
                .build();
        return f;
    }

    private Frame buildRegressionFrame(boolean positiveResponse) {
        double[] x1 = {1.2, 2.3, 3.1, 0.5, 1.8, 2.7, 3.5, 0.9, 1.5, 2.1, 3.0, 0.7, 1.1, 2.5, 3.3, 0.4, 1.6, 2.9, 3.7, 0.8};
        double[] x2 = {0.5, 1.5, 0.8, 1.2, 0.3, 1.8, 0.6, 1.1, 0.9, 1.4, 0.7, 1.6, 0.4, 1.3, 0.2, 1.7, 0.1, 1.9, 0.5, 1.0};
        double[] x3 = {3.0, 1.0, 2.0, 4.0, 3.5, 1.5, 2.5, 3.2, 1.8, 2.8, 3.8, 1.2, 2.2, 3.6, 1.6, 2.6, 4.2, 0.8, 3.4, 1.4};
        double[] offset = {0.1, 0.2, 0.3, 0.1, 0.2, 0.3, 0.15, 0.15, 0.25, 0.25, 0.05, 0.05, 0.12, 0.12, 0.22, 0.22, 0.08, 0.08, 0.18, 0.18};
        double[] response = positiveResponse
                ? new double[]{2.5, 4.1, 5.8, 1.2, 3.3, 5.0, 6.5, 1.8, 2.9, 4.5, 5.6, 1.5, 2.1, 4.8, 6.2, 0.9, 3.0, 5.3, 7.0, 1.6}
                : new double[]{2.5, 4.1, 5.8, -1.2, 3.3, 5.0, 6.5, -1.8, 2.9, 4.5, 5.6, -1.5, 2.1, 4.8, 6.2, -0.9, 3.0, 5.3, 7.0, -1.6};

        Frame f = new TestFrameBuilder()
                .withColNames("x1", "x2", "x3", "offset", "response")
                .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
                .withDataForCol(0, x1).withDataForCol(1, x2).withDataForCol(2, x3)
                .withDataForCol(3, offset).withDataForCol(4, response)
                .build();
        return f;
    }
}
