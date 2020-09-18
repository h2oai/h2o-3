package hex.tree.xgboost;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.H2O;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static hex.tree.xgboost.XGBoostTest.loadWeather;
import static water.TestUtil.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class ExternalXGBoostConsistencyTest {

    private static final String EXTERNAL_XGB_PROP = "sys.ai.h2o.xgboost.external.address";
    
    private static void enableExternalXGBoost() {
        System.setProperty(EXTERNAL_XGB_PROP, H2O.getIpPortString());
    }
    
    private static void disableExternalXGBoost() {
        System.clearProperty(EXTERNAL_XGB_PROP);
    }
    
    private static void assertRemoteXGBoostResultSame(TestUtil.Frames parts, XGBoostModel.XGBoostParameters parms) {
        XGBoostModel modelLocal = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
        Scope.track_generic(modelLocal);
        Frame predsLocal = Scope.track(modelLocal.score(parts.test));

        enableExternalXGBoost();
        XGBoostModel modelRemote = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
        Scope.track_generic(modelRemote);
        Frame predsRemote = Scope.track(modelRemote.score(parts.test));

        printOutFrameAsTable(predsLocal, false, 10);
        printOutFrameAsTable(predsRemote, false, 20);
        double delta = XGBoostPredictImplComparisonTest.usesGpu(parms) ? 1e-4d : 0;
        compareFrames(predsLocal, predsRemote, delta);
    }
    
    @Test
    public void WeatherBinary() {
        Scope.enter();
        try {
            String response = "RainTomorrow";
            Frame df = loadWeather(response);
            TestUtil.Frames parts = split(df, 0.1, 0.1);
            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._ntrees = 10;
            parms._max_depth = 5;
            parms._train = parts.train._key;
            parms._valid = parts.valid._key;
            parms._response_column = response;
            assertRemoteXGBoostResultSame(parts, parms);
        } finally {
            disableExternalXGBoost();
            Scope.exit();
        }
    }

    @Test
    public void ProstateRegression() {
        Scope.enter();
        try {
            Frame df = Scope.track(parse_test_file("./smalldata/prostate/prostate.csv"));
            Scope.track(df.replace(1, df.vecs()[1].toCategoricalVec()));   // Convert CAPSULE to categorical
            Scope.track(df.replace(3, df.vecs()[3].toCategoricalVec()));   // Convert RACE to categorical
            DKV.put(df);
            Scope.track(df);
            TestUtil.Frames parts = split(df, 0.1, 0.1);
            String response = "AGE";
            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._train = parts.train._key;
            parms._valid = parts.valid._key;
            parms._response_column = response;
            parms._ignored_columns = new String[] {"ID"};
            parms._ntrees = 20;
            parms._max_depth = 5;
            assertRemoteXGBoostResultSame(parts, parms);
        } finally {
            disableExternalXGBoost();
            Scope.exit();
        }
    }

    @Test
    public void MNIST() {
        Scope.enter();
        try {
            Frame df = parse_test_file("bigdata/laptop/mnist/train.csv.gz");
            Scope.track(df.replace(784, df.vecs()[784].toCategoricalVec()));   // Convert response 'C785' to categorical
            DKV.put(df);
            Scope.track(df);
            TestUtil.Frames parts = split(df, 0.1);
            String response = "C785";
            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._ntrees = 3;
            parms._max_depth = 3;
            parms._train = parts.train._key;
            parms._response_column = response;
            parms._seed = 0xCAFEBABE;
            assertRemoteXGBoostResultSame(parts, parms);
        } finally {
            disableExternalXGBoost();
            Scope.exit();
        }
    }

    @Test
    public void HIGGS() {
        Scope.enter();
        try {
            Frame df = parse_test_file("bigdata/laptop/higgs_head_2M.csv");
            Scope.track(df);
            TestUtil.Frames parts = split(df, 0.1);
            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._ntrees = 10;
            parms._max_depth = 10;
            parms._train = parts.train._key;
            parms._response_column = df.name(0);
            parms._tree_method = XGBoostModel.XGBoostParameters.TreeMethod.hist;
            parms._learn_rate = 0.1;
            parms._seed = 1;
            assertRemoteXGBoostResultSame(parts, parms);
        } finally {
            disableExternalXGBoost();
            Scope.exit();
        }
    }

}
