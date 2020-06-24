package hex.tree.xgboost.remote;

import hex.steam.SteamMessageSender;
import hex.steam.SteamMessenger;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.exec.RemoteXGBoostExecutor;
import org.apache.log4j.Logger;
import water.H2O;
import water.fvec.Frame;

import java.io.IOException;
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

    enum Status {STOPPED, STARTING, STARTED}

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
    
    private class ClusterUsageMonitor implements Runnable {
        
        private boolean shouldRun = true;
        private long terminateOn;
        private final int timeoutMs;

        private ClusterUsageMonitor(int timeoutSeconds) {
            this.timeoutMs = timeoutSeconds * 1000;
            terminateOn = System.currentTimeMillis() + this.timeoutMs;
        }

        synchronized void notifyUsed() {
            terminateOn = System.currentTimeMillis() + this.timeoutMs;
            this.notify();
        }

        @Override
        public synchronized void run() {
            while (shouldRun) {
                boolean shouldTerminate = terminateOn < System.currentTimeMillis();
                if (shouldTerminate) {
                    try {
                        stopCluster();
                        shouldRun = false;
                    } catch (IOException e) {
                        // cluster stopping failed, wait another timeout for Steam to reconnect
                        LOG.error("Failed to stop external cluster, will try again.", e);
                    }
                } else {
                    try {
                        this.wait(timeoutMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

    }

    private final Object sendingLock = new Object[0];
    private final Object clusterLock = new Object[0];

    private SteamMessageSender sender;
    private Status clusterStatus = Status.STOPPED;
    private ClusterInfo cluster;
    private ClusterUsageMonitor monitor;

    private final Map<String, Map<String, String>> receivedMessages = new HashMap<>();
    
    public SteamExecutorStarter() {
        instance = this;
    }

    public RemoteXGBoostExecutor getRemoteExecutor(XGBoostModel model) throws IOException {
        synchronized (clusterLock) {
            if (clusterStatus == Status.STARTING) {
                waitForClusterToStart();
            } else if (clusterStatus == Status.STOPPED) {
                startCluster();
            }
            monitor.notifyUsed();
            return makeExecutor(model);
        }
    }

    private void startCluster() throws IOException {
        clusterStatus = Status.STARTING;
        Map<String, String> startRequest = makeStartRequest();
        LOG.info("Requesting cluster start from Steam");
        sendMessage(startRequest);
        Map<String, String> response = waitForResponse(startRequest);
        if (response.get("status").equals("started")) {
            String remoteUri = response.get("uri");
            String userName = response.get("user");
            String password = response.get("password");
            int timeout = Integer.parseInt(response.get("timeout"));
            cluster = new ClusterInfo(remoteUri, userName, password);
            monitor = new ClusterUsageMonitor(timeout);
            new Thread(monitor, "XGBoostClusterMonitor").start();
            clusterStatus = Status.STARTED;
        } else {
            clusterStatus = Status.STOPPED;
            throw new IllegalStateException("Failed to start external cluster: " + response.get("reason"));
        }
    }

    private void waitForClusterToStart() {
        LOG.info("A cluster is already starting, waiting.");
        while (clusterStatus == Status.STARTING) {
            try {
                clusterLock.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (clusterStatus != Status.STARTED) {
            throw new IllegalStateException("External cluster did not start.");
        }
    }

    private RemoteXGBoostExecutor makeExecutor(XGBoostModel model) {
        return new RemoteXGBoostExecutor(model, cluster.uri, cluster.userName, cluster.password);
    }
    
    private void stopCluster() throws IOException {
        synchronized (clusterLock) {
            Map<String, String> stopRequest = makeStopRequest();
            sendMessage(stopRequest);
            clusterStatus = Status.STOPPED;
        }
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
        synchronized (receivedMessages) {
            receivedMessages.put(message.get(ID), message);
            receivedMessages.notifyAll();
        }
    }

    private Map<String, String> makeStartRequest() {
        Map<String, String> req = new HashMap<>();
        req.put(TYPE, "startXGBoostCluster");
        req.put(ID, H2O.SELF.getIpPortString() + "_startXGBoost");
        return req;
    }

    private Map<String, String> makeStopRequest() {
        Map<String, String> req = new HashMap<>();
        req.put(TYPE, "stopXGBoostCluster");
        req.put(ID, H2O.SELF.getIpPortString() + "_stopXGBoost");
        return req;
    }

}
