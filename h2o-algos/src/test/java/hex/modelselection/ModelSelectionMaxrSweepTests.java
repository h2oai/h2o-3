package hex.modelselection;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.Arrays;

import static hex.glm.GLMModel.GLMParameters.Family.gaussian;
import static hex.modelselection.ModelSelectionModel.ModelSelectionParameters.Mode.*;


@RunWith(H2ORunner.class)
@CloudSize(1)
public class ModelSelectionMaxrSweepTests extends TestUtil {
    @Test
    public void testMaxRSweepNumericalOnly() {
        Scope.enter();
        try {
            Frame origF = Scope.track(parseTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv"));
            ModelSelectionModel.ModelSelectionParameters parms = new ModelSelectionModel.ModelSelectionParameters();
            parms._response_column = "C21";
            parms._ignored_columns = new String[]{"C1","C2","C3","C4","C5","C6","C7","C8","C9","C10"};
            parms._family = gaussian;
            parms._max_predictor_number = 5;
            parms._train = origF._key;
            parms._mode = maxrsweep;
            ModelSelectionModel modelMaxRSweep = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Frame resultFrameSweep = modelMaxRSweep.result();
            Scope.track(resultFrameSweep);
            Scope.track_generic(modelMaxRSweep);

            parms._mode = maxr;
            ModelSelectionModel modelMaxR = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(modelMaxR);
            Frame resultMaxR = modelMaxR.result();
            Scope.track(resultMaxR);
            TestUtil.assertIdenticalUpToRelTolerance(new Frame(resultFrameSweep.vec(2)), new Frame(resultMaxR.vec(2)), 1e-6);
            TestUtil.assertIdenticalUpToRelTolerance(new Frame(resultFrameSweep.vec(3)), new Frame(resultMaxR.vec(3)), 0);
        } finally {
            Scope.exit();
        }
    }
}
