package hex.tree.xgboost.remote;

import hex.steam.SteamMessageSender;
import hex.steam.SteamMessenger;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.exec.RemoteXGBoostExecutor;
import org.apache.log4j.Logger;
import water.H2O;
import water.Job;
import water.fvec.Frame;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class SteamExecutorStarter implements SteamMessenger {

    private static final Logger LOG = Logger.getLogger(SteamExecutorStarter.class);

    /**
     * Initialized by Service lookup
     */
    private static SteamExecutorStarter instance;
    
    public static SteamExecutorStarter getInstance() {
        return instance;
    }

    private static class ClusterInfo {
        final String uri;
        final String userName;
        final String password;

        private ClusterInfo(String uri, String userName, String password) {
            this.uri = uri;
            this.userName = userName;
            this.password = password;
        }
    }
    
    private final Object sendingLock = new Object[0];
    private final Object clusterLock = new Object[0];

    private SteamMessageSender sender;
    private ClusterInfo cluster;

    private final Map<String, Map<String, String>> receivedMessages = new HashMap<>();
    
    public SteamExecutorStarter() {
        instance = this;
    }

    public RemoteXGBoostExecutor getRemoteExecutor(XGBoostModel model, Frame train) throws IOException {
        synchronized (clusterLock) {
            if (cluster == null) {
                LOG.info("Starting external cluster for model " + model._key + ".");
                startCluster();
            } else {
                LOG.info("External cluster available, starting model " + model._key + " now.");
            }
            return makeExecutor(model, train);
        }
    }

    private void startCluster() throws IOException {
        Map<String, String> startRequest = makeStartRequest();
        sendMessage(startRequest);
        Map<String, String> response = waitForResponse(startRequest);
        if (response.get("status").equals("started")) {
            String remoteUri = response.get("uri");
            String userName = response.get("user");
            String password = response.get("password");
            cluster = new ClusterInfo(remoteUri, userName, password);
            LOG.info("External cluster started at " + remoteUri + ".");
        } else {
            throw new IllegalStateException("Failed to start external cluster: " + response.get("reason"));
        }
    }

    private RemoteXGBoostExecutor makeExecutor(XGBoostModel model, Frame train) {
        return new RemoteXGBoostExecutor(model, train, cluster.uri, cluster.userName, cluster.password);
    }
    
    private Map<String, String> waitForResponse(Map<String, String> startRequest) {
        String responseKey = startRequest.get(ID) + "_response";
        synchronized (receivedMessages) {
            while (!receivedMessages.containsKey(responseKey)) {
                try {
                    receivedMessages.wait(10000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return receivedMessages.remove(responseKey);
        }
    }

    @Override
    public void onConnectionStateChange(SteamMessageSender sender) {
        synchronized (sendingLock) {
            this.sender = sender;
        }
    }

    private synchronized void sendMessage(Map<String, String> message) throws IOException {
        synchronized (sendingLock) {
            if (this.sender != null) {
                sender.sendMessage(message);
            } else {
                throw new IOException("Steam communication chanel is not open.");
            }
        }
    }

    @Override
    public void onMessage(Map<String, String> message) {
        if (message.get(TYPE).equals("stopXGBoostClusterNotification")) {
            handleStopRequest(message);
        } else {
            queueResponseMessage(message);
        }
    }

    private void queueResponseMessage(Map<String, String> message) {
        LOG.info("Received message response " + message.get(ID));
        synchronized (receivedMessages) {
            receivedMessages.put(message.get(ID), message);
            receivedMessages.notifyAll();
        }
    }

    private void handleStopRequest(Map<String, String> message) {
        LOG.info("Received stop request " + message.get(ID));
        synchronized (clusterLock) {
            boolean xgBoostInProgress = isXGBoostInProgress();
            try {
                LOG.info("Responding to stop request with allowed=" + !xgBoostInProgress);
                sendMessage(makeStopConfirmation(message, !xgBoostInProgress));
                if (!xgBoostInProgress) {
                    cluster = null;
                }
            } catch (IOException e) {
                LOG.error("Failed to send stop cluster response.", e);
            }
        }
    }

    private boolean isXGBoostInProgress() {
        return Arrays.stream(Job.jobs())
            .anyMatch(job -> job.isRunning() && job._result.get() instanceof XGBoostModel);
    }

    private Map<String, String> makeStartRequest() {
        Map<String, String> req = new HashMap<>();
        req.put(TYPE, "startXGBoostCluster");
        req.put(ID, H2O.SELF.getIpPortString() + "_startXGBoost");
        return req;
    }

    private Map<String, String> makeStopConfirmation(Map<String, String> message, boolean allow) {
        Map<String, String> req = new HashMap<>();
        req.put(TYPE, "stopXGBoostClusterConfirmation");
        req.put(ID, message.get(ID) + "_response");
        req.put("allowed", Boolean.toString(allow));
        return req;
    }

}
