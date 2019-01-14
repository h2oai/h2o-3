package water;

import water.util.Log;

import java.util.Random;

// Emulates Random Client disconnects
public class ClientRandomDisconnectThread extends Thread {

  public ClientRandomDisconnectThread() {
    super("ClientRandomDisconnectThread");
    setDaemon(true);
  }

  @Override
  public void run() {
    Log.warn("-----------------------------------------------------------");
    Log.warn("| Random Client Disconnect Attack - for development only! |");
    Log.warn("-----------------------------------------------------------");

    try {
      Thread.sleep(H2O.ARGS.clientDisconnectTimeout);
    } catch (InterruptedException ignore) {}

    Random r = new Random();
    while (true) {
      final int timeout = r.nextInt((int) H2O.ARGS.clientDisconnectTimeout / 10);
      Log.warn("Random Attack: Clients will get killed in " + timeout + "ms.");
      try {
        Thread.sleep(timeout);
      } catch (InterruptedException ignore) {}
      for (H2ONode client: H2O.getClients()) {
        if (client != H2O.SELF) {
          Log.warn("Random Attack: Emulating client disconnect: " + client._key);
          ClientDisconnectCheckThread.handleClientDisconnect(client);
        }
      }
    }
  }

}
