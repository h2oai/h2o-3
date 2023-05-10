package hex.tree.xgboost.remote;

import hex.tree.xgboost.XGBoost;
import hex.tree.xgboost.XGBoostModel;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.H2O;
import water.Job;
import water.Scope;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.test.WebsocketClient;

import java.util.Map;

import static hex.tree.xgboost.remote.SteamExecutorStarterTestHelper.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static water.TestUtil.parseTestFile;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class SteamExecutorStarterStopTest {

    static boolean orig_external_xgboost;

    @BeforeClass
    public static void configureRemoteXGBoost() {
        orig_external_xgboost = H2O.ARGS.use_external_xgboost;
        H2O.ARGS.use_external_xgboost = true;
    }

    @AfterClass
    public static void revertRemoteXGBoost() {
        H2O.ARGS.use_external_xgboost = orig_external_xgboost;
    }

    @Test
    public void testSteamClusterStop() throws Exception {
        Scope.enter();
        final WebsocketClient steam = new WebsocketClient();
        try {
            Frame train = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));

            XGBoostModel.XGBoostParameters params = new XGBoostModel.XGBoostParameters();
            params._train = train._key;
            params._ntrees = 200;
            params._response_column = "AGE";
            params._ignored_columns = new String[]{"ID"};

            Job<XGBoostModel> model = new XGBoost(params).trainModel();

            // first request will request external cluster start
            Map<String, String> startReq = steam.waitToReceiveMessage("start request");
            assertNotNull(startReq.get("_id"));
            assertEquals("startXGBoostCluster", startReq.get("_type"));
            steam.sendMessage(makeStartedResponse(startReq));

            // steam requests cluster stop
            Thread.sleep(500);
            steam.sendMessage(makeStopReq("03_stop_req_01"));
            expectAndCheckStopResponse(steam, "03_stop_req_01", false);

            // model finishes
            Scope.track_generic(model.get());

            // steam requests cluster stop again
            steam.sendMessage(makeStopReq("03_stop_req_02"));
            expectAndCheckStopResponse(steam, "03_stop_req_02", true);

            // building another model
            XGBoostModel.XGBoostParameters params2 = new XGBoostModel.XGBoostParameters();
            params2._train = train._key;
            params2._ntrees = 5;
            params2._response_column = "AGE";
            params2._ignored_columns = new String[]{"ID"};
            Job<XGBoostModel> model2 = new XGBoost(params2).trainModel();

            // will request external cluster start again
            Map<String, String> startReq2 = steam.waitToReceiveMessage("start request");
            assertNotNull(startReq2.get("_id"));
            assertEquals("startXGBoostCluster", startReq2.get("_type"));
            steam.sendMessage(makeStartedResponse(startReq));

            Scope.track_generic(model2.get());

            // steam requests cluster stop
            steam.sendMessage(makeStopReq("03_stop_req_03"));
            expectAndCheckStopResponse(steam, "03_stop_req_03", true);
        } finally {
            Scope.exit();
            steam.close();
        }
    }
}
