package water;

import water.util.Log;

/**
 * This thread checks whether the heartbeat from the bully client has been seen before the timeout and if not, it kills
 * the whole h2o cloud
 */
public class ClientHeartBeatCheckThread extends Thread {
    public ClientHeartBeatCheckThread() {
        super("ClientHeartbeatCheckThread");
        setDaemon(true);
    }

    @Override
    public void run(){
        while(true) {
            if(H2O.SELF._last_heard_from_bully_client != 0){
                // we need to make sure that rest of the nodes agree with our decision that the bully client is not
                // available anymore and the cloud should be stooped.
                if(H2O.SELF._last_heard_from_bully_client + H2O.ARGS.bully_client_timeout >= System.currentTimeMillis()) {
                    Log.warn("Bully client seems not to be available!");
                    BullyClientCheckMRTask tsk = new BullyClientCheckMRTask();
                    Log.warn("Asking the rest of the nodes if bully client is really gone.");
                    if(((BullyClientCheckMRTask)tsk.doAllNodes()).consensus) {
                        // we suspect that the client is gone, but we need to check with the rest of the nodes in the cluster
                        // if all nodes in the cluster agree on the fact that the client is not a available, stop the cluster
                        Log.fatal("Stopping H2O cloud since bully client hasn't seen heartbeat in specified timeout");
                        H2O.shutdown(0);
                    }
                }
            }
            // wait for the duration of timeout
            try {
                sleep(H2O.ARGS.bully_client_timeout);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static class BullyClientCheckMRTask extends MRTask {
        public boolean consensus = true;

        @Override
        public void reduce(MRTask mrt) {
            if(((BullyClientCheckMRTask)mrt).consensus) { // don't change the value in case negative consensus
                consensus = H2O.SELF._last_heard_from_bully_client + H2O.ARGS.bully_client_timeout >= System.currentTimeMillis();
            }
        }
    }
}
