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

    private Map<String, String> makeStartResponse(Map<String, String> startReq) {
        return makeStartResponse(startReq, "started", null);
    }

    private Map<String, String> makeStartResponse(Map<String, String> startReq, String status, String reason) {
        Map<String, String> startResp = new HashMap<>();
        startResp.put(ID, startReq.get(ID) + "_response");
        startResp.put(TYPE, "xgboostClusterStartNotification");
        startResp.put("uri", H2O.getIpPortString());
        startResp.put("status", status);
        if (reason != null) {
            startResp.put("reason", reason);
        }
        return startResp;
    }

    private Map<String, String> makeStopReq(String id) {
        Map<String, String> stopReq = new HashMap<>();
        stopReq.put(ID, id);
        stopReq.put(TYPE, "stopXGBoostClusterNotification");
        return stopReq;
    }
    
    private void expectAndCheckStopResponse(WebsocketClient steam, String id, boolean allowed) {
        Map<String, String> stop2Resp = steam.waitToReceiveMessage("response to " + id);
        assertEquals(id + "_response", stop2Resp.get("_id"));
        assertEquals("stopXGBoostClusterConfirmation", stop2Resp.get("_type"));
        assertEquals(Boolean.toString(allowed), stop2Resp.get("allowed"));
    }

    @Test
    public void testSteamClusterStart() throws Exception {
        Scope.enter();
        final WebsocketClient steam = new WebsocketClient();
        try {
            Frame train = Scope.track(parse_test_file("./smalldata/prostate/prostate.csv"));

            XGBoostModel.XGBoostParameters params = new XGBoostModel.XGBoostParameters();
            params._train = train._key;
            params._response_column = "AGE";
            params._ignored_columns = new String[]{"ID"};

            // queue two model in parallel
            Job<XGBoostModel> model1 = new XGBoost(params).trainModel();
            Job<XGBoostModel> model2 = new XGBoost(params).trainModel();

            // first request will request external cluster start
            Map<String, String> startReq = steam.waitToReceiveMessage("start request");
            assertNotNull(startReq.get("_id"));
            assertEquals("startXGBoostCluster", startReq.get("_type"));
            steam.sendMessage(makeStartResponse(startReq));
            Scope.track_generic(model1.get());

            // second model should not go to steam
            assertNull("Unexpected message to steam", steam.waitToReceiveMessage("none", 1_000, false));
            Scope.track_generic(model2.get());

            // steam requests cluster stop
            steam.sendMessage(makeStopReq("stop_req"));
            expectAndCheckStopResponse(steam, "stop_req", true);
        } finally {
            steam.close();
            Scope.exit();
        }
    }

    @Test
    public void testSteamClusterFail() throws Exception {
        Scope.enter();
        final WebsocketClient steam = new WebsocketClient();
        try {
            Frame train = Scope.track(parse_test_file("./smalldata/prostate/prostate.csv"));

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
            steam.sendMessage(makeStartResponse(startReq, "failed", "Testing in progress"));
            try {
                Scope.track_generic(model1.get());
                fail("model1 expected exception to be thrown.");
            } catch (Exception e) {
                Scope.track_generic(model1._result.get()); // even though the training failed we need to remove the model
                assertEquals("Failed to start external cluster: Testing in progress", e.getCause().getMessage());
            }

            // should send no more messages
            assertNull("Unexpected message to steam", steam.waitToReceiveMessage("none", 1_000, false));
        } finally {
            steam.close();
            Scope.exit();
        }
    }

    @Test
    public void testSteamClusterStop() throws Exception {
        Scope.enter();
        final WebsocketClient steam = new WebsocketClient();
        try {
            Frame train = Scope.track(parse_test_file("./smalldata/prostate/prostate.csv"));

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
            steam.sendMessage(makeStartResponse(startReq));

            // steam requests cluster stop
            Thread.sleep(500);
            steam.sendMessage(makeStopReq("stop_req_01"));
            expectAndCheckStopResponse(steam, "stop_req_01", false);

            // model finishes
            Scope.track_generic(model.get());

            // steam requests cluster stop again
            steam.sendMessage(makeStopReq("stop_req_02"));
            expectAndCheckStopResponse(steam, "stop_req_02", true);

            // building another model
            params._ntrees = 5;
            Job<XGBoostModel> model2 = new XGBoost(params).trainModel();

            // will request external cluster start again
            Map<String, String> startReq2 = steam.waitToReceiveMessage("start request");
            assertNotNull(startReq2.get("_id"));
            assertEquals("startXGBoostCluster", startReq2.get("_type"));
            steam.sendMessage(makeStartResponse(startReq));

            Scope.track_generic(model2.get());

            // steam requests cluster stop
            steam.sendMessage(makeStopReq("stop_req_03"));
            expectAndCheckStopResponse(steam, "stop_req_03", true);
        } finally {
            steam.close();
            Scope.exit();
        }
    }
    
}
