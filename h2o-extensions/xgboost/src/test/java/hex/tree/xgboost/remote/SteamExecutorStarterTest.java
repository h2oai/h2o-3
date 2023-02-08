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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static hex.steam.SteamMessenger.ID;
import static hex.steam.SteamMessenger.TYPE;
import static org.junit.Assert.*;
import static water.TestUtil.parseTestFile;

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

    private Map<String, String> makeStartedResponse(Map<String, String> startReq) {
        return makeStartedResponse(startReq, "started", null);
    }

    private Map<String, String> makeStartedResponse(Map<String, String> startReq, String status, String reason) {
        Map<String, String> startResp = new HashMap<>();
        startResp.put(ID, startReq.get(ID) + "_response");
        startResp.put(TYPE, "xgboostClusterStartNotification");
        startResp.put("uri", H2O.CLOUD.leader().getIpPortString());
        startResp.put("status", status);
        if (reason != null) {
            startResp.put("reason", reason);
        }
        return startResp;
    }

    private Map<String, String> makeStartingResponse(Map<String, String> startReq) {
        Map<String, String> startResp = new HashMap<>();
        startResp.put(ID, startReq.get(ID) + "_response");
        startResp.put(TYPE, "xgboostClusterStartNotification");
        startResp.put("status", "starting");
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
            Frame train = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));

            XGBoostModel.XGBoostParameters params = new XGBoostModel.XGBoostParameters();
            params._train = train._key;
            params._response_column = "AGE";
            params._ignored_columns = new String[]{"ID"};

            // queue two model in parallel
            Job<XGBoostModel> model1 = new XGBoost(params).trainModel();
            Job<XGBoostModel> model2 = new XGBoost(params).trainModel();

            // first request will request external cluster start
            System.out.println("first request will start external cluster start...");
            Map<String, String> startReq = steam.waitToReceiveMessage("start request");
            assertNotNull(startReq.get("_id"));
            assertEquals("startXGBoostCluster", startReq.get("_type"));
            steam.sendMessage(makeStartingResponse(startReq));
            steam.sendMessage(makeStartedResponse(startReq));
            Scope.track_generic(model1.get());
            System.out.println("grabbed model1...");

            // second model should not go to steam
            System.out.println("Second model should not go to steam.");
            Optional<Map<String, String>> startRequest2 = steam.waitToReceiveMessage("none", 2_000, false);
            assertFalse("Unexpected message to steam", startRequest2.isPresent());
            Scope.track_generic(model2.get());

            // steam requests cluster stop
            System.out.println("Steam requests cluster stop.");
            steam.sendMessage(makeStopReq("01_stop_req"));
            expectAndCheckStopResponse(steam, "01_stop_req", true);
            System.out.println("Steam cluster stopped.");
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
            steam.close();
            Scope.exit();
        }
    }

    @Test
    public void testSteamClusterTimeout() throws Exception {
        Scope.enter();
        final WebsocketClient steam = new WebsocketClient();
        System.setProperty("sys.ai.h2o.steam.notification.timeout", "3000"); // increase from 2000
        try {
            Frame train = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));

            XGBoostModel.XGBoostParameters params = new XGBoostModel.XGBoostParameters();
            params._train = train._key;
            params._response_column = "AGE";
            params._ignored_columns = new String[]{"ID"};

            // first request will request external cluster start
            Job<XGBoostModel> model1 = new XGBoost(params).trainModel();
            System.out.println("first request will request external cluster start....");
            Map<String, String> startReq = steam.waitToReceiveMessage("start request", 2000);
            assertNotNull(startReq.get("_id"));
            assertEquals("startXGBoostCluster", startReq.get("_type"));

            // steam does not respond in time
            Thread.sleep(4_000);
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
            Map<String, String> startRe2 = steam.waitToReceiveMessage("start request 2", 2000);
            assertNotNull(startRe2.get("_id"));
            assertEquals("startXGBoostCluster", startRe2.get("_type"));

            // original start response message is ignored, waits for new confirmation
            steam.sendMessage(makeStartingResponse(startRe2));
            steam.sendMessage(makeStartedResponse(startRe2));
            Scope.track_generic(model2.get());

            // steam requests cluster stop
            steam.sendMessage(makeStopReq("02_stop_req"));
            expectAndCheckStopResponse(steam, "02_stop_req", true);
            System.out.println("Steam cluster stopped.");
        } finally {
            System.clearProperty("sys.ai.h2o.steam.notification.timeout");
            steam.close();
            Scope.exit();
        }
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
            Thread.sleep(1000); // double from original 500
            steam.sendMessage(makeStopReq("03_stop_req_01"));
            System.out.println("Sent first cluster stop message and checking that cluster has stopped.");
            expectAndCheckStopResponse(steam, "03_stop_req_01", false);
            System.out.println("first cluster has stopped.");
            // model finishes
            Scope.track_generic(model.get());

            // steam requests cluster stop again
            steam.sendMessage(makeStopReq("03_stop_req_02"));
            System.out.println("Sent second cluster stop message and checking that cluster has stopped.");
            expectAndCheckStopResponse(steam, "03_stop_req_02", true);
            System.out.println("second cluster has stopped.");

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
            System.out.println("Sent last cluster stop message and checking that cluster has stopped.");
            expectAndCheckStopResponse(steam, "03_stop_req_03", true);
            System.out.println("last cluster has stopped.");
        } finally {
            steam.close();
            Scope.exit();
        }
    }

    @Test
    public void testSteamClusterTerminateNoCluster() throws Exception {
        Scope.enter();
        final WebsocketClient steam = new WebsocketClient();
        try {
            // steam requests cluster stop
            steam.sendMessage(makeStopReq("04_stop_req"));
            expectAndCheckStopResponse(steam, "04_stop_req", true);
        } finally {
            steam.close();
            Scope.exit();
        }
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
            steam.close();
            Scope.exit();
        }
    }
    
}
