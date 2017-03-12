package water;

import water.util.Log;

/**
 * Interface used to for hooks representing disconnection of h2o node
 */
public interface NodeDisconnectHook {
    void handleNodeDisconnect(H2ONode node);

    /**
     * A hook which is triggered when client disconnects from the network due to some problem such as client or network
     * is unreachable.
     */
    class ClientDisconnectedHook implements NodeDisconnectHook{
        @Override
        public void handleNodeDisconnect(H2ONode node) {
            if(node._heartbeat._bully_client){
                Log.warn("Bully client " + node + " disconnected!");
                BullyClientGoneTask tsk = new BullyClientGoneTask(node);
                Log.warn("Asking the rest of the nodes in the cloud if bully client is really gone.");
                if(((BullyClientGoneTask)tsk.doAllNodes()).consensus) {
                    Log.fatal("Stopping H2O cloud since bully client is disconnected from all nodes in the cluster!");
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

        // Helper MR task used to detect consensus on the fact that bully client is disconnected from the network
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
    }

}
