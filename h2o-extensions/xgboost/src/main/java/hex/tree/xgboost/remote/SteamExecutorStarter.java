package hex.tree.xgboost.remote;

import hex.steam.SteamMessageSender;
import hex.steam.SteamMessenger;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.exec.RemoteXGBoostExecutor;
import org.apache.log4j.Logger;
import water.H2O;
import water.Job;
import water.Key;
import water.fvec.Frame;

import java.io.IOException;
import java.util.*;

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

    private final Deque<Map<String, String>> receivedMessages = new LinkedList<>();
    
    public SteamExecutorStarter() {
        instance = this;
    }

    public RemoteXGBoostExecutor getRemoteExecutor(XGBoostModel model, Frame train, Frame valid, Job<XGBoostModel> job) throws IOException {
        ClusterInfo clusterInfo = ensureClusterStarted(model._key, job);
        return makeExecutor(model, train, valid, clusterInfo);
    }

    public void startCluster(Key<XGBoostModel> key, Job<XGBoostModel> job) throws IOException {
        ensureClusterStarted(key, job);
    }
    
    private ClusterInfo ensureClusterStarted(Key<XGBoostModel> key, Job<XGBoostModel> job) throws IOException {
        synchronized (clusterLock) {
            if (cluster == null) {
                LOG.info("Starting external cluster for model " + key + ".");
                startCluster(job);
            } else {
                LOG.info("External cluster available, starting model " + key + " now.");
            }
            return cluster;
        }
    }

    private void startCluster(Job<XGBoostModel> job) throws IOException {
        clearMessages();
        Map<String, String> startRequest = makeStartRequest();
        sendMessage(startRequest);
        while (!job.stop_requested()) {
            Map<String, String> response = waitForMessage();
            if (response != null) {
                if ("started".equals(response.get("status"))) {
                    String remoteUri = response.get("uri");
                    String userName = response.get("user");
                    String password = response.get("password");
                    cluster = new ClusterInfo(remoteUri, userName, password);
                    LOG.info("External cluster started at " + remoteUri + ".");
                    break;
                } else if ("starting".equals(response.get("status"))) {
                    LOG.info("Continuing to wait for external cluster to start.");                    
                } else if ("failed".equals(response.get("status"))) {
                    throw new IllegalStateException("Failed to start external cluster: " + response.get("reason"));
                } else {
                    throw new IllegalStateException(
                        "Unknown status received from steam: " + response.get("status") + ", reason:" + response.get("reason")
                    );
                }
            } else {
                throw new IllegalStateException("No response received from Steam.");
            }
        }
    }

    private static RemoteXGBoostExecutor makeExecutor(XGBoostModel model, Frame train, Frame valid, ClusterInfo cluster) {
        return new RemoteXGBoostExecutor(model, train, valid, cluster.uri, cluster.userName, cluster.password);
    }
    
    private void clearMessages() {
        synchronized (receivedMessages) {
            receivedMessages.clear();
        }
    }
    
    private Map<String, String> waitForMessage() {
        int timeout = Integer.parseInt(H2O.getSysProperty("steam.notification.timeout", "20000"));
        synchronized (receivedMessages) {
            if (!receivedMessages.isEmpty()) {
                return receivedMessages.pop();
            }
            try {
                receivedMessages.wait(timeout);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (!receivedMessages.isEmpty()) {
                return receivedMessages.pop();
            } else {
                return null;
            }
        }
    }

    @Override
    public void onConnectionStateChange(SteamMessageSender sender) {
        synchronized (sendingLock) {
            this.sender = sender;
        }
    }

    private void sendMessage(Map<String, String> message) throws IOException {
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
        if ("stopXGBoostClusterNotification".equals(message.get(TYPE))) {
            handleStopRequest(message);
        } else if ("xgboostClusterStartNotification".equals(message.get(TYPE))) {
            queueResponse(message);
        } else {
            LOG.debug("Ignoring message " + message.get(ID) + " " + message.get(TYPE));
        }
    }

    private void queueResponse(Map<String, String> message) {
        synchronized (receivedMessages) {
            LOG.info("Received message response " + message.get(ID));
            receivedMessages.add(message);
            receivedMessages.notifyAll();
        }
    }

    private void handleStopRequest(Map<String, String> message) {
        LOG.info("Received stop request " + message.get(ID));
        boolean xgBoostInProgress = isXGBoostInProgress();
        if (xgBoostInProgress) {
            LOG.info("Responding to stop request with allowed=false");
            sendStopResponse(message, false);
        } else {
            synchronized (clusterLock) {
                LOG.info("Responding to stop request with allowed=true");
                sendStopResponse(message, true);
                cluster = null;
            }
        }
    }

    private void sendStopResponse(Map<String, String> request, boolean allow) {
        try {
            sendMessage(makeStopConfirmation(request, allow));
        } catch (IOException e) {
            LOG.error("Failed to send stop cluster response.", e);
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
