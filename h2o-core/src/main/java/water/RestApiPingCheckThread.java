package water;

import water.api.PingHandler;
import water.util.Log;

public class RestApiPingCheckThread extends Thread {
  
  public RestApiPingCheckThread() {
    this.setDaemon(true);
  }

  @Override
  public void run() {
    while (!Thread.currentThread().isInterrupted()) {
      if (H2O.CLOUD._memary.length != 0 && H2O.SELF == H2O.CLOUD.leader()) {
        if (isTimeoutExceeded(PingHandler.lastAccessed, H2O.ARGS.rest_api_ping_timeout)) {
          Log.fatal("Stopping H2O cluster since we haven't received any REST api request on 3/Ping!");
          H2O.shutdown(-1);
        }
      } else if (H2O.CLOUD._memary.length != 0 && Paxos._cloudLocked) {
        // Cloud is locked, but we are not leader, we can stop the thread
        Thread.currentThread().interrupt();
      }
      try {
        Thread.sleep(H2O.ARGS.rest_api_ping_timeout);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
  
  private static boolean isTimeoutExceeded(long lastHeardFrom, long timeout) {
    return (System.currentTimeMillis() - lastHeardFrom) >= timeout;
  }
}
    
