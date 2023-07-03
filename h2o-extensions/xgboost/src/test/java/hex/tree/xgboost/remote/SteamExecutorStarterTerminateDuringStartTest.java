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
import static water.H2O.fail;
import static water.TestUtil.parseTestFile;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class SteamExecutorStarterTerminateDuringStartTest {

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
    public void testSteamClusterTerminateDuringStart() throws Exception {
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

            // steam requests cluster stop
            steam.sendMessage(makeStopReq("05_stop_req"));
            expectAndCheckStopResponse(steam, "05_stop_req", false);

            // steam fails cluster start
            steam.sendMessage(makeStartedResponse(startReq, "failed", "testing"));
            try {
                Scope.track_generic(model.get());
                fail("Expected exception to be thrown");
            } catch (Exception e) {
                Scope.track_generic(model._result.get()); // even though the training failed we need to remove the model
                assertEquals("Failed to start external cluster: testing", e.getCause().getMessage());
            }
        } finally {
            Scope.exit();
            steam.close();
        }
    }
}
