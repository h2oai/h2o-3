package hex.glm;

import hex.generic.Generic;
import hex.generic.GenericModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.TwoDimTable;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.*;

/**
 * GH-16859: remove_offset_effects must also work with lambda_search=true.
 *
 * remove_offset_effects does not change the optimization (the offset stays part
 * of the fit) - it only strips the offset contribution from the reported model.
 * So a model trained with remove_offset_effects=true and lambda_search=true must
 * carry an unrestricted model whose fit/metrics match a plain offset-present model
 * (remove_offset_effects=false) trained with the same lambda_search settings,
 * while the reported (restricted) predictions differ.
 */
@RunWith(H2ORunner.class)
@CloudSize(1)
public class GLMRemoveOffsetLambdaSearchTest extends TestUtil {

    private static final String RESPONSE = "C21";
    private static final String OFFSET = "C20";

    private Frame binomialFrame() {
        Frame train = parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv");
        int numCols = train.numCols();
        int enumCols = (numCols - 1) / 2;
        for (int cindex = 0; cindex < enumCols; cindex++) {
            train.replace(cindex, train.vec(cindex).toCategoricalVec()).remove();
        }
        train.replace((numCols - 1), train.vec(numCols - 1).toCategoricalVec()).remove();
        DKV.put(train);
        return train;
    }

    private GLMModel.GLMParameters baseParams(Frame train) {
        GLMModel.GLMParameters params = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.binomial);
        params._response_column = RESPONSE;
        params._train = train._key;
        params._offset_column = OFFSET;
        params._lambda_search = true;
        return params;
    }

    /**
     * remove_offset_effects + lambda_search trains, its unrestricted model reproduces the plain
     * offset model (same selected lambda, same active set, same metrics), and its reported
     * predictions differ.
     */
    @Test
    public void removeOffsetEffectsWorksWithLambdaSearch() {
        Frame train = null, test = null, preds = null, preds2 = null;
        GLMModel glm = null, glm2 = null;
        try {
            Scope.enter();
            train = binomialFrame();
            Scope.track_generic(train);

            test = new Frame(train);
            test.remove(RESPONSE);

            GLMModel.GLMParameters params = baseParams(train);
            params._remove_offset_effects = true;
            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);
            preds = glm.score(test);
            Scope.track_generic(preds);

            // same settings, offset effect kept -> what the unrestricted model must reproduce
            params._remove_offset_effects = false;
            glm2 = new GLM(params).trainModel().get();
            Scope.track_generic(glm2);
            preds2 = glm2.score(test);
            Scope.track_generic(preds2);

            double delta = 1e-8;
            // unrestricted metrics match the offset-present model
            assertEquals(glm2._output._training_metrics.auc_obj()._auc,
                    glm._output._training_metrics_unrestricted_model.auc_obj()._auc, delta);
            assertEquals(glm2._output._training_metrics.mse(),
                    glm._output._training_metrics_unrestricted_model.mse(), delta);
            assertEquals(glm2._output._training_metrics.rmse(),
                    glm._output._training_metrics_unrestricted_model.rmse(), delta);

            // lambda_search must select the same regularization strength and active set (fit is identical)
            assertEquals("lambda_best must match plain offset model",
                    glm2._output.lambda_best(), glm._output.lambda_best(), 0.0);
            assertEquals("active predictor count (rank) must match plain offset model",
                    glm2._output.bestSubmodel().rank(), glm._output.bestSubmodel().rank());

            // reported (restricted) predictions actually differ once the offset effect is removed
            int differ = 0;
            int testRowNumber = 100;
            double threshold = (2 * testRowNumber) / 1.1;
            for (int i = 0; i < testRowNumber; i++) {
                if (preds.vec(1).at(i) != preds2.vec(1).at(i)) differ++;
                if (preds.vec(2).at(i) != preds2.vec(2).at(i)) differ++;
            }
            assertTrue("Expected number of differing predictions to exceed threshold", differ > threshold);
        } finally {
            if (train != null) train.remove();
            if (test != null) test.remove();
            if (preds != null) preds.remove();
            if (preds2 != null) preds2.remove();
            if (glm != null) glm.remove();
            if (glm2 != null) glm2.remove();
            Scope.exit();
        }
    }

    /**
     * The removed offset effect is exactly the offset: the restricted predictions must equal the
     * plain offset model's predictions when scored with the offset column set to zero.
     */
    @Test
    public void restrictedPredictionsEqualOffsetZeroedModel() {
        Frame train = null, predsRO = null, predsZeroed = null;
        Vec oldOffset = null;
        GLMModel glm = null, glm2 = null;
        try {
            Scope.enter();
            train = binomialFrame();
            Scope.track_generic(train);

            GLMModel.GLMParameters params = baseParams(train);
            params._remove_offset_effects = true;
            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            params._remove_offset_effects = false;
            glm2 = new GLM(params).trainModel().get();
            Scope.track_generic(glm2);

            // the RO model ignores the offset, so we can score it before zeroing the offset column
            predsRO = glm.score(train);          // offset effect removed
            Scope.track_generic(predsRO);

            // zero out the offset column in place (both models are already trained)
            Vec zoff = train.anyVec().makeZero();
            Scope.track(zoff);
            oldOffset = train.replace(train.find(OFFSET), zoff);
            DKV.put(train);

            predsZeroed = glm2.score(train);     // plain model, offset manually zeroed
            Scope.track_generic(predsZeroed);

            assertFrameEquals(predsRO, predsZeroed, 1e-8);
        } finally {
            if (oldOffset != null) oldOffset.remove();
            if (train != null) train.remove();
            if (predsRO != null) predsRO.remove();
            if (predsZeroed != null) predsZeroed.remove();
            if (glm != null) glm.remove();
            if (glm2 != null) glm2.remove();
            Scope.exit();
        }
    }

    /**
     * With remove_offset_effects + lambda_search + generate_scoring_history, the model must expose
     * both the restricted scoring history and the unrestricted scoring history, and the unrestricted
     * one must reproduce the plain offset model's scoring history. A plain offset model must not carry
     * an unrestricted scoring history.
     */
    @Test
    public void scoringHistoryHasRestrictedAndUnrestrictedMetrics() {
        Frame train = null;
        GLMModel glm = null, glm2 = null;
        try {
            Scope.enter();
            train = binomialFrame();
            Scope.track_generic(train);

            GLMModel.GLMParameters params = baseParams(train);
            params._generate_scoring_history = true;
            params._remove_offset_effects = true;
            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            params._remove_offset_effects = false;
            glm2 = new GLM(params).trainModel().get();
            Scope.track_generic(glm2);

            TwoDimTable restricted = glm._output._scoring_history;
            TwoDimTable unrestricted = glm._output._scoring_history_unrestricted_model;
            TwoDimTable plainSH = glm2._output._scoring_history;
            System.out.println("Restricted scoring history:\n" + restricted);
            System.out.println("Unrestricted scoring history:\n" + unrestricted);
            System.out.println("Plain offset model scoring history:\n" + plainSH);

            assertNotNull("Restricted scoring history must be present", restricted);
            assertTrue("Restricted scoring history must have at least one row", restricted.getRowDim() > 0);
            assertNotNull("Unrestricted scoring history must be present when remove_offset_effects is on",
                    unrestricted);
            assertTrue("Unrestricted scoring history must have at least one row", unrestricted.getRowDim() > 0);

            // unrestricted scoring history must match the plain offset model's scoring history
            // (ignore timestamp/duration columns and the " unrestricted model" header suffix)
            plainSH.setTableHeader(unrestricted.getTableHeader());
            assertTwoDimTableEquals(unrestricted, plainSH, new int[]{0, 1});

            assertNull("Plain offset model must not have an unrestricted scoring history",
                    glm2._output._scoring_history_unrestricted_model);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (glm2 != null) glm2.remove();
            Scope.exit();
        }
    }

    /**
     * Validation-frame metrics of the unrestricted model must match the plain offset model, under
     * lambda_search.
     */
    @Test
    public void validationMetricsUnrestrictedMatchPlainOffsetModel() {
        Frame train = null;
        GLMModel glm = null, glm2 = null;
        try {
            Scope.enter();
            train = binomialFrame();
            Scope.track_generic(train);

            GLMModel.GLMParameters params = baseParams(train);
            params._valid = train._key; // reuse train as validation frame - enough to exercise the code path
            params._remove_offset_effects = true;
            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            params._remove_offset_effects = false;
            glm2 = new GLM(params).trainModel().get();
            Scope.track_generic(glm2);

            double delta = 1e-8;
            assertNotNull("Unrestricted validation metrics must be present",
                    glm._output._validation_metrics_unrestricted_model);
            assertEquals(glm2._output._validation_metrics.mse(),
                    glm._output._validation_metrics_unrestricted_model.mse(), delta);
            assertEquals(glm2._output._validation_metrics.rmse(),
                    glm._output._validation_metrics_unrestricted_model.rmse(), delta);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (glm2 != null) glm2.remove();
            Scope.exit();
        }
    }

    /**
     * remove_offset_effects + lambda_search + cross-validation must train without error and populate
     * cross-validation metrics (the CV second pass is the historically fragile intersection).
     */
    @Test
    public void worksWithCrossValidationAndLambdaSearch() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();
            train = binomialFrame();
            Scope.track_generic(train);

            GLMModel.GLMParameters params = baseParams(train);
            params._nfolds = 3;
            params._seed = 0xC0FFEE;
            params._remove_offset_effects = true;
            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            assertNotNull("cross-validation metrics must be populated", glm._output._cross_validation_metrics);
            assertNotNull("unrestricted training metrics must be populated with CV",
                    glm._output._training_metrics_unrestricted_model);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    /**
     * The MOJO must reproduce the in-H2O (restricted) predictions of a remove_offset_effects +
     * lambda_search model - guards the sparseOffset MOJO-vs-in-H2O divergence risk.
     */
    @Test
    public void mojoMatchesInH2OWithLambdaSearch() throws Exception {
        Frame train = null, h2oPreds = null, mojoPreds = null;
        GLMModel glm = null;
        GenericModel generic = null;
        try {
            Scope.enter();
            train = binomialFrame();
            Scope.track_generic(train);

            GLMModel.GLMParameters params = baseParams(train);
            params._remove_offset_effects = true;
            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);
            assertTrue("model should support MOJO", glm.haveMojo());

            h2oPreds = glm.score(train);
            Scope.track_generic(h2oPreds);

            File mojoFile = File.createTempFile("glm_mojo", ".zip");
            mojoFile.deleteOnExit();
            try (FileOutputStream fos = new FileOutputStream(mojoFile)) {
                glm.getMojo().writeTo(fos);
            }
            generic = Generic.importMojoModel(mojoFile.getAbsolutePath(), false);
            Scope.track_generic(generic);

            mojoPreds = generic.score(train);
            Scope.track_generic(mojoPreds);

            assertFrameEquals(h2oPreds, mojoPreds, 1e-8);
        } finally {
            if (train != null) train.remove();
            if (h2oPreds != null) h2oPreds.remove();
            if (mojoPreds != null) mojoPreds.remove();
            if (glm != null) glm.remove();
            if (generic != null) generic.remove();
            Scope.exit();
        }
    }
}
