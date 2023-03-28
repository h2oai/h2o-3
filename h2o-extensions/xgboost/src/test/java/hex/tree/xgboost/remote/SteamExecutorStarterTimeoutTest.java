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
import water.util.Log;

import java.util.Map;

import static hex.tree.xgboost.remote.SteamExecutorStarterTestHelper.*;
import static org.junit.Assert.*;
import static water.TestUtil.parseTestFile;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class SteamExecutorStarterTimeoutTest {

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
    public void testSteamClusterTimeout() throws Exception {
        Scope.enter();
        final WebsocketClient steam = new WebsocketClient();
        System.setProperty("sys.ai.h2o.steam.notification.timeout", "2000");
        try {
            Frame train = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));

            XGBoostModel.XGBoostParameters params = new XGBoostModel.XGBoostParameters();
            params._train = train._key;
            params._response_column = "AGE";
            params._ignored_columns = new String[]{"ID"};

            // first request will request external cluster start
            Job<XGBoostModel> model1 = new XGBoost(params).trainModel();
            Map<String, String> startReq = steam.waitToReceiveMessage("start request", 1000);
            assertNotNull(startReq.get("_id"));
            assertEquals("startXGBoostCluster", startReq.get("_type"));

            // steam does not respond in time
            Thread.sleep(3_000);
            try {
                Scope.track_generic(model1.get());
                fail("Expected exception to be thrown");
            } catch (Exception e) {
                Scope.track_generic(model1._result.get()); // even though the training failed we need to remove the model
                if (!(e.getCause() != null && e.getCause().getCause() != null && "No response received from Steam.".equals(e.getCause().getCause().getMessage()))) {
                    Log.err("Unexpected exception: ", e);
                    fail("Unexpected exception!");
                }
            }

            // steam responds eventually
            steam.sendMessage(makeStartedResponse(startReq));

            // try again
            Job<XGBoostModel> model2 = new XGBoost(params).trainModel();
            Map<String, String> startRe2 = steam.waitToReceiveMessage("start request 2", 1000);
            assertNotNull(startRe2.get("_id"));
            assertEquals("startXGBoostCluster", startRe2.get("_type"));

            // original start response message is ignored, waits for new confirmation
            steam.sendMessage(makeStartingResponse(startRe2));
            steam.sendMessage(makeStartedResponse(startRe2));
            Scope.track_generic(model2.get());

            // steam requests cluster stop
            steam.sendMessage(makeStopReq("02_stop_req"));
            expectAndCheckStopResponse(steam, "02_stop_req", true);
        } finally {
            System.clearProperty("sys.ai.h2o.steam.notification.timeout");
            Scope.exit();
            steam.close();
        }
    }
}
