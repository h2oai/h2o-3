package hex.glm;

import hex.StringPair;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

// GH-16858: remove_offset_effects has to work together with interactions.
// A normal offset model with interactions (fixed seed) must predict exactly like the remove_offset_effects
// model when the offset column is manually zeroed out (offset effect removed), and differently from the
// offset model that keeps the offset. Covered for both `interactions` and `interaction_pairs`.
@RunWith(H2ORunner.class)
@CloudSize(1)
public class GLMRemoveOffsetEffectsInteractionsTest extends TestUtil {

    private static final String RESPONSE = "C21";
    private static final String OFFSET = "C20";

    @Test
    public void removeOffsetEffectsMatchesManualZeroOffset_interactions() {
        try {
            Scope.enter();
            Frame train = prepareTrain();
            GLMModel.GLMParameters params = baseParams(train);
            params._interactions = new String[]{"C11", "C12"};
            assertRemoveOffsetMatchesManualZeroOffset(train, params);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void removeOffsetEffectsMatchesManualZeroOffset_interactionPairs() {
        try {
            Scope.enter();
            Frame train = prepareTrain();
            GLMModel.GLMParameters params = baseParams(train);
            params._interaction_pairs = new StringPair[]{new StringPair("C11", "C12")};
            assertRemoveOffsetMatchesManualZeroOffset(train, params);
        } finally {
            Scope.exit();
        }
    }

    private Frame prepareTrain() {
        Frame train = parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv");
        int numCols = train.numCols();
        int enumCols = (numCols - 1) / 2;
        for (int cindex = 0; cindex < enumCols; cindex++) {
            train.replace(cindex, train.vec(cindex).toCategoricalVec()).remove();
        }
        train.replace(numCols - 1, train.vec(numCols - 1).toCategoricalVec()).remove();
        DKV.put(train);
        Scope.track(train);
        return train;
    }

    private GLMModel.GLMParameters baseParams(Frame train) {
        GLMModel.GLMParameters params = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.binomial);
        params._response_column = RESPONSE;
        params._train = train._key;
        params._offset_column = OFFSET;
        params._seed = 1234;
        return params;
    }

    private void assertRemoveOffsetMatchesManualZeroOffset(Frame train, GLMModel.GLMParameters params) {
        // normal offset model with interactions
        params._remove_offset_effects = false;
        GLMModel glm = new GLM(params).trainModel().get();
        Scope.track_generic(glm);
        Frame preds = Scope.track(glm.score(train));

        // same model with remove_offset_effects enabled
        params._remove_offset_effects = true;
        GLMModel glmRoe = new GLM(params).trainModel().get();
        Scope.track_generic(glmRoe);
        Frame predsRoe = Scope.track(glmRoe.score(train));

        // manually remove the offset effect by scoring with a zeroed offset column
        Frame zeroOffset = train.deepCopy(Key.make().toString());
        zeroOffset.replace(zeroOffset.find(OFFSET), zeroOffset.vec(OFFSET).makeZero()).remove();
        DKV.put(zeroOffset);
        Scope.track(zeroOffset);
        Frame predsManual = Scope.track(glm.score(zeroOffset));

        double delta = 1e-6;
        // remove_offset_effects predictions match the manual zeroed-offset predictions and differ from the offset model
        for (long i = 0; i < preds.numRows(); i++) {
            assertEquals(predsManual.vec(1).at(i), predsRoe.vec(1).at(i), delta);
            assertEquals(predsManual.vec(2).at(i), predsRoe.vec(2).at(i), delta);
        }
        int differ = 0;
        int testRowNumber = 100;
        for (int i = 0; i < testRowNumber; i++) {
            if (preds.vec(1).at(i) != predsRoe.vec(1).at(i)) differ++;
        }
        assertTrue("Offset model predictions should differ from remove_offset_effects predictions",
                differ > testRowNumber / 1.1);

        // the remove_offset_effects "unrestricted" (with-offset) metrics match the plain offset model
        assertEquals(glm._output._training_metrics.mse(),
                glmRoe._output._training_metrics_unrestricted_model.mse(), delta);
        assertNotEquals(glmRoe._output._training_metrics.mse(),
                glmRoe._output._training_metrics_unrestricted_model.mse(), delta);
    }
}
