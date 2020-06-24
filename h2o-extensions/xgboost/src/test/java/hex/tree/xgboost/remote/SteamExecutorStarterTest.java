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

import java.util.HashMap;
import java.util.Map;

import static hex.steam.SteamMessenger.ID;
import static hex.steam.SteamMessenger.TYPE;
import static org.junit.Assert.*;
import static water.TestUtil.parse_test_file;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class SteamExecutorStarterTest {
    
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
    public void testSteamClusterStart() throws Exception {
        Scope.enter();
        try {
            Frame train = Scope.track(parse_test_file("./smalldata/prostate/prostate.csv"));

            XGBoostModel.XGBoostParameters params = new XGBoostModel.XGBoostParameters();
            params._train = train._key;
            params._response_column = "AGE";
            params._ignored_columns = new String[]{"ID"};

            final WebsocketClient steam = new WebsocketClient();

            // first request will request external cluster start
            Job<XGBoostModel> model1 = new XGBoost(params).trainModel();
            Map<String, String> startReq = steam.waitToReceiveMessage("start request");
            assertNotNull(startReq.get("_id"));
            assertEquals("startXGBoostCluster", startReq.get("_type"));
            Map<String, String> startResp = new HashMap<>();
            startResp.put(ID, startReq.get(ID) + "_response");
            startResp.put(TYPE, "xgboostClusterStartNotification");
            startResp.put("uri", H2O.getIpPortString());
            startResp.put("status", "started");
            startResp.put("timeout", "5");
            steam.sendMessage(startResp);
            Scope.track_generic(model1.get());

            // another request should not go to steam
            Job<XGBoostModel> model2 = new XGBoost(params).trainModel();
            assertNull("Unexpected message to steam", steam.waitToReceiveMessage("none", 1_000, false));
            Scope.track_generic(model2.get());

            // should terminate the cluster after 5sec
            Map<String, String> stopReq = steam.waitToReceiveMessage("stop request", 15_000);
            assertNotNull(stopReq.get("_id"));
            assertEquals("stopXGBoostCluster", stopReq.get("_type"));
        } finally {
            Scope.exit();
        }
    }
    
}
