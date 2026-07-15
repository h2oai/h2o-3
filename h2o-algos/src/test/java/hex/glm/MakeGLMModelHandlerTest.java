package hex.glm;

import hex.DataInfo;
import hex.api.MakeGLMModelHandler;
import hex.genmodel.utils.DistributionFamily;
import hex.schemas.GLMModelV3;
import hex.schemas.MakeGLMModelV3;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.api.schemas3.KeyV3;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.*;

/**
 * Coverage for {@link MakeGLMModelHandler#make_model(int, MakeGLMModelV3)}, the makeGLMModel REST
 * endpoint that lets a caller substitute arbitrary coefficient values into a copy of a trained GLM.
 * Covers: source DataInfo isolation, prediction correctness after coefficient substitution,
 * standardized vs. non-standardized coefficient handling, coefficient-length validation, categorical
 * predictor coefficient overrides, destination-key auto-generation, and multinomial models.
 */
@RunWith(H2ORunner.class)
@CloudSize(1)
public class MakeGLMModelHandlerTest extends TestUtil {

    /** Creates a small binomial frame with categorical predictors, offset, and response. */
    private static Frame makeBinomialOffsetFrame(String key) {
        Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0}, new String[]{"0","1"}, Vec.newKey());
        Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0}, new String[]{"0","1"}, Vec.newKey());
        Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
        Vec res = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1}, new String[]{"0","1"}, Vec.newKey());
        Frame f = new Frame(Key.<Frame>make(key), new String[]{"x1", "x2", "offset", "y"}, new Vec[]{cat1, cat2, offset, res});
        DKV.put(f);
        return f;
    }

    /** Creates a small binomial frame with numeric predictors, offset, and response.
     *  Use params._offset_column="offset" to treat the offset as an offset column,
     *  or params._ignored_columns={"offset"} to exclude it from predictors entirely. */
    private static Frame makeNumericBinomialOffsetFrame(String key) {
        Vec x1 = Vec.makeVec(new double[]{100,200,300,400,500,600,700,800,900,1000,
                150,250,350,450,550,650,750,850,950,1050,120,220,320,420,520,620}, Vec.newKey());
        Vec x2 = Vec.makeVec(new double[]{0.01,0.02,0.03,0.04,0.05,0.06,0.07,0.08,0.09,0.10,
                0.015,0.025,0.035,0.045,0.055,0.065,0.075,0.085,0.095,0.105,
                0.012,0.022,0.032,0.042,0.052,0.062}, Vec.newKey());
        Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
        Vec resp = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1}, new String[]{"0","1"}, Vec.newKey());
        Frame f = new Frame(Key.<Frame>make(key), new String[]{"x1","x2","offset","y"}, new Vec[]{x1,x2,offset,resp});
        DKV.put(f);
        return f;
    }

    /** Creates a small 3-class multinomial frame with a numeric predictor and no offset. */
    private static Frame makeNumericMultinomialFrame(String key) {
        Vec x1 = Vec.makeVec(new double[]{100,200,300,400,500,600,700,800,900,1000,
                150,250,350,450,550,650,750,850,950,1050,120,220,320,420,520,620}, Vec.newKey());
        Vec resp = Vec.makeVec(new long[]{0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,2,2,2,2,2,2,2,2},
                new String[]{"a","b","c"}, Vec.newKey());
        Frame f = new Frame(Key.<Frame>make(key), new String[]{"x1","y"}, new Vec[]{x1,resp});
        DKV.put(f);
        return f;
    }

    /**
     * Regression guard for removing model.dinfo().setPredictorTransform(NONE) from make_model.
     * The old code mutated the source model's DataInfo by zeroing _normMul/_normSub; the fix
     * must leave those arrays intact.
     */
    @Test
    public void testMakeModelDoesNotMutateSourceDataInfo() {
        Frame train = null;
        GLMModel glm = null;
        GLMModel derived = null;
        try {
            Scope.enter();
            train = makeNumericBinomialOffsetFrame("mm_no_mutate_train");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._ignored_columns = new String[]{"offset"};
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._standardize = true;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            DataInfo dinfoBefore = glm.dinfo();
            assertNotNull("DataInfo must exist on trained model", dinfoBefore);
            assertNotNull("_normMul must be non-null with standardize=true on numeric data", dinfoBefore._normMul);
            double[] normMulBefore = dinfoBefore._normMul.clone();

            // Call make_model with unchanged coefficients
            String[] coefNames = glm._output.coefficientNames();
            Key<GLMModel> destKey = Key.make("mm_no_mutate_derived");
            MakeGLMModelHandler handler = new MakeGLMModelHandler();
            MakeGLMModelV3 args = new MakeGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            args.dest = new KeyV3.ModelKeyV3(destKey);
            args.names = coefNames;
            args.beta = glm.beta().clone();
            handler.make_model(3, args);
            derived = DKV.getGet(destKey);
            assertNotNull("make_model must put a model into DKV", derived);
            Scope.track_generic(derived);

            // Source model DataInfo must be unmodified — the old code called
            // model.dinfo().setPredictorTransform(NONE) which nulled out _normMul
            DataInfo dinfoAfter = glm.dinfo();
            assertNotNull("make_model must not null out source DataInfo _normMul", dinfoAfter._normMul);
            assertArrayEquals("make_model must not mutate source DataInfo _normMul",
                    normMulBefore, dinfoAfter._normMul, 0);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (derived != null) derived.remove();
            Scope.exit();
        }
    }

    /**
     * Verifies that make_model produces correct predictions without calling setPredictorTransform.
     * A derived model with zeroed predictor coefficients (intercept only) must predict a constant
     * probability for all rows; the source model with fitted predictors must predict varying values.
     */
    @Test
    public void testMakeModelProducesCorrectPredictions() {
        Frame train = null;
        GLMModel glm = null;
        GLMModel derived = null;
        Frame predSource = null;
        Frame predDerived = null;
        try {
            Scope.enter();
            train = makeNumericBinomialOffsetFrame("mm_preds_train");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._ignored_columns = new String[]{"offset"};
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._standardize = true;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            // Zero out all predictor coefficients, keep only the intercept (always last).
            // The derived model must then predict sigmoid(intercept) for every row.
            String[] coefNames = glm._output.coefficientNames();
            double[] beta = glm.beta();
            double[] interceptOnlyBeta = new double[coefNames.length];
            interceptOnlyBeta[coefNames.length - 1] = beta[coefNames.length - 1];

            Key<GLMModel> destKey = Key.make("mm_preds_derived");
            MakeGLMModelHandler handler = new MakeGLMModelHandler();
            MakeGLMModelV3 args = new MakeGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            args.dest = new KeyV3.ModelKeyV3(destKey);
            args.names = coefNames;
            args.beta = interceptOnlyBeta;
            handler.make_model(3, args);
            derived = DKV.getGet(destKey);
            assertNotNull("make_model must produce a model", derived);
            Scope.track_generic(derived);

            predSource = glm.score(train);
            Scope.track(predSource);
            predDerived = derived.score(train);
            Scope.track(predDerived);

            // Binomial prediction frame: [predict, p(y=0), p(y=1)]; use vec(2) for p(y=1)
            Vec srcProb = predSource.vec(2);
            Vec derivedProb = predDerived.vec(2);

            double constantProb = derivedProb.at(0);
            assertTrue("Intercept-only derived model must predict a positive probability", constantProb > 0);
            for (long i = 1; i < predDerived.numRows(); i++) {
                assertEquals("Derived model with zeroed predictors must predict constant probability",
                        constantProb, derivedProb.at(i), 1e-10);
            }

            boolean sourceVaries = false;
            double firstSrcProb = srcProb.at(0);
            for (long i = 1; i < predSource.numRows(); i++) {
                if (Math.abs(srcProb.at(i) - firstSrcProb) > 1e-10) { sourceVaries = true; break; }
            }
            assertTrue("Source model with fitted numeric predictors must predict varying probabilities", sourceVaries);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (derived != null) derived.remove();
            if (predSource != null) predSource.remove();
            if (predDerived != null) predDerived.remove();
            Scope.exit();
        }
    }

    /**
     * Regression guard for GH-16890: make_model() built GLMOutput with the source model's dinfo
     * reused as-is, including its STANDARDIZE transform when the source was trained with
     * standardize=true. The beta passed to make_model is always raw/denormalized, so this made
     * isStandardized() report true for a model whose beta isn't standardized, causing beta(lambda)
     * to spuriously re-denormalize an already-raw beta via DataInfo.denormalizeBeta().
     */
    @Test
    public void testMakeModelDoesNotCorruptRawBetaViaDenormalize() {
        Frame train = null;
        GLMModel glm = null;
        GLMModel derived = null;
        try {
            Scope.enter();
            train = makeNumericBinomialOffsetFrame("mm_denorm_train");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._ignored_columns = new String[]{"offset"};
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._standardize = true;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);
            assertNotNull("Sanity check: source dinfo must carry real, non-trivial normalization stats",
                    glm.dinfo()._normMul);

            String[] coefNames = glm._output.coefficientNames();
            Key<GLMModel> destKey = Key.make("mm_denorm_derived");
            MakeGLMModelHandler handler = new MakeGLMModelHandler();
            MakeGLMModelV3 args = new MakeGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            args.dest = new KeyV3.ModelKeyV3(destKey);
            args.names = coefNames;
            args.beta = glm.beta().clone();
            handler.make_model(3, args);
            derived = DKV.getGet(destKey);
            assertNotNull("make_model must put a model into DKV", derived);
            Scope.track_generic(derived);

            assertFalse("Derived model's DataInfo must not report STANDARDIZE over a raw beta",
                    derived._output.isStandardized());
            assertArrayEquals("beta(lambda) must equal the raw beta() accessor, not a spuriously "
                    + "denormalized value computed from the inherited STANDARDIZE transform",
                    derived.beta(), derived.beta(0.0), 1e-8);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (derived != null) derived.remove();
            Scope.exit();
        }
    }

    /**
     * make_model requires args.beta to cover every model coefficient (Intercept included); a
     * shorter array must be rejected rather than silently read out of bounds or truncated.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testMakeModelThrowsOnCoefficientLengthMismatch() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();
            train = makeNumericBinomialOffsetFrame("mm_len_mismatch_train");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._ignored_columns = new String[]{"offset"};
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            String[] coefNames = glm._output.coefficientNames();
            MakeGLMModelV3 args = new MakeGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            args.dest = new KeyV3.ModelKeyV3(Key.<GLMModel>make("mm_len_mismatch_derived"));
            args.names = coefNames;
            args.beta = new double[coefNames.length - 1]; // deliberately too short
            new MakeGLMModelHandler().make_model(3, args);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    /**
     * Correctness check for categorical predictors: overriding a single expanded categorical
     * coefficient (keeping the intercept and every other coefficient at zero) must shift the
     * predicted log-odds by exactly that coefficient's value for rows carrying that factor level,
     * and leave rows at the reference level at the neutral (Intercept=0 => p=0.5) prediction.
     */
    @Test
    public void testMakeModelWithCategoricalPredictorsAppliesNewCoefficients() {
        Frame train = null;
        GLMModel glm = null;
        GLMModel derived = null;
        Frame predDerived = null;
        try {
            Scope.enter();
            train = makeBinomialOffsetFrame("mm_cat_train");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._ignored_columns = new String[]{"offset"};
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            String[] coefNames = glm._output.coefficientNames();
            int x1Idx = -1;
            for (int i = 0; i < coefNames.length; i++) {
                if (coefNames[i].startsWith("x1")) { x1Idx = i; break; }
            }
            assertTrue("Expanded coefficient for categorical x1 must exist", x1Idx >= 0);

            double[] newBeta = new double[coefNames.length]; // all zero, including Intercept
            newBeta[x1Idx] = 5.0;

            Key<GLMModel> destKey = Key.make("mm_cat_derived");
            MakeGLMModelHandler handler = new MakeGLMModelHandler();
            MakeGLMModelV3 args = new MakeGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            args.dest = new KeyV3.ModelKeyV3(destKey);
            args.names = coefNames;
            args.beta = newBeta;
            handler.make_model(3, args);
            derived = DKV.getGet(destKey);
            assertNotNull(derived);
            Scope.track_generic(derived);

            predDerived = derived.score(train);
            Scope.track(predDerived);
            Vec derivedProb = predDerived.vec(2); // p(y=1)
            Vec x1Vec = train.vec("x1");

            double expectedAtLevel1 = 1.0 / (1.0 + Math.exp(-5.0));
            for (long i = 0; i < train.numRows(); i++) {
                double expected = x1Vec.at8(i) == 1 ? expectedAtLevel1 : 0.5;
                assertEquals("Row " + i + " prediction must reflect only the overridden x1 coefficient",
                        expected, derivedProb.at(i), 1e-6);
            }
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (derived != null) derived.remove();
            if (predDerived != null) predDerived.remove();
            Scope.exit();
        }
    }

    /**
     * When the source model was trained with standardize=false, its DataInfo never carried a
     * STANDARDIZE transform in the first place. make_model's dinfo clone + setPredictorTransform(NONE)
     * must be a no-op in that case: isStandardized() stays false and the derived beta is untouched.
     */
    @Test
    public void testMakeModelWithoutStandardizeKeepsRawBeta() {
        Frame train = null;
        GLMModel glm = null;
        GLMModel derived = null;
        try {
            Scope.enter();
            train = makeNumericBinomialOffsetFrame("mm_nostd_train");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._ignored_columns = new String[]{"offset"};
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._standardize = false;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);
            assertFalse("Sanity check: source model must not be standardized", glm._output.isStandardized());

            String[] coefNames = glm._output.coefficientNames();
            Key<GLMModel> destKey = Key.make("mm_nostd_derived");
            MakeGLMModelHandler handler = new MakeGLMModelHandler();
            MakeGLMModelV3 args = new MakeGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            args.dest = new KeyV3.ModelKeyV3(destKey);
            args.names = coefNames;
            args.beta = glm.beta().clone();
            handler.make_model(3, args);
            derived = DKV.getGet(destKey);
            assertNotNull(derived);
            Scope.track_generic(derived);

            assertFalse("Derived model must remain unstandardized", derived._output.isStandardized());
            assertArrayEquals("beta(lambda) must equal beta() when there was never a STANDARDIZE transform to strip",
                    derived.beta(), derived.beta(0.0), 1e-8);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (derived != null) derived.remove();
            Scope.exit();
        }
    }

    /** When args.dest is omitted, make_model must auto-generate a destination key and still put the model. */
    @Test
    public void testMakeModelGeneratesKeyWhenDestNotSpecified() {
        Frame train = null;
        GLMModel glm = null;
        GLMModel derived = null;
        try {
            Scope.enter();
            train = makeNumericBinomialOffsetFrame("mm_autokey_train");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._ignored_columns = new String[]{"offset"};
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            String[] coefNames = glm._output.coefficientNames();
            MakeGLMModelV3 args = new MakeGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            args.dest = null;
            args.names = coefNames;
            args.beta = glm.beta().clone();
            GLMModelV3 res = new MakeGLMModelHandler().make_model(3, args);

            assertNotNull("make_model must auto-generate a destination key when dest is omitted", res.model_id);
            derived = DKV.getGet(res.model_id.key());
            assertNotNull("Auto-keyed derived model must be present in DKV", derived);
            Scope.track_generic(derived);
            assertArrayEquals(glm.beta(), derived.beta(), 1e-10);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (derived != null) derived.remove();
            Scope.exit();
        }
    }

    /**
     * make_model's multinomial path uses multiClassCoeffNames()/multiClassCoeffNames-ordered beta
     * rather than the binomial coefficientNames() path; verify it produces a working, unstandardized
     * derived model from a standardized multinomial source.
     */
    @Test
    public void testMakeModelSupportsMultinomialCoefficients() {
        Frame train = null;
        GLMModel glm = null;
        GLMModel derived = null;
        try {
            Scope.enter();
            train = makeNumericMultinomialFrame("mm_multi_train");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._family = GLMModel.GLMParameters.Family.multinomial;
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._standardize = true;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);
            assertTrue("Sanity check: source model must be multinomial", glm._output._multinomial);

            String[] coefNames = glm._output.multiClassCoeffNames();
            Key<GLMModel> destKey = Key.make("mm_multi_derived");
            MakeGLMModelHandler handler = new MakeGLMModelHandler();
            MakeGLMModelV3 args = new MakeGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            args.dest = new KeyV3.ModelKeyV3(destKey);
            args.names = coefNames;
            args.beta = glm.beta().clone();
            handler.make_model(3, args);
            derived = DKV.getGet(destKey);
            assertNotNull(derived);
            Scope.track_generic(derived);

            assertFalse("Multinomial derived model must not report STANDARDIZE over a raw beta",
                    derived._output.isStandardized());
            assertArrayEquals("Unchanged multinomial beta must round-trip through make_model",
                    glm.beta(), derived.beta(), 1e-10);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (derived != null) derived.remove();
            Scope.exit();
        }
    }
}
