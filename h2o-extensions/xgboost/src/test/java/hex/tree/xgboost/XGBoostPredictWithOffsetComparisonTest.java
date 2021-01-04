package hex.tree.xgboost;

import hex.SplitFrame;
import hex.genmodel.utils.DistributionFamily;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.*;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.util.Arrays;
import java.util.Collection;

import static hex.tree.xgboost.XGBoostPredictImplComparisonTest.getRelDelta;
import static hex.tree.xgboost.XGBoostPredictImplComparisonTest.usesGpu;

@RunWith(Parameterized.class)
public class XGBoostPredictWithOffsetComparisonTest extends TestUtil {

    @BeforeClass
    public static void setup() {
        stall_till_cloudsize(1);
    }

    @Parameterized.Parameters(name = "XGBoost(booster={0},distribution={1},response={2}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {"gbtree", "AGE"},
            {"gbtree", "CAPSULE"},
            {"dart", "AGE"},
            {"dart", "CAPSULE"},
            {"gblinear", "AGE"},
            {"gblinear", "CAPSULE"}
        });
    }

    @Parameterized.Parameter
    public String booster;

    @Parameterized.Parameter(1)
    public String response;

    @Test
    public void testPredictionsAreSame() {
        Scope.enter();
        try {
            Frame tfr = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));
            // define special columns
            Scope.track(tfr.replace(1, tfr.vecs()[1].toCategoricalVec()));   // Convert CAPSULE to categorical
            Scope.track(tfr.replace(3, tfr.vecs()[3].toCategoricalVec()));   // Convert RACE to categorical
            DKV.put(tfr);
            
            // create offset column
            Frame offsetFr = new TestFrameBuilder()
                .withName("offsetFrame")
                .withColNames("offset")
                .withVecTypes(Vec.T_NUM)
                .withRandomDoubleDataForCol(0, (int) tfr.numRows(), 0, 1, 42)
                .build();

            // merge offset into train
            tfr.add(offsetFr);

            // split into train/test
            SplitFrame sf = new SplitFrame(tfr, new double[] {0.7, 0.3}, null);
            sf.exec().get();
            Key[] splits = sf._destination_frames;
            Frame trainFrame = Scope.track((Frame) splits[0].get());
            Frame testFrame = Scope.track((Frame) splits[1].get());

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._booster = XGBoostModel.XGBoostParameters.Booster.valueOf(booster);
            parms._distribution = DistributionFamily.AUTO;
            parms._ntrees = 10;
            parms._max_depth = 5;
            parms._train = trainFrame._key;
            parms._valid = testFrame._key;
            parms._response_column = response;
            parms._offset_column = "offset";

            XGBoostModel model = new XGBoost(parms).trainModel().get();
            Scope.track_generic(model);

            System.setProperty("sys.ai.h2o.xgboost.predict.native.enable", "true");
            Frame predsNative = Scope.track(model.score(testFrame));
            System.setProperty("sys.ai.h2o.xgboost.predict.native.enable", "false");
            Frame predsJava = Scope.track(model.score(testFrame));

            if (usesGpu(parms)) {
                // for GPU only compare probabilities, actual class labels may be different due to precision
                // differences
                predsNative = Scope.track(predsNative.subframe(1, predsNative.numCols()));
                predsJava = Scope.track(predsJava.subframe(1, predsJava.numCols()));
            }
            assertFrameEquals(predsNative, predsJava, 1e-10, getRelDelta(parms, booster));
        } finally {
            System.clearProperty("sys.ai.h2o.xgboost.predict.native.enable");
            Scope.exit();
        }
    }

}
