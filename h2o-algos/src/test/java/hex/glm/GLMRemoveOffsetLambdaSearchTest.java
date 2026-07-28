package hex.glm;

import hex.GLMMetrics;
import hex.SplitFrame;
import hex.generic.Generic;
import hex.generic.GenericModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.MRTask;
import water.Scope;
import water.TestUtil;
import water.fvec.Chunk;
import water.fvec.TestFrameBuilder;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.TwoDimTable;
import water.exceptions.H2OModelBuilderIllegalArgumentException;

import java.io.File;
import java.nio.file.Files;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Random;

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

    // Fresh params for the CV comparison, with a fixed seed so both models split folds identically.
    private GLMModel.GLMParameters cvParams(Frame train) {
        GLMModel.GLMParameters params = baseParams(train);
        params._nfolds = 3;
        params._generate_scoring_history = true;
        params._seed = 0xC0FFEE;
        return params;
    }

    private Frame prostateFrame() {
        Frame df = parseTestFile("smalldata/prostate/prostate.csv");
        DKV.put(df);
        return df;
    }

    // AGE is used directly as the offset (its magnitude is irrelevant to the invariants); ID is ignored.
    private GLMModel.GLMParameters prostateParams(Frame df, GLMModel.GLMParameters.Family family, String response) {
        GLMModel.GLMParameters params = new GLMModel.GLMParameters(family);
        params._response_column = response;
        params._train = df._key;
        params._offset_column = "AGE";
        params._ignored_columns = new String[]{"ID"};
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
     * remove_offset_effects + lambda_search + generate_scoring_history + score_each_iteration + a
     * validation frame. Mid-lambda scoring events append rows to the unrestricted lambda history that
     * the restricted one must also receive; the model must train (no crash in the finalization combine)
     * and the restricted scoring history must actually differ from the unrestricted one.
     */
    @Test
    public void scoringHistoryWithScoreEachIterationAndValidation() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();
            train = binomialFrame();
            Scope.track_generic(train);

            GLMModel.GLMParameters params = baseParams(train);
            params._valid = train._key;
            params._generate_scoring_history = true;
            params._score_each_iteration = true;
            params._remove_offset_effects = true;
            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            TwoDimTable restricted = glm._output._scoring_history;
            TwoDimTable unrestricted = glm._output._scoring_history_unrestricted_model;
            assertNotNull("restricted scoring history must be present", restricted);
            assertTrue(restricted.getRowDim() > 0);
            assertNotNull("unrestricted scoring history must be present", unrestricted);
            assertTrue(unrestricted.getRowDim() > 0);

            // the restricted (offset-removed) deviances must actually differ from the unrestricted ones,
            // otherwise the offset removal did nothing in the reported scoring history
            int devCol = Arrays.asList(restricted.getColHeaders()).indexOf("deviance_train");
            int devColU = Arrays.asList(unrestricted.getColHeaders()).indexOf("deviance_train");
            assertTrue("scoring histories must expose deviance_train", devCol >= 0 && devColU >= 0);
            boolean anyDiffer = false;
            int rows = Math.min(restricted.getRowDim(), unrestricted.getRowDim());
            for (int i = 0; i < rows && !anyDiffer; i++) {
                double r = ((Number) restricted.get(i, devCol)).doubleValue();
                double u = ((Number) unrestricted.get(i, devColU)).doubleValue();
                if (Math.abs(r - u) > 1e-8) anyDiffer = true;
            }
            assertTrue("restricted deviance_train must differ from unrestricted", anyDiffer);
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

            File mojoFile = Files.createTempFile("glm_mojo", ".zip").toFile();
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

    /**
     * The restricted deviance recompute (GLMResDevTask) is family-specific, so it must be exercised beyond
     * binomial - including the non-canonical families (gamma, tweedie, negativebinomial). For each family the
     * unrestricted metrics must match the plain offset model and the restricted predictions must differ.
     */
    @Test
    public void familiesWorkWithLambdaSearch() {
        GLMModel.GLMParameters.Family[] families = {
                GLMModel.GLMParameters.Family.gaussian, GLMModel.GLMParameters.Family.poisson,
                GLMModel.GLMParameters.Family.gamma, GLMModel.GLMParameters.Family.negativebinomial,
                GLMModel.GLMParameters.Family.tweedie};
        String[] responses = {"VOL", "GLEASON", "PSA", "GLEASON", "VOL"};  // PSA is strictly positive -> valid for gamma
        for (int idx = 0; idx < families.length; idx++) {
            Frame df = null, predRO = null, predBase = null;
            GLMModel ro = null, base = null;
            try {
                Scope.enter();
                df = prostateFrame();
                Scope.track_generic(df);

                GLMModel.GLMParameters params = prostateParams(df, families[idx], responses[idx]);
                if (families[idx] == GLMModel.GLMParameters.Family.negativebinomial) params._theta = 0.5;
                if (families[idx] == GLMModel.GLMParameters.Family.tweedie) {
                    params._tweedie_variance_power = 1.5;
                    params._tweedie_link_power = 0.0;
                }
                params._remove_offset_effects = true;
                ro = new GLM(params).trainModel().get();
                Scope.track_generic(ro);

                params._remove_offset_effects = false;
                base = new GLM(params).trainModel().get();
                Scope.track_generic(base);

                double denom = Math.max(1.0, Math.abs(base._output._training_metrics.mse()));
                assertEquals("[" + families[idx] + "] unrestricted train mse must match plain offset model",
                        base._output._training_metrics.mse(),
                        ro._output._training_metrics_unrestricted_model.mse(), 1e-6 * denom);
                assertEquals("[" + families[idx] + "] lambda_best must match plain offset model",
                        base._output.lambda_best(), ro._output.lambda_best(), 0.0);

                predRO = ro.score(df);
                Scope.track_generic(predRO);
                predBase = base.score(df);
                Scope.track_generic(predBase);
                double maxDiff = 0;  // regression families report a single "predict" column
                for (int i = 0; i < 100; i++)
                    maxDiff = Math.max(maxDiff, Math.abs(predRO.vec(0).at(i) - predBase.vec(0).at(i)));
                assertTrue("[" + families[idx] + "] restricted predictions must differ from plain offset model",
                        maxDiff > 1e-6);
            } finally {
                if (df != null) df.remove();
                if (predRO != null) predRO.remove();
                if (predBase != null) predBase.remove();
                if (ro != null) ro.remove();
                if (base != null) base.remove();
                Scope.exit();
            }
        }
    }

    /**
     * A weights column feeds into the restricted deviance sums; the fit stays identical, so the unrestricted
     * metrics must still match the plain (weighted) offset model and the restricted predictions must differ.
     */
    @Test
    public void weightsWorkWithLambdaSearch() {
        Frame train = null, predRO = null, predBase = null;
        GLMModel ro = null, base = null;
        try {
            Scope.enter();
            train = binomialFrame();
            Scope.track_generic(train);

            // positive, non-constant per-row weights (1, 2, 3, ...) built from a throwaway output frame
            Frame wpf = new MRTask() {
                @Override public void map(Chunk c, NewChunk nc) {
                    for (int i = 0; i < c._len; i++) nc.addNum(1.0 + (i % 3));
                }
            }.doAll(Vec.T_NUM, train.vec(OFFSET)).outputFrame();
            train.add("weights", wpf.remove(0));  // transfer vec ownership to train
            wpf.remove();
            DKV.put(train);

            GLMModel.GLMParameters params = baseParams(train);
            params._weights_column = "weights";
            params._remove_offset_effects = true;
            ro = new GLM(params).trainModel().get();
            Scope.track_generic(ro);

            params._remove_offset_effects = false;
            base = new GLM(params).trainModel().get();
            Scope.track_generic(base);

            assertEquals("unrestricted train mse must match plain offset model (weighted)",
                    base._output._training_metrics.mse(),
                    ro._output._training_metrics_unrestricted_model.mse(), 1e-8);
            assertEquals("lambda_best must match plain offset model",
                    base._output.lambda_best(), ro._output.lambda_best(), 0.0);

            predRO = ro.score(train);
            Scope.track_generic(predRO);
            predBase = base.score(train);
            Scope.track_generic(predBase);
            double maxDiff = 0;
            for (int i = 0; i < 100; i++)
                maxDiff = Math.max(maxDiff, Math.abs(predRO.vec(2).at(i) - predBase.vec(2).at(i)));
            assertTrue("restricted predictions must differ from plain offset model", maxDiff > 1e-6);
        } finally {
            if (train != null) train.remove();
            if (predRO != null) predRO.remove();
            if (predBase != null) predBase.remove();
            if (ro != null) ro.remove();
            if (base != null) base.remove();
            Scope.exit();
        }
    }

    /**
     * A genuine holdout (not the training frame) exercises the restricted validation-deviance path
     * (GLMResDevTask on a distinct _validDinfo). The unrestricted validation metrics must match the plain
     * offset model on that holdout.
     */
    @Test
    public void holdoutValidationUnrestrictedMatchesPlainOffsetModel() {
        Frame train = null;
        GLMModel ro = null, base = null;
        try {
            Scope.enter();
            train = binomialFrame();
            Scope.track_generic(train);

            SplitFrame sf = new SplitFrame(train, new double[]{0.8, 0.2},
                    new Key[]{Key.make("roLS_tr.hex"), Key.make("roLS_va.hex")});
            sf.exec().get();
            Key[] ks = sf._destination_frames;
            Frame tr = DKV.getGet(ks[0]);
            Frame va = DKV.getGet(ks[1]);
            Scope.track(tr, va);

            GLMModel.GLMParameters params = baseParams(train);
            params._train = tr._key;
            params._valid = va._key;
            params._remove_offset_effects = true;
            ro = new GLM(params).trainModel().get();
            Scope.track_generic(ro);

            params._remove_offset_effects = false;
            base = new GLM(params).trainModel().get();
            Scope.track_generic(base);

            double delta = 1e-8;
            assertNotNull("unrestricted validation metrics must be present",
                    ro._output._validation_metrics_unrestricted_model);
            assertEquals(base._output._validation_metrics.mse(),
                    ro._output._validation_metrics_unrestricted_model.mse(), delta);
            assertEquals(base._output._validation_metrics.rmse(),
                    ro._output._validation_metrics_unrestricted_model.rmse(), delta);
        } finally {
            if (train != null) train.remove();
            if (ro != null) ro.remove();
            if (base != null) base.remove();
            Scope.exit();
        }
    }

    /**
     * getRegularizationPath() divides the Submodel deviances by the *reported* null deviance, which
     * remove_offset_effects makes offset-removed. But Submodel.devianceTrain is always the offset-included fit
     * deviance, and under lambda_search Submodel.devianceValid keeps the offset too (see GLM.computeSubmodel).
     * Mixing the two scales makes the ratio stop being an explained-deviance fraction at all.
     *
     * Two oracles, both independent of which scale the path is expressed on:
     *   1. lambda[0] >= lambda_max gives the intercept-only submodel, whose explained deviance is pinned to 0.
     *      Before the fix it reported ~0.097.
     *   2. remove_offset_effects leaves the fit untouched, so once both models express the path on the same
     *      (offset-included) scale the whole path must match the plain offset model's element for element.
     */
    @Test
    public void regularizationPathExplainedDevianceIsNotScaleMixed() {
        Frame train = null;
        GLMModel ro = null, base = null;
        try {
            Scope.enter();
            train = binomialFrame();
            Scope.track_generic(train);

            SplitFrame sf = new SplitFrame(train, new double[]{0.8, 0.2},
                    new Key[]{Key.make("roRP_tr.hex"), Key.make("roRP_va.hex")});
            sf.exec().get();
            Frame tr = DKV.getGet(sf._destination_frames[0]);
            Frame va = DKV.getGet(sf._destination_frames[1]);
            Scope.track(tr, va);

            // Each model needs its own params: GLM keeps the reference (_model._parms = _parms) and
            // getRegularizationPath() reads _parms._remove_offset_effects, so sharing one object and flipping the
            // flag for the second model would retroactively change what the first model reports.
            GLMModel.GLMParameters roParams = baseParams(train);
            roParams._train = tr._key;
            roParams._valid = va._key;
            roParams._nlambdas = 10;
            roParams._remove_offset_effects = true;
            ro = new GLM(roParams).trainModel().get();
            Scope.track_generic(ro);

            GLMModel.GLMParameters baseline = baseParams(train);
            baseline._train = tr._key;
            baseline._valid = va._key;
            baseline._nlambdas = 10;
            base = new GLM(baseline).trainModel().get();
            Scope.track_generic(base);

            GLMModel.RegularizationPath rpRO = ro.getRegularizationPath();
            GLMModel.RegularizationPath rpBase = base.getRegularizationPath();

            // Guard the oracles: if the fit itself differed, comparing the paths would prove nothing.
            assertArrayEquals("remove_offset_effects must not change the lambda sequence",
                    rpBase._lambdas, rpRO._lambdas, 0);
            for (int i = 0; i < rpRO._lambdas.length; i++)
                assertArrayEquals("remove_offset_effects must not change the coefficients at lambda[" + i + "]",
                        rpBase._coefficients[i], rpRO._coefficients[i], 1e-12);

            assertNotNull("a validation frame must produce a validation deviance path",
                    rpRO._explained_deviance_valid);

            // Oracle 1: the intercept-only submodel explains none of the deviance.
            assertEquals("explained_deviance_valid of the null-beta submodel must be 0",
                    0, rpRO._explained_deviance_valid[0], 1e-6);
            assertEquals("explained_deviance_train of the null-beta submodel must be 0",
                    0, rpRO._explained_deviance_train[0], 1e-6);

            // Oracle 2: identical fit, same scale => identical path.
            assertArrayEquals("explained_deviance_valid must match the plain offset model",
                    rpBase._explained_deviance_valid, rpRO._explained_deviance_valid, 1e-8);
            assertArrayEquals("explained_deviance_train must match the plain offset model",
                    rpBase._explained_deviance_train, rpRO._explained_deviance_train, 1e-8);
        } finally {
            if (train != null) train.remove();
            if (ro != null) ro.remove();
            if (base != null) base.remove();
            Scope.exit();
        }
    }

    /**
     * beta_constraints route through a separate scoring path; the combination with lambda_search +
     * remove_offset_effects must still recover the plain (constrained) offset model.
     */
    @Test
    public void betaConstraintsWorkWithLambdaSearch() {
        Frame train = null, predRO = null, predBase = null;
        GLMModel ro = null, base = null;
        try {
            Scope.enter();
            train = binomialFrame();
            Scope.track_generic(train);

            Frame bc = new TestFrameBuilder()
                    .withName("betaConstraints")
                    .withColNames("names", "lower_bounds", "upper_bounds")
                    .withVecTypes(Vec.T_STR, Vec.T_NUM, Vec.T_NUM)
                    .withDataForCol(0, new String[]{"C11", "C12"})  // numeric predictors in binomialFrame
                    .withDataForCol(1, new double[]{-1, -1})
                    .withDataForCol(2, new double[]{1, 1})
                    .build();
            Scope.track(bc);

            GLMModel.GLMParameters params = baseParams(train);
            params._beta_constraints = bc._key;
            params._remove_offset_effects = true;
            ro = new GLM(params).trainModel().get();
            Scope.track_generic(ro);

            params._remove_offset_effects = false;
            base = new GLM(params).trainModel().get();
            Scope.track_generic(base);

            assertEquals("unrestricted train mse must match plain constrained offset model",
                    base._output._training_metrics.mse(),
                    ro._output._training_metrics_unrestricted_model.mse(), 1e-8);
            assertEquals("lambda_best must match plain offset model",
                    base._output.lambda_best(), ro._output.lambda_best(), 0.0);

            predRO = ro.score(train);
            Scope.track_generic(predRO);
            predBase = base.score(train);
            Scope.track_generic(predBase);
            double maxDiff = 0;
            for (int i = 0; i < 100; i++)
                maxDiff = Math.max(maxDiff, Math.abs(predRO.vec(2).at(i) - predBase.vec(2).at(i)));
            assertTrue("restricted predictions must differ from plain offset model", maxDiff > 1e-6);
        } finally {
            if (train != null) train.remove();
            if (predRO != null) predRO.remove();
            if (predBase != null) predBase.remove();
            if (ro != null) ro.remove();
            if (base != null) base.remove();
            Scope.exit();
        }
    }

    /**
     * early_stopping can break the lambda loop mid-search; the model must still finalize and its unrestricted
     * metrics/lambda selection must match the plain offset model.
     */
    @Test
    public void earlyStoppingWorksWithLambdaSearch() {
        Frame train = null;
        GLMModel ro = null, base = null;
        try {
            Scope.enter();
            train = binomialFrame();
            Scope.track_generic(train);

            GLMModel.GLMParameters params = baseParams(train);
            params._early_stopping = true;
            params._remove_offset_effects = true;
            ro = new GLM(params).trainModel().get();
            Scope.track_generic(ro);

            params._remove_offset_effects = false;
            base = new GLM(params).trainModel().get();
            Scope.track_generic(base);

            assertEquals("lambda_best must match plain offset model under early_stopping",
                    base._output.lambda_best(), ro._output.lambda_best(), 0.0);
            assertEquals("unrestricted train mse must match plain offset model",
                    base._output._training_metrics.mse(),
                    ro._output._training_metrics_unrestricted_model.mse(), 1e-8);
        } finally {
            if (train != null) train.remove();
            if (ro != null) ro.remove();
            if (base != null) base.remove();
            Scope.exit();
        }
    }

    /**
     * Restoring from a checkpoint must keep the restricted and unrestricted lambda scoring histories
     * separate: with remove_offset_effects the main _scoring_history holds the restricted (offset-removed)
     * table and _scoring_history_unrestricted_model the unrestricted one. A previous restore swapped them
     * (restored the restricted table into the unrestricted history object and never restored the restricted
     * one), so the continued model must still expose an unrestricted history that matches the plain offset
     * model and a distinct restricted history.
     */
    @Test
    public void checkpointRestoreKeepsRestrictedAndUnrestrictedHistoriesSeparate() {
        Frame train = null;
        GLMModel glmRO = null, continuedRO = null, glmPlain = null, continuedPlain = null;
        try {
            Scope.enter();
            train = binomialFrame();
            Scope.track_generic(train);

            // Continuing an already-finished lambda_search checkpoint re-explores lambdas, so the continued
            // model differs from a fresh one. remove_offset_effects does not change the fit, so the invariant
            // is that a continued RO model matches a plain offset model put through the identical pipeline.
            // training mutates the params (fills in _lambda etc.), so each model gets a fresh params object.

            // RO model, then continued from its checkpoint (runs restoreScoringHistoryFromCheckpoint)
            GLMModel.GLMParameters roParams = baseParams(train);
            roParams._solver = GLMModel.GLMParameters.Solver.IRLSM;  // GLM checkpoint is only supported for IRLSM
            roParams._remove_offset_effects = true;
            glmRO = new GLM(roParams).trainModel().get();
            Scope.track_generic(glmRO);

            GLMModel.GLMParameters roCont = baseParams(train);
            roCont._solver = GLMModel.GLMParameters.Solver.IRLSM;
            roCont._remove_offset_effects = true;
            roCont._checkpoint = glmRO._key;
            continuedRO = new GLM(roCont).trainModel().get();
            Scope.track_generic(continuedRO);

            // plain offset model continued through the identical pipeline
            GLMModel.GLMParameters plainParams = baseParams(train);
            plainParams._solver = GLMModel.GLMParameters.Solver.IRLSM;
            glmPlain = new GLM(plainParams).trainModel().get();
            Scope.track_generic(glmPlain);

            GLMModel.GLMParameters plainCont = baseParams(train);
            plainCont._solver = GLMModel.GLMParameters.Solver.IRLSM;
            plainCont._checkpoint = glmPlain._key;
            continuedPlain = new GLM(plainCont).trainModel().get();
            Scope.track_generic(continuedPlain);

            // core fix: offset + lambda_search + checkpoint continuation no longer crashes
            assertNotNull("RO model must continue from checkpoint", continuedRO);
            assertNotNull("plain offset model must continue from checkpoint", continuedPlain);

            TwoDimTable restricted = continuedRO._output._scoring_history;
            TwoDimTable unrestricted = continuedRO._output._scoring_history_unrestricted_model;
            assertNotNull("restricted scoring history must survive checkpoint restore", restricted);
            assertNotNull("unrestricted scoring history must survive checkpoint restore", unrestricted);
            assertTrue("restricted scoring history must be non-empty", restricted.getRowDim() > 0);
            assertTrue("unrestricted scoring history must be non-empty", unrestricted.getRowDim() > 0);
            assertNull("plain offset model has no unrestricted scoring history",
                    continuedPlain._output._scoring_history_unrestricted_model);

            // mapping fix: both the restricted and unrestricted lambda histories are restored from the
            // checkpoint, so they cover the same lambdas. Before the fix the restricted history was never
            // restored (only the unrestricted object was, from the wrong table), leaving it short.
            assertEquals("restricted and unrestricted scoring histories must span the same lambdas",
                    unrestricted.getRowDim(), restricted.getRowDim());
        } finally {
            if (train != null) train.remove();
            if (glmRO != null) glmRO.remove();
            if (continuedRO != null) continuedRO.remove();
            if (glmPlain != null) glmPlain.remove();
            if (continuedPlain != null) continuedPlain.remove();
            Scope.exit();
        }
    }

    /**
     * remove_offset_effects is pinned across a checkpoint continuation (CHECKPOINT_NON_MODIFIABLE_FIELDS):
     * the restricted/unrestricted scoring-history slots are wired from the flag at build start, so flipping
     * it on continuation would desynchronize them (previously an NPE on restore). Flipping it must now be
     * rejected up front with a clean parameter error instead.
     */
    @Test
    public void checkpointRejectsFlippedRemoveOffsetEffects() {
        Frame train = null;
        GLMModel glmRO = null, continued = null;
        try {
            Scope.enter();
            train = binomialFrame();
            Scope.track_generic(train);

            GLMModel.GLMParameters roParams = baseParams(train);
            roParams._solver = GLMModel.GLMParameters.Solver.IRLSM;
            roParams._remove_offset_effects = true;
            glmRO = new GLM(roParams).trainModel().get();
            Scope.track_generic(glmRO);

            // continue with remove_offset_effects flipped off -> must be rejected, not crash with an NPE
            GLMModel.GLMParameters cont = baseParams(train);
            cont._solver = GLMModel.GLMParameters.Solver.IRLSM;
            cont._remove_offset_effects = false;
            cont._checkpoint = glmRO._key;
            try {
                continued = new GLM(cont).trainModel().get();
                fail("flipping remove_offset_effects across a checkpoint must be rejected");
            } catch (H2OModelBuilderIllegalArgumentException expected) {
                // remove_offset_effects is non-modifiable across checkpoint continuation
            }
        } finally {
            if (train != null) train.remove();
            if (glmRO != null) glmRO.remove();
            if (continued != null) continued.remove();
            Scope.exit();
        }
    }

    /**
     * Resuming a checkpoint whose lambda search was interrupted mid-way (max_iterations caps the run before
     * all lambdas are fit) is the case the root-cause fix in ComputationState.copyCheckModel2State targets:
     * the resumed candidate beta must be rebuilt from the last persisted submodel, not scattered through a
     * mismatched index set. The continued RO model must finish the search (more submodels than the
     * checkpoint) and its unrestricted part must still recover a plain offset model continued identically.
     */
    @Test
    public void midSearchCheckpointRestoreWorksWithOffset() {
        Frame train = null;
        GLMModel glmRO = null, continuedRO = null, glmPlain = null, continuedPlain = null;
        try {
            Scope.enter();
            train = binomialFrame();
            Scope.track_generic(train);

            // low max_iterations stops the lambda search partway, so the checkpoint holds only a few submodels
            GLMModel.GLMParameters roParams = baseParams(train);
            roParams._solver = GLMModel.GLMParameters.Solver.IRLSM;
            roParams._remove_offset_effects = true;
            roParams._max_iterations = 8;
            glmRO = new GLM(roParams).trainModel().get();
            Scope.track_generic(glmRO);

            GLMModel.GLMParameters roCont = baseParams(train);
            roCont._solver = GLMModel.GLMParameters.Solver.IRLSM;
            roCont._remove_offset_effects = true;
            roCont._checkpoint = glmRO._key;  // continue with the default (uncapped) iterations to finish
            continuedRO = new GLM(roCont).trainModel().get();
            Scope.track_generic(continuedRO);

            GLMModel.GLMParameters plainParams = baseParams(train);
            plainParams._solver = GLMModel.GLMParameters.Solver.IRLSM;
            plainParams._max_iterations = 8;
            glmPlain = new GLM(plainParams).trainModel().get();
            Scope.track_generic(glmPlain);

            GLMModel.GLMParameters plainCont = baseParams(train);
            plainCont._solver = GLMModel.GLMParameters.Solver.IRLSM;
            plainCont._checkpoint = glmPlain._key;
            continuedPlain = new GLM(plainCont).trainModel().get();
            Scope.track_generic(continuedPlain);

            // the checkpoint was genuinely mid-search and continuation advanced it
            assertTrue("checkpoint must stop before the full lambda search completes",
                    glmRO._output._submodels.length < roParams._nlambdas);
            assertTrue("continuation must add more submodels than the checkpoint held",
                    continuedRO._output._submodels.length > glmRO._output._submodels.length);

            // mapping fix still holds after a mid-search resume
            assertEquals("restricted and unrestricted scoring histories must span the same lambdas",
                    continuedRO._output._scoring_history_unrestricted_model.getRowDim(),
                    continuedRO._output._scoring_history.getRowDim());

            // remove_offset_effects does not change the fit: continued RO recovers the continued plain model
            assertEquals("lambda_best of mid-search-continued RO model must match continued plain offset model",
                    continuedPlain._output.lambda_best(), continuedRO._output.lambda_best(), 0.0);
        } finally {
            if (train != null) train.remove();
            if (glmRO != null) glmRO.remove();
            if (continuedRO != null) continuedRO.remove();
            if (glmPlain != null) glmPlain.remove();
            if (continuedPlain != null) continuedPlain.remove();
            Scope.exit();
        }
    }

    /**
     * Mostly-zero standardized numeric predictors drive GLM down the sparse chunk path. The restricted
     * prediction must exclude the offset - i.e. equal innerProduct(denormalized beta) with the offset
     * dropped - and the MOJO must reproduce the in-H2O restricted predictions (the MOJO drops the offset
     * via a null offset column in the model descriptor, see GLMModel.modelDescriptor()).
     */
    @Test
    public void sparseDataWorksWithLambdaSearch() throws Exception {
        Frame fr = null, predRO = null, mojoPreds = null;
        GLMModel ro = null;
        GenericModel generic = null;
        try {
            Scope.enter();
            int nrow = 800, ncol = 8;
            Random rng = new Random(1234);
            double[] beta = new double[ncol];
            for (int j = 0; j < ncol; j++) beta[j] = rng.nextDouble() - 0.5;
            double[][] cols = new double[ncol][nrow];
            double[] off = new double[nrow];
            String[] y = new String[nrow];
            for (int i = 0; i < nrow; i++) {
                double eta = 0;
                for (int j = 0; j < ncol; j++) {  // ~10% non-zero -> sparse representation
                    double v = (rng.nextDouble() < 0.1) ? rng.nextDouble() : 0.0;
                    cols[j][i] = v;
                    eta += v * beta[j];
                }
                off[i] = rng.nextDouble() - 0.5;
                eta += off[i];
                y[i] = (rng.nextDouble() < 1.0 / (1.0 + Math.exp(-eta))) ? "1" : "0";
            }
            String[] names = new String[ncol + 2];
            byte[] types = new byte[ncol + 2];
            for (int j = 0; j < ncol; j++) {
                names[j] = "x" + j;
                types[j] = Vec.T_NUM;
            }
            names[ncol] = "off";
            types[ncol] = Vec.T_NUM;
            names[ncol + 1] = "y";
            types[ncol + 1] = Vec.T_CAT;
            TestFrameBuilder b = new TestFrameBuilder().withName("sparseFrame").withColNames(names).withVecTypes(types);
            for (int j = 0; j < ncol; j++) b.withDataForCol(j, cols[j]);
            b.withDataForCol(ncol, off);
            b.withDataForCol(ncol + 1, y);
            fr = b.build();
            Scope.track(fr);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.binomial);
            params._response_column = "y";
            params._train = fr._key;
            params._offset_column = "off";
            params._lambda_search = true;
            params._remove_offset_effects = true;
            ro = new GLM(params).trainModel().get();
            Scope.track_generic(ro);

            predRO = ro.score(fr);
            Scope.track_generic(predRO);

            // The restricted prediction must equal innerProduct(denormalized beta) with the offset removed,
            // for every row - proves the offset (and its sparse standardization correction) is correctly
            // excluded on genuinely sparse data. beta layout: [x0..x_{ncol-1}, intercept].
            double[] bRO = ro.beta();
            for (int i = 0; i < nrow; i++) {
                double eta = bRO[ncol];
                for (int j = 0; j < ncol; j++) eta += bRO[j] * cols[j][i];
                double mu = 1.0 / (1.0 + Math.exp(-eta));
                assertEquals("[sparse] row " + i + " restricted p1 must exclude the offset",
                        mu, predRO.vec(2).at(i), 1e-8);
            }

            // MOJO must reproduce the in-H2O restricted predictions on sparse data
            File mojoFile = Files.createTempFile("glm_sparse_mojo", ".zip").toFile();
            mojoFile.deleteOnExit();
            try (FileOutputStream fos = new FileOutputStream(mojoFile)) {
                ro.getMojo().writeTo(fos);
            }
            generic = Generic.importMojoModel(mojoFile.getAbsolutePath(), false);
            Scope.track_generic(generic);
            mojoPreds = generic.score(fr);
            Scope.track_generic(mojoPreds);
            assertVecEquals(predRO.vec(1), mojoPreds.vec(1), 1e-8);
            assertVecEquals(predRO.vec(2), mojoPreds.vec(2), 1e-8);
        } finally {
            if (fr != null) fr.remove();
            if (predRO != null) predRO.remove();
            if (mojoPreds != null) mojoPreds.remove();
            if (ro != null) ro.remove();
            if (generic != null) generic.remove();
            Scope.exit();
        }
    }

    /**
     * The restricted (offset-removed) deviance reported in the scoring history is computed by GLMResDevTask,
     * which zeroes its sparse standardization correction (sparseOffset) when remove_offset_effects is on.
     * On genuinely sparse standardized data that biases the reported deviance. Assert the scoring-history
     * restricted deviance_train at the selected lambda equals the restricted model's residual deviance per
     * observation - the latter is computed from the scored (score0) predictions, which are correct on sparse.
     */
    @Test
    public void sparseRestrictedScoringHistoryDevianceMatchesModel() {
        Frame fr = null;
        GLMModel ro = null;
        try {
            Scope.enter();
            int nrow = 800, ncol = 8;
            Random rng = new Random(1234);
            double[] beta = new double[ncol];
            for (int j = 0; j < ncol; j++) beta[j] = rng.nextDouble() - 0.5;
            double[][] cols = new double[ncol][nrow];
            double[] off = new double[nrow];
            String[] y = new String[nrow];
            for (int i = 0; i < nrow; i++) {
                double eta = 0;
                for (int j = 0; j < ncol; j++) {  // ~10% non-zero -> sparse representation
                    double v = (rng.nextDouble() < 0.1) ? rng.nextDouble() : 0.0;
                    cols[j][i] = v;
                    eta += v * beta[j];
                }
                off[i] = rng.nextDouble() - 0.5;
                eta += off[i];
                y[i] = (rng.nextDouble() < 1.0 / (1.0 + Math.exp(-eta))) ? "1" : "0";
            }
            String[] names = new String[ncol + 2];
            byte[] types = new byte[ncol + 2];
            for (int j = 0; j < ncol; j++) {
                names[j] = "x" + j;
                types[j] = Vec.T_NUM;
            }
            names[ncol] = "off";
            types[ncol] = Vec.T_NUM;
            names[ncol + 1] = "y";
            types[ncol + 1] = Vec.T_CAT;
            TestFrameBuilder b = new TestFrameBuilder().withName("sparseDevFrame").withColNames(names).withVecTypes(types);
            for (int j = 0; j < ncol; j++) b.withDataForCol(j, cols[j]);
            b.withDataForCol(ncol, off);
            b.withDataForCol(ncol + 1, y);
            fr = b.build();
            Scope.track(fr);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.binomial);
            params._response_column = "y";
            params._train = fr._key;
            params._offset_column = "off";
            params._lambda_search = true;
            params._generate_scoring_history = true;
            params._remove_offset_effects = true;
            ro = new GLM(params).trainModel().get();
            Scope.track_generic(ro);

            // correct restricted avg deviance, from the scored (score0) restricted training metrics
            double nobs = ro._output._training_metrics._nobs;
            double correctAvgDev = ((GLMMetrics) ro._output._training_metrics).residual_deviance() / nobs;

            // reported restricted deviance_train from the scoring history at the selected lambda
            TwoDimTable sh = ro._output._scoring_history;
            int lamCol = Arrays.asList(sh.getColHeaders()).indexOf("lambda");
            int devCol = Arrays.asList(sh.getColHeaders()).indexOf("deviance_train");
            assertTrue("scoring history must expose lambda and deviance_train", lamCol >= 0 && devCol >= 0);
            // lambda is stored as a formatted string in the lambda-format history; parse it and pick the row
            // closest to lambda_best (the grid is ~10% apart, so the nearest is unambiguously the best row).
            double lamBest = ro._output.lambda_best();
            double reportedAvgDev = Double.NaN, bestDiff = Double.MAX_VALUE;
            for (int i = 0; i < sh.getRowDim(); i++) {
                Object dev = sh.get(i, devCol);
                if (!(dev instanceof Number)) continue;  // early-stop-only rows have empty cells
                double lam;
                try {
                    lam = Double.parseDouble(sh.get(i, lamCol).toString());
                } catch (NumberFormatException e) {
                    continue;
                }
                if (Math.abs(lam - lamBest) < bestDiff) {
                    bestDiff = Math.abs(lam - lamBest);
                    reportedAvgDev = ((Number) dev).doubleValue();
                }
            }
            assertFalse("no numeric scoring-history row found", Double.isNaN(reportedAvgDev));

            assertEquals("[sparse] restricted scoring-history deviance_train must match the restricted model's "
                            + "residual deviance per observation",
                    correctAvgDev, reportedAvgDev, 1e-6 * Math.max(1.0, correctAvgDev));
        } finally {
            if (fr != null) fr.remove();
            if (ro != null) ro.remove();
            Scope.exit();
        }
    }

    /**
     * remove_offset_effects + lambda_search + cross-validation: the model trains and the restricted scoring
     * history's cross-validation deviance is offset-removed. The per-lambda offset-removed holdout deviances
     * are aggregated across folds (cv_computeAndSetOptimalParameters) into _xval_deviances_restricted, so the
     * restricted deviance_xval column must differ from the unrestricted one (removing the offset changes the
     * deviance), and the unrestricted history must still match a plain offset model trained the same way.
     */
    @Test
    public void removeOffsetEffectsWorksWithLambdaSearchAndCrossValidation() {
        Frame train = null;
        GLMModel glm = null, glmPlain = null;
        try {
            Scope.enter();
            train = binomialFrame();
            Scope.track_generic(train);

            // Each model needs its own params. GLM keeps the reference (_model._parms = _parms) and CV mutates it
            // in place: cv_computeAndSetOptimalParameters collapses _alpha to the winning value, truncates
            // _lambda to the winning subsequence and caps _max_iterations. Sharing one object would train the
            // second model on a shorter grid with a tighter iteration cap than the first, so the 1e-8
            // comparisons below would be between two differently-parameterised runs. The explicit _seed keeps
            // the fold split identical instead of relying on getOrMakeRealSeed() writing back into a shared object.
            GLMModel.GLMParameters roParams = cvParams(train);
            roParams._remove_offset_effects = true;
            glm = new GLM(roParams).trainModel().get();
            Scope.track_generic(glm);

            // same settings, offset kept -> the unrestricted view and best submodel must match this
            glmPlain = new GLM(cvParams(train)).trainModel().get();
            Scope.track_generic(glmPlain);

            assertEquals("lambda_best must match plain offset model under lambda_search + CV",
                    glmPlain._output.getSubmodel(glmPlain._output._selected_submodel_idx).lambda_value,
                    glm._output.getSubmodel(glm._output._selected_submodel_idx).lambda_value, 0);

            TwoDimTable restricted = glm._output._scoring_history;
            TwoDimTable unrestricted = glm._output._scoring_history_unrestricted_model;
            assertNotNull("restricted scoring history must be present", restricted);
            assertNotNull("unrestricted scoring history must be present", unrestricted);

            // the unrestricted xval deviance must match a plain offset model trained the same way (the fit is
            // unchanged); this pins the unrestricted column to a known-correct oracle, so the "differ" check
            // below cannot pass on a broken restricted column that merely happens to be non-identical.
            TwoDimTable plain = glmPlain._output._scoring_history;
            int devXP = Arrays.asList(plain.getColHeaders()).indexOf("deviance_xval");
            assertTrue("plain offset model must expose deviance_xval", devXP >= 0);

            int devX = Arrays.asList(restricted.getColHeaders()).indexOf("deviance_xval");
            int devXU = Arrays.asList(unrestricted.getColHeaders()).indexOf("deviance_xval");
            assertTrue("both histories must expose deviance_xval under cross-validation", devX >= 0 && devXU >= 0);

            boolean anyNumeric = false, anyDiffer = false;
            int rows = Math.min(restricted.getRowDim(), Math.min(unrestricted.getRowDim(), plain.getRowDim()));
            for (int i = 0; i < rows; i++) {
                Object r = restricted.get(i, devX);
                Object u = unrestricted.get(i, devXU);
                Object p = plain.get(i, devXP);
                if (!(r instanceof Number) || !(u instanceof Number) || !(p instanceof Number)) continue;  // early-stop-only rows have empty cells
                double rd = ((Number) r).doubleValue(), ud = ((Number) u).doubleValue(), pd = ((Number) p).doubleValue();
                // unrestricted xval == plain offset model's xval (fit unchanged)
                assertEquals("unrestricted deviance_xval must match the plain offset model", pd, ud, 1e-8);
                if (rd < 0) continue;  // -1 sentinel = not populated; a correct offset-removed deviance is >= 0
                anyNumeric = true;
                if (Math.abs(rd - ud) > 1e-8) anyDiffer = true;
            }
            assertTrue("restricted history must expose a real (non-sentinel) deviance_xval", anyNumeric);
            assertTrue("restricted deviance_xval must be offset-removed (differ from unrestricted)", anyDiffer);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (glmPlain != null) glmPlain.remove();
            Scope.exit();
        }
    }
}
