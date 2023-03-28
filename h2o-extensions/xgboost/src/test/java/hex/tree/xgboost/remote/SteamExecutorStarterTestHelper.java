package hex.tree.xgboost.remote;

import org.junit.Ignore;
import water.H2O;
import water.test.WebsocketClient;

import java.util.HashMap;
import java.util.Map;

import static hex.steam.SteamMessenger.ID;
import static hex.steam.SteamMessenger.TYPE;
import static org.junit.Assert.assertEquals;

@Ignore("Helper method")
public class SteamExecutorStarterTestHelper {

    public static Map<String, String> makeStartedResponse(Map<String, String> startReq) {
        return makeStartedResponse(startReq, "started", null);
    }

    public static Map<String, String> makeStartedResponse(Map<String, String> startReq, String status, String reason) {
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

    public static Map<String, String> makeStartingResponse(Map<String, String> startReq) {
        Map<String, String> startResp = new HashMap<>();
        startResp.put(ID, startReq.get(ID) + "_response");
        startResp.put(TYPE, "xgboostClusterStartNotification");
        startResp.put("status", "starting");
        return startResp;
    }

    public static Map<String, String> makeStopReq(String id) {
        Map<String, String> stopReq = new HashMap<>();
        stopReq.put(ID, id);
        stopReq.put(TYPE, "stopXGBoostClusterNotification");
        return stopReq;
    }

    public static void expectAndCheckStopResponse(WebsocketClient steam, String id, boolean allowed) {
        Map<String, String> stop2Resp = steam.waitToReceiveMessage("response to " + id);
        assertEquals(id + "_response", stop2Resp.get("_id"));
        assertEquals("stopXGBoostClusterConfirmation", stop2Resp.get("_type"));
        assertEquals(Boolean.toString(allowed), stop2Resp.get("allowed"));
    }
}
