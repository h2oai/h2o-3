package water;

import water.util.Log;

class ClientDisconnectCheckThread extends Thread {

  public ClientDisconnectCheckThread() {
    super("ClientDisconnectCheckThread");
    setDaemon(true);
  }

  private boolean isTimeoutExceeded(H2ONode client, long timeout) {
    return (System.currentTimeMillis() - client._last_heard_from) >= timeout;
  }

  /**
   * This method checks whether the client is disconnected from this node due to some problem such as client or network
   * is unreachable.
   */
  private void handleClientDisconnect(H2ONode node) {
    if(node != H2O.SELF) {
      Log.warn("Client " + node + " disconnected!");
      if (H2O.isFlatfileEnabled()) {
        H2O.removeNodeFromFlatfile(node);
      }
      H2O.removeClient(node);
    }
  }

  @Override
  public void run() {
    while (true) {
      for(H2ONode client: H2O.getClients()){
        if(isTimeoutExceeded(client, HeartBeatThread.CLIENT_TIMEOUT * 2)){
          handleClientDisconnect(client);
        }
      }
      try {
        Thread.sleep(HeartBeatThread.CLIENT_TIMEOUT * 2);
      } catch (InterruptedException ignore) {}
    }
  }
}