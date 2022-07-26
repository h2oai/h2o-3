package hex.modelselection;

import hex.DataInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.*;

import static hex.glm.GLMModel.GLMParameters.Family.gaussian;
import static hex.modelselection.ModelSelection.forwardStep;
import static hex.modelselection.ModelSelectionModel.ModelSelectionParameters.Mode.*;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class ModelSelectionMaxRSweepFullTests extends TestUtil {
    /***
     * maxrsweepFull use functions written for maxrsweepSmall, hence there is no need to re-tests those functions.
     * Instead the test will be written to compare the run results with maxrsweepFull and maxrsweepSmall.
     */
    
    @Test
    public void testMaxRSweepEnumOnly() {
        Scope.enter();
        try {
            Frame origF = Scope.track(parseTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv"));
            int[] eCol = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
            Arrays.stream(eCol).forEach(x -> origF.replace(x, origF.vec(x).toCategoricalVec()).remove());
            DKV.put(origF);
            Scope.track(origF);
            ModelSelectionModel.ModelSelectionParameters parms = new ModelSelectionModel.ModelSelectionParameters();
            parms._response_column = "C21";
            parms._ignored_columns = new String[]{"C11","C12","C13","C14","C15","C16","C17","C18","C19","C20"};
            parms._family = gaussian;
            parms._max_predictor_number = 5;
            parms._train = origF._key;
            parms._mode = maxrsweepfull;
            assertMaxrSweepFullNMaxrSweepSmall(parms, origF);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testMaxRSweepMixedColumns() {
        Scope.enter();
        try {
            Frame origF = Scope.track(parseTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv"));
            int[] eCol = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
            Arrays.stream(eCol).forEach(x -> origF.replace(x, origF.vec(x).toCategoricalVec()).remove());
            DKV.put(origF);
            Scope.track(origF);
            ModelSelectionModel.ModelSelectionParameters parms = new ModelSelectionModel.ModelSelectionParameters();
            parms._response_column = "C21";
            parms._ignored_columns = new String[]{"C11","C12","C13","C14","C15","C16","C17","C18"};
            parms._family = gaussian;
            parms._max_predictor_number = 5;
            parms._train = origF._key;
            parms._mode = maxrsweepfull;
            assertMaxrSweepFullNMaxrSweepSmall(parms, origF);
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void testMaxRSweepNumColumns() {
        Scope.enter();
        try {
            Frame origF = Scope.track(parseTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv"));
            Scope.track(origF);
            ModelSelectionModel.ModelSelectionParameters parms = new ModelSelectionModel.ModelSelectionParameters();
            parms._response_column = "C21";
            parms._ignored_columns = new String[]{"C1","C2","C3","C4","C5","C6","C7","C8", "C9", "C10", "C11"};
            parms._family = gaussian;
            parms._max_predictor_number = 5;
            parms._train = origF._key;
            parms._mode = maxrsweepfull;
            assertMaxrSweepFullNMaxrSweepSmall(parms, origF);
        } finally {
            Scope.exit();
        }
    }

    public static void assertMaxrSweepFullNMaxrSweepSmall(ModelSelectionModel.ModelSelectionParameters parms, Frame train) {
        ModelSelectionModel modelMaxRSweepFull = new hex.modelselection.ModelSelection(parms).trainModel().get();
        Frame resultFrameSweepFull = modelMaxRSweepFull.result();
        Scope.track(resultFrameSweepFull);
        Scope.track_generic(modelMaxRSweepFull);

        parms._mode = maxrsweepsmall;
        ModelSelectionModel modelMaxrSweepSmall = new hex.modelselection.ModelSelection(parms).trainModel().get();
        Scope.track_generic(modelMaxrSweepSmall);
        Frame resultMaxRSweepSmall = modelMaxrSweepSmall.result();
        Scope.track(resultMaxRSweepSmall);
        TestUtil.assertIdenticalUpToRelTolerance(new Frame(resultFrameSweepFull.vec(2)),
                new Frame(resultMaxRSweepSmall.vec(2)), 1e-6);
        TestUtil.assertIdenticalUpToRelTolerance(new Frame(resultFrameSweepFull.vec(3)),
                new Frame(resultMaxRSweepSmall.vec(3)), 0);
    }

    public static DataInfo getDataInfo(Frame train) {
        ModelSelectionModel.ModelSelectionParameters parms = new ModelSelectionModel.ModelSelectionParameters();
        parms._response_column = "C21";
        parms._family = gaussian;
        parms._max_predictor_number = 5;
        parms._train = train._key;
        parms._mode = maxrsweepsmall;
        ModelSelectionModel modelMaxRSweep = new hex.modelselection.ModelSelection(parms).trainModel().get();
        Frame resultFrameSweep = modelMaxRSweep.result();
        Scope.track(resultFrameSweep);
        Scope.track_generic(modelMaxRSweep);
        return modelMaxRSweep._output._dinfo;
    }
}
