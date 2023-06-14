package hex.tree.xgboost;

import hex.genmodel.utils.DistributionFamily;
import hex.tree.xgboost.util.GpuUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.*;
import water.fvec.Frame;

import java.util.Arrays;
import java.util.Collection;

/**
 * Test that changes in xgboost-predictor:0.3.19 are correct on smaller dataset. This test is connected to 
 * {@link XGBoostPredictImplComparisonTest} but designed only to changes in predictor needed for upgrade to xgboost 1.6.1
 */
@RunWith(Parameterized.class)
public class XGBoostPredictImplComparisonSmallTest extends TestUtil {

    @BeforeClass
    public static void setup() {
        stall_till_cloudsize(1);
    }

    @Parameterized.Parameters(name = "XGBoost(booster={0},distribution={1},ntrees={2},rate_drop={3}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {"gbtree", "AUTO", 8, 0},
            {"gbtree", "gaussian", 8, 0},
            {"gbtree", "gamma", 8, 0},
            {"gbtree", "poisson", 8, 0},
            {"gbtree", "tweedie", 8, 0},
            {"dart", "AUTO", 20, 0.1f},
            {"dart", "gaussian", 20, 0.1f},
            {"dart", "gamma", 50, 0.1f},
            {"dart", "poisson", 20, 0.1f},
            {"dart", "tweedie", 20, 0.1f},
            {"gblinear", "AUTO", 8, 0},
            {"gblinear", "gaussian", 8, 0},
            {"gblinear", "gamma", 8, 0},
            {"gblinear", "poisson", 8, 0},
            {"gblinear", "tweedie", 8, 0}
        });
    }

    @Parameterized.Parameter
    public String booster;

    @Parameterized.Parameter(1)
    public String distribution;

    @Parameterized.Parameter(2)
    public int ntrees;

    @Parameterized.Parameter(3)
    public float rate_drop;    

    @Test
    public void testPredictionsAreSame() {
        Scope.enter();
        try {
            Frame tfr = Scope.track(parseTestFile("./smalldata/prostate/prostate54-2.csv"));
            String response = "AGE";

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._booster = XGBoostModel.XGBoostParameters.Booster.valueOf(booster);
            parms._distribution = DistributionFamily.valueOf(distribution);
            parms._ntrees = ntrees;
            parms._rate_drop = rate_drop; // For DART - this parameter make DART prediction different from GBTree
            parms._max_depth = 5;
            parms._seed = 0xCECAC;
            parms._train = tfr._key;
            parms._response_column = response;

            XGBoostModel model = new XGBoost(parms).trainModel().get();
            Scope.track_generic(model);

            System.setProperty("sys.ai.h2o.xgboost.predict.native.enable", "true");
            Frame predsNative = Scope.track(model.score(tfr));
            System.setProperty("sys.ai.h2o.xgboost.predict.native.enable", "false");
            Frame predsJava = Scope.track(model.score(tfr));

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

    public static Double getRelDelta(XGBoostModel.XGBoostParameters parms, String booster) {
        if (usesGpu(parms)) {
            // train/predict on gpu is non-deterministic
            return 1e-3;
        } else if ("gblinear".equals(booster)) {
            return 1e-6;
        } else {
            return null;
        }
    }

    public static boolean usesGpu(XGBoostModel.XGBoostParameters parms) {
        return parms._backend == XGBoostModel.XGBoostParameters.Backend.gpu ||
            (parms._backend == XGBoostModel.XGBoostParameters.Backend.auto &&
                GpuUtils.hasGPU(H2O.CLOUD.members()[0], null));
    }

}
