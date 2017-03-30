package water;

import water.util.Log;


/**
 * Extension used for checking failed nodes
 */
public class FailedNodeWatchdogExtension extends AbstractH2OExtension {
    private long watchdogClientRetryTimeout = 10000;
    private boolean watchDogClient = false;
    @Override
    public String getExtensionName() {
        return "Failed node watchdog";
    }

    @Override
    public void printHelp() {
        System.out.println(
                "\nFailed node watchdog extension:\n" +
                        "    -watchdog_client_retry_timeout\n" +
                        "          Time in milliseconds specifying in which intervals the failed nodes are checked. If not \n" +
                        "          specified, the default value of 10000 ms is used. \n" +
                        "    -watchdog_client\n" +
                        "          Same as the client except the that cluster is stopped when this client \n" +
                        "          disconnects from the rest of the cloud or the cloud is stopped when it doesn't \n" +
                        "          hear heartbeat from the client for specified amount of time."
        );
    }

    private String[] parseClient(String[] args){
        for (int i = 0; i < args.length; i++) {
            H2O.OptString s = new H2O.OptString(args[i]);
                if(s.matches("watchdog_client")){
                    watchDogClient = true; H2O.ARGS.client = true;
                    String[] new_args = new String[args.length - 1];
                    System.arraycopy(args, 0, new_args, 0, i);
                    System.arraycopy(args, i + 1, new_args, i, args.length - (i + 1));
                    return new_args;
            }
        }
        return args;
    }

    private String[] parseTimeout(String args[]){
        for (int i = 0; i < args.length; i++) {
            H2O.OptString s = new H2O.OptString(args[i]);
            if(s.matches("watchdog_client_retry_timeout")){
                watchdogClientRetryTimeout = s.parseInt(args[i + 1]);
                String[] new_args = new String[args.length - 2];
                System.arraycopy(args, 0, new_args, 0, i);
                System.arraycopy(args, i + 2, new_args, i, args.length - (i + 2));
                return new_args;
            }
        }
        return args;
    }
    @Override
    public String[] parseArguments(String[] args) {
        return parseClient(parseTimeout(args));
    }


    public void validateArguments() {
        if (watchdogClientRetryTimeout < 0) {
            H2O.parseFailed("Watchdog client retry timeout has to be positive: " + watchdogClientRetryTimeout);
        }
    }

    @Override
    public void onLocalNodeStarted() {
        new FailedNodeWatchdogThread().start();
        H2O.SELF._heartbeat._watchdog_client = watchDogClient;
    }

    /**
     * This method checks whether the client is disconnected from this node due to some problem such as client or network
     * is unreachable.
     */
    private static void handleClientDisconnect(H2ONode node, long watchdogClientRetryTimeout) {
        if(node._heartbeat._watchdog_client){
            Log.warn("Watchdog client " + node + " disconnected!");
            WatchdogClientDisconnectedTask tsk = new WatchdogClientDisconnectedTask(node, watchdogClientRetryTimeout);
            Log.warn("Asking the rest of the nodes in the cloud whether watchdog client is really gone.");
            if((tsk.doAllNodes()).clientDisconnectedConsensus) {
                Log.fatal("Stopping H2O cloud since the watchdog client is disconnected from all nodes in the cluster!");
                H2O.shutdown(0);
            }
        }else if(node._heartbeat._client) {
            Log.warn("Client "+ node +" disconnected!");
        }

        // in both cases remove the client since the timeout is out
        if(node._heartbeat._client){
            H2O.removeClient(node);
            if(H2O.isFlatfileEnabled()){
                H2O.removeNodeFromFlatfile(node);
            }
        }
    }

    /**
     * Helper MR task used to detect clientDisconnectedConsensus on the timeout we last heard from the watchdog client
     */
    private static class WatchdogClientDisconnectedTask extends MRTask<WatchdogClientDisconnectedTask> {
        private boolean clientDisconnectedConsensus = false;
        private H2ONode clientNode;
        private long watchdogClientRetryTimeout;
        WatchdogClientDisconnectedTask(H2ONode clientNode, long  watchdogClientRetryTimeout) {
            this.clientNode = clientNode;
            this.watchdogClientRetryTimeout = watchdogClientRetryTimeout;
        }

        @Override
        public void reduce(WatchdogClientDisconnectedTask mrt) {
            this.clientDisconnectedConsensus = this.clientDisconnectedConsensus && mrt.clientDisconnectedConsensus;
        }

        @Override
        protected void setupLocal() {
            final H2ONode foundClient = H2O.getClientsByKey().get(clientNode._key);

            if (foundClient == null || isTimeoutExceeded(foundClient, watchdogClientRetryTimeout )) {
                // Agree on the consensus if this node does not see the client at all or if this node sees the client
                // however the timeout is out
                clientDisconnectedConsensus = true;
            }
        }
    }

    private static boolean isTimeoutExceeded(H2ONode client, long timeout) {
        return (System.currentTimeMillis() - client._last_heard_from) >= timeout;
    }

    /**
     * Thread used to run disconnect hooks on nodes who disconnects from the cloud
     */
    private class FailedNodeWatchdogThread extends Thread {

        public FailedNodeWatchdogThread() {
            super("FailedNodeWatchdogThread");
            setDaemon(true);
        }

        @Override
        public void run() {
            while (true) {
                for(H2ONode client: H2O.getClients()){
                    if(isTimeoutExceeded(client, watchdogClientRetryTimeout)){
                        handleClientDisconnect(client, watchdogClientRetryTimeout);
                    }
                }

                try {
                    Thread.sleep(watchdogClientRetryTimeout);
                } catch (InterruptedException ignore) {}
            }
        }
    }

}
