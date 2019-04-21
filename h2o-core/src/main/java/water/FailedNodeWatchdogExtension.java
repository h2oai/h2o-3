package water;

import water.util.Log;

import java.util.HashSet;


/**
 * Extension used for checking failed nodes
 */
public class FailedNodeWatchdogExtension extends AbstractH2OExtension {
    private long watchdogClientRetryTimeout = 10000;
    private long watchdogClientConnectTimeout = 60000;
    private boolean watchDogClient = false;
    private boolean watchDogStopWithout = false;
    @Override
    public String getExtensionName() {
        return "Watchdog";
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
                        "          hear heartbeat from the client for specified amount of time. \n" +
                        "    -watchdog_client_connect_timeout\n" +
                        "         Time in milliseconds specifying how long to wait for watchdog client to\n" +
                        "         connect to the cluster before the cluster is stopped. \n" +
                        "         The default value of 10000 ms is used \n" +
                        "    -watchdog_stop_without_client\n" +
                        "         When set to true this property ensures that this cloud kills itself \n" +
                        "         when no watchdog client doesn't connect to the cluster for the specified timeout"

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

    private String[] parseClientStopWithout(String[] args){
        for (int i = 0; i < args.length; i++) {
            H2O.OptString s = new H2O.OptString(args[i]);
            if(s.matches("watchdog_stop_without_client")){
                watchDogStopWithout = true;
                String[] new_args = new String[args.length - 1];
                System.arraycopy(args, 0, new_args, 0, i);
                System.arraycopy(args, i + 1, new_args, i, args.length - (i + 1));
                return new_args;
            }
        }
        return args;
    }

    private String[] parseRetryTimeout(String args[]){
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

    private String[] parseConnectionTimeout(String args[]){
        for (int i = 0; i < args.length; i++) {
            H2O.OptString s = new H2O.OptString(args[i]);
            if(s.matches("watchdog_client_connect_timeout")){
                watchdogClientConnectTimeout = s.parseInt(args[i + 1]);
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
        return parseClient(parseClientStopWithout(parseRetryTimeout(parseConnectionTimeout(args))));
    }


    public void validateArguments() {
        if (watchdogClientRetryTimeout < 0) {
            H2O.parseFailed("Watchdog client retry timeout has to be positive: " + watchdogClientRetryTimeout);
        }
        if(watchdogClientConnectTimeout < 0) {
            H2O.parseFailed("Watchdog client connect timeout has to be positive: " + watchdogClientConnectTimeout);
        }
    }

    @Override
    public void onLocalNodeStarted() {
        if(watchDogStopWithout){
            new CheckWatchdogConnectedThread().start();
        }
        new FailedNodeWatchdogThread().start();
        H2O.SELF._heartbeat._watchdog_client = watchDogClient;
        // When running on External Backend, we need to set the client disconnect timeout
        // to the same value as the watchdogClientRetryTimeout

        // Failed Watchdog thread is responsible for checking whether the client has disappeared from all the nodes
        // in the cluster, however the ClientDisconnectCheckThread (which uses clientDisconnectTimeout) is responsible
        // for disconnecting single clients if the timeout has been reached. In case of external backend
        // we need to set the client disconnect timeout to the same value as the watchdogClientRetryTimeout
        // as we don't want to remove client from specific nodes earlier
        H2O.ARGS.clientDisconnectTimeout = watchdogClientRetryTimeout;
    }

    private class CheckWatchdogConnectedThread extends Thread {
        public CheckWatchdogConnectedThread() {
            super("CheckWatchdogConnectedThread");
        }

        @Override
        public void run() {
            try {
                sleep(watchdogClientConnectTimeout);
                boolean watchDogConnected = false;
                H2ONode[] clients = H2O.getClients();

                if (Log.getLogLevel() == Log.DEBUG) {
                    Log.debug("Checking if watchdog client connected to the cluster, available clients at this moment are: ");
                    for (H2ONode client : clients) {
                        Log.debug("Client: " + client.toDebugString());
                    }
                }
                for(H2ONode client: clients){
                    if(client._heartbeat._watchdog_client){
                        watchDogConnected = true;
                        break;
                    }
                }
                if(!watchDogConnected){
                    // in this case we expect the watchdog to connect, however it is still not available
                    // this is not a planned situation, exit with negative status
                    Log.fatal("Stopping H2O cloud since the watchdog client never connected");
                    H2O.shutdown(-1);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
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
            final H2ONode foundClient = H2O.getClientByIPPort(clientNode.getIpPortString());

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
                H2ONode[] clients = H2O.getClients();

                if (Log.getLogLevel() == Log.DEBUG) {
                    Log.debug("Checking if watchdog client is connected, available clients are: ");
                    for (H2ONode client : clients) {
                        Log.debug("Client: " + client.toDebugString());
                    }
                }
                for (H2ONode client : clients) {
                    if(isTimeoutExceeded(client, watchdogClientRetryTimeout)){
                        // if timeout exceeded, check if the client is disconnected
                        // from other nodes as well and if it is, shutdown the cluster
                        if(client._heartbeat._watchdog_client){
                            Log.warn("Watchdog client " + client + " disconnected!");
                            WatchdogClientDisconnectedTask tsk = new WatchdogClientDisconnectedTask(client, watchdogClientRetryTimeout);
                            Log.warn("Asking the rest of the nodes in the cloud whether watchdog client is really gone.");
                            if((tsk.doAllNodes()).clientDisconnectedConsensus) {
                                Log.fatal("Stopping H2O cloud since the watchdog client is disconnected from all nodes in the cluster!");
                                // we should fail with negative status as this is not planned shutdown
                                H2O.shutdown(-1);
                            }
                        }
                    }
                }

                try {
                    Thread.sleep(watchdogClientRetryTimeout);
                } catch (InterruptedException ignore) {}
            }
        }
    }

}
