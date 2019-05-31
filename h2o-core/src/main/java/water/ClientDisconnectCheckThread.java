package water;

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
  static void handleClientDisconnect(H2ONode client) {
    if(client != H2O.SELF) {
      if (H2O.isFlatfileEnabled()) {
        H2O.removeNodeFromFlatfile(client);
      }
      H2O.removeClient(client);
    }
  }

  @Override
  public void run() {
    while (true) {
      for(H2ONode client: H2O.getClients()){
        if(isTimeoutExceeded(client, H2O.ARGS.clientDisconnectTimeout)){
          handleClientDisconnect(client);
        }
      }
      try {
        Thread.sleep(H2O.ARGS.clientDisconnectTimeout);
      } catch (InterruptedException ignore) {}
    }
  }
}
