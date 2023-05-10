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
import java.util.Optional;

import static hex.tree.xgboost.remote.SteamExecutorStarterTestHelper.makeStartedResponse;
import static org.junit.Assert.*;
import static water.TestUtil.parseTestFile;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class SteamExecutorStarterFailTest {

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
    public void testSteamClusterFail() throws Exception {
        Scope.enter();
        final WebsocketClient steam = new WebsocketClient();
        try {
            Frame train = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));

            XGBoostModel.XGBoostParameters params = new XGBoostModel.XGBoostParameters();
            params._train = train._key;
            params._response_column = "AGE";
            params._ignored_columns = new String[]{"ID"};

            // first request will request external cluster start
            Job<XGBoostModel> model1 = new XGBoost(params).trainModel();
            Map<String, String> startReq = steam.waitToReceiveMessage("start request", 50000);
            assertNotNull(startReq.get("_id"));
            assertEquals("startXGBoostCluster", startReq.get("_type"));

            // fail first cluster request
            steam.sendMessage(makeStartedResponse(startReq, "failed", "Testing in progress"));
            try {
                Scope.track_generic(model1.get());
                fail("Expected exception to be thrown");
            } catch (Exception e) {
                Scope.track_generic(model1._result.get()); // even though the training failed we need to remove the model
                assertEquals("Failed to start external cluster: Testing in progress", e.getCause().getMessage());
            }

            // should send no more messages
            Optional<Map<String, String>> finalMessage = steam.waitToReceiveMessage("none", 1_000, false);
            assertFalse("Unexpected message to steam", finalMessage.isPresent());
        } finally {
            Scope.exit();
            steam.close();
        }
    }
}
