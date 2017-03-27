package water;

import water.util.Log;

/**
 * Extension used for checking failed nodes
 */
public class FailedNodeWatchdogExtension extends AbstractH2OExtension {
    private long watchdogClientRetryTimeout = 6000;
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
                        "          specified, the default value of 6000 ms is used. \n" +
                        "    -watchdog_client\n" +
                        "          Same as the client except the that cluster is stopped when this client \n" +
                        "          disconnects from the rest of the cloud or the cloud is stopped when it doesn't \n" +
                        "          hear heartbeat from the client for specified amount of time."
        );
    }

    @Override
    public String[] parseArguments(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            H2O.OptString s = new H2O.OptString(args[i]);
            if (s.matches("watchdog_client_retry_timeout")) {
                watchdogClientRetryTimeout = s.parseInt(args[i + 1]);
                String[] new_args = new String[args.length - 2];
                System.arraycopy(args, 0, new_args, 0, i);
                System.arraycopy(args, i + 2, new_args, i, args.length - (i + 2));
                return new_args;
            }
            else if(s.matches("watchdog_client")){
                watchDogClient = true; H2O.ARGS.client = true;
                String[] new_args = new String[args.length - 1];
                System.arraycopy(args, 0, new_args, 0, i);
                System.arraycopy(args, i + 1, new_args, i, args.length - (i + 1));
                return new_args;
            }
        }
        return args;
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
    private static void handleClientDisconnect(H2ONode node) {
        if(node._heartbeat._watchdog_client){
            Log.warn("Watchdog client " + node + " disconnected!");
            BullyClientGoneTask tsk = new BullyClientGoneTask(node);
            Log.warn("Asking the rest of the nodes in the cloud whether watchdog client is really gone.");
            if(((BullyClientGoneTask)tsk.doAllNodes()).consensus) {
                Log.fatal("Stopping H2O cloud since the watchdog client is disconnected from all nodes in the cluster!");
                H2O.shutdown(0);
            }
        }else if(node._heartbeat._client) {
            Log.warn("Client "+ node +" disconnected!");
        }

        // in both cases remove the client
        if(node._heartbeat._client){
            H2O.removeClient(node);
            if(H2O.isFlatfileEnabled()){
                H2O.removeNodeFromFlatfile(node);
            }
        }
    }

    /**
     * Helper MR task used to detect consensus on the fact that bully client is disconnected from the network
     */
    private static class BullyClientGoneTask extends MRTask {
        public boolean consensus = true;
        private H2ONode clientNode;

        public BullyClientGoneTask(H2ONode clientNode) {
            this.clientNode = clientNode;
        }

        @Override
        public void reduce(MRTask mrt) {
            if(((BullyClientGoneTask)mrt).consensus) { // don't change the value in case negative consensus
                for(H2ONode node: H2O.getClients()){
                    // find the same client node on the other nodes
                    if(node.equals(clientNode) && node._connection_closed){
                        consensus = true;
                        break;
                    }
                }
            }
        }
    }

    /**
     * Thread used to run disconnect hooks on nodes who disconnects from the cloud
     */
    private static class FailedNodeWatchdogThread extends Thread {
        final private int sleepMillis = 6000; // 6 seconds

        public FailedNodeWatchdogThread() {
            super("FailedNodeWatchdogThread");
            setDaemon(true);
        }

        @Override
        public void run() {
            while (true) {

                // in multicast mode the the _connection_closed is not set on clients so we don't know which client
                // is still available and which not. We can check the clients to see if they are still available based on
                // _last_heard_from field
                if(!H2O.isFlatfileEnabled()){
                    for(H2ONode client: H2O.getClients()){
                        if((System.currentTimeMillis() - client._last_heard_from) >= HeartBeatThread.CLIENT_TIMEOUT){
                            client._connection_closed = true;
                        }
                    }
                }

                for(H2ONode node : H2O.getMembersAndClients()){
                    if(node._connection_closed){
                        handleClientDisconnect(node);
                    }
                }
                try {
                    Thread.sleep(sleepMillis);
                } catch (InterruptedException ignore) {}
            }
        }
    }

}
